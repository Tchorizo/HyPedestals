package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.UpgradeVisual;
import org.joml.Vector3i;

/**
 * Keeps a Harvester block in the {@link UpgradeVisual} state of its upgrades (each tier's
 * {@code State.Definitions}: particles only; model and tier texture inherited from the tier).
 * The state is part of the block id, so it is saved with the chunk and survives restarts; the
 * switch keeps the block entity (Harvester component and container).
 */
final class HarvesterVisual {

    private HarvesterVisual() {}

    /** State name of the block at {@code pos} ({@link UpgradeVisual#DEFAULT} for the base block). */
    static String currentState(World world, Vector3i pos) {
        BlockType type = blockAt(world.getChunkStore(), pos);
        return type == null ? null : stateOf(type);
    }

    /**
     * Applies {@link #apply} after the current tick. {@code setBlockInteractionState} replaces the
     * block in the chunk store itself, not through a {@code CommandBuffer}; from inside a system
     * (the Harvester's tick) or an interaction that may end in "Store is currently processing".
     */
    static void request(World world, Vector3i pos) {
        Vector3i at = new Vector3i(pos);
        world.execute(() -> apply(world, at));
    }

    /**
     * Puts the Harvester at {@code pos} into the state of its current upgrades, re-read here, and
     * only when the block shows another state. {@code false} when nothing was written.
     */
    static boolean apply(World world, Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        Ref<ChunkStore> ref = section == null ? null : BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
        HarvesterComponent harvester = ref == null || !ref.isValid() ? null : store.getComponent(ref, HarvesterComponent.TYPE);
        BlockType type = harvester == null ? null : blockAt(chunkStore, pos);
        if (type == null) {
            return false;
        }
        String desired = UpgradeVisual.stateFor(harvester.upgrades());
        String current = stateOf(type);
        if (desired.equals(current)) {
            return false;
        }
        BlockOperations.setBlockInteractionState(chunkStore, section, pos.x, pos.y, pos.z, type, desired, false);
        String after = currentState(world, pos);
        HyPedestalsPlugin.log("visual: Harvester at %d %d %d %s -> %s%s", pos.x, pos.y, pos.z, current, desired,
                desired.equals(after) ? "" : " (block still shows " + after + ")");
        return desired.equals(after);
    }

    private static BlockType blockAt(ChunkStore chunkStore, Vector3i pos) {
        return BlockType.getAssetMap().getAsset(new SectionReader(chunkStore).getBlock(pos.x, pos.y, pos.z));
    }

    private static String stateOf(BlockType type) {
        String state = HytaleIds.stateName(type.getId());
        return state == null ? UpgradeVisual.DEFAULT : state;
    }
}
