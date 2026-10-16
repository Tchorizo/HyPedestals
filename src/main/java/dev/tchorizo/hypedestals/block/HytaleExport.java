package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.ExportPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ExportPlan} over one Hytale world, for a single export of one Harvester: the neighbors'
 * {@code ItemContainerBlock}s are the targets. Not reusable across ticks.
 *
 * <p>Moving items between two block containers is outside the engine's own flows, so both
 * blocks are marked for saving afterwards ({@link #run}), as for any such mutation (AGENTS.md,
 * spike S4).
 */
final class HytaleExport implements ExportPlan.ExportWorld<HytaleExport.Source> {

    private final ChunkStore chunkStore;
    private final ComponentAccessor<ChunkStore> accessor;
    private final SectionReader reader;
    private final List<Target> touched = new ArrayList<>();

    /**
     * @param accessor the command buffer of the current {@code ChunkStore} tick
     */
    HytaleExport(World world, ComponentAccessor<ChunkStore> accessor) {
        this.chunkStore = world.getChunkStore();
        this.accessor = accessor;
        this.reader = new SectionReader(chunkStore);
    }

    /**
     * Exports {@code container} (the Harvester at {@code center}, whose block state is
     * {@code info}) into its neighbors and marks every changed block for saving.
     */
    ExportPlan.Result run(ItemContainer container, BlockModule.BlockStateInfo info, BlockPos center) {
        ExportPlan.Result result = ExportPlan.run(this, new Source(container), center);
        if (result.moved() > 0) {
            info.markNeedsSaving(accessor);
            for (Target t : touched) {
                if (t.received) {
                    t.info.markNeedsSaving(accessor);
                }
            }
        }
        return result;
    }

    /**
     * The container whose block covers {@code p}: a filler position (part of a multi-block
     * container) resolves to its origin block. {@code null} for air, an unloaded section or a
     * block without {@code ItemContainerBlock}.
     */
    @Override
    public Target targetAt(BlockPos p) {
        if (chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z()) == null
                || reader.getBlock(p.x(), p.y(), p.z()) == BlockType.EMPTY_ID) {
            return null;
        }
        int filler = reader.getFiller(p.x(), p.y(), p.z());
        int x = p.x(), y = p.y(), z = p.z();
        if (filler != 0) {
            // The filler value is the offset from the origin, as ModernStorage resolves it too.
            x -= FillerBlockUtil.unpackX(filler);
            y -= FillerBlockUtil.unpackY(filler);
            z -= FillerBlockUtil.unpackZ(filler);
        }
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        Ref<ChunkStore> ref = section == null ? null : BlockModule.getBlockEntity(accessor, section, x, y, z);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        ItemContainerBlock block = accessor.getComponent(ref, ItemContainerBlock.getComponentType());
        BlockModule.BlockStateInfo info = accessor.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (block == null || block.getItemContainer() == null || info == null) {
            return null;
        }
        boolean harvester = accessor.getComponent(ref, HarvesterComponent.TYPE) != null;
        Target target = new Target(new BlockPos(x, y, z), harvester, block.getItemContainer(), info);
        touched.add(target);
        return target;
    }

    /** The Harvester's own container. */
    static final class Source implements ExportPlan.ExportSource {
        final ItemContainer container;

        Source(ItemContainer container) {
            this.container = container;
        }

        @Override
        public int capacity() {
            return container.getCapacity();
        }

        @Override
        public boolean isEmpty(int slot) {
            return ItemStack.isEmpty(container.getItemStack((short) slot));
        }

        int quantity(int slot) {
            ItemStack stack = container.getItemStack((short) slot);
            return ItemStack.isEmpty(stack) ? 0 : stack.getQuantity();
        }
    }

    static final class Target implements ExportPlan.ExportTarget<Source> {
        private final BlockPos pos;
        private final boolean harvester;
        private final ItemContainer container;
        final BlockModule.BlockStateInfo info;
        boolean received;

        Target(BlockPos pos, boolean harvester, ItemContainer container, BlockModule.BlockStateInfo info) {
            this.pos = pos;
            this.harvester = harvester;
            this.container = container;
            this.info = info;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }

        @Override
        public boolean isHarvester() {
            return harvester;
        }

        /** Partial fit counts ({@code canAddItemStack} defaults to not all-or-nothing); unknown items never move. */
        @Override
        public boolean accepts(Source from, int slot) {
            ItemStack stack = from.container.getItemStack((short) slot);
            return !ItemStack.isEmpty(stack) && stack.getItem() != null && container.canAddItemStack(stack);
        }

        /**
         * Moves what fits of the slot (the engine's default move is partial and honors the
         * target's slot filters). The count is read from the slot itself, before and after.
         */
        @Override
        public int moveFrom(Source from, int slot) {
            int before = from.quantity(slot);
            from.container.moveItemStackFromSlot((short) slot, container);
            int moved = before - from.quantity(slot);
            if (moved > 0) {
                received = true;
            }
            return moved;
        }
    }
}
