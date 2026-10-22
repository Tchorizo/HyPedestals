package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/**
 * When a machine with upgrades is broken, drops one item per installed upgrade at the block. One
 * subclass per machine component ({@link HarvesterRemovalSystem}, {@link LumberjackRemovalSystem});
 * the drop is the same for both. Only {@link RemoveReason#REMOVE}: {@code UNLOAD} (chunk unload,
 * shutdown) keeps the upgrades in the saved block, and {@code BUILDER_TOOLS_UNDO} restores a
 * previous world state. The container content is left to the engine, like any block container.
 */
abstract class UpgradeDropSystem<C extends UpgradeHost> extends RefSystem<ChunkStore> {

    /** The machine's component type (read when the system is queried, after the plugin's setup assigned it). */
    abstract ComponentType<ChunkStore, C> machineType();

    /**
     * The machine at {@code ref} was broken ({@code REMOVE}); runs before its upgrades are dropped.
     * Nothing by default.
     */
    void onBroken(C machine, World world) {}

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(machineType(), BlockModule.BlockStateInfo.getComponentType());
    }

    @Override
    public void onEntityAdded(Ref<ChunkStore> ref, AddReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
    }

    @Override
    public void onEntityRemove(Ref<ChunkStore> ref, RemoveReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        if (reason != RemoveReason.REMOVE) {
            return;
        }
        C machine = cb.getComponent(ref, machineType());
        BlockModule.BlockStateInfo info = cb.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (machine == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        onBroken(machine, world);
        if (info == null || machine.upgrades().isEmpty()) {
            return;
        }
        Vector3i pos = new Vector3i();
        if (!info.fillWorldPos(cb, pos)) {
            HyPedestalsPlugin.warn("%s removed with upgrades %s but its position is unknown; nothing dropped",
                    machine.machineName(), machine.upgrades());
            return;
        }
        String[] itemIds = machine.upgrades().toArray();
        List<ItemStack> drops = new ArrayList<>(itemIds.length);
        for (String itemId : itemIds) {
            drops.add(new ItemStack(itemId, 1));
        }
        // Cleared so a second removal callback for the same component cannot drop them again.
        machine.upgrades().clear();
        HyPedestalsPlugin.log("%s at %d %d %d broken: dropping upgrades %s",
                machine.machineName(), pos.x, pos.y, pos.z, String.join(", ", itemIds));

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, drops,
                new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5), Rotation3f.IDENTITY);
        // The removal can run while the entity store is processing (a player breaking the block),
        // and adding entities then throws; the next world task runs outside any store tick.
        world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
    }
}
