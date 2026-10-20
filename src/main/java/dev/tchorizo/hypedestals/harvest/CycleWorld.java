package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/** What one harvest cycle needs from the game world and the harvester's container. */
public interface CycleWorld {

    /** Block type id at {@code p}, or {@code null} if that block has no farming behaviour. */
    String cropTypeAt(BlockPos p);

    /**
     * Whether the chunk section holding {@code p} is in memory. One that is not reads as empty, so
     * {@link #cropTypeAt} is {@code null} there whatever grows on it.
     */
    boolean loaded(BlockPos p);

    /**
     * The most a harvest at {@code p} can drop, one entry per item id (every item its drop list
     * can roll, at its maximum quantity); empty when the block drops nothing on harvest.
     */
    List<ItemCount> maxDrops(BlockPos p);

    /**
     * Harvests the block at {@code p}. Game failures are reported as {@link HarvestOutcome#TRANSIENT_ERROR}
     * or {@link HarvestOutcome#TYPE_ERROR}; an unexpected RuntimeException counts as a type error.
     */
    HarvestOutcome harvest(BlockPos p);

    /**
     * Pulls dropped items within {@code radius} of {@code center} into the container, reporting
     * each stack put in to the Harvester's {@link PendingDrops#collected}, and a stack that did not
     * fit to {@link PendingDrops#collectBlocked}; returns items inserted. Items inside another
     * active Harvester's area and outside this one's are left to it ({@link HarvestAreas}).
     */
    int collectDroppedItems(BlockPos center, double radius);

    /** Whether no slot of the container can accept another item (see {@link HarvestCycle#slotHasRoom}). */
    boolean containerFull();

    /** A snapshot of the container's room, for the cycle to reserve its harvests' drops in. */
    ContainerRoom room();
}
