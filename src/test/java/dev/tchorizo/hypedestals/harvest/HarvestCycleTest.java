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
import org.junit.jupiter.api.Test;

class HarvestCycleTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final String WHEAT = "Plant_Crop_Wheat_Block";
    private static final String BERRY = "Plant_Crop_Berry_Block";

    @Test
    void collectsBeforeHarvesting() {
        // The collection fills one of the two empty slots, so only one harvest fits afterwards.
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = 2;
        world.slotsFilledByCollect = 1;
        world.mature(new BlockPos(1, 64, 0), WHEAT);
        world.mature(new BlockPos(2, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, new HashSet<>());

        assertEquals(List.of("collect " + CENTER + " r=4.0", "freeSlots"), world.calls.subList(0, 2),
                "free slots are read only after collecting; extent = radius + COLLECT_MARGIN");
        assertEquals(1, result.harvested(), "the budget reflects the slots the collection filled");
        assertEquals(List.of("harvest " + new BlockPos(1, 64, 0)), world.harvestCalls());
    }

    @Test
    void collectsAgainAfterHarvestingAndSumsBothPasses() {
        // Drops the item index already registered are picked up in the same cycle; the rest
        // waits for the next one. Both passes use the same box extent.
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = 5;
        world.collectedOnCollect = 3;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 5, new HashSet<>());

        assertEquals(6, result.collected(), "both collect passes are summed");
        assertEquals("collect " + CENTER + " r=7.0", world.calls.getFirst());
        assertEquals("collect " + CENTER + " r=7.0", world.calls.getLast(),
                "the cycle ends with a second collect after harvesting");
        assertEquals(1, result.harvested());
    }

    @Test
    void pausesWhenFreeSlotsIsNegative() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = -1;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, new HashSet<>());

        assertTrue(result.pausedFull());
        assertEquals(List.of(), world.harvestCalls());
    }

    @Test
    void pausesWhenNoFreeSlotEvenIfStacksCouldMerge() {
        // freeSlots counts fully empty slots only: a container full of partial wheat stacks
        // reports 0 even though more wheat could still merge into those stacks.
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = 0;
        world.collectedOnCollect = 7;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, new HashSet<>());

        assertTrue(result.pausedFull());
        assertEquals(7, result.collected());
        assertEquals(0, result.harvested());
        assertEquals(List.of(), world.harvestCalls());
    }

    @Test
    void harvestBudgetEqualsFreeSlots() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = 2;
        for (int x = -2; x <= 2; x++) {
            world.mature(new BlockPos(x, 64, 0), WHEAT);
        }

        CycleResult result = HarvestCycle.run(world, CENTER, 2, new HashSet<>());

        assertEquals(2, result.harvested());
        assertEquals(2, world.harvestCalls().size(), "stops calling harvest once the budget is spent");
        assertFalse(result.pausedFull());
    }

    @Test
    void skipsNonCropsAndImmature() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = 1;
        // Iteration order is y, x, z ascending: the immature crop comes before the mature one.
        BlockPos immature = new BlockPos(-1, 64, 0);
        BlockPos mature = new BlockPos(1, 64, 0);
        world.immature(immature, WHEAT);
        world.mature(mature, WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, new HashSet<>());

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
        first.freeSlots = 9;
        first.mature(berryA, BERRY);
        first.mature(berryB, BERRY);
        first.mature(wheat, WHEAT);
        first.throwingTypes.add(BERRY);

        CycleResult firstResult = HarvestCycle.run(first, CENTER, 2, warnedTypes);

        assertEquals(List.of(BERRY), firstResult.newWarnings());
        assertEquals(List.of("harvest " + berryA, "harvest " + wheat), first.harvestCalls(),
                "other blocks of a failing type are skipped; other types keep being harvested");
        assertEquals(1, firstResult.harvested());
        assertEquals(Set.of(BERRY), warnedTypes);

        FakeCycleWorld second = new FakeCycleWorld();
        second.freeSlots = 9;
        second.mature(berryA, BERRY);
        second.mature(berryB, BERRY);
        second.mature(wheat, WHEAT);
        second.throwingTypes.add(BERRY);

        CycleResult secondResult = HarvestCycle.run(second, CENTER, 2, warnedTypes);

        assertEquals(List.of(), secondResult.newWarnings());
        assertEquals(List.of("harvest " + wheat), second.harvestCalls());
        assertEquals(1, secondResult.harvested());
    }

    /** Records every call in order; crop layout, maturity, failures and free slots are configurable. */
    private static final class FakeCycleWorld implements CycleWorld {
        final List<String> calls = new ArrayList<>();
        final Map<BlockPos, String> cropTypes = new HashMap<>();
        final Set<BlockPos> maturePositions = new HashSet<>();
        final Set<String> throwingTypes = new HashSet<>();
        int freeSlots;
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
        public boolean harvest(BlockPos p) {
            calls.add("harvest " + p);
            if (throwingTypes.contains(cropTypes.get(p))) {
                throw new IllegalArgumentException("Duplicate block components");
            }
            return maturePositions.contains(p);
        }

        @Override
        public int collectDroppedItems(BlockPos center, double radius) {
            calls.add("collect " + center + " r=" + radius);
            freeSlots -= slotsFilledByCollect;
            return collectedOnCollect;
        }

        @Override
        public int freeSlots() {
            calls.add("freeSlots");
            return freeSlots;
        }
    }
}
