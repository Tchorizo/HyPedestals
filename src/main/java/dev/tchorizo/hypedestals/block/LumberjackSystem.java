package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.config.LumberSettings;
import dev.tchorizo.hypedestals.config.ModSettings;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.CycleStagger;
import dev.tchorizo.hypedestals.harvest.CycleStats;
import dev.tchorizo.hypedestals.harvest.ExportPlan;
import dev.tchorizo.hypedestals.lumber.LumberCycle;
import dev.tchorizo.hypedestals.lumber.LumberResult;
import dev.tchorizo.hypedestals.lumber.TreeLimits;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Runs one {@link LumberCycle} per Lumberjack block every {@code Lumberjack.<Tier>.IntervalSeconds}
 * (Lumberjack spec §2): cut the first whole tree standing in the area, store its drops, fill the
 * stump with soil and plant a sapling back; then, with {@code ExportToAdjacent}, export into a
 * container touching the block, keeping up to {@code PlanterSeedStock} saplings of each kind.
 *
 * <p>The tick only runs the timer; the cycle itself is queued with {@code world.execute} and runs
 * on the world thread after the systems: it writes the chunk store directly (removing blocks,
 * planting a sapling adds its {@code FarmingBlock} entity), which the store refuses while a system
 * is processing. One cycle at a time per Lumberjack. The queued cycle reads the block entity
 * again, so a Lumberjack broken in between does nothing; two Lumberjacks on the same tree run one
 * after the other and the second finds it gone.
 *
 * <p>Each tick and each cycle are timed with {@code System.nanoTime()} into the Lumberjack's
 * {@link CycleStats} ({@code inspect}) and {@link PerfRegistry} ({@code perf}, as
 * {@code Lumberjack <Tier>}); the cycle's {@code scan} phase covers the area scan, the tree
 * search and the cut.
 */
public final class LumberjackSystem extends EntityTickingSystem<ChunkStore> {

    /** Vanilla burst also used by the Harvester: a puff on top of the pedestal when a tree is cut. */
    private static final String CUT_PARTICLES = "Seed_Place_Dust";

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(LumberjackComponent.TYPE, BlockModule.BlockStateInfo.getComponentType());
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        long start = System.nanoTime();
        LumberjackComponent lumberjack = chunk.getComponent(index, LumberjackComponent.TYPE);
        try {
            run(dt, lumberjack, chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()), store, cb);
        } finally {
            PerfRegistry.global().tick(PerfRegistry.Machine.LUMBERJACK, lumberjack.tier, micros(start));
        }
    }

    private static void run(float dt, LumberjackComponent lumberjack, BlockModule.BlockStateInfo info,
                            Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        LumberSettings.Lumber settings = lumberjack.settings();
        if (settings.radius() == 0 || settings.intervalSeconds() == 0) {
            if (!lumberjack.inactiveWarned) {
                lumberjack.inactiveWarned = true;
                Vector3i at = new Vector3i();
                HyPedestalsPlugin.warn("Lumberjack tier %d at %s inactive: tier not in the config and invalid Radius/IntervalSeconds in asset",
                        lumberjack.tier, info.fillWorldPos(cb, at) ? at : "?");
            }
            return;
        }
        if (lumberjack.center == null) {
            Vector3i at = new Vector3i();
            if (!info.fillWorldPos(cb, at)) {
                return;
            }
            lumberjack.center = new BlockPos(at.x, at.y, at.z);
            lumberjack.elapsed = CycleStagger.initialElapsed(lumberjack.center, settings.intervalSeconds());
        }
        if (lumberjack.queued) {
            return;
        }
        lumberjack.elapsed += dt;
        if (lumberjack.elapsed < settings.intervalSeconds()) {
            return;
        }
        lumberjack.elapsed = 0;
        World world = store.getExternalData().getWorld();
        lumberjack.queued = true;
        BlockPos center = lumberjack.center;
        world.execute(() -> {
            try {
                cycle(world, lumberjack, center);
            } catch (RuntimeException e) {
                HyPedestalsPlugin.warn("Lumberjack at %s: cycle failed: %s", center, e.toString());
            } finally {
                lumberjack.queued = false;
            }
        });
    }

    /** One cycle, on the world thread outside the systems. */
    private static void cycle(World world, LumberjackComponent lumberjack, BlockPos center) {
        long cycleStart = System.nanoTime();
        Store<ChunkStore> store = world.getChunkStore().getStore();
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, center.x(), center.y(), center.z());
        if (ref == null || !ref.isValid() || store.getComponent(ref, LumberjackComponent.TYPE) != lumberjack) {
            return; // broken or unloaded since the tick that queued this cycle
        }
        BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        ItemContainerBlock containerBlock = store.getComponent(ref, ItemContainerBlock.getComponentType());
        if (info == null || containerBlock == null || containerBlock.getItemContainer() == null) {
            if (!lumberjack.missingContainerWarned) {
                lumberjack.missingContainerWarned = true;
                HyPedestalsPlugin.warn("Lumberjack at %s has no ItemContainerBlock; inactive", center);
            }
            return;
        }
        ModSettings global = HyPedestalsPlugin.settings();
        LumberSettings.Lumber settings = lumberjack.settings();

        LumberResult result = LumberCycle.run(new HytaleLumberWorld(world, containerBlock.getItemContainer(), info),
                center, settings.radius(), TreeLimits.of(global.lumber().maxTreeBlocks()), lumberjack.scanIndex);
        phase(lumberjack, CycleStats.Phase.SCAN, micros(cycleStart));
        lumberjack.scanIndex = result.nextIndex();
        lumberjack.lastResult = result;
        lumberjack.treesTotal += result.trees();
        lumberjack.lastCycleTick = world.getTick();

        if (global.harvestParticles() && result.trees() > 0) {
            ParticleUtil.spawnParticleEffect(CUT_PARTICLES,
                    new Vector3d(center.x() + 0.5, center.y(), center.z() + 0.5), world.getEntityStore().getStore());
        }
        if (global.exportToAdjacent()) {
            long exportStart = System.nanoTime();
            lumberjack.lastExport = new HytaleExport(world, store).run(containerBlock.getItemContainer(), info, center,
                    false, global.manurePerApplication(), global.manureStock(), HytaleExport.SAPLINGS, global.planterSeedStock());
            phase(lumberjack, CycleStats.Phase.EXPORT, micros(exportStart));
        } else {
            lumberjack.lastExport = ExportPlan.Result.NONE;
        }

        long cycleMicros = micros(cycleStart);
        lumberjack.stats.addCycle(cycleMicros);
        PerfRegistry.global().tick(PerfRegistry.Machine.LUMBERJACK, lumberjack.tier, cycleMicros);
        PerfRegistry.global().cycle(PerfRegistry.Machine.LUMBERJACK, lumberjack.tier,
                world.getName() + " " + center.x() + " " + center.y() + " " + center.z(),
                cycleMicros, result.trees(), lumberjack.stats);
    }

    private static long micros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1000;
    }

    private static void phase(LumberjackComponent lumberjack, CycleStats.Phase phase, long micros) {
        PerfRegistry.global().phase(PerfRegistry.Machine.LUMBERJACK, lumberjack.tier, phase, micros);
    }
}
