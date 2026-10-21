package dev.tchorizo.hypedestals.harvest;

/** Result of {@link CycleWorld#harvest} for one position. */
public enum HarvestOutcome {
    /** The crop was harvested; counts towards {@code MaxHarvestsPerCycle}. */
    HARVESTED,
    /** Nothing to harvest there (immature crop, no harvest drop); not an error. */
    NOT_READY,
    /**
     * The harvest cannot happen right now but will on a later cycle (the game is still updating
     * that block); the crop type stays eligible and it does not count towards {@code MaxHarvestsPerCycle}.
     */
    TRANSIENT_ERROR,
    /**
     * The block's section is in memory but its block entities are parked (the section is not
     * ticking): the game's harvest would queue a second entity for the block and crash the world.
     * Nothing was touched; counted with the positions in unloaded sections ({@link CycleResult#unloaded()}),
     * does not count towards {@code MaxHarvestsPerCycle}, and the crop is harvested by the cycle that
     * finds the section ticking again.
     */
    UNLOADED,
    /** The harvest failed in a way that will repeat; the crop type is skipped from then on. */
    TYPE_ERROR
}
