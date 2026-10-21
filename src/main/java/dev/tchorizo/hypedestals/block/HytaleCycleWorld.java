package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.builtin.adventure.farming.FarmingUtil;
import com.hypixel.hytale.builtin.adventure.farming.config.stages.PrefabFarmingStageData;
import com.hypixel.hytale.builtin.adventure.farming.config.stages.spread.SpreadFarmingStageData;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.CollectFilter;
import dev.tchorizo.hypedestals.harvest.ContainerRoom;
import dev.tchorizo.hypedestals.harvest.CycleWorld;
import dev.tchorizo.hypedestals.harvest.HarvestAreas;
import dev.tchorizo.hypedestals.harvest.HarvestCycle;
import dev.tchorizo.hypedestals.harvest.HarvestOutcome;
import dev.tchorizo.hypedestals.harvest.HarvestTargetRule;
import dev.tchorizo.hypedestals.harvest.ItemCount;
import dev.tchorizo.hypedestals.harvest.PendingDrops;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** {@link CycleWorld} over one Hytale world, for a single cycle of one harvester. Not reusable across ticks. */
final class HytaleCycleWorld implements CycleWorld {

    /** Message of the {@link IllegalStateException} thrown by {@code Store.assertWriteProcessing}. */
    private static final String STORE_PROCESSING = "Store is currently processing";
    /** Block types whose harvest drop list did not resolve; warned about once each (worlds tick on their own threads). */
    private static final Set<String> UNRESOLVED_DROPS_WARNED = ConcurrentHashMap.newKeySet();

    private final CommandBuffer<ChunkStore> chunkBuffer;
    private final ChunkStore chunkStore;
    private final Store<EntityStore> entityStore;
    private final SectionReader reader;
    private final ItemContainer container;
    /** Block state of the Harvester that owns {@link #container}. */
    private final BlockModule.BlockStateInfo info;
    private final boolean collectAnyItem;
    /** The Harvester's reservations still on their way; told about everything the collects put in or leave behind. */
    private final PendingDrops pending;
    /** Active Harvester areas of the world; drops inside a neighbour's area are left to it. */
    private final HarvestAreas areas;
    /** The Harvester's harvest radius (its own area, for {@link HarvestAreas#mayCollect}). */
    private final int radius;
    /** World tick of this cycle (or burst tick), for the areas' expiry and the per-tick harvest claims. */
    private final long tick;
    /** Scratch list of the neighbours reaching into the collect box. */
    private final List<HarvestAreas.Area> neighbours = new ArrayList<>();
    /** Time spent in {@link #collectDroppedItems} so far; see {@link #collectNanos()}. */
    private long collectNanos;
    /** Items the collects found that they could take (inserted or not); see {@link #itemsSeen()}. */
    private int itemsSeen;
    /** {@link #maxHarvestDrops} per block type id, for this cycle; created on first use (a burst tick never needs it). */
    private Map<String, List<ItemCount>> maxDropsByBlock;

