package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * When a Harvester is broken, forgets its area ({@link dev.tchorizo.hypedestals.harvest.HarvestAreas#remove})
 * so its neighbours may take the drops in it right away, and drops its upgrades ({@link UpgradeDropSystem}).
 * The area of an unloaded Harvester expires on its own.
 */
public final class HarvesterRemovalSystem extends UpgradeDropSystem<HarvesterComponent> {

    @Override
    ComponentType<ChunkStore, HarvesterComponent> machineType() {
        return HarvesterComponent.TYPE;
    }

    @Override
    void onBroken(HarvesterComponent harvester, World world) {
        if (harvester.center != null) {
            // Set on its first tick, when the area was first refreshed: without it there is none.
            HarvesterSystem.areas(world).remove(harvester.center);
        }
    }
}
