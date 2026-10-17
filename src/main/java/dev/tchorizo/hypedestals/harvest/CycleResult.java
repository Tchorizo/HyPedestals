package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/**
 * Outcome of one harvest cycle.
 *
 * @param collected items put into the container by the cycle's two collects, plus those of the
 *     {@link CollectBurst} that followed it (see {@link #plusCollected})
 * @param containerFull whether the cycle stopped at a crop whose drops did not fit in the
 *     container (it and the rest wait on the plant), or the container had no room left after the
 *     cycle's second collect; see {@link HarvestCycle#run}
 * @param newWarnings crop types that failed to harvest for the first time in this cycle
 * @param transientFailures positions whose harvest was deferred to a later cycle ({@link HarvestOutcome#TRANSIENT_ERROR})
 * @param nextIndex index in {@link HarvestArea#positions} where the next cycle's scan starts
 */
public record CycleResult(int harvested, int collected, boolean containerFull, List<String> newWarnings,
                          int transientFailures, int nextIndex) {

    /** This result with {@code more} items collected after the cycle (by the post-harvest burst). */
    public CycleResult plusCollected(int more) {
        return more == 0 ? this
                : new CycleResult(harvested, collected + more, containerFull, newWarnings, transientFailures, nextIndex);
    }
}
