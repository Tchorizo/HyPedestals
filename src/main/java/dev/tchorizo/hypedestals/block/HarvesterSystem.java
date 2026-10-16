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
import dev.tchorizo.hypedestals.harvest.ExportPlan;
import dev.tchorizo.hypedestals.harvest.FertilizerInput;
import dev.tchorizo.hypedestals.harvest.HarvestCycle;
import dev.tchorizo.hypedestals.harvest.SoilPass;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import dev.tchorizo.hypedestals.harvest.UpgradeVisual;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.time.Instant;

/**
 * Runs one {@link HarvestCycle} per harvester block every {@code IntervalSeconds}, then, with
 * {@code ExportToAdjacent}, an {@link ExportPlan} into the containers touching the block; and,
 * every {@code SoilSweepSeconds} on a timer of its own, a {@link SoilPass} over the same area
 * when the watering or the fertilizer upgrade is installed. With the fertilizer upgrade, the
 * container's last slot is the fertilizer input slot ({@link HytaleFertilizerInput}): filtered
 * to the fertilizer bag, left out of the harvest budget, and the source of the pass's
 * applications. Each cycle also re-asserts the block state that shows the upgrades
 * ({@link HarvesterVisual}) and, with {@code HarvestParticles}, puffs a short particle burst when
 * something was harvested. Radius, interval, margin, item filter, export and the soil settings are read
 * from the global config on every tick, so changes made with {@code /hypedestals config} (command
 * or window) apply right away; hand edits to the config file are read at server start only.
 */
public final class HarvesterSystem extends EntityTickingSystem<ChunkStore> {

    /** Vanilla {@code Server/Particles/Item/Seeds/Seed_Place_Dust.particlesystem}: one burst, then done. */
    private static final String HARVEST_PARTICLES = "Seed_Place_Dust";

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
        HarvesterComponent harvester = chunk.getComponent(index, HarvesterComponent.TYPE);
        BlockModule.BlockStateInfo info = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());

        if (!harvester.ignoredUpgradesWarned && !harvester.ignoredUpgrades.isEmpty()) {
            harvester.ignoredUpgradesWarned = true;
            Vector3i at = new Vector3i();
            HyPedestalsPlugin.warn("Harvester at %s: ignored unknown or repeated upgrades %s; they are dropped on the next save",
                    info.fillWorldPos(cb, at) ? at : "?", harvester.ignoredUpgrades);
        }

        ItemContainerBlock containerBlock = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        SimpleItemContainer container = containerBlock == null ? null : containerBlock.getItemContainer();
        // Every tick, before anything adds to the container: the filter is runtime-only and follows
        // the upgrade (installed or removed by an interaction or a command between ticks).
        harvester.inputFiltered = HytaleFertilizerInput.sync(container, harvester.inputFiltered,
                harvester.hasUpgrade(UpgradeType.FERTILIZER));

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

        // Own timer, independent of the harvest interval: the soil is cared for even while the
        // container is full (upgrades spec §2.1).
        harvester.soilElapsed += dt;
        if (harvester.soilElapsed >= global.soilSweepSeconds()) {
            harvester.soilElapsed = 0;
            if (harvester.hasUpgrade(UpgradeType.WATERING) || harvester.hasUpgrade(UpgradeType.FERTILIZER)) {
                sweepSoil(harvester, info, store, cb, settings);
            }
        }

        harvester.elapsed += dt;
        if (harvester.elapsed < settings.intervalSeconds()) {
            return;
        }
        harvester.elapsed = 0;

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

        if (container == null) {
            if (!harvester.missingContainerWarned) {
                harvester.missingContainerWarned = true;
                HyPedestalsPlugin.warn("Harvester at %s has no ItemContainerBlock; inactive", pos);
            }
            return;
        }

        harvester.lastResult = HarvestCycle.run(
                new HytaleCycleWorld(world, cb, container, global.collectAnyItem(), harvester.inputFiltered != null),
                new BlockPos(pos.x, pos.y, pos.z), settings.radius(), global.collectMargin(),
                harvester.warnedTypes, harvester.scanIndex);
        harvester.scanIndex = harvester.lastResult.nextIndex();
        harvester.lastCycleTick = world.getTick();
        for (String type : harvester.lastResult.newWarnings()) {
            HyPedestalsPlugin.warn("crop type %s at %s skipped until chunk reload", type, pos);
        }
        if (harvester.lastResult.transientFailures() > 0) {
            // At most one line per cycle; those crops stay eligible and are retried next cycle.
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
        // cycle paused because the container was full, which is when export matters most.
        harvester.lastExport = global.exportToAdjacent()
                ? new HytaleExport(world, cb).run(container, info, new BlockPos(pos.x, pos.y, pos.z))
                : ExportPlan.Result.NONE;
    }

    /**
     * Waters the tilled soil of the area that needs it, for {@code WateringSeconds} of game time
     * (watering upgrade), and fertilizes the soil under crops with the bag in the input slot
     * (fertilizer upgrade, filter applied), taking one durability point per soil block.
     */
    private static void sweepSoil(HarvesterComponent harvester, BlockModule.BlockStateInfo info,
                                  Store<ChunkStore> store, CommandBuffer<ChunkStore> cb, TierSettings settings) {
        Vector3i pos = new Vector3i();
        if (!info.fillWorldPos(cb, pos)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Instant now = HytaleSoilWorld.gameTime(world);
        if (now == null) {
            return;
        }
        SimpleItemContainer input = harvester.inputFiltered;
        int applications = input == null ? 0 : FertilizerInput.applications(HytaleFertilizerInput.read(input));
        SoilPass.Result result = SoilPass.run(new HytaleSoilWorld(world, cb), new BlockPos(pos.x, pos.y, pos.z),
                settings.radius(), now, harvester.hasUpgrade(UpgradeType.WATERING), settings.wateringSeconds(), applications);
        if (result.applicationsUsed() > 0) {
            HytaleFertilizerInput.consume(input, result.applicationsUsed());
            // The container is the Harvester's own component; a write outside the engine's flows (S4).
            info.markNeedsSaving(cb);
        }
        harvester.lastSoil = result;
        harvester.wateredTotal += result.watered();
        harvester.fertilizedTotal += result.fertilized();
    }
}
