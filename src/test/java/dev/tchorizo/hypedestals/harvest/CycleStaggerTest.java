package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CycleStaggerTest {

    /** One world tick at the default 30 TPS. */
    private static final float TICK = 1f / 30;

    @Test
    void startsWithinTheInterval() {
        for (float interval : new float[] {0.5f, 3f, 8f, 12f, 600f}) {
            for (int x = -40; x <= 40; x += 3) {
                for (int z = -40; z <= 40; z += 7) {
                    float elapsed = CycleStagger.initialElapsed(new BlockPos(x, 123, z), interval);
                    assertTrue(elapsed >= 0 && elapsed < interval,
                            "elapsed " + elapsed + " for interval " + interval + " at " + x + " " + z);
                }
            }
        }
    }

    @Test
    void sameOffsetForTheSamePosition() {
        BlockPos pos = new BlockPos(8, 123, 280);
        assertEquals(CycleStagger.initialElapsed(pos, 8f), CycleStagger.initialElapsed(new BlockPos(8, 123, 280), 8f));
    }

    @Test
    void productionNeighboursDoNotStartTogether() {
        // Hybrothers (Task 37): the two Iron with a shared column, and the two Cobalt, each pair in
        // one chunk, so the server start loaded them in the same tick.
        assertDifferentTicks(new BlockPos(8, 123, 280), new BlockPos(14, 123, 280), 8f);
        assertDifferentTicks(new BlockPos(2, 123, 268), new BlockPos(13, 123, 268), 3f);
    }

    @Test
    void neighboursSpreadOverTheInterval() {
        // A row of 16 Iron one block apart: the offsets land in many different ticks of the 8 s
        // interval (240 ticks), not in a few.
        Set<Integer> ticks = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            ticks.add((int) (CycleStagger.initialElapsed(new BlockPos(x, 64, 0), 8f) / TICK));
        }
        assertTrue(ticks.size() >= 14, "distinct start ticks: " + ticks.size());
    }

    @Test
    void noOffsetWithoutAnInterval() {
        assertEquals(0f, CycleStagger.initialElapsed(new BlockPos(1, 2, 3), 0f));
        assertEquals(0f, CycleStagger.initialElapsed(new BlockPos(1, 2, 3), -1f));
    }

    /** Offsets at least one tick apart: with the same {@code dt} on every tick, the two timers then never fire in the same tick. */
    private static void assertDifferentTicks(BlockPos a, BlockPos b, float interval) {
        float gap = Math.abs(CycleStagger.initialElapsed(a, interval) - CycleStagger.initialElapsed(b, interval));
        assertTrue(gap >= TICK, a + " and " + b + " start " + gap + " s apart");
    }
}
