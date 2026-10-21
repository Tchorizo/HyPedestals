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
    private static final String POOP = "Ingredient_Poop";
    /** The default {@code ManurePerApplication}. */
    private static final int K = 2;
    /** The default {@code ManureStock}: one stack of poop. */
    private static final int STOCK = 100;

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
        assertFalse(ExportPlan.isExportable("Tool_Fertilizer", true, K));
        assertTrue(ExportPlan.isExportable("Tool_Fertilizer", false, K));
        assertFalse(ExportPlan.isExportable("Tool_Fertilizer", true, 0), "bags stay even with poop off");
        assertTrue(ExportPlan.isExportable("Plant_Crop_Wheat_Item", true, K));
        assertTrue(ExportPlan.isExportable("Tool_Fertilizer_Crystal", true, K), "only the vanilla bag is kept");
    }

    @Test
    void poopStaysWhileTheUpgradeIsInstalledAndPoopIsFuel() {
        assertFalse(ExportPlan.isExportable(POOP, true, K));
        assertTrue(ExportPlan.isExportable(POOP, false, K), "without the upgrade poop is an ordinary item");
        assertTrue(ExportPlan.isExportable(POOP, true, 0), "ManurePerApplication 0: poop is not fuel");
    }

    @Test
    void exportSkipsPoopWhenUpgradeInstalled() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 5, 1, 3).item(1, POOP).item(2, FERTILIZER).item(3, POOP);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

        assertEquals(4, r.moved());
        assertEquals(4, below.received);
        assertArrayEquals(new int[] {0, 5, 1, 3}, source.counts, "every poop slot and the bag stay");

        FakeSource off = new FakeSource(4, 5, 1, 3).item(1, POOP).item(2, FERTILIZER).item(3, POOP);
        ExportPlan.run(world, off, CENTER, true, 0, STOCK, null, 0);
        assertArrayEquals(new int[] {0, 0, 1, 0}, off.counts, "with poop off only the bag stays");
    }

    @Test
    void exportsPoopAboveStock() {
        // 60 + 50 + 40 poop = 150 with a stock of 100: 50 leave, from the highest slot down
        // (all 40 of slot 4, 10 of slot 2); slot 0 and the bag stay.
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(60, 1, 50, 7, 40)
                .item(0, POOP).item(1, FERTILIZER).item(2, POOP).item(4, POOP);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(1000));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

        assertEquals(57, r.moved(), "the cobble and the 50 poop above the stock");
        assertEquals(57, below.received);
        assertArrayEquals(new int[] {60, 1, 40, 0, 0}, source.counts);

        FakeSource atStock = new FakeSource(60, 40).item(0, POOP).item(1, POOP);
        assertEquals(ExportPlan.Result.NONE, ExportPlan.run(world, atStock, CENTER, true, K, STOCK, null, 0),
                "exactly the stock: nothing leaves");

        FakeSource none = new FakeSource(60, 40).item(0, POOP).item(1, POOP);
        ExportPlan.run(world, none, CENTER, true, K, 0, null, 0);
        assertArrayEquals(new int[] {0, 0}, none.counts, "ManureStock 0: all the poop leaves");

        FakeSource noUpgrade = new FakeSource(60, 40).item(0, POOP).item(1, POOP);
        ExportPlan.run(world, noUpgrade, CENTER, false, K, STOCK, null, 0);
        assertArrayEquals(new int[] {0, 0}, noUpgrade.counts, "without the upgrade poop is an ordinary item");
    }

    @Test
    void aPartialPoopMoveStopsAtTheExcess() {
        // 130 poop, stock 100: 30 leave; the first target takes 20, the next one the other 10.
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(100, 30).item(0, POOP).item(1, POOP);
        FakeTarget small = world.put(new BlockPos(10, 63, 20), new FakeTarget(20));
        FakeTarget big = world.put(new BlockPos(11, 64, 20), new FakeTarget(1000));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

        assertEquals(30, r.moved());
        assertEquals(20, small.received);
        assertEquals(10, big.received);
        assertArrayEquals(new int[] {100, 0}, source.counts);
    }

    @Test
    void withThePlanterEachSeedKindKeepsItsStockFromTheLowestSlots() {
        // wheat seeds 50 + 30 (stock 64: 16 leave from slot 3), saplings 10 (all stay), cobble leaves,
        // carrot seeds 70 (6 leave).
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(50, 10, 5, 30, 70)
                .item(0, "Plant_Seeds_Wheat").item(1, "Plant_Sapling_Oak").item(3, "Plant_Seeds_Wheat")
                .item(4, "Plant_Seeds_Carrot");
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(1000));
        java.util.function.Predicate<String> plantable = id -> id.startsWith("Plant_Seeds_") || id.startsWith("Plant_Sapling_");

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, false, K, STOCK, plantable, 64);

        assertEquals(5 + 16 + 6, r.moved());
        assertEquals(27, below.received);
        assertArrayEquals(new int[] {50, 10, 0, 14, 64}, source.counts);

        FakeSource all = new FakeSource(50, 10).item(0, "Plant_Seeds_Wheat").item(1, "Plant_Sapling_Oak");
        ExportPlan.run(world, all, CENTER, false, K, STOCK, plantable, 0);
        assertArrayEquals(new int[] {0, 0}, all.counts, "PlanterSeedStock 0: every seed leaves");

        FakeSource noPlanter = new FakeSource(50).item(0, "Plant_Seeds_Wheat");
        ExportPlan.run(world, noPlanter, CENTER, false, K, STOCK, null, 64);
        assertArrayEquals(new int[] {0}, noPlanter.counts, "without the upgrade seeds are ordinary items");

        FakeSource bags = new FakeSource(1, 5).item(0, FERTILIZER).item(1, "Plant_Seeds_Wheat");
        assertEquals(ExportPlan.Result.NONE, ExportPlan.run(world, bags, CENTER, true, K, STOCK, plantable, 64),
                "both upgrades: bags and seeds under the stock stay");
    }

    @Test
    void emptySourceDoesNotLookAtTheWorld() {
        FakeWorld world = new FakeWorld();
        ExportPlan.Result r = ExportPlan.run(world, new FakeSource(0, 0, 0), CENTER, false, K, STOCK, null, 0);
        assertEquals(ExportPlan.Result.NONE, r);
        assertEquals(List.of(), world.lookups);
    }

    @Test
    void onlyFertilizerBagsWithTheUpgradeDoesNotLookAtTheWorld() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(0, 1, 0, 1).item(1, FERTILIZER).item(3, FERTILIZER);
        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);
        assertEquals(ExportPlan.Result.NONE, r);
        assertEquals(List.of(), world.lookups, "only fertilizer bags, kept by the upgrade");
    }

    @Test
    void exportSkipsFertilizerBagsWhenUpgradeInstalled() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 1, 6, 1).item(1, FERTILIZER).item(3, FERTILIZER);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

        assertEquals(10, r.moved());
        assertEquals(10, below.received);
        assertArrayEquals(new int[] {0, 1, 0, 1}, source.counts, "the bags stay, whatever their slot");
    }

    @Test
    void exportMovesFertilizerBagsWhenNoUpgrade() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 1, 0, 1).item(1, FERTILIZER).item(3, FERTILIZER);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, false, K, STOCK, null, 0);

        assertEquals(6, r.moved());
        assertEquals(6, below.received);
        assertArrayEquals(new int[] {0, 0, 0, 0}, source.counts);
    }

    @Test
    void everySlotIsExportedIncludingTheLast() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(0, 0, 0, 5);
        FakeTarget below = world.put(new BlockPos(10, 63, 20), new FakeTarget(100));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

        assertEquals(5, r.moved());
        assertEquals(5, below.received);
        assertArrayEquals(new int[] {0, 0, 0, 0}, source.counts);
    }

    @Test
    void noContainerAroundMovesNothing() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(4, 0, 0);
        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);
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

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

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

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

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

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

        assertEquals(3, r.moved());
        assertEquals(small.pos, r.target());
        assertArrayEquals(new int[] {2, 0}, source.counts);
    }

    @Test
    void machinesAreNeverTargets() {
        FakeWorld world = new FakeWorld();
        FakeSource source = new FakeSource(5, 0);
        FakeTarget other = world.put(new BlockPos(10, 63, 20), new FakeTarget(100, true));

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

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

        ExportPlan.Result r = ExportPlan.run(world, source, CENTER, true, K, STOCK, null, 0);

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
        public int quantity(int slot) {
            return counts[slot];
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
        final boolean machine;
        int room;
        int received;
        int acceptCalls;
        int moveCalls;
        BlockPos pos;

        FakeTarget(int room) {
            this(room, false);
        }

        FakeTarget(int room, boolean machine) {
            this.room = room;
            this.machine = machine;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }

        @Override
        public boolean isMachine() {
            return machine;
        }

        @Override
        public boolean accepts(FakeSource from, int slot) {
            acceptCalls++;
            return room > 0;
        }

        @Override
        public int moveFrom(FakeSource from, int slot, int max) {
            moveCalls++;
            int n = Math.min(Math.min(room, max), from.counts[slot]);
            from.counts[slot] -= n;
            room -= n;
            received += n;
            return n;
        }
    }
}
