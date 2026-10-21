package dev.tchorizo.hypedestals.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tchorizo.hypedestals.harvest.CycleStats;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerfRegistryTest {

    private static final PerfRegistry.Machine H = PerfRegistry.Machine.HARVESTER;
    private static final PerfRegistry.Machine L = PerfRegistry.Machine.LUMBERJACK;

    /** Manual clock in nanoseconds. */
    private long now;

    @Test
    void reportsRatesShareOfTheTickAndPercentilesPerTier() {
        PerfRegistry perf = new PerfRegistry(() -> now);
        CycleStats copper = new CycleStats();
        CycleStats cobalt = new CycleStats();
        // 10 s: Copper 1 cycle of 300 us; Cobalt 4 cycles of 1000..4000 us. Every tick adds its
        // whole duration (cycle ticks included) to the mod time.
        perf.tick(H, 1, 300);
        perf.phase(H, 1, CycleStats.Phase.SCAN, 200);
        copper.addCycle(300);
        perf.cycle(H, 1, "default 1 2 3", 300, 2, copper);
        for (int i = 1; i <= 4; i++) {
            perf.tick(H, 4, i * 1000L);
            perf.phase(H, 4, CycleStats.Phase.COLLECT, 500);
            cobalt.addCycle(i * 1000L);
            perf.cycle(H, 4, "default 9 9 9", i * 1000L, 10, cobalt);
        }
        perf.tick(H, 4, 700); // a burst tick between cycles
        now = 10_000_000_000L;

        List<String> lines = perf.report(30);

        assertEquals(5, lines.size());
        assertEquals("perf: 10.0 s since reset; tick budget 33.3 ms (30 TPS); times in us (microseconds) of world-thread time in HyPedestals code",
                lines.get(0));
        assertEquals("Harvester Copper: harvesters=1 cycles=1 (0.10/s) harvested=2 | mod 30 us/s = 0.003% of the world thread"
                        + " (1.0 us per tick of 33333) | cycle p50=300 p95=300 max=300 us"
                        + " | us/s by phase: scan=20 collect=0 burst=0 soil=0 export=0 visual=0",
                lines.get(1));
        // Cobalt: 10700 us in 10 s.
        assertEquals("Harvester Cobalt: harvesters=1 cycles=4 (0.40/s) harvested=40 | mod 1070 us/s = 0.107% of the world thread"
                        + " (35.7 us per tick of 33333) | cycle p50=2000 p95=4000 max=4000 us"
                        + " | us/s by phase: scan=0 collect=200 burst=0 soil=0 export=0 visual=0",
                lines.get(2));
        assertEquals("total: machines=2 cycles=5 (0.50/s) | mod 1100 us/s = 0.110% of the world thread"
                        + " (36.7 us per tick of 33333) | cycle p50=2000 p95=4000 max=4000 us"
                        + " | us/s by phase: scan=20 collect=200 burst=0 soil=0 export=0 visual=0",
                lines.get(3));
        assertEquals("worst: Harvester Cobalt at default 9 9 9 p95=4000 us (window 4 of 4 cycles)", lines.get(4));
    }

    @Test
    void staleHarvestersAreForgotten() {
        PerfRegistry perf = new PerfRegistry(() -> now);
        CycleStats slow = new CycleStats();
        slow.addCycle(9000);
        perf.cycle(H, 4, "default 1 1 1", 9000, 0, slow);
        now += PerfRegistry.STALE_NANOS + 1;
        CycleStats fast = new CycleStats();
        fast.addCycle(100);
        perf.cycle(H, 1, "default 2 2 2", 100, 0, fast);

        List<String> lines = perf.report(30);
        assertEquals("worst: Harvester Copper at default 2 2 2 p95=100 us (window 1 of 1 cycles)", lines.getLast(),
                "an unloaded Harvester's old p95 does not hide the live ones");
        assertTrue(lines.get(1).startsWith("Harvester Copper: harvesters=1 "), lines.get(1));
        assertTrue(lines.get(2).startsWith("Harvester Cobalt: harvesters=0 "), "the stale Cobalt is no longer counted: " + lines.get(2));
        assertTrue(lines.get(3).startsWith("total: machines=1 "), lines.get(3));

        now += PerfRegistry.STALE_NANOS + 1;
        assertEquals("worst: none", perf.report(30).getLast(), "every Harvester is stale: none is reported");
    }

    @Test
    void resetStartsOver() {
        PerfRegistry perf = new PerfRegistry(() -> now);
        CycleStats s = new CycleStats();
        s.addCycle(500);
        perf.tick(H, 2, 500);
        perf.cycle(H, 2, "default 0 0 0", 500, 1, s);
        now = 5_000_000_000L;
        perf.reset();
        now = 7_000_000_000L;

        assertEquals(List.of(
                "perf: 2.0 s since reset; tick budget 50.0 ms (20 TPS); times in us (microseconds) of world-thread time in HyPedestals code",
                "total: machines=0 cycles=0 (0.00/s) | mod 0 us/s = 0.000% of the world thread"
                        + " (0.0 us per tick of 50000) | cycle p50=0 p95=0 max=0 us"
                        + " | us/s by phase: scan=0 collect=0 burst=0 soil=0 export=0 visual=0",
                "worst: none"), perf.report(20));
    }

    @Test
    void eachBlockTypeHasItsOwnLines() {
        PerfRegistry perf = new PerfRegistry(() -> now);
        CycleStats harvester = new CycleStats();
        CycleStats lumberjack = new CycleStats();
        harvester.addCycle(100);
        perf.tick(H, 1, 100);
        perf.cycle(H, 1, "default 0 0 0", 100, 3, harvester);
        lumberjack.addCycle(2500);
        perf.tick(L, 1, 2500);
        perf.phase(L, 1, CycleStats.Phase.SCAN, 2400);
        perf.cycle(L, 1, "default 5 0 0", 2500, 1, lumberjack);
        now = 1_000_000_000L;

        List<String> lines = perf.report(30);

        assertEquals(5, lines.size());
        assertTrue(lines.get(1).startsWith("Harvester Copper: harvesters=1 cycles=1 (1.00/s) harvested=3 |"), lines.get(1));
        assertTrue(lines.get(2).startsWith("Lumberjack Copper: lumberjacks=1 cycles=1 (1.00/s) trees=1 |"), lines.get(2));
        assertTrue(lines.get(3).startsWith("total: machines=2 cycles=2 "), lines.get(3));
        assertEquals("worst: Lumberjack Copper at default 5 0 0 p95=2500 us (window 1 of 1 cycles)", lines.get(4));
    }
}
