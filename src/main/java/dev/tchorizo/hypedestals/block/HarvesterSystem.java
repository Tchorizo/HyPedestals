package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.config.ModSettings;
import dev.tchorizo.hypedestals.config.TierSettings;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.HarvestCycle;
import org.joml.Vector3i;

/**
 * Runs one {@link HarvestCycle} per harvester block every {@code IntervalSeconds}. Radius,
 * interval, margin and item filter are read from the global config on every tick, so changes made
 * with {@code /hypedestals config} (command or window) apply right away; hand edits to the config
 * file are read at server start only.
 */
public final class HarvesterSystem extends EntityTickingSystem<ChunkStore> {

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

        ItemContainerBlock containerBlock = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        if (containerBlock == null) {
            if (!harvester.missingContainerWarned) {
                harvester.missingContainerWarned = true;
                HyPedestalsPlugin.warn("Harvester at %s has no ItemContainerBlock; inactive", pos);
            }
            return;
        }

        harvester.lastResult = HarvestCycle.run(
                new HytaleCycleWorld(world, cb, containerBlock.getItemContainer(), global.collectAnyItem()),
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
    }
}
