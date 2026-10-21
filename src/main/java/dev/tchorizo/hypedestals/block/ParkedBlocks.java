package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Parked block entities (Task 40). A chunk section that is in memory but not ticking carries the
 * {@code NonTicking} component, and the engine then keeps every block entity of the section as a
 * parked {@code Holder} in its {@code BlockComponentSection} instead of a live {@code Ref}
 * ({@code BlockComponentSectionLoadingSystem}). {@code BlockModule.getBlockEntity} only sees live
 * {@code Ref}s, so for such a block it answers {@code null} as if the block had no entity, and
 * anything built on that answer is wrong: {@code FarmingUtil.harvest} queues a second entity and the
 * world crashes ({@code Duplicate block components}); a growing sapling reads as a grown tree; soil
 * reads as absent. The machines therefore skip what is in such a section, as if it were not loaded;
 * a later cycle finds it ticking again. World thread only.
 */
final class ParkedBlocks {

    private ParkedBlocks() {}

    /** Whether the block's own entity sits parked ({@code BlockComponentSection#getBlockHolder} non-null). */
    static boolean holderAt(ComponentAccessor<ChunkStore> store, Ref<ChunkStore> section, int x, int y, int z) {
        if (section == null || !section.isValid()) {
            return false;
        }
        BlockComponentSection components = store.getComponent(section, BlockComponentSection.getComponentType());
        return components != null && components.getBlockHolder(ChunkUtil.indexBlock(x, y, z)) != null;
    }

    /**
     * Whether the section holding the block is in memory and not ticking (so its block entities are
     * parked). {@code false} for a section not in memory (a different case, read as air).
     */
    static boolean notTicking(ChunkStore chunkStore, int x, int y, int z) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        return section != null && section.isValid()
                && chunkStore.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }

    /**
     * The section's ref when it is in memory and ticking, else {@code null}: what a machine may
     * read block entities from and write to.
     */
    static Ref<ChunkStore> tickingSection(ChunkStore chunkStore, int x, int y, int z) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        return section != null && section.isValid()
                && !chunkStore.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType())
                ? section : null;
    }
}
