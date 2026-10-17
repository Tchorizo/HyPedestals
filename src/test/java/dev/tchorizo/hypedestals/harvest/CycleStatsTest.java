package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CycleStatsTest {

    @Test
    void percentilesUseNearestRank() {
        DurationWindow w = new DurationWindow(100);
        // 1..100 added out of order.
        for (int i = 100; i >= 1; i--) {
            w.add(i);
        }
        assertEquals(50, w.percentile(50));
        assertEquals(95, w.percentile(95));
        assertEquals(100, w.percentile(100));
        assertEquals(1, w.percentile(1));
        assertEquals(100, w.max());
        assertEquals(100, w.size());
    }

    @Test
    void percentilesOfFewSamples() {
        DurationWindow w = new DurationWindow(64);
        assertEquals(0, w.percentile(95), "empty window");
        assertEquals(0, w.max());
        w.add(700);
        assertEquals(700, w.percentile(50));
        assertEquals(700, w.percentile(95));
        w.add(100);
        w.add(300);
        // Sorted 100, 300, 700: rank ceil(0.5*3)=2 -> 300; rank ceil(0.95*3)=3 -> 700.
        assertEquals(300, w.percentile(50));
        assertEquals(700, w.percentile(95));
        assertEquals(700, w.max());
    }

    @Test
    void windowKeepsOnlyTheLastSamples() {
        DurationWindow w = new DurationWindow(4);
        for (long d : new long[] {1000, 2000, 1, 2, 3, 4}) {
            w.add(d);
        }
        assertEquals(4, w.size());
        assertEquals(4, w.max(), "1000 and 2000 fell out of the window");
        assertEquals(2, w.percentile(50));
        w.clear();
        assertEquals(0, w.size());
        assertEquals(0, w.percentile(50));
        assertThrows(IllegalArgumentException.class, () -> new DurationWindow(0));
        assertThrows(IllegalArgumentException.class, () -> w.percentile(0));
    }

    @Test
    void cycleStatsCountPhasesCyclesAndPercentiles() {
        CycleStats s = new CycleStats();
        assertEquals(0, s.p95());
        assertEquals(0, s.lastCycleMicros());
        for (int i = 1; i <= 70; i++) {
            s.addPhase(CycleStats.Phase.SCAN, 10);
            s.addPhase(CycleStats.Phase.COLLECT, 5);
            s.addCycle(i * 100L, 2, 3);
        }
        s.addPhase(CycleStats.Phase.BURST, 7);
        s.addCollected(4);

        assertEquals(70, s.cycles());
        assertEquals(140, s.harvested());
        assertEquals(214, s.collected(), "cycles plus the burst");
        assertEquals(7000, s.lastCycleMicros());
        assertEquals(700, s.phaseMicros(CycleStats.Phase.SCAN));
        assertEquals(70, s.phaseCount(CycleStats.Phase.COLLECT));
        assertEquals(7, s.phaseMicros(CycleStats.Phase.BURST));
        assertEquals(0, s.phaseCount(CycleStats.Phase.SOIL));
        assertEquals(100L * (70 * 71 / 2), s.cycleMicros());
        // The window keeps cycles 7..70 (64 of them): 700..7000 us.
        assertEquals(CycleStats.WINDOW, s.windowSize());
        assertEquals(7000, s.max());
        assertEquals(3800, s.p50(), "rank 32 of 700..7000 step 100");
        assertEquals(6700, s.p95(), "rank ceil(0.95*64)=61 of 700..7000");
    }
}
