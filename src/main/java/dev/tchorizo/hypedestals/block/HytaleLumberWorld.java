package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.ContainerRoom;
import dev.tchorizo.hypedestals.harvest.ItemCount;
import dev.tchorizo.hypedestals.lumber.DropSource;
import dev.tchorizo.hypedestals.lumber.LumberWorld;
import dev.tchorizo.hypedestals.lumber.TreePart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link LumberWorld} over one Hytale world, for one Lumberjack cycle. Writes the chunk store
 * directly ({@code BlockOperations.setBlock}), so it must run on the world thread outside a
 * system's tick ({@code world.execute}): placing a sapling adds its {@code FarmingBlock} entity
 * and removing a block with an entity removes it, which the store refuses while a system runs.
 * Not reusable across cycles.
 * Tree blocks are classified by {@link HytaleTrees}.
 */
final class HytaleLumberWorld implements LumberWorld {

    private final ChunkStore chunkStore;
    private final Store<ChunkStore> store;
    private final SectionReader reader;
    private final ItemContainer container;
    private final BlockModule.BlockStateInfo info;
    private final HytaleTrees.Lists trees = HytaleTrees.Lists.current();
    /** Block index → its part, for this cycle (a tree reads the same few ids hundreds of times). */
    private final Map<Integer, TreePart> parts = new HashMap<>();
    /** Section key → whether it is ticking (or not in memory), for this cycle. */
    private final Map<Long, Boolean> tickingSections = new HashMap<>();

    /**
     * @param info the Lumberjack's block state, marked for saving when its container changes
     */
    HytaleLumberWorld(World world, ItemContainer container, BlockModule.BlockStateInfo info) {
        this.chunkStore = world.getChunkStore();
        this.store = chunkStore.getStore();
        this.reader = new SectionReader(chunkStore);
        this.container = container;
        this.info = info;
    }

    private BlockType typeAt(BlockPos p) {
        int id = reader.getBlock(p.x(), p.y(), p.z());
        return id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
    }

    // ---------------------------------------------------------------- TreeWorld

    @Override
    public TreePart part(BlockPos p) {
        if (!ticking(p)) {
            return TreePart.NONE;
        }
        int id = reader.getBlock(p.x(), p.y(), p.z());
        if (id == BlockType.EMPTY_ID) {
            return TreePart.NONE;
        }
        return parts.computeIfAbsent(id, i -> trees.part(BlockType.getAssetMap().getAsset(i)));
    }

    /**
     * In memory and ticking. A section in memory that is not ticking has its block entities
     * parked ({@link ParkedBlocks}): a growing sapling's {@code FarmingBlock} cannot be read there,
     * so such a section reads as not loaded, whatever blocks it holds, and a tree touching it is
     * left alone ({@code unloaded}).
     */
    @Override
    public boolean loaded(BlockPos p) {
        return reader.hasStorageInMemory(p.x(), p.y(), p.z()) && ticking(p);
    }

    /** Whether the position's section is not parked; answered once per section for the cycle. */
    private boolean ticking(BlockPos p) {
        long key = ((long) Math.floorDiv(p.x(), 32) & 0x1FFFFF) << 42 | ((long) Math.floorDiv(p.y(), 32) & 0x1FFFFF) << 21
                | ((long) Math.floorDiv(p.z(), 32) & 0x1FFFFF);
        return tickingSections.computeIfAbsent(key, k -> !ParkedBlocks.notTicking(chunkStore, p.x(), p.y(), p.z()));
    }

