package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/** What one harvest cycle needs from the game world and the harvester's container. */
public interface CycleWorld {

    /** Block type id at {@code p}, or {@code null} if that block has no farming behaviour. */
    String cropTypeAt(BlockPos p);

    /**
     * The most a harvest at {@code p} can drop, per item id (every item its drop list can roll, at
     * its maximum quantity); empty when the block drops nothing on harvest.
     */
    List<ItemCount> maxDrops(BlockPos p);

    /**
     * Harvests the block at {@code p}. Game failures are reported as {@link HarvestOutcome#TRANSIENT_ERROR}
     * or {@link HarvestOutcome#TYPE_ERROR}; an unexpected RuntimeException counts as a type error.
     */
    HarvestOutcome harvest(BlockPos p);

    /**
     * Pulls dropped items within {@code radius} of {@code center} into the container, reporting
     * each stack put in to the Harvester's {@link PendingDrops#collected}; returns items inserted.
     */
    int collectDroppedItems(BlockPos center, double radius);

    /** Whether no slot of the container can accept another item (see {@link HarvestCycle#slotHasRoom}). */
    boolean containerFull();

    /** A snapshot of the container's room, for the cycle to reserve its harvests' drops in. */
    ContainerRoom room();
}
