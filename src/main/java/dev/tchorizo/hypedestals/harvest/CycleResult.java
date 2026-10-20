package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/**
 * Outcome of one harvest cycle.
 *
 * @param collected items put into the container by the cycle's two collects, plus those of the
 *     {@link CollectBurst} that followed it (see {@link #plusCollected})
 * @param containerFull whether the cycle stopped at a crop whose drops did not fit in the
 *     container ({@code cropsWaiting}), or the container had no room left after the cycle's
 *     second collect; see {@link HarvestCycle#run}
 * @param cropsWaiting whether the cycle stopped at a crop whose drops did not fit: it and the
 *     rest of the area wait on the plant, and the next cycle starts at that crop
 * @param newWarnings crop types that failed to harvest for the first time in this cycle
 * @param transientFailures positions whose harvest was deferred to a later cycle ({@link HarvestOutcome#TRANSIENT_ERROR})
 * @param unloaded positions the scan visited whose chunk section was not in memory (read as
 *     empty, so skipped); a crop there is harvested once a later cycle finds the section loaded
 * @param nextIndex index in {@link HarvestArea#positions} where the next cycle's scan starts
 */
public record CycleResult(int harvested, int collected, boolean containerFull, boolean cropsWaiting,
                          List<String> newWarnings, int transientFailures, int unloaded, int nextIndex) {

    /** This result with {@code more} items collected after the cycle (by the post-harvest burst). */
    public CycleResult plusCollected(int more) {
        return more == 0 ? this
                : new CycleResult(harvested, collected + more, containerFull, cropsWaiting, newWarnings,
                        transientFailures, unloaded, nextIndex);
    }
}