    /**
     * A growing sapling keeps its {@code FarmingBlock} on the origin block after each prefab stage,
     * with the sapling as {@code previousBlockType} (spike: {@code Wood_Oak_Branch_Long} with
     * {@code previous=Plant_Sapling_Oak}); a grown tree has none. A stripped trunk regrowing its
     * bark also has a {@code FarmingBlock}, without a previous block type: not growing.
     */
    @Override
    public boolean growing(BlockPos p) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        Ref<ChunkStore> ref = section == null ? null : BlockModule.getBlockEntity(store, section, p.x(), p.y(), p.z());
        FarmingBlock farming = ref == null || !ref.isValid() ? null : store.getComponent(ref, FarmingBlock.getComponentType());
        return farming != null && farming.getPreviousBlockType() != null;
    }

    @Override
    public boolean needsSupport(BlockPos p) {
        BlockType type = typeAt(p);
        return type != null && type.hasSupport();
    }

    @Override
    public boolean soil(BlockPos p) {
        BlockType type = typeAt(p);
        return type != null && HytalePlantables.isSaplingSoil(type);
    }

    @Override
    public boolean solid(BlockPos p) {
        BlockType type = typeAt(p);
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }

    // ---------------------------------------------------------------- LumberWorld

    @Override
    public String blockId(BlockPos p) {
        BlockType type = typeAt(p);
        return type == null ? null : HytaleIds.baseBlockId(type.getId());
    }

    /**
     * One roll of what the block drops when its tree is felled ({@link DropSource}): leaves roll
     * {@code Gathering.Physics} (the drop of leaves that fall with a tree) when it names an item or
     * a drop list, everything else {@code Breaking} (wood), else {@code Soft}, else {@code Harvest}
     * (fruit), else {@code Physics}, through {@code BlockHarvestUtils.getDrops}. Filler parts of a
     * multi-block drop nothing (the origin does).
     */
    @Override
    public List<ItemCount> drops(BlockPos p) {
        BlockType type = typeAt(p);
        BlockGathering g = type == null ? null : type.getGathering();
        if (g == null || reader.getFiller(p.x(), p.y(), p.z()) != 0) {
            return List.of();
        }
        PhysicsDropType fall = g.getPhysics();
        boolean fallDrops = fall != null && (fall.getItemId() != null || fall.getDropListId() != null);
        List<ItemStack> stacks;
        switch (DropSource.pick(part(p), g.getBreaking() != null, g.getSoft() != null, g.getHarvest() != null,
                fall != null, fallDrops)) {
            case BREAKING -> stacks = BlockHarvestUtils.getDrops(type, Math.max(1, g.getBreaking().getQuantity()),
                    g.getBreaking().getItemId(), g.getBreaking().getDropListId());
            case SOFT -> stacks = BlockHarvestUtils.getDrops(type, 1, g.getSoft().getItemId(), g.getSoft().getDropListId());
            case HARVEST -> stacks = BlockHarvestUtils.getDrops(type, 1, g.getHarvest().getItemId(), g.getHarvest().getDropListId());
            case PHYSICS -> stacks = BlockHarvestUtils.getDrops(type, 1, fall.getItemId(), fall.getDropListId());
            default -> {
                return List.of();
            }
        }
        Map<String, Integer> sum = new LinkedHashMap<>();
        for (ItemStack s : stacks) {
            if (s != null && !s.isEmpty()) {
                sum.merge(s.getItemId(), s.getQuantity(), Integer::sum);
            }
        }
        List<ItemCount> out = new ArrayList<>(sum.size());
        sum.forEach((id, n) -> out.add(new ItemCount(id, n)));
        return out;
    }

    @Override
    public ContainerRoom room() {
        return HytaleCycleWorld.roomOf(container);
    }

    /**
     * Grass tufts: the {@code Family=Grass} tag ({@code Plant_Grass_*}, all inheriting
     * {@code Plant_Grass_Lush}: {@code Gathering.Soft} with an empty drop list), without farming
     * state or block entity. Flowers, ferns, mushrooms and saplings are not tufts.
     */
    @Override
    public boolean isTuft(BlockPos p) {
        BlockType type = typeAt(p);
        return type != null && type.getFarming() == null && HytaleTrees.hasTag(type, "Family", "Grass")
                && !BlockEntity.declaresComponent(type, FarmingBlock.getComponentType());
    }

    @Override
    public String soilNextTo(BlockPos p) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockType type = typeAt(new BlockPos(p.x() + d[0], p.y(), p.z() + d[1]));
            if (type != null && HytalePlantables.isSaplingSoil(type) && !HytalePlantables.isFarmland(type)) {
                return HytaleIds.baseBlockId(type.getId());
            }
        }
        return null;
    }

    @Override
    public void remove(BlockPos p) {
        set(p, BlockType.EMPTY_KEY, BlockType.EMPTY);
    }

    @Override
    public void setSoil(BlockPos p, String soilId) {
        BlockType type = BlockType.getAssetMap().getAsset(soilId);
        if (type != null) {
            set(p, soilId, type);
        }
    }

    private boolean set(BlockPos p, String id, BlockType type) {
        Ref<ChunkStore> section = ParkedBlocks.tickingSection(chunkStore, p.x(), p.y(), p.z());
        return section != null && BlockOperations.setBlock(chunkStore, section, p.x(), p.y(), p.z(),
                BlockType.getAssetMap().getIndex(id), type, 0, 0, SetBlockSettings.NONE);
    }

    /**
     * Adds the items in stacks of at most their {@code MaxStack}, all or nothing each. The cycle
     * checked the room first; a stack that still does not fit (the container changed in between,
     * which the world thread rules out) is logged and lost rather than duplicated.
     */
    @Override
    public void store(List<ItemCount> items) {
        for (ItemCount item : items) {
            int left = item.quantity();
            int max = Math.max(1, HytaleCycleWorld.maxStack(item.itemId()));
            while (left > 0) {
                int n = Math.min(left, max);
                ItemStackTransaction tx = container.addItemStack(new ItemStack(item.itemId(), n), true, false, true);
                ItemStack remainder = tx.getRemainder();
                if (!tx.succeeded() || (remainder != null && !remainder.isEmpty())) {
                    HyPedestalsPlugin.warn("Lumberjack: %d %s did not fit after the room check", n, item.itemId());
                }
                left -= n;
            }
        }
        info.markNeedsSaving(store);
    }

    @Override
    public List<String> saplings() {
        List<String> out = new ArrayList<>();
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && !out.contains(stack.getItemId())
                    && HytalePlantables.saplingBlock(stack.getItemId()) != null) {
                out.add(stack.getItemId());
            }
        }
        return out;
    }

    /** Places the sapling's block, then takes one sapling from the lowest slot holding it. */
    @Override
    public boolean plant(BlockPos spot, String saplingItemId) {
        String blockId = HytalePlantables.saplingBlock(saplingItemId);
        BlockType type = blockId == null ? null : BlockType.getAssetMap().getAsset(blockId);
        if (type == null) {
            return false;
        }
        short slot = -1;
        short capacity = container.getCapacity();
        for (short s = 0; s < capacity && slot < 0; s++) {
            ItemStack stack = container.getItemStack(s);
            if (stack != null && !stack.isEmpty() && saplingItemId.equals(stack.getItemId())) {
                slot = s;
            }
        }
        if (slot < 0 || !set(spot, blockId, type)) {
            return false;
        }
        container.removeItemStackFromSlot(slot, 1);
        info.markNeedsSaving(store);
        return true;
    }

    // ---------------------------------------------------------------- SaplingRule.Ground

    /**
     * Air in a section that is in memory: a section that is not loaded reads as air from the
     * reader, and a sapling's clearance ring or column crossing into one would be checked against
     * nothing (the grid's points near a chunk edge), so it counts as occupied.
     */
    @Override
    public boolean isEmpty(BlockPos p) {
        return reader.hasStorageInMemory(p.x(), p.y(), p.z())
                && reader.getBlock(p.x(), p.y(), p.z()) == BlockType.EMPTY_ID;
    }

    @Override
    public boolean isSaplingSoil(BlockPos p) {
        BlockType type = typeAt(p);
        return type != null && HytalePlantables.isSaplingSoil(type);
    }

    @Override
    public boolean isFarmland(BlockPos p) {
        BlockType type = typeAt(p);
        return type != null && HytalePlantables.isFarmland(type);
    }
}
