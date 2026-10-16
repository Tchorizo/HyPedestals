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
import dev.tchorizo.hypedestals.harvest.CycleResult;
import dev.tchorizo.hypedestals.harvest.HarvestArea;
import org.joml.Vector3i;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /hypedestals inspect <x y z>}: one line with the harvester's settings, timer, scan
 * position, container usage, last cycle result, installed upgrades and watering sweeps,
 * {@code no Harvester at <x y z>}, or {@code section not loaded at <x y z>} when the section was
 * never generated (inspecting never generates terrain).
 * Loads the section from disk if it exists (never generating it) and marks it as ticking.
 */
final class InspectCommand extends HyPedestalsCommand.PositionCommand {

    InspectCommand() {
        super("inspect", "Show the state of the Harvester at a position");
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
        return String.format(Locale.ROOT, "tier=%d radius=%d interval=%.1fs next=%.1fs scan=%d/%d slots=%s last: %s upgrades=%s soil: %s",
                harvester.tier(), harvester.radius(), harvester.intervalSeconds(), harvester.secondsToNextCycle(),
                harvester.scanIndex(), HarvestArea.size(harvester.radius()),
                containerBlock == null ? "missing" : slots(containerBlock.getItemContainer()),
                last == null ? "none" : String.format(Locale.ROOT, "harvested=%d collected=%d paused=%b exported=%d",
                        last.harvested(), last.collected(), last.pausedFull(), harvester.lastExport().moved()),
                harvester.upgrades(), soil(harvester));
    }

    /**
     * {@code watered=<last sweep> total=<since load> lastSweep=<seconds ago>s}, or {@code none}
     * before the first watering sweep since the load. A sweep rewaters only soil with less than
     * half the tier's duration left, so after the first sweep {@code watered} is usually 0.
     */
    private static String soil(HarvesterComponent harvester) {
        return harvester.lastWatered() < 0 ? "none" : String.format(Locale.ROOT, "watered=%d total=%d lastSweep=%.1fs",
                harvester.lastWatered(), harvester.wateredTotal(), harvester.secondsSinceSoilSweep());
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
