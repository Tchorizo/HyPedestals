package dev.tchorizo.hypedestals.harvest;

/**
 * The post-harvest collect burst. The game's item index registers drops a few seconds after they
 * spawn, so the cycle's own two collects pick up little of what it just harvested. After a cycle
 * that harvested something, the Harvester therefore collects on every tick for
 * {@code PostHarvestCollectSeconds}, and the drops go into the container within the same cycle
 * instead of waiting for the next one. Outside that window nothing extra runs. The window closes
 * early when nothing can go in: right away when the container is full, and after
 * {@link #MAX_TICKS_WITHOUT_PROGRESS} ticks in a row that found items in the box but put none in
 * (e.g. other items that do not fit, or are not collected). Pure; times are real seconds (the
 * tick's {@code dt}).
 */
public final class CollectBurst {

    /** Consecutive collecting ticks that saw items but inserted none before the window closes. */
    public static final int MAX_TICKS_WITHOUT_PROGRESS = 3;

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

    /**
     * Consecutive ticks without progress after one collect: counts up when the collect saw
     * {@code present > 0} items it could take but inserted none; back to {@code 0} when it inserted
     * something or the box was empty (the item index may still be registering the drops).
     */
    public static int ticksWithoutProgress(int before, int inserted, int present) {
        return inserted == 0 && present > 0 ? before + 1 : 0;
    }

    /**
     * Seconds left after a collecting tick: {@code 0} (the window closes) when the container was
     * full, so nothing was collected, or after {@link #MAX_TICKS_WITHOUT_PROGRESS} ticks without
     * progress; otherwise {@code remaining} as it was.
     */
    public static float afterCollect(float remaining, boolean containerFull, int ticksWithoutProgress) {
        return containerFull || ticksWithoutProgress >= MAX_TICKS_WITHOUT_PROGRESS ? 0f : remaining;
    }
}
