package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CollectBurstTest {

    /** The default {@code PostHarvestCollectSeconds}. */
    private static final float WINDOW = 3f;

    /** Ticks that collect, from a burst of {@code remaining} seconds ticked every {@code dt} until it closes. */
    private static int collectingTicks(float remaining, float dt, float window) {
        int ticks = 0;
        for (int guard = 0; guard < 10_000 && CollectBurst.collects(remaining, window); guard++) {
            remaining = CollectBurst.afterTick(remaining, dt);
            ticks++;
        }
        return ticks;
    }

    @Test
    void aHarvestingCycleOpensTheWindowForItsDuration() {
        float remaining = CollectBurst.afterCycle(0f, 2, WINDOW);

        assertEquals(WINDOW, remaining);
        assertEquals(12, collectingTicks(remaining, 0.25f, WINDOW), "3 s at 0.25 s per tick");
        assertEquals(90, collectingTicks(remaining, 1f / 30f, WINDOW), 1, "about 3 s of 30 Hz ticks");
    }

    @Test
    void aCycleThatHarvestedNothingStartsNoBurst() {
        float remaining = CollectBurst.afterCycle(0f, 0, WINDOW);

        assertEquals(0f, remaining);
        assertFalse(CollectBurst.collects(remaining, WINDOW), "no harvest, no extra collects");
    }

    @Test
    void zeroSecondsTurnsTheBurstOff() {
        assertEquals(0f, CollectBurst.afterCycle(0f, 5, 0f), "a harvest opens no window");
        assertFalse(CollectBurst.collects(2f, 0f), "a running window stops once the config is set to 0");
        assertEquals(0, collectingTicks(CollectBurst.afterCycle(0f, 5, 0f), 0.25f, 0f));
    }

    @Test
    void aHarvestingCycleRestartsARunningWindow() {
        assertEquals(WINDOW, CollectBurst.afterCycle(0.5f, 1, WINDOW));
        assertEquals(10f, CollectBurst.afterCycle(0.5f, 1, 10f), "the window follows the config");
    }

    @Test
    void aCycleWithoutHarvestLeavesARunningWindowAsItWas() {
        assertEquals(1.5f, CollectBurst.afterCycle(1.5f, 0, WINDOW),
                "an empty or paused cycle neither extends nor cuts the burst of the one before");
    }

    @Test
    void ticksCountDownToZeroAndNeverBelow() {
        assertEquals(2.75f, CollectBurst.afterTick(3f, 0.25f));
        assertEquals(0f, CollectBurst.afterTick(0.1f, 0.25f));
        assertTrue(CollectBurst.collects(0.1f, WINDOW), "the last partial tick still collects");
        assertFalse(CollectBurst.collects(CollectBurst.afterTick(0.1f, 0.25f), WINDOW), "then the window is closed");
    }

    @Test
    void burstCollectsAddUpInTheCycleResult() {
        CycleResult cycle = new CycleResult(2, 1, false, List.of(), 0, 5);

        assertEquals(new CycleResult(2, 4, false, List.of(), 0, 5), cycle.plusCollected(3),
                "only the collected count changes");
        assertSame(cycle, cycle.plusCollected(0));
    }
}
