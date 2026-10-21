package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.PlantPass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link PlantPass} over one Hytale world, for the Planter upgrade. It writes blocks with
 * {@code BlockOperations.setBlock} on the chunk store itself, which is not allowed while a system
 * of that store is running, so the Harvester's tick only {@link #request}s a pass and this runs it
 * through {@code world.execute}, after the tick, re-reading the Harvester (it may be gone) and its
 * container then. A crop placed by {@code setBlock} gets its {@code FarmingBlock} from the block
 * type, as a player's seed does; the soil's {@code planted} flag is set by the vanilla soil tick
 * either way (planter spec §0 S5). Positions in a section that is not loaded count as occupied.
 */
final class HytalePlanter implements PlantPass.PlantWorld {

    private final ChunkStore chunkStore;
    private final SectionReader reader;

    private HytalePlanter(World world) {
        this.chunkStore = world.getChunkStore();
        this.reader = new SectionReader(chunkStore);
    }

    /**
     * Schedules a planting pass for the Harvester at {@code center} after the current tick.
     *
     * @param saplingSpacing the config's {@code SaplingSpacing}
     */
    static void request(World world, BlockPos center, int radius, int saplingSpacing) {
        world.execute(() -> {
            try {
                run(world, center, radius, saplingSpacing);
            } catch (RuntimeException e) {
                HyPedestalsPlugin.warn("planter at %d %d %d failed: %s", center.x(), center.y(), center.z(), e.toString());
            }
        });
    }

    private static void run(World world, BlockPos center, int radius, int saplingSpacing) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(center.x(), center.y(), center.z());
        Ref<ChunkStore> ref = section == null ? null
                : BlockModule.getBlockEntity(store, section, center.x(), center.y(), center.z());
        HarvesterComponent harvester = ref == null ? null : store.getComponent(ref, HarvesterComponent.TYPE);
        ItemContainerBlock block = ref == null ? null : store.getComponent(ref, ItemContainerBlock.getComponentType());
        if (harvester == null || block == null || block.getItemContainer() == null
                || !harvester.hasUpgrade(dev.tchorizo.hypedestals.harvest.UpgradeType.PLANTER)) {
            return;
        }
        ItemContainer container = block.getItemContainer();
        List<PlantPass.Stack> stacks = new ArrayList<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && HytalePlantables.isPlantable(stack.getItemId())) {
                stacks.add(new PlantPass.Stack(slot, stack.getItemId(), stack.getQuantity()));
            }
        }
        PlantPass.Result result = stacks.isEmpty() ? PlantPass.Result.NONE
                : PlantPass.run(new HytalePlanter(world), stacks, center, radius, saplingSpacing);
        if (!result.taken().isEmpty()) {
            take(container, result.taken());
            BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
            if (info != null) {
                info.markNeedsSaving(store);
            }
        }
        harvester.lastPlant = result;
        harvester.seedsPlantedTotal += result.seeds();
        harvester.saplingsPlantedTotal += result.saplings();
    }

    /** Removes {@code count} items from each slot (seeds and saplings have no durability). */
    private static void take(ItemContainer container, Map<Integer, Integer> taken) {
        for (Map.Entry<Integer, Integer> e : taken.entrySet()) {
            short slot = (short) (int) e.getKey();
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            int rest = stack.getQuantity() - e.getValue();
            if (rest <= 0) {
                container.removeItemStackFromSlot(slot);
            } else {
                container.setItemStackForSlot(slot, stack.withQuantity(rest));
            }
        }
    }

    private BlockType type(BlockPos p) {
        if (chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z()) == null) {
            return null;
        }
        int id = reader.getBlock(p.x(), p.y(), p.z());
        return id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
    }

    @Override
    public boolean isEmpty(BlockPos p) {
        return chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z()) != null
                && reader.getBlock(p.x(), p.y(), p.z()) == BlockType.EMPTY_ID;
    }

    @Override
    public boolean isSaplingSoil(BlockPos p) {
        return HytalePlantables.isSaplingSoil(type(p));
    }

    @Override
    public boolean isFarmland(BlockPos p) {
        return HytalePlantables.isFarmland(type(p));
    }

    @Override
    public String seedBlockOn(String itemId, BlockPos soil) {
        BlockType type = type(soil);
        // The upper half of a tall block (filler) is not a surface anything was planted on.
        return type == null || reader.getFiller(soil.x(), soil.y(), soil.z()) != 0 ? null
                : HytalePlantables.seedBlockOn(itemId, type);
    }

    @Override
    public String saplingBlock(String itemId) {
        return HytalePlantables.saplingBlock(itemId);
    }

    @Override
    public boolean place(BlockPos p, String blockId) {
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        return type != null && section != null && BlockOperations.setBlock(chunkStore, section, p.x(), p.y(), p.z(),
                BlockType.getAssetMap().getIndex(blockId), type, RotationTuple.NONE_INDEX, 0, SetBlockSettings.NONE);
    }
}
