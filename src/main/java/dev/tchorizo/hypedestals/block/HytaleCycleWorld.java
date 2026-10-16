package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.builtin.adventure.farming.FarmingUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.CycleWorld;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;

/** {@link CycleWorld} over one Hytale world, for a single cycle of one harvester. Not reusable across ticks. */
final class HytaleCycleWorld implements CycleWorld {

    private final CommandBuffer<ChunkStore> chunkBuffer;
    private final Store<EntityStore> entityStore;
    private final SectionReader reader;
    private final ItemContainer container;

    /**
     * @param chunkBuffer the command buffer of the current {@code ChunkStore} tick; harvesting
     *     must go through it rather than the chunk store itself while the system is ticking
     */
    HytaleCycleWorld(World world, CommandBuffer<ChunkStore> chunkBuffer, ItemContainer container) {
        this.chunkBuffer = chunkBuffer;
        this.entityStore = world.getEntityStore().getStore();
        this.reader = new SectionReader(world.getChunkStore());
        this.container = container;
    }

    /**
     * Base crop id (e.g. {@code Plant_Crop_Wheat_Block}) so every growth state of a crop shares one
     * id for {@code warnedTypes}. A state variant may not carry the farming config itself, so the
     * base asset is consulted when the variant has none.
     */
    @Override
    public String cropTypeAt(BlockPos p) {
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

    @Override
    public boolean harvest(BlockPos p) {
        try {
            return FarmingUtil.harvest(chunkBuffer, entityStore, null, new Vector3i(p.x(), p.y(), p.z()));
        } catch (RuntimeException e) {
            // HarvestCycle keeps only the crop type; log the cause here, once per type thanks to warnedTypes.
            HyPedestalsPlugin.warn("harvest failed at %s (%s): %s", p, cropTypeAt(p), e.toString());
            throw e;
        }
    }

    @Override
    public int collectDroppedItems(BlockPos center, double radius) {
        var spatial = entityStore.getResource(EntityModule.get().getItemSpatialResourceType());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return 0;
        }
        List<Ref<EntityStore>> refs = SpatialResource.getThreadLocalReferenceList();
        refs.clear();
        spatial.getSpatialStructure().collect(
                new Vector3d(center.x() + 0.5, center.y() + 0.5, center.z() + 0.5), radius, refs);

        int inserted = 0;
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            ItemComponent item = entityStore.getComponent(ref, ItemComponent.getComponentType());
            ItemStack stack = item == null ? null : item.getItemStack();
            if (stack == null || stack.isEmpty()) {
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

    @Override
    public int freeSlots() {
        int free = 0;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                free++;
            }
        }
        return free;
    }
}
