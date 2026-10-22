package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** When a Lumberjack is broken, drops its upgrades ({@link UpgradeDropSystem}). */
public final class LumberjackRemovalSystem extends UpgradeDropSystem<LumberjackComponent> {

    @Override
    ComponentType<ChunkStore, LumberjackComponent> machineType() {
        return LumberjackComponent.TYPE;
    }
}
