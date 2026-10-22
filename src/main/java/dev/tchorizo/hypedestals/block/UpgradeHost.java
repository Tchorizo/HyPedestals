package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.harvest.UpgradeSet;

/**
 * A block component that holds upgrades: the Harvester and the Lumberjack. The install and remove
 * interactions, the visual state and the drop on breaking work on this, so they are the same code
 * for both machines; what each accepts is the {@link UpgradeSet}'s machine
 * ({@link dev.tchorizo.hypedestals.harvest.UpgradeMachine}).
 */
public interface UpgradeHost extends Component<ChunkStore> {

    /**
     * Installed upgrades, live and mutable. After changing them outside the engine's own flows,
     * call {@code BlockModule.BlockStateInfo.markNeedsSaving} on the block entity, or the change
     * may not reach disk.
     */
    UpgradeSet upgrades();

    /** The machine's name for logs and messages, e.g. {@code Harvester}. */
    default String machineName() {
        return upgrades().machine().displayName();
    }

    /** The upgrade host on the block entity {@code ref}, or {@code null} when it is neither a Harvester nor a Lumberjack. */
    static UpgradeHost of(Store<ChunkStore> store, Ref<ChunkStore> ref) {
        HarvesterComponent harvester = store.getComponent(ref, HarvesterComponent.TYPE);
        return harvester != null ? harvester : store.getComponent(ref, LumberjackComponent.TYPE);
    }
}
