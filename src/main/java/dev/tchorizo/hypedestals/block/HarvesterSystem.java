package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.config.ModSettings;
import dev.tchorizo.hypedestals.config.TierSettings;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.CollectBurst;
import dev.tchorizo.hypedestals.harvest.CycleStats;
import dev.tchorizo.hypedestals.harvest.ExportPlan;
import dev.tchorizo.hypedestals.harvest.FertilizerInput;
import dev.tchorizo.hypedestals.harvest.HarvestAreas;
import dev.tchorizo.hypedestals.harvest.HarvestCycle;
import dev.tchorizo.hypedestals.harvest.SoilPass;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import dev.tchorizo.hypedestals.harvest.UpgradeVisual;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs one {@link HarvestCycle} per harvester block every {@code IntervalSeconds}, then, with
 * {@code ExportToAdjacent}, an {@link ExportPlan} into the containers touching the block; and,
 * every {@code SoilSweepSeconds} on a timer of its own, a {@link SoilPass} over the same area
 * when the watering or the fertilizer upgrade is installed. With the fertilizer upgrade, the pass
 * takes its applications from the fuel in the container, whatever its slot: poop first
 * ({@code ManurePerApplication} per application), then fertilizer bags
 * ({@link HytaleFertilizerInput}), and the export leaves that fuel in place. Each cycle also
 * re-asserts the block state that shows the upgrades ({@link HarvesterVisual}); with
 * {@code UpgradeParticlesSeconds} above 0 the same effects are also spawned as world particles
 * on that period ({@link UpgradeParticles}); and, with {@code HarvestParticles}, it puffs a short particle burst when
 * something was harvested. After a cycle that harvested, a {@link CollectBurst} collects drops on
 * every tick for {@code PostHarvestCollectSeconds}. Radius, interval, margin, item filter, export,
 * the burst and the soil settings are read from the global config on every tick, so changes made
 * with {@code /hypedestals config} (command or window) apply right away; hand edits to the config
 * file are read at server start only.
 *
 * <p>Every tick of every Harvester is timed with {@code System.nanoTime()} (two calls per phase,
 * tens of nanoseconds each): the cycles go to the Harvester's {@link CycleStats} ({@code inspect})
 * and, with the phases and the whole tick, to {@link PerfRegistry} ({@code /hypedestals perf}).
 */
public final class HarvesterSystem extends EntityTickingSystem<ChunkStore> {

    /** Vanilla {@code Server/Particles/Item/Seeds/Seed_Place_Dust.particlesystem}: one burst, then done. */
    private static final String HARVEST_PARTICLES = "Seed_Place_Dust";
    /** World name → the active Harvester areas of that world; each one is only used by its world's thread. */
    private static final Map<String, HarvestAreas> AREAS = new ConcurrentHashMap<>();

    /** The active Harvester areas of {@code world} ({@link HarvestAreas}). */
    static HarvestAreas areas(World world) {
        return AREAS.computeIfAbsent(world.getName(), name -> new HarvestAreas());
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(HarvesterComponent.TYPE, BlockModule.BlockStateInfo.getComponentType());
    }

