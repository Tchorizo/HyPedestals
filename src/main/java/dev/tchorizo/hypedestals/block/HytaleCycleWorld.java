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

/** {@link CycleWorld} over one Hytale world, for a single cycle of one harvester. Not reusable across ticks. */
final class HytaleCycleWorld implements CycleWorld {

    /** Message of the {@link IllegalStateException} thrown by {@code Store.assertWriteProcessing}. */
    private static final String STORE_PROCESSING = "Store is currently processing";

    private final CommandBuffer<ChunkStore> chunkBuffer;
    private final ChunkStore chunkStore;
    private final Store<EntityStore> entityStore;
    private final SectionReader reader;
    private final ItemContainer container;
    /** Block state of the Harvester that owns {@link #container}. */
    private final BlockModule.BlockStateInfo info;
    private final boolean collectAnyItem;

    /**
     * @param chunkBuffer the command buffer of the current {@code ChunkStore} tick; harvesting
     *     must go through it rather than the chunk store itself while the system is ticking
     * @param info block state of the Harvester whose container this is; marked for saving
     *     whenever collected items go into the container
     * @param collectAnyItem the config's {@code CollectAnyItem}; see {@link CollectFilter}
     */
    HytaleCycleWorld(World world, CommandBuffer<ChunkStore> chunkBuffer, ItemContainer container,
                     BlockModule.BlockStateInfo info, boolean collectAnyItem) {
        this.chunkBuffer = chunkBuffer;
        this.chunkStore = world.getChunkStore();
        this.entityStore = world.getEntityStore().getStore();
        this.reader = new SectionReader(chunkStore);
        this.container = container;
        this.info = info;
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
        return cropTypeAt(reader, p);
    }

    /** {@link #cropTypeAt(BlockPos)} over any reader; also the soil pass's "crop above" test. */
    static String cropTypeAt(SectionReader reader, BlockPos p) {
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
     * Harvest that does not throw for the known "store busy" case. Observed on 0.7.2 with plain
     * {@code Plant_Crop_Wheat_Block} (no {@code StageSetAfterHarvest}) harvested from the
     * Harvester's tick a few seconds after {@code dev ripen}: {@code FarmingUtil.harvest} spawned
     * the drops, set the block to air and then threw
     * {@code IllegalStateException: Store is currently processing! …} from {@code Store.removeEntity}
     * (stack trace through {@code FarmingUtil.harvest} → {@code BlockOperations.setBlock} →
     * {@code BlockEntity.setBlockEntity}), leaving the crop's block entity behind. The same crop
     * harvested once its block entity was gone (the farming tick removes it after the last stage:
     * about one tick for a naturally ripened crop) came off cleanly, and crops that regrow
     * ({@code StageSetAfterHarvest}, e.g. the Eternal ones) never threw. So such a position is
     * skipped as {@link HarvestOutcome#TRANSIENT_ERROR} before anything happens
     * ({@link #removalBlockedByBlockEntity}), and the exception itself, should a case slip past
     * that check, is classified the same way by its message.
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
     * The externally observable rule behind the "store busy" failure (see {@link #harvest}): a
     * harvestable block ({@code Gathering.Harvest}) that does not regrow in place (no
     * {@code StageSetAfterHarvest} naming a non-empty stage set) and still has a block entity.
     */
    private boolean removalBlockedByBlockEntity(BlockPos p) {
        BlockType block = BlockType.getAssetMap().getAsset(reader.getBlock(p.x(), p.y(), p.z()));
        BlockGathering gathering = block == null ? null : block.getGathering();
        if (gathering == null || gathering.getHarvest() == null) {
            return false; // nothing to harvest: harvest returns false without touching the block
        }
        FarmingData farming = block.getFarming();
        if (farming != null && farming.getStages() != null && farming.getStageSetAfterHarvest() != null) {
            FarmingStageData[] after = farming.getStages().get(farming.getStageSetAfterHarvest());
            if (after != null && after.length > 0) {
                return false; // regrows in place
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
     * drops roll a block or two past the crop they came from. When anything went in, the Harvester
     * is marked for saving. The engine already does that on every content change of a block
     * container (a change listener registered when the block entity is added); the explicit mark
     * keeps collected items safe even for a container that listener does not watch (e.g. one
     * swapped in after the block entity was added).
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
        if (inserted > 0) {
            info.markNeedsSaving(chunkBuffer);
        }
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
