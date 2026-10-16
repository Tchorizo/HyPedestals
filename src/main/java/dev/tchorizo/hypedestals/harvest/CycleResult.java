package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/**
 * Outcome of one harvest cycle.
 *
 * @param newWarnings crop types that failed to harvest for the first time in this cycle
 * @param transientFailures positions whose harvest was deferred to a later cycle ({@link HarvestOutcome#TRANSIENT_ERROR})
 * @param nextIndex index in {@link HarvestArea#positions} where the next cycle's scan starts
 */
public record CycleResult(int harvested, int collected, boolean pausedFull, List<String> newWarnings,
                          int transientFailures, int nextIndex) {}
