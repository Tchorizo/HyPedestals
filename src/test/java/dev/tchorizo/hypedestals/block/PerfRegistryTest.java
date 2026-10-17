package dev.tchorizo.hypedestals.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tchorizo.hypedestals.harvest.CycleStats;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerfRegistryTest {

    /** Manual clock in nanoseconds. */
    private long now;

    @Test
    void reportsRatesShareOfTheTickAndPercentilesPerTier() {
        PerfRegistry perf = new PerfRegistry(() -> now);
        CycleStats copper = new CycleStats();
        CycleStats cobalt = new CycleStats();
        // 10 s: Copper 1 cycle of 300 us; Cobalt 4 cycles of 1000..4000 us. Every tick adds its
        // whole duration (cycle ticks included) to the mod time.
        perf.tick(1, 300);
        perf.phase(1, CycleStats.Phase.SCAN, 200);
        copper.addCycle(300, 2, 0);
        perf.cycle(1, "default 1 2 3", 300, 2, copper);
        for (int i = 1; i <= 4; i++) {
            perf.tick(4, i * 1000L);
            perf.phase(4, CycleStats.Phase.COLLECT, 500);
            cobalt.addCycle(i * 1000L, 10, 0);
            perf.cycle(4, "default 9 9 9", i * 1000L, 10, cobalt);
        }
        perf.tick(4, 700); // a burst tick between cycles
        now = 10_000_000_000L;

        List<String> lines = perf.report(30);

        assertEquals(5, lines.size());
        assertEquals("perf: 10.0 s since reset; tick budget 33.3 ms (30 TPS); times in us (microseconds) of world-thread time in HyPedestals code",
                lines.get(0));
        assertEquals("Copper: harvesters=1 cycles=1 (0.10/s) harvested=2 | mod 30 us/s = 0.003% of the world thread"
                        + " (1.0 us per tick of 33333) | cycle p50=300 p95=300 max=300 us"
                        + " | us/s by phase: scan=20 collect=0 burst=0 soil=0 export=0 visual=0",
                lines.get(1));
        // Cobalt: 10700 us in 10 s.
        assertEquals("Cobalt: harvesters=1 cycles=4 (0.40/s) harvested=40 | mod 1070 us/s = 0.107% of the world thread"
                        + " (35.7 us per tick of 33333) | cycle p50=2000 p95=4000 max=4000 us"
                        + " | us/s by phase: scan=0 collect=200 burst=0 soil=0 export=0 visual=0",
                lines.get(2));
        assertEquals("total: harvesters=2 cycles=5 (0.50/s) harvested=42 | mod 1100 us/s = 0.110% of the world thread"
                        + " (36.7 us per tick of 33333) | cycle p50=2000 p95=4000 max=4000 us"
                        + " | us/s by phase: scan=20 collect=200 burst=0 soil=0 export=0 visual=0",
                lines.get(3));
        assertEquals("worst: Cobalt at default 9 9 9 p95=4000 us (window 4 of 4 cycles)", lines.get(4));
    }

    @Test
    void worstSkipsHarvestersNotSeenLately() {
        PerfRegistry perf = new PerfRegistry(() -> now);
        CycleStats slow = new CycleStats();
        slow.addCycle(9000, 0, 0);
        perf.cycle(4, "default 1 1 1", 9000, 0, slow);
        now += PerfRegistry.WORST_STALE_NANOS + 1;
        CycleStats fast = new CycleStats();
        fast.addCycle(100, 0, 0);
        perf.cycle(1, "default 2 2 2", 100, 0, fast);

        assertEquals("worst: Copper at default 2 2 2 p95=100 us (window 1 of 1 cycles)", perf.report(30).getLast(),
                "an unloaded Harvester's old p95 does not hide the live ones");
    }

    @Test
    void resetStartsOver() {
        PerfRegistry perf = new PerfRegistry(() -> now);
        CycleStats s = new CycleStats();
        s.addCycle(500, 1, 0);
        perf.tick(2, 500);
        perf.cycle(2, "default 0 0 0", 500, 1, s);
        now = 5_000_000_000L;
        perf.reset();
        now = 7_000_000_000L;

        assertEquals(List.of(
                "perf: 2.0 s since reset; tick budget 50.0 ms (20 TPS); times in us (microseconds) of world-thread time in HyPedestals code",
                "total: harvesters=0 cycles=0 (0.00/s) harvested=0 | mod 0 us/s = 0.000% of the world thread"
                        + " (0.0 us per tick of 50000) | cycle p50=0 p95=0 max=0 us"
                        + " | us/s by phase: scan=0 collect=0 burst=0 soil=0 export=0 visual=0",
                "worst: none"), perf.report(20));
    }
}
