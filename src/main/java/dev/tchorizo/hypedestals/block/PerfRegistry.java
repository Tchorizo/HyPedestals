package dev.tchorizo.hypedestals.block;

import dev.tchorizo.hypedestals.config.Tier;
import dev.tchorizo.hypedestals.harvest.CycleStats;
import dev.tchorizo.hypedestals.harvest.DurationWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Server-wide performance totals of the Harvesters, per tier and overall, since the plugin started
 * or the last {@link #reset()}: world-thread time spent in the mod's code (every tick of every
 * Harvester, cycle or not), cycles, harvests, time per {@link CycleStats.Phase}, cycle-duration
 * percentiles, and the Harvester with the worst p95. Read by {@code /hypedestals perf}.
 *
 * <p>Durations are microseconds measured by {@code HarvesterSystem} with {@code System.nanoTime()};
 * work the game defers to the end of the tick (the command buffer: block changes of the harvest,
 * spawning the drops) is not in them. Methods are synchronized: worlds tick on their own threads,
 * and the calls (one per Harvester and tick) are short. Pure Java, so the arithmetic is unit-tested.
 */
public final class PerfRegistry {

    /** Cycles per tier (and overall) kept for the percentiles. */
    static final int WINDOW = 512;
    /** A Harvester not seen for this long no longer counts as the worst one (unloaded or removed). */
    static final long WORST_STALE_NANOS = 60_000_000_000L;

    private static final PerfRegistry GLOBAL = new PerfRegistry(System::nanoTime);

    private final LongSupplier clock;
    private final Map<Integer, Totals> tiers = new TreeMap<>();
    private final Totals all = new Totals();
    private final Map<String, Seen> harvesters = new HashMap<>();
    private long since;

    PerfRegistry(LongSupplier clock) {
        this.clock = clock;
        this.since = clock.getAsLong();
    }

    public static PerfRegistry global() {
        return GLOBAL;
    }

    /** One tick of one Harvester of {@code tier}: everything the mod did for it, cycle or not. */
    public synchronized void tick(int tier, long micros) {
        totals(tier).tickMicros += micros;
        all.tickMicros += micros;
    }

    public synchronized void phase(int tier, CycleStats.Phase phase, long micros) {
        totals(tier).phaseMicros[phase.ordinal()] += micros;
        all.phaseMicros[phase.ordinal()] += micros;
    }

    /**
     * One finished cycle of the Harvester at {@code where} (e.g. {@code default 142 115 100}),
     * whose own counters (already including this cycle) are {@code stats}.
     */
    public synchronized void cycle(int tier, String where, long micros, int harvested, CycleStats stats) {
        for (Totals t : List.of(totals(tier), all)) {
            t.cycles++;
            t.harvested += harvested;
            t.window.add(micros);
        }
        harvesters.put(where, new Seen(tier, where, stats.p95(), stats.windowSize(), stats.cycles(), clock.getAsLong()));
    }

    public synchronized void reset() {
        tiers.clear();
        all.clear();
        harvesters.clear();
        since = clock.getAsLong();
    }

    /**
     * The report, one line per tier that ran, a total line and the worst Harvester.
     *
     * @param tps the world's ticks per second (the tick budget is {@code 1 / tps})
     */
    public synchronized List<String> report(int tps) {
        double seconds = Math.max((clock.getAsLong() - since) / 1e9, 1e-9);
        double tickBudgetMicros = 1e6 / tps;
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
                "perf: %.1f s since reset; tick budget %.1f ms (%d TPS); times in us (microseconds) of world-thread time in HyPedestals code",
                seconds, tickBudgetMicros / 1000, tps));
        for (Map.Entry<Integer, Totals> e : tiers.entrySet()) {
            lines.add(line(tierName(e.getKey()), e.getValue(), count(e.getKey()), seconds, tps, tickBudgetMicros));
        }
        lines.add(line("total", all, harvesters.size(), seconds, tps, tickBudgetMicros));
        Seen worst = worst();
        lines.add(worst == null ? "worst: none"
                : String.format(Locale.ROOT, "worst: %s at %s p95=%d us (window %d of %d cycles)",
                        tierName(worst.tier()), worst.where(), worst.p95(), worst.window(), worst.cycles()));
        return lines;
    }

    private static String line(String name, Totals t, int harvesterCount, double seconds, int tps, double tickBudgetMicros) {
        double modPerSecond = t.tickMicros / seconds;
        StringBuilder phases = new StringBuilder();
        for (CycleStats.Phase p : CycleStats.Phase.values()) {
            phases.append(' ').append(p.label()).append('=')
                    .append(String.format(Locale.ROOT, "%.0f", t.phaseMicros[p.ordinal()] / seconds));
        }
        return String.format(Locale.ROOT,
                "%s: harvesters=%d cycles=%d (%.2f/s) harvested=%d | mod %.0f us/s = %.3f%% of the world thread"
                        + " (%.1f us per tick of %.0f) | cycle p50=%d p95=%d max=%d us | us/s by phase:%s",
                name, harvesterCount, t.cycles, t.cycles / seconds, t.harvested, modPerSecond,
                modPerSecond / 1e4, modPerSecond / tps, tickBudgetMicros,
                t.window.percentile(50), t.window.percentile(95), t.window.max(), phases);
    }

    /** Highest p95 among the Harvesters seen within {@link #WORST_STALE_NANOS}; else among all; {@code null} when none. */
    private Seen worst() {
        long now = clock.getAsLong();
        Seen worst = null;
        Seen worstAny = null;
        for (Seen s : harvesters.values()) {
            if (worstAny == null || s.p95() > worstAny.p95()) {
                worstAny = s;
            }
            if (now - s.at() <= WORST_STALE_NANOS && (worst == null || s.p95() > worst.p95())) {
                worst = s;
            }
        }
        return worst != null ? worst : worstAny;
    }

    private int count(int tier) {
        int n = 0;
        for (Seen s : harvesters.values()) {
            n += s.tier() == tier ? 1 : 0;
        }
        return n;
    }

    private Totals totals(int tier) {
        return tiers.computeIfAbsent(tier, t -> new Totals());
    }

    private static String tierName(int tier) {
        Tier t = Tier.byNumber(tier);
        return t == null ? "tier " + tier : t.displayName();
    }

    /** Last report of one Harvester: its p95 over its own window, and when it was seen. */
    private record Seen(int tier, String where, long p95, int window, long cycles, long at) {}

    private static final class Totals {
        long tickMicros;
        long cycles;
        long harvested;
        final long[] phaseMicros = new long[CycleStats.Phase.values().length];
        final DurationWindow window = new DurationWindow(WINDOW);

        void clear() {
            tickMicros = 0;
            cycles = 0;
            harvested = 0;
            Arrays.fill(phaseMicros, 0);
            window.clear();
        }
    }
}