    /**
     * @param chunkBuffer the command buffer of the current {@code ChunkStore} tick; harvesting
     *     must go through it rather than the chunk store itself while the system is ticking
     * @param info block state of the Harvester whose container this is; marked for saving
     *     whenever collected items go into the container
     * @param collectAnyItem the config's {@code CollectAnyItem}; see {@link CollectFilter}
     * @param pending the Harvester's {@link PendingDrops}
     * @param areas the world's active Harvester areas ({@link HarvesterSystem#areas})
     * @param radius the Harvester's harvest radius
     */
    HytaleCycleWorld(World world, CommandBuffer<ChunkStore> chunkBuffer, ItemContainer container,
                     BlockModule.BlockStateInfo info, boolean collectAnyItem, PendingDrops pending,
                     HarvestAreas areas, int radius) {
        this.chunkBuffer = chunkBuffer;
        this.chunkStore = world.getChunkStore();
        this.entityStore = world.getEntityStore().getStore();
        this.reader = new SectionReader(chunkStore);
        this.container = container;
        this.info = info;
        this.collectAnyItem = collectAnyItem;
        this.pending = pending;
        this.areas = areas;
        this.radius = radius;
        this.tick = world.getTick();
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

    /** {@code SectionReader.getBlock} reads 0 (empty) where {@code hasStorageInMemory} is false [JAR]. */
    @Override
    public boolean loaded(BlockPos p) {
        return reader.hasStorageInMemory(p.x(), p.y(), p.z());
    }

    /** {@link #cropTypeAt(BlockPos)} over any reader, for the harvest scan only. */
    static String cropTypeAt(SectionReader reader, BlockPos p) {
        if (reader.getFiller(p.x(), p.y(), p.z()) != 0) {
            return null;
        }
        BlockType block = BlockType.getAssetMap().getAsset(reader.getBlock(p.x(), p.y(), p.z()));
        if (block == null) {
            return null;
        }
        String baseId = HytaleIds.baseBlockId(block.getId());
        return isHarvestTarget(farmingData(block, baseId)) ? baseId : null;
    }

    /** Whether a growing farming block occupies the origin position, including Prefab and Spread growers. */
    static boolean growingFarmingBlockAt(SectionReader reader, BlockPos p) {
        if (reader.getFiller(p.x(), p.y(), p.z()) != 0) {
            return false;
        }
        BlockType block = BlockType.getAssetMap().getAsset(reader.getBlock(p.x(), p.y(), p.z()));
        if (block == null) {
            return false;
        }
        FarmingData farming = farmingData(block, HytaleIds.baseBlockId(block.getId()));
        return HarvestTargetRule.isGrowing(farming != null, hasGrowthStages(farming));
    }

    /** Finds farming metadata on a state variant or its base asset. */
    private static FarmingData farmingData(BlockType block, String baseId) {
        FarmingData farming = block.getFarming();
        if (farming != null) {
            return farming;
        }
        BlockType base = BlockType.getAssetMap().getAsset(baseId);
        return base == null ? null : base.getFarming();
    }

    /** Excludes farming infrastructure and growth seeds that harvesting would destroy. */
    private static boolean isHarvestTarget(FarmingData farming) {
        boolean hasStages = false;
        boolean hasPrefabOrSpread = false;
        if (farming != null && farming.getStages() != null) {
            for (FarmingStageData[] stages : farming.getStages().values()) {
                if (stages == null || stages.length == 0) {
                    continue;
                }
                hasStages = true;
                for (FarmingStageData stage : stages) {
                    if (stage instanceof PrefabFarmingStageData || stage instanceof SpreadFarmingStageData) {
                        hasPrefabOrSpread = true;
                        break;
                    }
                }
            }
        }
        return HarvestTargetRule.isTarget(farming != null, hasStages, hasPrefabOrSpread);
    }

    /** Whether a farming asset exposes at least one non-empty growth stage set. */
    private static boolean hasGrowthStages(FarmingData farming) {
        if (farming == null || farming.getStages() == null) {
            return false;
        }
        for (FarmingStageData[] stages : farming.getStages().values()) {
            if (stages != null && stages.length > 0) {
                return true;
            }
        }
        return false;
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
     *
     * <p>A ripe block whose entity is parked ({@link #blockEntityParked}: the section is in memory
     * but not ticking) is {@link HarvestOutcome#UNLOADED}. {@code FarmingUtil.harvest} [JAR] looks
     * the block's entity up among the live ones only ({@code BlockModule.getBlockEntity}), finds
     * none and queues {@code addEntity} of a new {@code FarmingBlock}; when the tick's command
     * buffer is consumed {@code BlockComponentSection.addBlockReference} meets the parked
     * {@code Holder} of that index and throws {@code Duplicate block components at: <idx>}, which
     * stops the world (Task 40). Nothing is touched, and a later cycle that finds the entity live
     * again harvests the crop.
     *
     * <p>A position another Harvester of the world already harvested in this world tick (areas
     * overlap and both cycles fell on the same tick) is {@link HarvestOutcome#NOT_READY}: a second
     * {@code harvest} of the block in the same tick crashes the world when the tick's command
     * buffer is consumed ({@link HarvestAreas#claimHarvest}).
     */
    @Override
    public HarvestOutcome harvest(BlockPos p) {
        if (blockEntityParked(p)) {
            return HarvestOutcome.UNLOADED;
        }
        if (removalBlockedByBlockEntity(p)) {
            return HarvestOutcome.TRANSIENT_ERROR;
        }
        if (!areas.claimHarvest(p, tick)) {
            return HarvestOutcome.NOT_READY;
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
     * Whether the block's entity sits parked in its section ({@code BlockComponentSection#getBlockHolder}
     * non-null) instead of live: what the engine does to every block entity of a section in memory
     * that is not ticking ({@code NonTicking}, [JAR] {@code BlockComponentSectionLoadingSystem}),
     * for example the part of an area that crosses into a chunk no player is near. A block with
     * both a ripe state and a parked entity cannot be harvested with {@code FarmingUtil.harvest}
     * (see {@link #harvest}).
     */
    private boolean blockEntityParked(BlockPos p) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        if (section == null || !section.isValid()) {
            return false;
        }
        BlockComponentSection components = chunkBuffer.getComponent(section, BlockComponentSection.getComponentType());
        return components != null && components.getBlockHolder(ChunkUtil.indexBlock(p.x(), p.y(), p.z())) != null;
    }

    /**
     * Collects dropped items inside an axis-aligned box around {@code center}: {@code halfExtent}
     * blocks horizontally and {@link HarvestAreas#BELOW}/{@link HarvestAreas#ABOVE} vertically
     * ({@code -2..+3}). A box (not a sphere) is used because the
     * harvest area is a square: with a sphere the corners of larger tiers fall outside it, and
     * drops roll a block or two past the crop they came from. Items inside the area of another
     * active Harvester and outside this one's are left to it ({@link HarvestAreas#mayCollect},
     * by the item's {@code TransformComponent} position; only read when a neighbour's area
     * reaches into the box). A stack that does not fit is reported to
     * {@link PendingDrops#collectBlocked}. When anything went in, the Harvester
     * is marked for saving. The engine already does that on every content change of a block
     * container (a change listener registered when the block entity is added); the explicit mark
     * keeps collected items safe even for a container that listener does not watch (e.g. one
     * swapped in after the block entity was added).
     */
    @Override
    public int collectDroppedItems(BlockPos center, double halfExtent) {
        long start = System.nanoTime();
        try {
            return collect(center, halfExtent);
        } finally {
            collectNanos += System.nanoTime() - start;
        }
    }

    /** Nanoseconds spent collecting through this instance (both collects of a cycle). */
    long collectNanos() {
        return collectNanos;
    }

    /**
     * Items the collects through this instance found in the box and could take (the item filter
     * passed), whether they went in or not. With nothing inserted, a positive count means what is
     * there does not fit; {@code 0} means the box was empty (or the index has not registered the
     * drops yet).
     */
    int itemsSeen() {
        return itemsSeen;
    }

    private int collect(BlockPos center, double halfExtent) {
        var spatial = entityStore.getResource(EntityModule.get().getItemSpatialResourceType());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return 0;
        }
        List<Ref<EntityStore>> refs = SpatialResource.getThreadLocalReferenceList();
        refs.clear();
        double cx = center.x() + 0.5, cz = center.z() + 0.5;
        spatial.getSpatialStructure().collectBox(
                new Vector3d(cx - halfExtent, center.y() - HarvestAreas.BELOW, cz - halfExtent),
                new Vector3d(cx + halfExtent, center.y() + HarvestAreas.ABOVE, cz + halfExtent), refs);
        areas.neighbours(center, halfExtent, tick, neighbours);

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
            if (!neighbours.isEmpty()) {
                TransformComponent transform = entityStore.getComponent(ref, TransformComponent.getComponentType());
                Vector3d at = transform == null ? null : transform.getPosition();
                if (at != null && !HarvestAreas.mayCollect(center, radius, neighbours, at.x, at.y, at.z)) {
                    continue; // a neighbour's drop: it collects it
                }
            }
            itemsSeen += stack.getQuantity();
            // All-or-nothing (the container default is partial): a partial insert would leave the
            // full stack on the ground too and duplicate the difference. The remainder check backs
            // up succeeded(): the entity is only removed once the whole stack is in the container.
            ItemStackTransaction tx = container.addItemStack(stack, true, false, true);
            ItemStack remainder = tx.getRemainder();
            if (!tx.succeeded() || (remainder != null && !remainder.isEmpty())) {
                pending.collectBlocked();
                continue; // nothing was added; stays in the world for a later cycle
            }
            entityStore.removeEntity(ref, RemoveReason.REMOVE);
            inserted += stack.getQuantity();
            pending.collected(stack.getItemId(), stack.getQuantity());
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
    public boolean containerFull() {
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            Item item = stack.getItem();
            if (HarvestCycle.slotHasRoom(stack.getQuantity(), item == null ? 0 : item.getMaxStack())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ContainerRoom room() {
        return roomOf(container);
    }

    /**
     * Empty slots of {@code container}, and per item id the room left in its partial stacks. A
     * partial stack counts only when a fresh stack of its item (as a drop is) would merge with it
     * ({@code isStackableWith}: same durability, quality and metadata), so a worn tool or a stack
     * with metadata does not pass for room that drops could use. Also the Lumberjack's room.
     */
    static ContainerRoom roomOf(ItemContainer container) {
        int free = 0;
        Map<String, Integer> partial = new HashMap<>();
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                free++;
                continue;
            }
            Item item = stack.getItem();
            int maxStack = item == null ? 0 : item.getMaxStack();
            if (HarvestCycle.slotHasRoom(stack.getQuantity(), maxStack)
                    && stack.isStackableWith(new ItemStack(stack.getItemId(), 1))) {
                partial.merge(stack.getItemId(), maxStack - stack.getQuantity(), Integer::sum);
            }
        }
        return new ContainerRoom(free, partial, HytaleCycleWorld::maxStack);
    }

    static int maxStack(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? 0 : item.getMaxStack();
    }

    @Override
    public List<ItemCount> maxDrops(BlockPos p) {
        BlockType block = BlockType.getAssetMap().getAsset(reader.getBlock(p.x(), p.y(), p.z()));
        if (block == null) {
            return List.of();
        }
        List<ItemCount> drops = maxDropsByBlock == null ? null : maxDropsByBlock.get(block.getId());
        if (drops == null) {
            drops = maxHarvestDrops(block);
            if (maxDropsByBlock == null) {
                maxDropsByBlock = new HashMap<>();
            }
            maxDropsByBlock.put(block.getId(), drops);
        }
        return drops;
    }

    /**
     * The most a harvest of {@code block} drops, following {@code FarmingUtil.harvest}: it gives
     * {@code BlockHarvestUtils.getDrops(block, 1, harvest.getItemId(), harvest.getDropListId())}
     * [JAR], that is one roll of the drop list plus one of the item, or one of the block's own item
     * when the harvest names neither. Every item the drop list can roll counts, at its
     * {@code QuantityMax} ({@code getAllDrops}): an upper bound for the {@code Multiple}/
     * {@code Single} lists of the vanilla crops [ASSET]. A {@code Choice} list with more than one
     * roll would be under-counted ({@code getAllDrops} lists each choice once); no crop uses one.
     *
     * <p>A drop list that does not resolve (unknown id, or no container) on a harvest without an
     * {@code ItemId} does not mean "drops nothing": what the game drops is unknown, so a full stack
     * of the block's item is reserved (one slot of its own) and the type is warned about once.
     */
    private static List<ItemCount> maxHarvestDrops(BlockType block) {
        BlockGathering gathering = block.getGathering();
        HarvestingDropType harvest = gathering == null ? null : gathering.getHarvest();
        if (harvest == null) {
            return List.of(); // harvest returns false without dropping anything
        }
        if (harvest.getItemId() == null && harvest.getDropListId() == null) {
            Item item = block.getItem();
            return item == null ? List.of() : List.of(new ItemCount(item.getId(), 1));
        }
        Map<String, Integer> drops = new LinkedHashMap<>();
        ItemDropList list = harvest.getDropListId() == null ? null
                : ItemDropList.getAssetMap().getAsset(harvest.getDropListId());
        if (list != null && list.getContainer() != null) {
            for (ItemDrop drop : list.getContainer().getAllDrops(new ArrayList<>())) {
                if (drop != null && drop.getItemId() != null) {
                    drops.merge(drop.getItemId(),
                            Math.max(1, Math.max(drop.getQuantityMin(), drop.getQuantityMax())), Integer::sum);
                }
            }
        } else if (harvest.getItemId() == null) {
            return unresolvedDropList(block, harvest.getDropListId());
        }
        if (harvest.getItemId() != null) {
            drops.merge(harvest.getItemId(), 1, Integer::sum);
        }
        // One entry per item id (CycleWorld.maxDrops): the calibration learns per item.
        List<ItemCount> merged = new ArrayList<>(drops.size());
        drops.forEach((id, quantity) -> merged.add(new ItemCount(id, quantity)));
        return List.copyOf(merged);
    }

    /**
     * Conservative bound for a harvest whose drop list did not resolve: one full stack of the
     * block's item, or one slot (unknown stack size, see {@link ContainerRoom}) under the block's
     * id when it has no item.
     */
    private static List<ItemCount> unresolvedDropList(BlockType block, String dropListId) {
        if (UNRESOLVED_DROPS_WARNED.add(block.getId())) {
            HyPedestalsPlugin.warn("harvest drop list %s of %s did not resolve; reserving a full stack of room per harvest",
                    dropListId, block.getId());
        }
        Item item = block.getItem();
        return item == null ? List.of(new ItemCount(block.getId(), 1))
                : List.of(new ItemCount(item.getId(), Math.max(1, item.getMaxStack())));
    }
}
