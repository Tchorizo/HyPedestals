package dev.tchorizo.hypedestals.harvest;

/**
 * Cycle durations of one Harvester since its block entity was loaded (transient; they reset with
 * the chunk), for {@code inspect}. Durations are microseconds of the world thread spent in the
 * mod's own code during a cycle, measured with {@code System.nanoTime()}; work the game defers to
 * the end of the tick (command buffer) is not included. The time per {@link Phase} is only kept
 * server-wide ({@code PerfRegistry}). Pure and not thread-safe: one Harvester is only ticked by
 * its world's thread.
 */
public final class CycleStats {

    /** Cycles kept for {@link #p50()}, {@link #p95()} and {@link #max()}. */
    public static final int WINDOW = 64;

    /** Where the time goes; {@link #SCAN} to {@link #VISUAL} make up a cycle. */
    public enum Phase {
        /** The harvest scan: block reads and {@code FarmingUtil.harvest} over the area. */
        SCAN("scan"),
        /** The cycle's two collects (before and after harvesting). */
        COLLECT("collect"),
        /** One collect of the post-harvest burst, on a tick between cycles. */
        BURST("burst"),
        /** One soil pass (watering, fertilizer), on its own timer. */
        SOIL("soil"),
        /** The export to an adjacent container at the end of a cycle. */
        EXPORT("export"),
        /**
         * Checking (and requesting) the block state that shows the upgrades, once per cycle, and
         * spawning the upgrade particles every {@code UpgradeParticlesSeconds}.
         */
        VISUAL("visual");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final DurationWindow window = new DurationWindow(WINDOW);
    private long cycles;
    private long lastCycleMicros;

    /** Adds one whole cycle that took {@code micros}. */
    public void addCycle(long micros) {
        cycles++;
        lastCycleMicros = micros;
        window.add(micros);
    }

    public long cycles() {
        return cycles;
    }

    /** Duration of the most recent cycle; {@code 0} before the first one. */
    public long lastCycleMicros() {
        return lastCycleMicros;
    }

    /** Median cycle duration over the last {@link #WINDOW} cycles; {@code 0} before the first. */
    public long p50() {
        return window.percentile(50);
    }

    /** 95th percentile cycle duration over the last {@link #WINDOW} cycles; {@code 0} before the first. */
    public long p95() {
        return window.percentile(95);
    }

    /** Longest cycle among the last {@link #WINDOW}; {@code 0} before the first. */
    public long max() {
        return window.max();
    }

    /** Cycles in the percentile window ({@code min(cycles, WINDOW)}). */
    public int windowSize() {
        return window.size();
    }
}
