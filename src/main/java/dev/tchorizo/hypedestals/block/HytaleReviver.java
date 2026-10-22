package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.builtin.adventure.farming.config.stages.BlockStateFarmingStageData;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.ReviveRule;
import dev.tchorizo.hypedestals.harvest.RevivePass;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RevivePass.ReviveWorld} over one Hytale world: gives a young crop that lost its
 * {@code FarmingBlock} entity a new one in the stage it stands in ({@link ReviveRule}), so the
 * game's farming tick grows it again. The entity is made like {@code FarmingUtil.harvest0} makes
 * one (a {@code Holder} with {@code BlockStateInfo} and {@code FarmingBlock}) but added to the
 * chunk store itself, outside the tick of any system: the Harvester's tick only {@link #request}s
 * the pass and it runs through {@code world.execute}, like the Planter. Every write is preceded by
 * the live/parked/ticking reads of {@link RevivePass}, on the same thread with nothing in between.
 */
final class HytaleReviver implements RevivePass.ReviveWorld {

    /** The stage layout of one crop block, computed once per block id and pass. */
    private record Layout(Map<String, List<String>> sets, String afterHarvest, String starting) {}

    private final ChunkStore chunkStore;
    private final Store<ChunkStore> store;
    private final SectionReader reader;
    private final Instant now;
    private final Map<Integer, Layout> layouts = new HashMap<>();

    private HytaleReviver(World world) {
        this.chunkStore = world.getChunkStore();
        this.store = chunkStore.getStore();
        this.reader = new SectionReader(chunkStore);
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        this.now = time == null ? null : time.getGameTime();
    }

    /** Schedules a revival pass for the Harvester at {@code center} after the current tick. */
    static void request(World world, BlockPos center, int radius) {
        world.execute(() -> {
            try {
                run(world, center, radius);
            } catch (RuntimeException e) {
                HyPedestalsPlugin.warn("reviver at %d %d %d failed: %s", center.x(), center.y(), center.z(), e.toString());
            }
        });
    }

    private static void run(World world, BlockPos center, int radius) {
        HytaleReviver reviver = new HytaleReviver(world);
        if (reviver.now == null) {
            return;
        }
        int revived = RevivePass.run(reviver, center, radius);
        Store<ChunkStore> store = reviver.store;
        Ref<ChunkStore> section = reviver.chunkStore.getChunkSectionReferenceAtBlock(center.x(), center.y(), center.z());
        Ref<ChunkStore> ref = section == null ? null : BlockModule.getBlockEntity(store, section, center.x(), center.y(), center.z());
        HarvesterComponent harvester = ref == null ? null : store.getComponent(ref, HarvesterComponent.TYPE);
        if (harvester != null) {
            harvester.lastRevived = revived;
            harvester.revivedTotal += revived;
        }
        if (revived > 0) {
            HyPedestalsPlugin.log("Harvester at %d %d %d revived %d crop(s) without FarmingBlock", center.x(), center.y(), center.z(), revived);
        }
    }

    @Override
    public ReviveRule.Stage stageAt(BlockPos p) {
        if (!reader.hasStorageInMemory(p.x(), p.y(), p.z()) || HytaleCycleWorld.cropTypeAt(reader, p) == null) {
            return null;
        }
        int id = reader.getBlock(p.x(), p.y(), p.z());
        BlockType block = BlockType.getAssetMap().getAsset(id);
        Layout layout = layouts.computeIfAbsent(id, i -> layout(block));
        if (layout == null) {
            return null;
        }
        String state = HytaleIds.stateName(block.getId());
        return ReviveRule.stageOf(layout.sets(), layout.afterHarvest(), layout.starting(), state == null ? "default" : state);
    }

    private static Layout layout(BlockType block) {
        FarmingData farming = HytaleCycleWorld.farmingData(block, HytaleIds.baseBlockId(block.getId()));
        if (farming == null || farming.getStages() == null) {
            return null;
        }
        Map<String, List<String>> sets = new LinkedHashMap<>();
        for (Map.Entry<String, FarmingStageData[]> e : farming.getStages().entrySet()) {
            List<String> states = new ArrayList<>();
            for (FarmingStageData stage : e.getValue() == null ? new FarmingStageData[0] : e.getValue()) {
                states.add(stage instanceof BlockStateFarmingStageData blockState ? blockState.getState() : null);
            }
            sets.put(e.getKey(), states);
        }
        return new Layout(sets, farming.getStageSetAfterHarvest(), farming.getStartingStageSet());
    }

    @Override
    public boolean liveEntityAt(BlockPos p) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        return section != null && section.isValid() && BlockModule.getBlockEntity(store, section, p.x(), p.y(), p.z()) != null;
    }

    @Override
    public boolean parkedEntityAt(BlockPos p) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        return ParkedBlocks.holderAt(store, section, p.x(), p.y(), p.z());
    }

    @Override
    public boolean ticking(BlockPos p) {
        return ParkedBlocks.tickingSection(chunkStore, p.x(), p.y(), p.z()) != null;
    }

    @Override
    public boolean revive(BlockPos p, ReviveRule.Stage stage) {
        Ref<ChunkStore> section = ParkedBlocks.tickingSection(chunkStore, p.x(), p.y(), p.z());
        if (section == null) {
            return false;
        }
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.putComponent(BlockModule.BlockStateInfo.getComponentType(),
                new BlockModule.BlockStateInfo(ChunkUtil.indexBlock(p.x(), p.y(), p.z()), section));
        FarmingBlock farming = new FarmingBlock();
        farming.setCurrentStageSet(stage.stageSet());
        farming.setGrowthProgress(stage.index());
        farming.setLastTickGameTime(now);
        holder.addComponent(FarmingBlock.getComponentType(), farming);
        Ref<ChunkStore> ref = store.addEntity(holder, AddReason.SPAWN);
        BlockModule.BlockStateInfo info = ref == null ? null
                : store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving(store);
        }
        return ref != null;
    }
}
