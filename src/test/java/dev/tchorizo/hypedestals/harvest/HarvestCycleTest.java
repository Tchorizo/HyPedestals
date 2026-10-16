package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HarvestCycleTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    /** The default {@code CollectMargin}. */
    private static final int MARGIN = 2;
    private static final String WHEAT = "Plant_Crop_Wheat_Block";
    private static final String BERRY = "Plant_Crop_Berry_Block";

    @Test
    void collectsBeforeHarvesting() {
        // The collection uses up one of the two budget units, so only one harvest fits afterwards.
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 2;
        world.slotsFilledByCollect = 1;
        world.mature(new BlockPos(1, 64, 0), WHEAT);
        world.mature(new BlockPos(2, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0);

        assertEquals(List.of("collect " + CENTER + " r=4.0", "harvestBudget"), world.calls.subList(0, 2),
                "the budget is read only after collecting; extent = radius + collectMargin");
        assertEquals(1, result.harvested(), "the budget reflects what the collection stored");
        assertEquals(List.of("harvest " + new BlockPos(1, 64, 0)), world.harvestCalls());
    }

    @Test
    void collectsAgainAfterHarvestingAndSumsBothPasses() {
        // Drops the item index already registered are picked up in the same cycle; the rest
        // waits for the next one. Both passes use the same box extent.
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 5;
        world.collectedOnCollect = 3;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 5, MARGIN, new HashSet<>(), 0);

        assertEquals(6, result.collected(), "both collect passes are summed");
        assertEquals("collect " + CENTER + " r=7.0", world.calls.getFirst());
        assertEquals("collect " + CENTER + " r=7.0", world.calls.getLast(),
                "the cycle ends with a second collect after harvesting");
        assertEquals(1, result.harvested());
    }

    @Test
    void collectExtentFollowsTheConfiguredMargin() {
        FakeCycleWorld zero = new FakeCycleWorld();
        zero.budget = 1;
        HarvestCycle.run(zero, CENTER, 3, 0, new HashSet<>(), 0);
        assertEquals("collect " + CENTER + " r=3.0", zero.calls.getFirst(), "margin 0 collects the area only");

        FakeCycleWorld four = new FakeCycleWorld();
        four.budget = 1;
        HarvestCycle.run(four, CENTER, 3, 4, new HashSet<>(), 0);
        assertEquals("collect " + CENTER + " r=7.0", four.calls.getFirst());
        assertEquals("collect " + CENTER + " r=7.0", four.calls.getLast(), "both passes use the same extent");
    }

    @Test
    void pausesWhenBudgetIsNegative() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = -1;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0);

        assertTrue(result.pausedFull());
        assertEquals(List.of(), world.harvestCalls());
    }

    @Test
    void pausesWhenBudgetIsZeroButStillCollects() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 0;
        world.collectedOnCollect = 7;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0);

        assertTrue(result.pausedFull());
        assertEquals(7, result.collected());
        assertEquals(0, result.harvested());
        assertEquals(List.of(), world.harvestCalls());
    }

    @Test
    void harvestStopsWhenBudgetIsSpent() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 2;
        for (int x = -2; x <= 2; x++) {
            world.mature(new BlockPos(x, 64, 0), WHEAT);
        }

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0);

        assertEquals(2, result.harvested());
        assertEquals(2, world.harvestCalls().size(), "stops calling harvest once the budget is spent");
        assertFalse(result.pausedFull());
    }

    @Test
    void skipsNonCropsAndImmature() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 1;
        // From index 0 the order is y, x, z ascending: the immature crop comes before the mature one.
        BlockPos immature = new BlockPos(-1, 64, 0);
        BlockPos mature = new BlockPos(1, 64, 0);
        world.immature(immature, WHEAT);
        world.mature(mature, WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0);

        assertEquals(1, result.harvested(), "an immature crop does not consume the budget");
        assertEquals(List.of("harvest " + immature, "harvest " + mature), world.harvestCalls(),
                "positions without a crop type are never harvested");
    }

    @Test
    void isolatesExceptionPerTypeAndDedupes() {
        BlockPos berryA = new BlockPos(-1, 64, 0);
        BlockPos berryB = new BlockPos(0, 64, 1);
        BlockPos wheat = new BlockPos(1, 64, 0);
        Set<String> warnedTypes = new HashSet<>();

        FakeCycleWorld first = new FakeCycleWorld();
        first.budget = 9;
        first.mature(berryA, BERRY);
        first.mature(berryB, BERRY);
        first.mature(wheat, WHEAT);
        first.throwingTypes.add(BERRY);

        CycleResult firstResult = HarvestCycle.run(first, CENTER, 2, MARGIN, warnedTypes, 0);

        assertEquals(List.of(BERRY), firstResult.newWarnings());
        assertEquals(List.of("harvest " + berryA, "harvest " + wheat), first.harvestCalls(),
                "other blocks of a failing type are skipped; other types keep being harvested");
        assertEquals(1, firstResult.harvested());
        assertEquals(Set.of(BERRY), warnedTypes);

        FakeCycleWorld second = new FakeCycleWorld();
        second.budget = 9;
        second.mature(berryA, BERRY);
        second.mature(berryB, BERRY);
        second.mature(wheat, WHEAT);
        second.throwingTypes.add(BERRY);

        CycleResult secondResult = HarvestCycle.run(second, CENTER, 2, MARGIN, warnedTypes, 0);

        assertEquals(List.of(), secondResult.newWarnings());
        assertEquals(List.of("harvest " + wheat), second.harvestCalls());
        assertEquals(1, secondResult.harvested());
    }

    @Test
    void transientErrorIsRetriedNextCycle() {
        BlockPos busy = new BlockPos(-1, 64, 0);
        BlockPos other = new BlockPos(1, 64, 0);
        Set<String> warnedTypes = new HashSet<>();

        FakeCycleWorld first = new FakeCycleWorld();
        first.budget = 1;
        first.mature(busy, WHEAT);
        first.mature(other, WHEAT);
        first.transientPositions.add(busy);

        CycleResult firstResult = HarvestCycle.run(first, CENTER, 2, MARGIN, warnedTypes, 0);

        assertEquals(List.of("harvest " + busy, "harvest " + other), first.harvestCalls(),
                "a transient failure spends no budget and does not skip the type");
        assertEquals(1, firstResult.harvested());
        assertEquals(1, firstResult.transientFailures());
        assertEquals(List.of(), firstResult.newWarnings());
        assertEquals(Set.of(), warnedTypes);

        FakeCycleWorld second = new FakeCycleWorld();
        second.budget = 1;
        second.mature(busy, WHEAT);

        // As HarvesterSystem does: the next cycle starts where this one stopped (after `other`),
        // so the busy crop is reached by wrapping around the area.
        CycleResult secondResult = HarvestCycle.run(second, CENTER, 2, MARGIN, warnedTypes, firstResult.nextIndex());

        assertEquals(List.of("harvest " + busy), second.harvestCalls());
        assertEquals(1, secondResult.harvested(), "the crop that was busy is harvested on the next cycle");
        assertEquals(0, secondResult.transientFailures());
    }

    @Test
    void rotatesStartAcrossCycles() {
        // Nine crops that stay mature after each harvest (like Eternal crops under `dev grow`),
        // spread over the area including opposite corners, and a budget of two per cycle. Without
        // rotation the same two crops would be harvested every cycle.
        List<BlockPos> crops = new ArrayList<>();
        for (int x = -2; x <= 2; x += 2) {
            for (int z = -2; z <= 2; z += 2) {
                crops.add(new BlockPos(x, 64, z));
            }
        }
        List<String> harvestedInOrder = new ArrayList<>();
        int start = 0;
        for (int cycle = 0; cycle < 5; cycle++) {
            FakeCycleWorld world = new FakeCycleWorld();
            world.budget = 2;
            crops.forEach(c -> world.mature(c, WHEAT));

            CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), start);

            assertEquals(2, result.harvested());
            harvestedInOrder.addAll(world.harvestCalls());
            start = result.nextIndex();
        }

        assertEquals(10, harvestedInOrder.size());
        List<String> firstLap = harvestedInOrder.subList(0, 9);
        assertEquals(crops.stream().map(c -> "harvest " + c).collect(Collectors.toSet()), Set.copyOf(firstLap),
                "every crop is harvested within the first lap");
        assertEquals(9, Set.copyOf(firstLap).size(), "no crop is harvested twice before the lap completes");
        assertEquals(harvestedInOrder.getFirst(), harvestedInOrder.getLast(),
                "the fifth cycle wraps around to the first crop of the lap");
    }

    @Test
    void nextIndexFollowsTheLastHarvestedPosition() {
        // Radius 1: 27 positions; (0, 63, 0) is index 4 and (1, 64, -1) is index 15.
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 1;
        world.mature(new BlockPos(0, 63, 0), WHEAT);
        world.mature(new BlockPos(1, 64, -1), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 1, MARGIN, new HashSet<>(), 10);

        assertEquals(List.of("harvest " + new BlockPos(1, 64, -1)), world.harvestCalls(),
                "the scan starts at startIndex");
        assertEquals(16, result.nextIndex());
    }

    @Test
    void fullLapWithBudgetLeftKeepsStartIndex() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 9;
        world.mature(new BlockPos(0, 63, 0), WHEAT);
        world.mature(new BlockPos(1, 64, -1), WHEAT);

        // 37 is out of range for 27 positions and wraps to 10.
        CycleResult result = HarvestCycle.run(world, CENTER, 1, MARGIN, new HashSet<>(), 37);

        assertEquals(List.of("harvest " + new BlockPos(1, 64, -1), "harvest " + new BlockPos(0, 63, 0)),
                world.harvestCalls(), "the scan wraps around past the last position");
        assertEquals(2, result.harvested());
        assertEquals(10, result.nextIndex(), "after a full lap the next cycle starts where this one did");
    }

    @Test
    void pausedCycleKeepsStartIndex() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.budget = 0;

        CycleResult result = HarvestCycle.run(world, CENTER, 1, MARGIN, new HashSet<>(), 5);

        assertTrue(result.pausedFull());
        assertEquals(5, result.nextIndex());
    }

    @Test
    void budgetCountsPartialStacks() {
        // One unit per slot that can still take at least one item: empty slots and partial stacks.
        int[][] slots = {{0, 0}, {5, 100}, {100, 100}, {3, 25}, {25, 25}, {0, 100}};
        int budget = 0;
        for (int[] slot : slots) {
            budget += HarvestCycle.slotBudget(slot[0], slot[1]);
        }
        assertEquals(4, budget);
        assertEquals(1, HarvestCycle.slotBudget(99, 100), "a stack one short of full still counts");
        assertEquals(0, HarvestCycle.slotBudget(7, 0), "an item without a known stack size counts as full");
    }

    /** Records every call in order; crop layout, maturity, failures and the budget are configurable. */
    private static final class FakeCycleWorld implements CycleWorld {
        final List<String> calls = new ArrayList<>();
        final Map<BlockPos, String> cropTypes = new HashMap<>();
        final Set<BlockPos> maturePositions = new HashSet<>();
        final Set<String> throwingTypes = new HashSet<>();
        final Set<BlockPos> transientPositions = new HashSet<>();
        int budget;
        int collectedOnCollect;
        int slotsFilledByCollect;

        void mature(BlockPos pos, String type) {
            cropTypes.put(pos, type);
            maturePositions.add(pos);
        }

        void immature(BlockPos pos, String type) {
            cropTypes.put(pos, type);
        }

        List<String> harvestCalls() {
            return calls.stream().filter(call -> call.startsWith("harvest ")).toList();
        }

        @Override
        public String cropTypeAt(BlockPos p) {
            return cropTypes.get(p);
        }

        @Override
        public HarvestOutcome harvest(BlockPos p) {
            calls.add("harvest " + p);
            if (throwingTypes.contains(cropTypes.get(p))) {
                // An unexpected exception from the adapter counts as a TYPE_ERROR.
                throw new IllegalArgumentException("Duplicate block components");
            }
            if (transientPositions.contains(p)) {
                return HarvestOutcome.TRANSIENT_ERROR;
            }
            return maturePositions.contains(p) ? HarvestOutcome.HARVESTED : HarvestOutcome.NOT_READY;
        }

        @Override
        public int collectDroppedItems(BlockPos center, double radius) {
            calls.add("collect " + center + " r=" + radius);
            budget -= slotsFilledByCollect;
            return collectedOnCollect;
        }

        @Override
        public int harvestBudget() {
            calls.add("harvestBudget");
            return budget;
        }
    }
}
