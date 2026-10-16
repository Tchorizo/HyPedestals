package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExportPlanTest {

    private static final BlockPos CENTER = new BlockPos(10, 64, 20);

    @Test
    void neighborsAreBelowThenTheFourSidesNeverAbove() {
        assertEquals(List.of(
                new BlockPos(10, 63, 20),
                new BlockPos(11, 64, 20),
                new BlockPos(9, 64, 20),
                new BlockPos(10, 64, 21),
                new BlockPos(10, 64, 19)), ExportPlan.neighbors(CENTER));
    }

    @Test
    void theLastSlotIsTheInputAndNeverExported() {
        assertTrue(ExportPlan.isExportable(0, 9));
        assertTrue(ExportPlan.isExportable(7, 9));
        assertFalse(ExportPlan.isExportable(8, 9));
        assertFalse(ExportPlan.isExportable(9, 9), "out of range");
        assertFalse(ExportPlan.isExportable(-1, 9), "out of range");
        assertFalse(ExportPlan.isExportable(0, 1), "a one-slot container has only the input slot");
    }

    @Test
    void emptySourceDoesNotLookAtTheWorld() {
        FakeWorld world = new FakeWorld();
        ExportPlan.Result r = ExportPlan.run(world, new FakeSource(0, 0, 0, 5), CENTER);
        assertEquals(ExportPlan.Result.NONE, r);
        assertEquals(List.of(), world.lookups, "only the input slot holds items");
    }

    @Test
    void noContainerAroundMovesNothing() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 0, 0);
        ExportPlan.Result r = ExportPlan.run(world, source, CENTER);
        assertEquals(0, r.moved());
        assertNull(r.target());
        assertEquals(ExportPlan.neighbors(CENTER), world.lookups, "every neighbor is looked up once, in order");
        assertEquals(4, source.counts[0]);
    }

    @Test
    void everythingGoesToTheFirstTargetThatFits() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 0, 6, 2);
        FakeTarget side = world.put(new BlockPos(11, 64, 20), new FakeTarget(100));
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER);

        assertEquals(10, r.moved());
        assertEquals(below.pos, r.target(), "below comes first");
        assertEquals(10, below.received);
        assertEquals(0, side.received);
        assertArrayEquals(new int[] {0, 0, 0, 2}, source.counts, "the input slot stays");
    }

    @Test
    void aFullTargetIsSkippedAndAPartialMoveSpillsToTheNext() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(5, 3, 0);
        FakeTarget full = world.put(new BlockPos(10, 63, 20), new FakeTarget(0));
        FakeTarget small = world.put(new BlockPos(11, 64, 20), new FakeTarget(2));
        FakeTarget big = world.put(new BlockPos(10, 64, 19), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER);

        assertEquals(8, r.moved());
        assertEquals(0, full.received);
        assertEquals(0, full.moveCalls, "accepts() false: moveFrom never called");
        assertEquals(2, small.received);
        assertEquals(6, big.received);
        assertEquals(big.pos, r.target(), "the last target that received items");
        assertArrayEquals(new int[] {0, 0, 0}, source.counts);
    }

    @Test
    void everyTargetFullLeavesTheRest() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(5, 0);
        FakeTarget small = world.put(new BlockPos(9, 64, 20), new FakeTarget(3));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER);

        assertEquals(3, r.moved());
        assertEquals(small.pos, r.target());
        assertArrayEquals(new int[] {2, 0}, source.counts);
    }

    @Test
    void harvestersAreNeverTargets() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(5, 0);
        FakeTarget other = world.put(new BlockPos(10, 63, 20), new FakeTarget(100, true));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER);

        assertEquals(0, r.moved());
        assertEquals(0, other.moveCalls);
        assertEquals(0, other.acceptCalls);
        assertEquals(5, source.counts[0]);
    }

    @Test
    void aContainerTouchingTwoSidesIsUsedOnce() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(5, 0);
        FakeTarget chest = new FakeTarget(2);
        world.put(new BlockPos(10, 63, 20), chest);
        world.put(new BlockPos(11, 64, 20), chest); // same origin through a filler block

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER);

        assertEquals(2, r.moved());
        assertEquals(1, chest.moveCalls, "deduplicated by origin position");
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String message) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual), message);
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        assertArrayEquals(expected, actual, "source counts");
    }

    /** Slots with plain item counts; the last one is the input slot. */
    private static final class FakeSource implements ExportPlan.ExportSource {
        final int[] counts;

        FakeSource(int... counts) {
            this.counts = counts;
        }

        @Override
        public int capacity() {
            return counts.length;
        }

        @Override
        public boolean isEmpty(int slot) {
            return counts[slot] == 0;
        }
    }

    private static final class FakeWorld implements ExportPlan.ExportWorld<FakeSource> {
        final Map<BlockPos, FakeTarget> targets = new HashMap<>();
        final List<BlockPos> lookups = new ArrayList<>();

        FakeTarget put(BlockPos at, FakeTarget target) {
            if (target.pos == null) {
                target.pos = at;
            }
            targets.put(at, target);
            return target;
        }

        @Override
        public FakeTarget targetAt(BlockPos p) {
            lookups.add(p);
            return targets.get(p);
        }
    }

    /** A container with room for {@code room} items of anything. */
    private static final class FakeTarget implements ExportPlan.ExportTarget<FakeSource> {
        final boolean harvester;
        int room;
        int received;
        int acceptCalls;
        int moveCalls;
        BlockPos pos;

        FakeTarget(int room) {
            this(room, false);
        }

        FakeTarget(int room, boolean harvester) {
            this.room = room;
            this.harvester = harvester;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }

        @Override
        public boolean isHarvester() {
            return harvester;
        }

        @Override
        public boolean accepts(FakeSource from, int slot) {
            acceptCalls++;
            return room > 0;
        }

        @Override
        public int moveFrom(FakeSource from, int slot) {
            moveCalls++;
            int n = Math.min(room, from.counts[slot]);
            from.counts[slot] -= n;
            room -= n;
            received += n;
            return n;
        }
    }
}
