package dev.tchorizo.hypedestals.harvest;

/**
 * The post-harvest collect burst. The game's item index registers drops a few seconds after they
 * spawn, so the cycle's own two collects pick up little of what it just harvested. After a cycle
 * that harvested something, the Harvester therefore collects on every tick for
 * {@code PostHarvestCollectSeconds}, and the drops go into the container within the same cycle
 * instead of waiting for the next one. Outside that window nothing extra runs. Pure; times are
 * real seconds (the tick's {@code dt}).
 */
public final class CollectBurst {

    private CollectBurst() {}

    /**
     * Seconds left of the burst after a cycle: a cycle that harvested (re)starts the full window;
     * any other cycle (nothing ripe) leaves the running window as it was.
     *
     * @param windowSeconds the config's {@code PostHarvestCollectSeconds}; {@code 0} turns the burst off
     */
    public static float afterCycle(float remaining, int harvested, float windowSeconds) {
        return harvested > 0 ? Math.max(0f, windowSeconds) : remaining;
    }

    /**
     * Whether a tick between cycles collects. A window turned off in the config meanwhile
     * ({@code windowSeconds == 0}) stops a running burst right away.
     */
    public static boolean collects(float remaining, float windowSeconds) {
        return remaining > 0f && windowSeconds > 0f;
    }

    /** Seconds left after a tick of {@code dt}; never below {@code 0}. */
    public static float afterTick(float remaining, float dt) {
        return Math.max(0f, remaining - dt);
    }
}
