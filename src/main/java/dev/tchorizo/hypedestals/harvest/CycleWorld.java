package dev.tchorizo.hypedestals.harvest;

/** What one harvest cycle needs from the game world and the harvester's container. */
public interface CycleWorld {

    /** Block type id at {@code p}, or {@code null} if that block has no farming behaviour. */
    String cropTypeAt(BlockPos p);

    /**
     * Harvests the block at {@code p}. Game failures are reported as {@link HarvestOutcome#TRANSIENT_ERROR}
     * or {@link HarvestOutcome#TYPE_ERROR}; an unexpected RuntimeException counts as a type error.
     */
    HarvestOutcome harvest(BlockPos p);

    /** Pulls dropped items within {@code radius} of {@code center} into the container; returns items inserted. */
    int collectDroppedItems(BlockPos center, double radius);

    /**
     * Whether no slot of the container can accept another item (see
     * {@link HarvestCycle#slotHasRoom}). Informative only: the cycle harvests regardless.
     */
    boolean containerFull();
}
