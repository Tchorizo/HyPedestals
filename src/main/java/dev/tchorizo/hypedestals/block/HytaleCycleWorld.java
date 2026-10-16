package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.builtin.adventure.farming.FarmingUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.CollectFilter;
import dev.tchorizo.hypedestals.harvest.CycleWorld;
import dev.tchorizo.hypedestals.harvest.HarvestCycle;
import dev.tchorizo.hypedestals.harvest.HarvestOutcome;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;

/** {@link CycleWorld} over one Hytale world, for a single cycle of one harvester. Not reusable across ticks. */
final class HytaleCycleWorld implements CycleWorld {

    /** Message of the {@link IllegalStateException} thrown by {@code Store.assertWriteProcessing}. */
    private static final String STORE_PROCESSING = "Store is currently processing";

    private final CommandBuffer<ChunkStore> chunkBuffer;
    private final ChunkStore chunkStore;
    private final Store<EntityStore> entityStore;
    private final SectionReader reader;
    private final ItemContainer container;
    private final boolean collectAnyItem;

    /**
     * @param chunkBuffer the command buffer of the current {@code ChunkStore} tick; harvesting
     *     must go through it rather than the chunk store itself while the system is ticking
     * @param collectAnyItem the config's {@code CollectAnyItem}; see {@link CollectFilter}
     */
    HytaleCycleWorld(World world, CommandBuffer<ChunkStore> chunkBuffer, ItemContainer container, boolean collectAnyItem) {
        this.chunkBuffer = chunkBuffer;
        this.chunkStore = world.getChunkStore();
        this.entityStore = world.getEntityStore().getStore();
        this.reader = new SectionReader(chunkStore);
        this.container = container;
        this.collectAnyItem = collectAnyItem;
    }

    /**
     * Base crop id (e.g. {@code Plant_Crop_Wheat_Block}) so every growth state of a crop shares one
     * id for {@code warnedTypes}. A state variant may not carry the farming config itself, so the
     * base asset is consulted when the variant has none. Filler parts of a multi-block crop (the
     * upper half of ripe wheat) report {@code null}: the crop is harvested only through its origin
     * block, because {@code harvest} on the upper half removes an Eternal crop instead of resetting
     * it, and with the rotating scan the upper half can come up before its origin.
     */
    @Override
    public String cropTypeAt(BlockPos p) {
        if (reader.getFiller(p.x(), p.y(), p.z()) != 0) {
            return null;
        }
        BlockType block = BlockType.getAssetMap().getAsset(reader.getBlock(p.x(), p.y(), p.z()));
        if (block == null) {
            return null;
        }
        String baseId = HytaleIds.baseBlockId(block.getId());
        if (block.getFarming() != null) {
            return baseId;
        }
        BlockType base = BlockType.getAssetMap().getAsset(baseId);
        return base != null && base.getFarming() != null ? baseId : null;
    }

