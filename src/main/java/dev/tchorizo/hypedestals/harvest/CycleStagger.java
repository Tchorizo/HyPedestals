package dev.tchorizo.hypedestals.harvest;

/**
 * Where a Harvester's cycle timer starts after a load (or a placement). The timer is runtime
 * state: it used to start at {@code 0} for every Harvester, so Harvesters of the same tier loaded
 * together (same section, server start, a reload of the world) ran their cycles in the same world
 * tick forever after. Starting each one at an offset taken from its position spreads them over the
 * interval; the offset is deterministic, so a Harvester keeps its phase across loads.
 *
 * <p>Spreading is not what makes overlapping areas safe (two positions can still map to the same
 * tick, and tiers with different intervals meet periodically): that is
 * {@link HarvestAreas#claimHarvest}.
 */
public final class CycleStagger {

    private CycleStagger() {}

    /**
     * Seconds already elapsed on the cycle timer of the Harvester at {@code pos} when it starts
     * ticking: in {@code [0, intervalSeconds)}, so the first cycle comes within one interval, as
     * before. {@code 0} for a non-positive interval.
     */
    public static float initialElapsed(BlockPos pos, float intervalSeconds) {
        if (!(intervalSeconds > 0)) {
            return 0f;
        }
        float elapsed = (float) (fraction(pos) * intervalSeconds);
        // The float product can round up to the interval itself: that would fire on the first tick.
        return Math.min(elapsed, Math.nextDown(intervalSeconds));
    }

    /** A well-mixed value in {@code [0, 1)} from the position (SplitMix64 finalizer over the packed coordinates). */
    static double fraction(BlockPos pos) {
        long h = pos.x() * 0x9E3779B97F4A7C15L + pos.y() * 0xC2B2AE3D27D4EB4FL + pos.z() * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
