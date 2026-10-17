package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import dev.tchorizo.hypedestals.block.HarvesterComponent;
import dev.tchorizo.hypedestals.block.HytaleFertilizerInput;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.CycleResult;
import dev.tchorizo.hypedestals.harvest.ExportPlan;
import dev.tchorizo.hypedestals.harvest.FertilizerInput;
import dev.tchorizo.hypedestals.harvest.HarvestArea;
import dev.tchorizo.hypedestals.harvest.SoilPass;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import org.joml.Vector3i;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /hypedestals inspect <x y z>}: one line with the harvester's settings, timer, scan
 * position, container usage, last cycle result, installed upgrades, soil sweeps and (with the
 * fertilizer upgrade) the next fertilizer bag,
 * {@code no Harvester at <x y z>}, or {@code section not loaded at <x y z>} when the section was
 * never generated (inspecting never generates terrain).
 * Loads the section from disk if it exists (never generating it) and marks it as ticking.
 */
final class InspectCommand extends HyPedestalsCommand.PositionCommand {

    InspectCommand() {
        super("inspect", "Show the state of the Harvester at a position");
        // Operator-only: reads the state of any block, including other players' Harvesters.
        requirePermission(HyPedestalsCommand.PERMISSION + ".inspect");
    }

    /** Inspecting must never generate terrain; a never-generated section is reported instead. */
    @Override
    int loadFlags() {
        return super.loadFlags() | GetChunkFlags.NO_GENERATE;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        Vector3i pos = position(context);
        return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", describe(world, pos)), pos);
    }

    private static String describe(World world, Vector3i pos) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        Store<ChunkStore> store = world.getChunkStore().getStore();
        HarvesterComponent harvester = ref == null ? null : store.getComponent(ref, HarvesterComponent.TYPE);
        if (harvester == null) {
            return "no Harvester at " + HyPedestalsCommand.format(pos);
        }
        ItemContainerBlock containerBlock = store.getComponent(ref, ItemContainerBlock.getComponentType());
        CycleResult last = harvester.lastResult();
        return String.format(Locale.ROOT, "tier=%d radius=%d interval=%.1fs next=%.1fs scan=%d/%d slots=%s last: %s burst=%.1fs upgrades=%s soil: %s%s",
                harvester.tier(), harvester.radius(), harvester.intervalSeconds(), harvester.secondsToNextCycle(),
                harvester.scanIndex(), HarvestArea.size(harvester.radius()),
                containerBlock == null ? "missing" : slots(containerBlock.getItemContainer()),
                last == null ? "none" : String.format(Locale.ROOT, "harvested=%d collected=%d paused=%b exported=%s",
                        last.harvested(), last.collected(), last.pausedFull(), export(harvester.lastExport())),
                harvester.burstSecondsLeft(), harvester.upgrades(), soil(harvester),
                harvester.hasUpgrade(UpgradeType.FERTILIZER) ? " fertilizer=" + fertilizer(containerBlock) : "");
    }

    /** {@code <moved>→<x y z>} of the last container that received items, or {@code <moved>→none}. */
    private static String export(ExportPlan.Result result) {
        BlockPos t = result.target();
        return result.moved() + "→" + (t == null ? "none" : t.x() + " " + t.y() + " " + t.z());
    }

    /**
     * {@code watered=<last sweep> total=<since load> fertilized=<last sweep> fertilizedTotal=<since load>
     * lastSweep=<seconds ago>s}, or {@code none} before the first soil sweep since the load. A sweep
     * rewaters only soil with less than half the tier's duration left and fertilizes only soil not
     * fertilized yet, so after the first sweeps both counts are usually 0.
     */
    private static String soil(HarvesterComponent harvester) {
        SoilPass.Result last = harvester.lastSoil();
        return last == null ? "none" : String.format(Locale.ROOT,
                "watered=%d total=%d fertilized=%d fertilizedTotal=%d lastSweep=%.1fs",
                last.watered(), harvester.wateredTotal(), last.fertilized(), harvester.fertilizedTotal(),
                harvester.secondsSinceSoilSweep());
    }

    /**
     * The bag the next soil sweep uses (any slot; the most worn one first, see
     * {@link FertilizerInput}): {@code <durability>/<max> (slot N)} (with {@code xN} after the
     * durability for a stack of N bags), or {@code none} when no slot holds a usable bag.
     */
    private static String fertilizer(ItemContainerBlock containerBlock) {
        List<FertilizerInput.InputStack> slots = containerBlock == null
                ? List.of() : HytaleFertilizerInput.read(containerBlock.getItemContainer());
        int slot = FertilizerInput.nextSlot(slots);
        if (slot < 0) {
            return "none";
        }
        FertilizerInput.InputStack stack = slots.get(slot);
        return number(stack.durability()) + "/" + number(stack.maxDurability())
                + (stack.quantity() > 1 ? "x" + stack.quantity() : "") + " (slot " + slot + ")";
    }

    private static String number(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    /** {@code <used>/<capacity>}, counting slots that hold a non-empty stack. */
    private static String slots(ItemContainer container) {
        short capacity = container.getCapacity();
        int used = 0;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty()) {
                used++;
            }
        }
        return used + "/" + capacity;
    }
}
