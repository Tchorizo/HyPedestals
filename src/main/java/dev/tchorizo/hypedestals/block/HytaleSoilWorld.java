package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.SoilWorld;

import java.time.Instant;

/**
 * {@link SoilWorld} over the {@code TilledSoilBlock} components of one Hytale world. Soil whose
 * section is not loaded, or that has no block entity, counts as absent (never created here).
 */
public final class HytaleSoilWorld implements SoilWorld {

    private final ChunkStore chunkStore;
    private final ComponentAccessor<ChunkStore> accessor;

    /**
     * @param accessor the command buffer of the current {@code ChunkStore} tick, or the chunk
     *     store itself outside a tick (commands)
     */
    public HytaleSoilWorld(World world, ComponentAccessor<ChunkStore> accessor) {
        this.chunkStore = world.getChunkStore();
        this.accessor = accessor;
    }

    /** The world's game time (the clock {@code TilledSoilBlock.wateredUntil} is in), or {@code null} if unavailable. */
    public static Instant gameTime(World world) {
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        return time == null ? null : time.getGameTime();
    }

    @Override
    public Soil soilAt(BlockPos p) {
        TilledSoilBlock soil = component(p);
        return soil == null ? null : new Soil(soil.getWateredUntil(), soil.isFertilized());
    }

    /**
     * Sets {@code wateredUntil} like the vanilla watering can: the block is set ticking with a
     * tick scheduled at {@code until}, so the soil switches to its watered look now and back to dry
     * when the watering ends; then marked for saving, since this mutation is outside the engine's
     * own flows (AGENTS.md, spike S4).
     */
    @Override
    public void water(BlockPos p, Instant until) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        Ref<ChunkStore> ref = section == null ? null : BlockModule.getBlockEntity(accessor, section, p.x(), p.y(), p.z());
        TilledSoilBlock soil = ref == null ? null : accessor.getComponent(ref, TilledSoilBlock.getComponentType());
        if (soil == null) {
            return;
        }
        soil.setWateredUntil(until);
        BlockSection blocks = accessor.getComponent(section, BlockSection.getComponentType());
        if (blocks != null) {
            blocks.setTicking(p.x(), p.y(), p.z(), true);
            blocks.scheduleTick(ChunkUtil.indexBlock(p.x(), p.y(), p.z()), until);
        }
        BlockModule.BlockStateInfo info = accessor.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving(accessor);
        }
    }

    /** The raw component, for the dev commands' fuller report; {@code null} where {@link #soilAt} is. */
    public TilledSoilBlock component(BlockPos p) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        if (section == null) {
            return null;
        }
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(accessor, section, p.x(), p.y(), p.z());
        return ref == null ? null : accessor.getComponent(ref, TilledSoilBlock.getComponentType());
    }
}
