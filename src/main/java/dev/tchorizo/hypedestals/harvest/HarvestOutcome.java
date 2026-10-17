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
    /** The harvest failed in a way that will repeat; the crop type is skipped from then on. */
    TYPE_ERROR
}
