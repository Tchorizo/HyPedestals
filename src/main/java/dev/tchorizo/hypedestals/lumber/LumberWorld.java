package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.ContainerRoom;
import dev.tchorizo.hypedestals.harvest.ItemCount;
import dev.tchorizo.hypedestals.harvest.SaplingRule;

import java.util.List;

/** What one {@link LumberCycle} needs from the world and the Lumberjack's container. */
public interface LumberWorld extends TreeWorld, SaplingRule.Ground {

    /** Base block id at {@code p} (state variants share it), or {@code null} for air. */
    String blockId(BlockPos p);

    /** One roll of what breaking the block at {@code p} drops (empty when nothing). */
    List<ItemCount> drops(BlockPos p);

    /** The container's room now. */
    ContainerRoom room();

    /** Id of a soil block ({@code Type=Soil}) beside {@code p} on its layer, or {@code null}. */
    String soilNextTo(BlockPos p);

    /** Removes the block at {@code p} (to air), without drops of its own. */
    void remove(BlockPos p);

    /** Puts a soil block at {@code p}. */
    void setSoil(BlockPos p, String soilId);

    /** Puts {@code items} in the container; they fit ({@link ContainerRoom#fits} was checked). */
    void store(List<ItemCount> items);

    /** Distinct sapling item ids in the container, in slot order. */
    List<String> saplings();

    /** Places the sapling of {@code saplingItemId} at {@code spot} and takes one from the container. */
    boolean plant(BlockPos spot, String saplingItemId);
}
