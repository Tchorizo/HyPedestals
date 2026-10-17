package dev.tchorizo.hypedestals.harvest;

/**
 * Performance counters of one Harvester since its block entity was loaded (transient; they reset
 * with the chunk). Durations are microseconds of the world thread spent in the mod's own code,
 * measured with {@code System.nanoTime()} around each {@link Phase}; work the game defers to the
 * end of the tick (command buffer) is not included. Pure and not thread-safe: one Harvester is
 * only ticked by its world's thread.
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
        /** Checking (and requesting) the block state that shows the upgrades, once per cycle. */
        VISUAL("visual");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final long[] phaseMicros = new long[Phase.values().length];
    private final long[] phaseCount = new long[Phase.values().length];
    private final DurationWindow window = new DurationWindow(WINDOW);
    private long cycles;
    private long cycleMicros;
    private long lastCycleMicros;
    private long harvested;
    private long collected;

    /** Adds one run of {@code phase} that took {@code micros}. */
    public void addPhase(Phase phase, long micros) {
        phaseMicros[phase.ordinal()] += micros;
        phaseCount[phase.ordinal()]++;
    }

    /**
     * Adds one whole cycle (its phases are added separately with {@link #addPhase}): its total
     * duration and what it harvested and collected.
     */
    public void addCycle(long micros, int harvestedNow, int collectedNow) {
        cycles++;
        cycleMicros += micros;
        lastCycleMicros = micros;
        window.add(micros);
        harvested += harvestedNow;
        collected += collectedNow;
    }

    /** Items collected outside a cycle (the post-harvest burst). */
    public void addCollected(int items) {
        collected += items;
    }

    public long cycles() {
        return cycles;
    }

    /** Sum of every cycle's duration. */
    public long cycleMicros() {
        return cycleMicros;
    }

    /** Duration of the most recent cycle; {@code 0} before the first one. */
    public long lastCycleMicros() {
        return lastCycleMicros;
    }

    public long harvested() {
        return harvested;
    }

    public long collected() {
        return collected;
    }

    public long phaseMicros(Phase phase) {
        return phaseMicros[phase.ordinal()];
    }

    public long phaseCount(Phase phase) {
        return phaseCount[phase.ordinal()];
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
