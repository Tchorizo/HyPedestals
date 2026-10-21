package dev.tchorizo.hypedestals.block;

import dev.tchorizo.hypedestals.config.Tier;
import dev.tchorizo.hypedestals.harvest.CycleStats;
import dev.tchorizo.hypedestals.harvest.DurationWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Server-wide performance totals of the mod's machines, per block type ({@link Machine} and tier)
 * and overall, since the plugin started or the last {@link #reset()}: world-thread time spent in
 * the mod's code (every tick of every machine, cycle or not), cycles, work done (harvests, trees),
 * time per {@link CycleStats.Phase}, cycle-duration percentiles, and the machine with the worst
 * p95. Read by {@code /hypedestals perf}.
 *
 * <p>Durations are microseconds measured by {@code HarvesterSystem} and {@code LumberjackSystem}
 * with {@code System.nanoTime()};
 * work the game defers to the end of the tick (the command buffer: block changes of the harvest,
 * spawning the drops) is not in them. Methods are synchronized: worlds tick on their own threads,
 * and the calls (one per Harvester and tick) are short. Pure Java, so the arithmetic is unit-tested.
 */
public final class PerfRegistry {

    /** Cycles per tier (and overall) kept for the percentiles. */
    static final int WINDOW = 512;
    /**
     * A machine without a cycle for this long (unloaded or removed) is forgotten: it no longer
     * counts in {@code harvesters=}/{@code lumberjacks=} nor as the worst one.
     */
    static final long STALE_NANOS = 60_000_000_000L;

    private static final PerfRegistry GLOBAL = new PerfRegistry(System::nanoTime);

    private final LongSupplier clock;
    private final Map<Key, Totals> tiers = new TreeMap<>(Comparator.comparing(Key::machine).thenComparingInt(Key::tier));
    private final Totals all = new Totals();
    private final Map<String, Seen> machines = new HashMap<>();
    private long since;
    /** When {@link #machines} was last pruned; {@link #cycle} prunes at most once per {@link #STALE_NANOS}. */
    private long pruned;

    PerfRegistry(LongSupplier clock) {
        this.clock = clock;
        this.since = clock.getAsLong();
        this.pruned = since;
    }

    public static PerfRegistry global() {
        return GLOBAL;
    }

    /** The block types measured; {@code work} names what their cycles count. */
    public enum Machine {
        HARVESTER("Harvester", "harvesters", "harvested"),
        LUMBERJACK("Lumberjack", "lumberjacks", "trees");

        private final String label;
        private final String plural;
        private final String work;

        Machine(String label, String plural, String work) {
            this.label = label;
            this.plural = plural;
            this.work = work;
        }
    }

    /** One tick of one machine of {@code tier}: everything the mod did for it, cycle or not. */
    public synchronized void tick(Machine machine, int tier, long micros) {
        totals(machine, tier).tickMicros += micros;
        all.tickMicros += micros;
    }

    public synchronized void phase(Machine machine, int tier, CycleStats.Phase phase, long micros) {
        totals(machine, tier).phaseMicros[phase.ordinal()] += micros;
        all.phaseMicros[phase.ordinal()] += micros;
    }

    /**
     * One finished cycle of the machine at {@code where} (e.g. {@code default 142 115 100}),
     * whose own counters (already including this cycle) are {@code stats}.
     *
     * @param work what the cycle did: crops harvested, trees cut
     */
    public synchronized void cycle(Machine machine, int tier, String where, long micros, int work, CycleStats stats) {
        for (Totals t : List.of(totals(machine, tier), all)) {
            t.cycles++;
            t.work += work;
            t.window.add(micros);
        }
        long now = clock.getAsLong();
        if (now - pruned >= STALE_NANOS) {
            // Not on every cycle: that would walk every Harvester once per cycle of each.
            pruneStale(now);
        }
        machines.put(where, new Seen(new Key(machine, tier), where, stats.p95(), stats.windowSize(), stats.cycles(), now));
    }

    public synchronized void reset() {
        tiers.clear();
        all.clear();
        machines.clear();
        since = clock.getAsLong();
    }

    /**
     * The report, one line per block type and tier that ran, a total line and the worst machine.
     *
     * @param tps the world's ticks per second (the tick budget is {@code 1 / tps})
     */
    public synchronized List<String> report(int tps) {
        long now = clock.getAsLong();
        pruneStale(now);
        double seconds = Math.max((now - since) / 1e9, 1e-9);
        double tickBudgetMicros = 1e6 / tps;
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
                "perf: %.1f s since reset; tick budget %.1f ms (%d TPS); times in us (microseconds) of world-thread time in HyPedestals code",
                seconds, tickBudgetMicros / 1000, tps));
        for (Map.Entry<Key, Totals> e : tiers.entrySet()) {
            Machine m = e.getKey().machine();
            lines.add(line(name(e.getKey()), m.plural, e.getValue(), count(e.getKey()), " " + m.work + "=" + e.getValue().work,
                    seconds, tps, tickBudgetMicros));
        }
        lines.add(line("total", "machines", all, machines.size(), "", seconds, tps, tickBudgetMicros));
        Seen worst = worst();
        lines.add(worst == null ? "worst: none"
                : String.format(Locale.ROOT, "worst: %s at %s p95=%d us (window %d of %d cycles)",
                        name(worst.key()), worst.where(), worst.p95(), worst.window(), worst.cycles()));
        return lines;
    }

    private static String line(String name, String countLabel, Totals t, int count, String work, double seconds, int tps,
                               double tickBudgetMicros) {
        double modPerSecond = t.tickMicros / seconds;
        StringBuilder phases = new StringBuilder();
        for (CycleStats.Phase p : CycleStats.Phase.values()) {
            phases.append(' ').append(p.label()).append('=')
                    .append(String.format(Locale.ROOT, "%.0f", t.phaseMicros[p.ordinal()] / seconds));
        }
        return String.format(Locale.ROOT,
                "%s: %s=%d cycles=%d (%.2f/s)%s | mod %.0f us/s = %.3f%% of the world thread"
                        + " (%.1f us per tick of %.0f) | cycle p50=%d p95=%d max=%d us | us/s by phase:%s",
                name, countLabel, count, t.cycles, t.cycles / seconds, work, modPerSecond,
                modPerSecond / 1e4, modPerSecond / tps, tickBudgetMicros,
                t.window.percentile(50), t.window.percentile(95), t.window.max(), phases);
    }

    /** Highest p95 among the machines seen within {@link #STALE_NANOS}; {@code null} when none. */
    private Seen worst() {
        Seen worst = null;
        for (Seen s : machines.values()) {
            if (worst == null || s.p95() > worst.p95()) {
                worst = s;
            }
        }
        return worst;
    }

    /** Forgets the machines not seen within {@link #STALE_NANOS}, so the map does not keep every position ever loaded. */
    private void pruneStale(long now) {
        machines.values().removeIf(s -> now - s.at() > STALE_NANOS);
        pruned = now;
    }

    private int count(Key key) {
        int n = 0;
        for (Seen s : machines.values()) {
            n += s.key().equals(key) ? 1 : 0;
        }
        return n;
    }

    private Totals totals(Machine machine, int tier) {
        return tiers.computeIfAbsent(new Key(machine, tier), k -> new Totals());
    }

    /** {@code Harvester Copper}, {@code Lumberjack tier 7}. */
    private static String name(Key key) {
        Tier t = Tier.byNumber(key.tier());
        return key.machine().label + " " + (t == null ? "tier " + key.tier() : t.displayName());
    }

    /** A block type: machine and tier. */
    private record Key(Machine machine, int tier) {}

    /** Last report of one machine: its p95 over its own window, and when it was seen. */
    private record Seen(Key key, String where, long p95, int window, long cycles, long at) {}

    private static final class Totals {
        long tickMicros;
        long cycles;
        long work;
        final long[] phaseMicros = new long[CycleStats.Phase.values().length];
        final DurationWindow window = new DurationWindow(WINDOW);

        void clear() {
            tickMicros = 0;
            cycles = 0;
            work = 0;
            Arrays.fill(phaseMicros, 0);
            window.clear();
        }
    }
}