    /**
     * Harvest that does not throw for the known "store busy" case. {@code FarmingUtil.harvest}
     * removes a crop that does not regrow (no {@code StageSetAfterHarvest}, e.g. plain
     * {@code Plant_Crop_Wheat_Block}) with {@code BlockOperations.setBlock(air)}, which writes to
     * the chunk store itself rather than to {@link #chunkBuffer}: if the block still has a block
     * entity, {@code BlockEntity.setBlockEntity} calls {@code Store.removeEntity}, and from inside
     * a system that throws {@code IllegalStateException("Store is currently processing! ...")}
     * after the drops were already spawned and the block set to air (leaving the entity behind).
     * A ripe crop keeps its {@code FarmingBlock} entity only until its next farming tick removes
     * it ({@code FarmingUtil.tickFarming} drops the entity once the last stage is reached): about
     * one tick when the crop ripens naturally, a few seconds after {@code dev ripen}. So such a
     * position is skipped as {@link HarvestOutcome#TRANSIENT_ERROR} before anything happens, and
     * the exception itself, should it still occur, is classified the same way by its message.
     */
    @Override
    public HarvestOutcome harvest(BlockPos p) {
        if (removalBlockedByBlockEntity(p)) {
            return HarvestOutcome.TRANSIENT_ERROR;
        }
        try {
            return FarmingUtil.harvest(chunkBuffer, entityStore, null, new Vector3i(p.x(), p.y(), p.z()))
                    ? HarvestOutcome.HARVESTED
                    : HarvestOutcome.NOT_READY;
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException && e.getMessage() != null
                    && e.getMessage().startsWith(STORE_PROCESSING)) {
                // Counted in CycleResult.transientFailures; HarvesterSystem logs one line per cycle.
                return HarvestOutcome.TRANSIENT_ERROR;
            }
            // HarvestCycle keeps only the crop type; log the cause here, once per type thanks to warnedTypes.
            HyPedestalsPlugin.warn("harvest failed at %s (%s): %s", p, cropTypeAt(p), e.toString());
            return HarvestOutcome.TYPE_ERROR;
        }
    }

    /**
     * Whether {@code FarmingUtil.harvest0} would take one of its "replace with air" branches at
     * {@code p} (same checks, on the block type actually there) while a block entity still exists
     * at that block, i.e. whether harvesting now would throw "Store is currently processing".
     */
    private boolean removalBlockedByBlockEntity(BlockPos p) {
        BlockType block = BlockType.getAssetMap().getAsset(reader.getBlock(p.x(), p.y(), p.z()));
        BlockGathering gathering = block == null ? null : block.getGathering();
        if (gathering == null || gathering.getHarvest() == null) {
            return false; // nothing to harvest: harvest returns false without touching the block
        }
        FarmingData farming = block.getFarming();
        if (farming != null && farming.getStages() != null && farming.getStageSetAfterHarvest() != null) {
            Map<String, FarmingStageData[]> stages = farming.getStages();
            if (farming.getStartingStageSet() == null || stages.get(farming.getStartingStageSet()) == null) {
                return false; // harvest gives the drops and returns false, block untouched
            }
            FarmingStageData[] after = stages.get(farming.getStageSetAfterHarvest());
            if (after != null && after.length > 0) {
                return false; // regrows in place through the command buffer
            }
        }
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        return section != null && section.isValid()
                && BlockModule.getBlockEntity(chunkBuffer, section, p.x(), p.y(), p.z()) != null;
    }

    /**
     * Collects dropped items inside an axis-aligned box around {@code center}: {@code halfExtent}
     * blocks horizontally and {@code -2..+3} vertically. A box (not a sphere) is used because the
     * harvest area is a square: with a sphere the corners of larger tiers fall outside it, and
     * drops roll a block or two past the crop they came from.
     */
    @Override
    public int collectDroppedItems(BlockPos center, double halfExtent) {
        var spatial = entityStore.getResource(EntityModule.get().getItemSpatialResourceType());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return 0;
        }
        List<Ref<EntityStore>> refs = SpatialResource.getThreadLocalReferenceList();
        refs.clear();
        double cx = center.x() + 0.5, cz = center.z() + 0.5;
        spatial.getSpatialStructure().collectBox(
                new Vector3d(cx - halfExtent, center.y() - 2.0, cz - halfExtent),
                new Vector3d(cx + halfExtent, center.y() + 3.0, cz + halfExtent), refs);

        int inserted = 0;
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            ItemComponent item = entityStore.getComponent(ref, ItemComponent.getComponentType());
            ItemStack stack = item == null ? null : item.getItemStack();
            if (stack == null || stack.isEmpty() || !accepts(stack)) {
                continue;
            }
            // All-or-nothing (the container default is partial): a partial insert would leave the
            // full stack on the ground too and duplicate the difference. The remainder check backs
            // up succeeded(): the entity is only removed once the whole stack is in the container.
            ItemStackTransaction tx = container.addItemStack(stack, true, false, true);
            ItemStack remainder = tx.getRemainder();
            if (!tx.succeeded() || (remainder != null && !remainder.isEmpty())) {
                continue; // nothing was added; stays in the world for a later cycle
            }
            entityStore.removeEntity(ref, RemoveReason.REMOVE);
            inserted += stack.getQuantity();
        }
        refs.clear();
        return inserted;
    }

    /** {@link CollectFilter} over the stack's item definition (only looked up when the filter is on). */
    private boolean accepts(ItemStack stack) {
        Item def = collectAnyItem ? null : stack.getItem();
        return CollectFilter.accepts(collectAnyItem, def != null,
                def != null && def.getTool() != null,
                def != null && def.getWeapon() != null,
                def != null && def.getArmor() != null);
    }

    @Override
    public int harvestBudget() {
        int budget = 0;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                budget += HarvestCycle.slotBudget(0, 0);
            } else {
                Item item = stack.getItem();
                budget += HarvestCycle.slotBudget(stack.getQuantity(), item == null ? 0 : item.getMaxStack());
            }
        }
        return budget;
    }
}
