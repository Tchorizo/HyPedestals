package dev.tchorizo.hypedestals.harvest;

/** What one harvest cycle needs from the game world and the harvester's container. */
public interface CycleWorld {

    /** Block type id at {@code p}, or {@code null} if that block has no farming behaviour. */
    String cropTypeAt(BlockPos p);

    /** Harvests the block at {@code p}; {@code true} if it was harvested. May throw a RuntimeException. */
    boolean harvest(BlockPos p);

    /** Pulls dropped items within {@code radius} of {@code center} into the container; returns items inserted. */
    int collectDroppedItems(BlockPos center, double radius);

    /** Number of completely empty container slots. */
    int freeSlots();
}
