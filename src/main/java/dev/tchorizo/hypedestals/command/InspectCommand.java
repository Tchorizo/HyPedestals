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
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.block.HarvesterComponent;
import dev.tchorizo.hypedestals.block.HytaleFertilizerInput;
import dev.tchorizo.hypedestals.block.HytalePlantables;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.block.LumberjackComponent;
import dev.tchorizo.hypedestals.config.LumberSettings;
import dev.tchorizo.hypedestals.lumber.LumberResult;
import dev.tchorizo.hypedestals.harvest.CycleResult;
import dev.tchorizo.hypedestals.harvest.CycleStats;
import dev.tchorizo.hypedestals.harvest.ExportPlan;
import dev.tchorizo.hypedestals.harvest.FertilizerInput;
import dev.tchorizo.hypedestals.harvest.HarvestArea;
import dev.tchorizo.hypedestals.harvest.PlantPass;
import dev.tchorizo.hypedestals.harvest.SoilPass;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import org.joml.Vector3i;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /hypedestals inspect <x y z>}: one line with the harvester's settings, timer, scan
 * position, container usage, last cycle result, installed upgrades, soil sweeps, (with the
 * fertilizer upgrade) the fuel left and the cycle timings since the load
 * ({@code perf: last=<us> p50=<us> p95=<us> max=<us> (n=<cycles>)}; percentiles over the last
 * {@link CycleStats#WINDOW} cycles),
 * the Lumberjack's line ({@link #describe(LumberjackComponent, ItemContainerBlock)}),
 * {@code no Harvester or Lumberjack at <x y z>}, or {@code section not loaded at <x y z>} when the section was
 * never generated (inspecting never generates terrain).
 * Loads the section from disk if it exists (never generating it) and marks it as ticking.
 */
final class InspectCommand extends HyPedestalsCommand.PositionCommand {

    InspectCommand() {
        super("inspect", "Show the state of the Harvester or Lumberjack at a position");
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
            LumberjackComponent lumberjack = ref == null ? null : store.getComponent(ref, LumberjackComponent.TYPE);
            return lumberjack == null ? "no Harvester or Lumberjack at " + HyPedestalsCommand.format(pos)
                    : describe(lumberjack, store.getComponent(ref, ItemContainerBlock.getComponentType()));
        }
        ItemContainerBlock containerBlock = store.getComponent(ref, ItemContainerBlock.getComponentType());
        CycleResult last = harvester.lastResult();
        CycleStats stats = harvester.stats();
        return String.format(Locale.ROOT, "tier=%d radius=%d interval=%.1fs next=%.1fs scan=%d/%d slots=%s last: %s burst=%.1fs revived=%d upgrades=%s soil: %s%s%s"
                        + " perf: last=%dus p50=%dus p95=%dus max=%dus (n=%d)",
                harvester.tier(), harvester.radius(), harvester.intervalSeconds(), harvester.secondsToNextCycle(),
                harvester.scanIndex(), HarvestArea.size(harvester.radius()),
                containerBlock == null ? "missing" : slots(containerBlock.getItemContainer()),
                last == null ? "none" : String.format(Locale.ROOT, "harvested=%d collected=%d containerFull=%s exported=%s",
                        last.harvested(), last.collected(), full(last), export(harvester.lastExport())),
                harvester.burstSecondsLeft(), harvester.revivedTotal(), harvester.upgrades(), soil(harvester),
                harvester.hasUpgrade(UpgradeType.FERTILIZER) ? " fertilizer=" + fertilizer(containerBlock) : "",
                harvester.hasUpgrade(UpgradeType.PLANTER) ? " planter: " + planter(harvester, containerBlock) : "",
                stats.lastCycleMicros(), stats.p50(), stats.p95(), stats.max(), stats.cycles());
    }

    /**
     * The Lumberjack's line: {@code lumberjack tier= radius= interval= next= scan= slots= saplings=}
     * then the last cycle ({@code cut=<blocks> trees=<0|1> items=<n> replant=<planted|no-sapling|no-room|none>
     * [sapling=<id>] skipped growing=<n> tooBig=<n> notTree=<n> unloaded=<n> budgetHit=<bool> containerFull=<bool> exported=<n>→<x y z>},
     * or {@code none} before the first cycle since the load), {@code treesTotal=}, {@code upgrades=[…]}
     * and, with the Planter, {@code planter: planted= total= spots=} ({@link #planter(LumberjackComponent)}),
     * then the cycle timings.
     */
    private static String describe(LumberjackComponent lumberjack, ItemContainerBlock containerBlock) {
        LumberSettings.Lumber settings = lumberjack.settings();
        LumberResult last = lumberjack.lastResult();
        CycleStats stats = lumberjack.stats();
        int saplings = 0;
        if (containerBlock != null) {
            ItemContainer container = containerBlock.getItemContainer();
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!ItemStack.isEmpty(stack) && HytalePlantables.saplingBlock(stack.getItemId()) != null) {
                    saplings += stack.getQuantity();
                }
            }
        }
        return String.format(Locale.ROOT, "lumberjack tier=%d radius=%d interval=%.1fs next=%.1fs scan=%d/%d slots=%s saplings=%d"
                        + " last: %s treesTotal=%d upgrades=%s%s perf: last=%dus p50=%dus p95=%dus max=%dus (n=%d)",
                lumberjack.tier(), settings.radius(), settings.intervalSeconds(), lumberjack.secondsToNextCycle(),
                lumberjack.scanIndex(), HarvestArea.size(settings.radius()),
                containerBlock == null ? "missing" : slots(containerBlock.getItemContainer()), saplings,
                last == null ? "none" : String.format(Locale.ROOT,
                        "cut=%d trees=%d items=%d replant=%s%s skipped growing=%d tooBig=%d notTree=%d unloaded=%d budgetHit=%b containerFull=%s exported=%s",
                        last.blocks(), last.trees(), last.items(), last.replant().label(),
                        last.sapling() == null ? "" : " sapling=" + last.sapling(),
                        last.growing(), last.tooBig(), last.notTree(), last.unloaded(), last.budgetHit(),
                        last.containerFull() ? "true (trees waiting)" : "false", export(lumberjack.lastExport())),
                lumberjack.treesTotal(), lumberjack.upgrades(),
                lumberjack.hasUpgrade(UpgradeType.PLANTER) ? " planter: " + planter(lumberjack) : "",
                stats.lastCycleMicros(), stats.p50(), stats.p95(), stats.max(), stats.cycles());
    }

    /**
     * The Lumberjack's Planter: {@code planted=<n> total=<n> spots=<n>}, the saplings the grid
     * planted in the last cycle, since the load, and the grid points that could take one in the
     * last cycle ({@code spots=0}: the grid has no free point in the area, e.g. a Copper at the
     * default spacing 4, or {@code Lumberjack.SaplingSpacing} is 0); {@code none} before the first
     * cycle since the load. The stump's own replant is {@code replant=} in the last-cycle part.
     */
    private static String planter(LumberjackComponent lumberjack) {
        LumberResult last = lumberjack.lastResult();
        return last == null ? "none" : String.format(Locale.ROOT, "planted=%d total=%d spots=%d",
                last.gridPlanted(), lumberjack.gridPlantedTotal(), last.gridSpots());
    }

    /**
     * {@code planted=<seeds>+<saplings> total=<seeds>+<saplings> seeds=<n>}: the last planting pass,
     * the totals since the load and the seeds and saplings in the container; {@code none} instead
     * of the first two before the first pass since the load.
     */
    private static String planter(HarvesterComponent harvester, ItemContainerBlock containerBlock) {
        int seeds = 0;
        if (containerBlock != null) {
            ItemContainer container = containerBlock.getItemContainer();
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!ItemStack.isEmpty(stack) && HytalePlantables.isPlantable(stack.getItemId())) {
                    seeds += stack.getQuantity();
                }
            }
        }
        PlantPass.Result last = harvester.lastPlant();
        return (last == null ? "none" : String.format(Locale.ROOT, "planted=%d+%d total=%d+%d", last.seeds(),
                last.saplings(), harvester.seedsPlantedTotal(), harvester.saplingsPlantedTotal())) + " seeds=" + seeds;
    }

    /**
     * {@code true (crops waiting)} when the cycle stopped at a crop whose drops did not fit (it and
     * the rest of the area stay ripe until room frees up, and the next cycle starts there), plain
     * {@code true|false} otherwise; then {@code unloaded=<n>} when the scan met positions in a chunk
     * section that was not in memory, or in memory but not ticking (its block entities parked), which
     * are skipped this cycle.
     */
    private static String full(CycleResult last) {
        String full = last.cropsWaiting() ? "true (crops waiting)" : Boolean.toString(last.containerFull());
        return last.unloaded() > 0 ? full + " unloaded=" + last.unloaded() : full;
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
     * The fuel the soil sweeps can still use (any slot, see {@link FertilizerInput}):
     * {@code poop <n> (K=<k>) + bags <uses>}, with {@code n} the poop in the container, {@code k}
     * the config's {@code ManurePerApplication} (poop per application, 0 = poop off) and
     * {@code uses} the applications left in the bags; or {@code none} when there is neither.
     */
    private static String fertilizer(ItemContainerBlock containerBlock) {
        List<FertilizerInput.InputStack> slots = containerBlock == null
                ? List.of() : HytaleFertilizerInput.read(containerBlock.getItemContainer());
        int poop = FertilizerInput.poop(slots);
        int bags = FertilizerInput.bagApplications(slots);
        if (poop == 0 && bags == 0) {
            return "none";
        }
        return "poop " + poop + " (K=" + HyPedestalsPlugin.settings().manurePerApplication() + ") + bags " + bags;
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
