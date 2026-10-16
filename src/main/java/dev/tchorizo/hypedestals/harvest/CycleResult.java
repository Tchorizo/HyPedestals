package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/**
 * Outcome of one harvest cycle.
 *
 * @param collected items put into the container by the cycle's two collects, plus those of the
 *     {@link CollectBurst} that followed it (see {@link #plusCollected})
 * @param newWarnings crop types that failed to harvest for the first time in this cycle
 * @param transientFailures positions whose harvest was deferred to a later cycle ({@link HarvestOutcome#TRANSIENT_ERROR})
 * @param nextIndex index in {@link HarvestArea#positions} where the next cycle's scan starts
 */
public record CycleResult(int harvested, int collected, boolean pausedFull, List<String> newWarnings,
                          int transientFailures, int nextIndex) {

    /** This result with {@code more} items collected after the cycle (by the post-harvest burst). */
    public CycleResult plusCollected(int more) {
        return more == 0 ? this
                : new CycleResult(harvested, collected + more, pausedFull, newWarnings, transientFailures, nextIndex);
    }
}