    /** Harvesting mutates the world and the entity store; keep it on the world thread, sequentially. */
    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        long start = System.nanoTime();
        HarvesterComponent harvester = chunk.getComponent(index, HarvesterComponent.TYPE);
        try {
            run(dt, harvester, index, chunk, store, cb);
        } finally {
            PerfRegistry.global().tick(harvester.tier, micros(start));
        }
    }

    private static void run(float dt, HarvesterComponent harvester, int index, ArchetypeChunk<ChunkStore> chunk,
                            Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        BlockModule.BlockStateInfo info = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());

        if (!harvester.ignoredUpgradesWarned && !harvester.ignoredUpgrades.isEmpty()) {
            harvester.ignoredUpgradesWarned = true;
            Vector3i at = new Vector3i();
            HyPedestalsPlugin.warn("Harvester at %s: ignored unknown or repeated upgrades %s; they are dropped on the next save",
                    info.fillWorldPos(cb, at) ? at : "?", harvester.ignoredUpgrades);
        }

        ItemContainerBlock containerBlock = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        SimpleItemContainer container = containerBlock == null ? null : containerBlock.getItemContainer();

        ModSettings global = HyPedestalsPlugin.settings();
        TierSettings settings = global.effective(harvester.tier, harvester.radius, harvester.intervalSeconds);
        // Config values are always valid; only the asset fallback (a tier the config does not
        // know) can be 0 here, from the component sanitizers. Checked before the timer because a
        // 0 interval would otherwise fire every tick.
        if (settings.radius() == 0 || settings.intervalSeconds() == 0) {
            if (!harvester.inactiveWarned) {
                harvester.inactiveWarned = true;
                Vector3i pos = new Vector3i();
                HyPedestalsPlugin.warn("Harvester tier %d at %s inactive: tier not in the config and invalid Radius/IntervalSeconds in asset (Radius must be 1..%d, IntervalSeconds > 0)",
                        harvester.tier, info.fillWorldPos(cb, pos) ? pos : "?", HarvesterSettings.MAX_RADIUS);
            }
            return;
        }

        if (container != null && !refreshArea(harvester, info, store, cb, settings.radius())) {
            return;
        }

        // Own timer, independent of the harvest interval: the soil is cared for even while the
        // container is full (upgrades spec §2.1). Starts expired, so the first sweep after a load
        // runs on the first tick.
        harvester.soilElapsed += dt;
        if (harvester.soilElapsed >= global.soilSweepSeconds()) {
            harvester.soilElapsed = 0;
            if (harvester.hasUpgrade(UpgradeType.WATERING) || harvester.hasUpgrade(UpgradeType.FERTILIZER)) {
                long soilStart = System.nanoTime();
                sweepSoil(harvester, info, container, store, cb, settings, global.manurePerApplication());
                phase(harvester, CycleStats.Phase.SOIL, micros(soilStart));
            }
        }

        // Own timer too, so the effect keeps going between slow cycles; each spawn lasts one period.
        float particleSeconds = global.upgradeParticlesSeconds();
        if (particleSeconds > 0 && !harvester.upgrades().isEmpty()) {
            harvester.upgradeParticlesElapsed += dt;
            if (harvester.upgradeParticlesElapsed >= particleSeconds) {
                harvester.upgradeParticlesElapsed = 0;
                long particlesStart = System.nanoTime();
                Vector3i at = new Vector3i();
                if (info.fillWorldPos(cb, at)) {
                    UpgradeParticles.spawn(store.getExternalData().getWorld(), at, harvester.upgrades(), particleSeconds);
                }
                phase(harvester, CycleStats.Phase.VISUAL, micros(particlesStart));
            }
        }

        harvester.elapsed += dt;
        if (harvester.elapsed < settings.intervalSeconds()) {
            collectBurst(dt, harvester, info, container, store, cb, global, settings);
            return;
        }
        harvester.elapsed = 0;

        long cycleStart = System.nanoTime();
        Vector3i pos = new Vector3i();
        if (!info.fillWorldPos(cb, pos)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        // Once per cycle, so a state lost to something else (e.g. a state change by another
        // interaction) comes back; written only when it differs (upgrades spec §5.1).
        if (!UpgradeVisual.stateFor(harvester.upgrades()).equals(HarvesterVisual.currentState(world, pos))) {
            HarvesterVisual.request(world, pos);
        }
        phase(harvester, CycleStats.Phase.VISUAL, micros(cycleStart));

        if (container == null) {
            if (!harvester.missingContainerWarned) {
                harvester.missingContainerWarned = true;
                HyPedestalsPlugin.warn("Harvester at %s has no ItemContainerBlock; inactive", pos);
            }
            return;
        }

        long runStart = System.nanoTime();
        HytaleCycleWorld cycleWorld = new HytaleCycleWorld(world, cb, container, info, global.collectAnyItem(),
                harvester.pendingDrops, areas(world), settings.radius());
        harvester.lastResult = HarvestCycle.run(cycleWorld,
                new BlockPos(pos.x, pos.y, pos.z), settings.radius(), global.collectMargin(),
                harvester.warnedTypes, harvester.scanIndex, global.maxHarvestsPerCycle(), harvester.pendingDrops);
        long runMicros = micros(runStart);
        long collectMicros = cycleWorld.collectNanos() / 1000;
        phase(harvester, CycleStats.Phase.COLLECT, collectMicros);
        phase(harvester, CycleStats.Phase.SCAN, Math.max(0, runMicros - collectMicros));
        if (harvester.lastResult.nextIndex() != harvester.scanIndex) {
            // Persisted field: without the mark a reload of the section (or a restart) would bring
            // back the old index and restart the rotation there.
            harvester.scanIndex = harvester.lastResult.nextIndex();
            info.markNeedsSaving(cb);
        }
        harvester.lastCycleTick = world.getTick();
        for (String type : harvester.lastResult.newWarnings()) {
            HyPedestalsPlugin.warn("crop type %s at %s skipped until chunk reload", type, pos);
        }
        if (harvester.lastResult.transientFailures() == 0) {
            harvester.transientWarned = false;
        } else if (!harvester.transientWarned) {
            // Once until a cycle without such failures; those crops stay eligible and are retried
            // every cycle meanwhile.
            harvester.transientWarned = true;
            HyPedestalsPlugin.warn("Harvester at %s: %d crop(s) still being updated by the game, retrying next cycle",
                    pos, harvester.lastResult.transientFailures());
        }
        if (global.harvestParticles() && harvester.lastResult.harvested() > 0) {
            // Finite vanilla burst (5 particles, the seed-planting dust); its spawner sits 1 block up,
            // so this lands on top of the pedestal. Sent to the players near the position.
            ParticleUtil.spawnParticleEffect(HARVEST_PARTICLES,
                    new Vector3d(pos.x + 0.5, pos.y, pos.z + 0.5), world.getEntityStore().getStore());
        }
        // After the cycle's second collect, so this cycle's items leave right away; also when the
        // container is full, which is when export matters most.
        // With the fertilizer upgrade its fuel (bags, poop) is the pass's supply: it stays in the Harvester.
        if (global.exportToAdjacent()) {
            long exportStart = System.nanoTime();
            harvester.lastExport = new HytaleExport(world, cb).run(container, info, new BlockPos(pos.x, pos.y, pos.z),
                    harvester.hasUpgrade(UpgradeType.FERTILIZER), global.manurePerApplication());
            phase(harvester, CycleStats.Phase.EXPORT, micros(exportStart));
        } else {
            harvester.lastExport = ExportPlan.Result.NONE;
        }
        harvester.burstRemaining = CollectBurst.afterCycle(harvester.burstRemaining,
                harvester.lastResult.harvested(), global.postHarvestCollectSeconds());
        if (harvester.lastResult.harvested() > 0) {
            harvester.burstTicksWithoutProgress = 0;
        }

        long cycleMicros = micros(cycleStart);
        harvester.stats.addCycle(cycleMicros);
        PerfRegistry.global().cycle(harvester.tier, world.getName() + " " + pos.x + " " + pos.y + " " + pos.z,
                cycleMicros, harvester.lastResult.harvested(), harvester.stats);
    }

    /**
     * Marks the Harvester's area active for this tick, so its neighbours leave the drops inside it
     * alone ({@link HarvestAreas}). On every tick, not only on cycles: the areas expire after a
     * few ticks without a refresh, and the intervals differ per tier.
     *
     * @return whether the Harvester's position is known (it always is for a placed block)
     */
    private static boolean refreshArea(HarvesterComponent harvester, BlockModule.BlockStateInfo info,
                                       Store<ChunkStore> store, CommandBuffer<ChunkStore> cb, int radius) {
        if (harvester.center == null) {
            Vector3i at = new Vector3i();
            if (!info.fillWorldPos(cb, at)) {
                return false;
            }
            harvester.center = new BlockPos(at.x, at.y, at.z);
        }
        World world = store.getExternalData().getWorld();
        areas(world).refresh(harvester.center, radius, world.getTick());
        return true;
    }

    private static long micros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1000;
    }

    /** Adds a phase to the server totals. */
    private static void phase(HarvesterComponent harvester, CycleStats.Phase phase, long micros) {
        PerfRegistry.global().phase(harvester.tier, phase, micros);
    }

    /**
     * Between cycles, while the post-harvest burst is running: one collect (the cycle's box) per
     * tick, added to the last cycle's {@code collected}, so drops the item index registers a few
     * seconds after the harvest reach the container within the same cycle. Nothing runs outside
     * the burst. With the container full the tick does not collect and the burst ends (nothing
     * could go in until the next cycle's export); it also ends after a few ticks that found items
     * but put none in ({@link CollectBurst#afterCollect}).
     */
    private static void collectBurst(float dt, HarvesterComponent harvester, BlockModule.BlockStateInfo info,
                                     SimpleItemContainer container, Store<ChunkStore> store,
                                     CommandBuffer<ChunkStore> cb, ModSettings global, TierSettings settings) {
        if (!CollectBurst.collects(harvester.burstRemaining, global.postHarvestCollectSeconds())) {
            harvester.burstRemaining = 0f;
            return;
        }
        harvester.burstRemaining = CollectBurst.afterTick(harvester.burstRemaining, dt);
        Vector3i pos = new Vector3i();
        if (container == null || harvester.lastResult == null || !info.fillWorldPos(cb, pos)) {
            return;
        }
        long burstStart = System.nanoTime();
        World world = store.getExternalData().getWorld();
        HytaleCycleWorld cycleWorld = new HytaleCycleWorld(world, cb, container, info, global.collectAnyItem(),
                harvester.pendingDrops, areas(world), settings.radius());
        boolean full = cycleWorld.containerFull();
        if (full) {
            harvester.pendingDrops.collectBlocked();
        }
        int collected = full ? 0 : cycleWorld.collectDroppedItems(new BlockPos(pos.x, pos.y, pos.z),
                HarvestCycle.collectExtent(settings.radius(), global.collectMargin()));
        harvester.burstTicksWithoutProgress = CollectBurst.ticksWithoutProgress(harvester.burstTicksWithoutProgress,
                collected, cycleWorld.itemsSeen());
        harvester.burstRemaining = CollectBurst.afterCollect(harvester.burstRemaining, full,
                harvester.burstTicksWithoutProgress);
        harvester.lastResult = harvester.lastResult.plusCollected(collected);
        phase(harvester, CycleStats.Phase.BURST, micros(burstStart));
    }

    /**
     * Waters the tilled soil of the area that needs it, for {@code WateringSeconds} of game time
     * (watering upgrade), and fertilizes the soil under crops with the fuel in the container
     * (fertilizer upgrade): {@code manurePerApplication} poop per soil block first, then one
     * durability point per soil block from the most worn bag.
     */
    private static void sweepSoil(HarvesterComponent harvester, BlockModule.BlockStateInfo info,
                                  SimpleItemContainer container, Store<ChunkStore> store,
                                  CommandBuffer<ChunkStore> cb, TierSettings settings, int manurePerApplication) {
        Vector3i pos = new Vector3i();
        if (!info.fillWorldPos(cb, pos)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Instant now = HytaleSoilWorld.gameTime(world);
        if (now == null) {
            return;
        }
        List<FertilizerInput.InputStack> fuel = container == null || !harvester.hasUpgrade(UpgradeType.FERTILIZER)
                ? List.of() : HytaleFertilizerInput.read(container);
        SoilPass.Result result = SoilPass.run(new HytaleSoilWorld(world, cb), new BlockPos(pos.x, pos.y, pos.z),
                settings.radius(), now, harvester.hasUpgrade(UpgradeType.WATERING), settings.wateringSeconds(),
                FertilizerInput.totalApplications(fuel, manurePerApplication));
        if (result.applicationsUsed() > 0) {
            HytaleFertilizerInput.consume(container, fuel, result.applicationsUsed(), manurePerApplication);
            // The container is the Harvester's own component, changed outside the engine's flows.
            info.markNeedsSaving(cb);
        }
        harvester.lastSoil = result;
        harvester.wateredTotal += result.watered();
        harvester.fertilizedTotal += result.fertilized();
    }
}
