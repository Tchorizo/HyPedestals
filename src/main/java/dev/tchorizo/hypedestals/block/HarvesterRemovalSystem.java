package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
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
import dev.tchorizo.hypedestals.harvest.UpgradeSet;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops one item per installed upgrade at the block when a Harvester is broken. Only
 * {@link RemoveReason#REMOVE} drops: {@code UNLOAD} (chunk unload, shutdown) keeps the upgrades in
 * the saved block, and {@code BUILDER_TOOLS_UNDO} restores a previous world state. The container
 * content is left to the engine, like any block container.
 */
public final class HarvesterRemovalSystem extends RefSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(HarvesterComponent.TYPE, BlockModule.BlockStateInfo.getComponentType());
    }

    @Override
    public void onEntityAdded(Ref<ChunkStore> ref, AddReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
    }

    @Override
    public void onEntityRemove(Ref<ChunkStore> ref, RemoveReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        if (reason != RemoveReason.REMOVE) {
            return;
        }
        HarvesterComponent harvester = cb.getComponent(ref, HarvesterComponent.TYPE);
        BlockModule.BlockStateInfo info = cb.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (harvester == null || info == null || harvester.upgrades.isEmpty()) {
            return;
        }
        Vector3i pos = new Vector3i();
        if (!info.fillWorldPos(cb, pos)) {
            HyPedestalsPlugin.warn("Harvester removed with upgrades %s but its position is unknown; nothing dropped",
                    harvester.upgrades);
            return;
        }
        String[] itemIds = harvester.upgrades.toArray();
        List<ItemStack> drops = new ArrayList<>(itemIds.length);
        for (String itemId : itemIds) {
            drops.add(new ItemStack(itemId, 1));
        }
        // Cleared so a second removal callback for the same component cannot drop them again.
        harvester.upgrades = new UpgradeSet();
        HyPedestalsPlugin.log("Harvester at %d %d %d broken: dropping upgrades %s",
                pos.x, pos.y, pos.z, String.join(", ", itemIds));

        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, drops,
                new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5), Rotation3f.IDENTITY);
        // The removal can run while the entity store is processing (a player breaking the block),
        // and adding entities then throws; the next world task runs outside any store tick.
        world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
    }
}
