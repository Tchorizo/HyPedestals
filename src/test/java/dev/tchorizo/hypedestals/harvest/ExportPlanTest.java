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
    private static final String FERTILIZER = "Tool_Fertilizer";

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
    void fertilizerBagsStayOnlyWhileTheUpgradeIsInstalled() {
        assertFalse(ExportPlan.isExportable("Tool_Fertilizer", true));
        assertTrue(ExportPlan.isExportable("Tool_Fertilizer", false));
        assertTrue(ExportPlan.isExportable("Plant_Crop_Wheat_Item", true));
        assertTrue(ExportPlan.isExportable("Tool_Fertilizer_Crystal", true), "only the vanilla bag is kept");
    }

    @Test
    void emptySourceDoesNotLookAtTheWorld() {
        FakeWorld world = new FakeWorld();
        ExportPlan.Result r = ExportPlan.run(world, new FakeSource(0, 0, 0), CENTER, false);
        assertEquals(ExportPlan.Result.NONE, r);
        assertEquals(List.of(), world.lookups);
    }

    @Test
    void onlyFertilizerBagsWithTheUpgradeDoesNotLookAtTheWorld() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(0, 1, 0, 1).item(1, FERTILIZER).item(3, FERTILIZER);
        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);
        assertEquals(ExportPlan.Result.NONE, r);
        assertEquals(List.of(), world.lookups, "only fertilizer bags, kept by the upgrade");
    }

    @Test
    void exportSkipsFertilizerBagsWhenUpgradeInstalled() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 1, 6, 1).item(1, FERTILIZER).item(3, FERTILIZER);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);

        assertEquals(10, r.moved());
        assertEquals(10, below.received);
        assertArrayEquals(new int[] {0, 1, 0, 1}, source.counts, "the bags stay, whatever their slot");
    }

    @Test
    void exportMovesFertilizerBagsWhenNoUpgrade() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 1, 0, 1).item(1, FERTILIZER).item(3, FERTILIZER);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, false);

        assertEquals(6, r.moved());
        assertEquals(6, below.received);
        assertArrayEquals(new int[] {0, 0, 0, 0}, source.counts);
    }

    @Test
    void everySlotIsExportedIncludingTheLast() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(0, 0, 0, 5);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);

        assertEquals(5, r.moved());
        assertEquals(5, below.received);
        assertArrayEquals(new int[] {0, 0, 0, 0}, source.counts);
    }

    @Test
    void noContainerAroundMovesNothing() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 0, 0);
        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);
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

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);

        assertEquals(12, r.moved());
        assertEquals(below.pos, r.target(), "below comes first");
        assertEquals(12, below.received);
        assertEquals(0, side.received);
        assertArrayEquals(new int[] {0, 0, 0, 0}, source.counts);
    }

    @Test
    void aFullTargetIsSkippedAndAPartialMoveSpillsToTheNext() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(5, 3, 0);
        FakeTarget full = world.put(new BlockPos(10, 63, 20), new FakeTarget(0));
        FakeTarget small = world.put(new BlockPos(11, 64, 20), new FakeTarget(2));
        FakeTarget big = world.put(new BlockPos(10, 64, 19), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);

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

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);

        assertEquals(3, r.moved());
        assertEquals(small.pos, r.target());
        assertArrayEquals(new int[] {2, 0}, source.counts);
    }

    @Test
    void harvestersAreNeverTargets() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(5, 0);
        FakeTarget other = world.put(new BlockPos(10, 63, 20), new FakeTarget(100, true));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);

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

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true);

        assertEquals(2, r.moved());
        assertEquals(1, chest.moveCalls, "deduplicated by origin position");
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String message) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual), message);
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        assertArrayEquals(expected, actual, "source counts");
    }

    /** Slots with plain item counts, of {@code "Rock_Stone_Cobble"} unless {@link #item} says otherwise. */
    private static final class FakeSource implements ExportPlan.ExportSource {
        final int[] counts;
        final String[] ids;

        FakeSource(int... counts) {
            this.counts = counts;
            this.ids = new String[counts.length];
            Arrays.fill(ids, "Rock_Stone_Cobble");
        }

        FakeSource item(int slot, String id) {
            ids[slot] = id;
            return this;
        }

        @Override
        public String itemId(int slot) {
            return ids[slot];
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
