package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.HarvestCycle;
import org.joml.Vector3i;

/** Runs one {@link HarvestCycle} per harvester block every {@code IntervalSeconds}. */
public final class HarvesterSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(HarvesterComponent.TYPE, BlockModule.BlockStateInfo.getComponentType());
    }

    /** Harvesting mutates the world and the entity store; keep it on the world thread, sequentially. */
    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
        HarvesterComponent harvester = chunk.getComponent(index, HarvesterComponent.TYPE);
        harvester.elapsed += dt;
        if (harvester.elapsed < harvester.intervalSeconds) {
            return;
        }
        harvester.elapsed = 0;

        if (harvester.radius == 0) {
            if (!harvester.inactiveWarned) {
                harvester.inactiveWarned = true;
                HyPedestalsPlugin.warn("Harvester tier %d inativo: Radius fora de 1..%d",
                        harvester.tier, HarvesterSettings.MAX_RADIUS);
            }
            return;
        }

        BlockModule.BlockStateInfo info = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        Vector3i pos = new Vector3i();
        if (!info.fillWorldPos(cb, pos)) {
            return;
        }
        World world = store.getExternalData().getWorld();

        ItemContainerBlock containerBlock = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        if (containerBlock == null) {
            if (!harvester.missingContainerWarned) {
                harvester.missingContainerWarned = true;
                HyPedestalsPlugin.warn("Harvester em %s sem ItemContainerBlock; inativo", pos);
            }
            return;
        }

        harvester.lastResult = HarvestCycle.run(
                new HytaleCycleWorld(world, cb, containerBlock.getItemContainer()),
                new BlockPos(pos.x, pos.y, pos.z), harvester.radius, harvester.warnedTypes);
        harvester.lastCycleTick = world.getTick();
        for (String type : harvester.lastResult.newWarnings()) {
            HyPedestalsPlugin.warn("tipo %s ignorado neste bloco", type);
        }
    }
}
