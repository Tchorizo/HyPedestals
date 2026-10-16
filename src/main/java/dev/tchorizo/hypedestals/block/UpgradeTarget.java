package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import org.joml.Vector3i;

/** The Harvester an upgrade interaction targets, plus the helpers both interactions share. */
record UpgradeTarget(HarvesterComponent harvester, BlockModule.BlockStateInfo info, Store<ChunkStore> store) {

    /** The Harvester at {@code pos}, or {@code null} when that block is not one (or is not loaded). */
    static UpgradeTarget at(World world, Vector3i pos) {
        Store<ChunkStore> store = world.getChunkStore().getStore();
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        Ref<ChunkStore> ref = section == null ? null : BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        HarvesterComponent harvester = store.getComponent(ref, HarvesterComponent.TYPE);
        BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        return harvester == null || info == null ? null : new UpgradeTarget(harvester, info, store);
    }

    /** The upgrade list changed outside the engine; without this it is lost on unload/restart (spike S4). */
    void markNeedsSaving() {
        info.markNeedsSaving(store);
    }

    /** Chat message to the interacting player; silently skipped for non-player interactors. */
    static void tell(CommandBuffer<EntityStore> cb, InteractionContext ctx, String key, UpgradeType type) {
        PlayerRef player = cb.getComponent(ctx.getEntity(), PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        Message message = Message.translation("hypedestals.upgrade." + key);
        if (type != null) {
            message = message.param("upgrade",
                    Message.translation(HytaleIds.itemTranslationKey(type.itemId(), "name")));
        }
        player.sendMessage(message);
    }
}
