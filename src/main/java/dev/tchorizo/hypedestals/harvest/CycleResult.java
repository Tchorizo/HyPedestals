package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/**
 * Outcome of one harvest cycle.
 *
 * @param newWarnings crop types that failed to harvest for the first time in this cycle
 */
public record CycleResult(int harvested, int collected, boolean pausedFull, List<String> newWarnings) {}
