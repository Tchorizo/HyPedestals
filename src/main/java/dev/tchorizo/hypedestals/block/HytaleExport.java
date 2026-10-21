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
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.ExportPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * {@link ExportPlan} over one Hytale world, for a single export of one Harvester: the neighbors'
 * {@code ItemContainerBlock}s are the targets. Not reusable across ticks.
 *
 * <p>Both blocks are marked for saving afterwards ({@link #run}): a block entity changed outside
 * the engine's own flows reaches disk only when marked. The engine also marks a block container on
 * every content change; the explicit marks do not depend on that.
 */
final class HytaleExport implements ExportPlan.ExportWorld<HytaleExport.Source> {

    /** What the planter upgrade keeps: seeds and saplings. */
    static final Predicate<String> SEEDS_AND_SAPLINGS = HytalePlantables::isPlantable;
    /** What the Lumberjack keeps: saplings. */
    static final Predicate<String> SAPLINGS = id -> HytalePlantables.saplingBlock(id) != null;

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
     * {@code info}) into its neighbors and marks every changed block for saving. An exception from
     * the engine (e.g. a neighbor's container in an unexpected state) ends this export with
     * {@link ExportPlan.Result#NONE} and a warning; what already moved is still marked for saving,
     * and the next cycle tries again.
     *
     * @param keepFertilizer whether the fertilizer upgrade is installed (its fuel stays)
     * @param manurePerApplication the config's {@code ManurePerApplication} ({@code 0}: poop is not fuel and leaves)
     * @param manureStock the config's {@code ManureStock} (poop kept as fuel; the rest leaves)
     * @param plantable the items that stay up to {@code seedStock} of each kind ({@code null}: none):
     *     seeds and saplings with the planter upgrade ({@link #SEEDS_AND_SAPLINGS}), the Lumberjack's
     *     saplings ({@link #SAPLINGS})
     * @param seedStock the config's {@code PlanterSeedStock}
     */
    ExportPlan.Result run(ItemContainer container, BlockModule.BlockStateInfo info, BlockPos center,
                          boolean keepFertilizer, int manurePerApplication, int manureStock,
                          Predicate<String> plantable, int seedStock) {
        ExportPlan.Result result;
        try {
            result = ExportPlan.run(this, new Source(container), center, keepFertilizer, manurePerApplication, manureStock,
                    plantable, seedStock);
        } catch (RuntimeException e) {
            HyPedestalsPlugin.warn("export from the machine at %d %d %d failed: %s",
                    center.x(), center.y(), center.z(), e.toString());
            result = ExportPlan.Result.NONE;
        }
        boolean anyReceived = false;
        for (Target t : touched) {
            if (t.received) {
                t.info.markNeedsSaving(accessor);
                anyReceived = true;
            }
        }
        if (anyReceived) {
            info.markNeedsSaving(accessor);
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
            // A filler block (a non-origin part of a multi-block) stores its packed offset from the
            // origin block, which holds the block entity: subtract it to reach the container.
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
        boolean machine = accessor.getComponent(ref, HarvesterComponent.TYPE) != null
                || accessor.getComponent(ref, LumberjackComponent.TYPE) != null;
        Target target = new Target(new BlockPos(x, y, z), machine, block.getItemContainer(), info);
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

        @Override
        public String itemId(int slot) {
            return container.getItemStack((short) slot).getItemId();
        }

        @Override
        public int quantity(int slot) {
            ItemStack stack = container.getItemStack((short) slot);
            return ItemStack.isEmpty(stack) ? 0 : stack.getQuantity();
        }
    }

    static final class Target implements ExportPlan.ExportTarget<Source> {
        private final BlockPos pos;
        private final boolean machine;
        private final ItemContainer container;
        final BlockModule.BlockStateInfo info;
        boolean received;

        Target(BlockPos pos, boolean machine, ItemContainer container, BlockModule.BlockStateInfo info) {
            this.pos = pos;
            this.machine = machine;
            this.container = container;
            this.info = info;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }

        @Override
        public boolean isMachine() {
            return machine;
        }

        /** Partial fit counts ({@code canAddItemStack} defaults to not all-or-nothing); unknown items never move. */
        @Override
        public boolean accepts(Source from, int slot) {
            ItemStack stack = from.container.getItemStack((short) slot);
            return !ItemStack.isEmpty(stack) && stack.getItem() != null && container.canAddItemStack(stack);
        }

        /**
         * Moves what fits of the slot, at most {@code max} items (the engine's default moves are
         * partial and honor the target's slot filters; the quantity overload is
         * {@code (slot, quantity, target, allOrNothing=false, filter=true)} [JAR]). The count is
         * read from the slot itself, before and after.
         */
        @Override
        public int moveFrom(Source from, int slot, int max) {
            int before = from.quantity(slot);
            if (max >= before) {
                from.container.moveItemStackFromSlot((short) slot, container);
            } else {
                from.container.moveItemStackFromSlot((short) slot, max, container);
            }
            int moved = before - from.quantity(slot);
            if (moved > 0) {
                received = true;
            }
            return moved;
        }
    }
}
