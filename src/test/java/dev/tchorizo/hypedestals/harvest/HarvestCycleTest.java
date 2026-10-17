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

    /** No {@code MaxHarvestsPerCycle} limit (the default). */
    private static final int NO_LIMIT = 0;

    @Test
    void collectsBeforeHarvesting() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertEquals(List.of("containerFull", "collect " + CENTER + " r=4.0", "room",
                        "harvest " + new BlockPos(1, 64, 0), "containerFull", "collect " + CENTER + " r=4.0",
                        "containerFull"), world.calls,
                "collect, read the room, harvest, collect again, each collect after a cheap full check; "
                        + "extent = radius + collectMargin");
    }

    @Test
    void collectsAgainAfterHarvestingAndSumsBothPasses() {
        // Drops the item index already registered are picked up in the same cycle; the rest
        // waits for the burst or the next cycle. Both passes use the same box extent.
        FakeCycleWorld world = new FakeCycleWorld();
        world.collectedOnCollect = 3;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 5, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertEquals(6, result.collected(), "both collect passes are summed");
        assertEquals(List.of("collect " + CENTER + " r=7.0", "collect " + CENTER + " r=7.0"), world.collectCalls());
        assertEquals(1, result.harvested());
    }

    @Test
    void collectExtentFollowsTheConfiguredMargin() {
        FakeCycleWorld zero = new FakeCycleWorld();
        HarvestCycle.run(zero, CENTER, 3, 0, new HashSet<>(), 0, NO_LIMIT);
        assertEquals(List.of("collect " + CENTER + " r=3.0", "collect " + CENTER + " r=3.0"), zero.collectCalls(),
                "margin 0 collects the area only");

        FakeCycleWorld four = new FakeCycleWorld();
        HarvestCycle.run(four, CENTER, 3, 4, new HashSet<>(), 0, NO_LIMIT);
        assertEquals(List.of("collect " + CENTER + " r=7.0", "collect " + CENTER + " r=7.0"), four.collectCalls(),
                "both passes use the same extent");
    }

    @Test
    void harvestsAllRipeWhenRoomIsPlenty() {
        // Every block of the 5x5 layer is a mature crop and the container has room for all drops.
        FakeCycleWorld world = new FakeCycleWorld();
        Set<String> expected = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos crop = new BlockPos(x, 64, z);
                world.mature(crop, WHEAT);
                expected.add("harvest " + crop);
            }
        }

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertEquals(25, result.harvested());
        assertEquals(expected, Set.copyOf(world.harvestCalls()));
        assertEquals(25, world.harvestCalls().size(), "each crop once");
        assertFalse(result.containerFull());
        assertEquals(0, result.nextIndex(), "a full lap ends where it started");
    }

    @Test
    void stopsHarvestingWhenNoRoomLeavesCropsStanding() {
        // Each crop drops up to 2 wheat + 2 essence, 4 per stack: two empty slots take the first
        // crop's drops (2 of 4 in each), the second fills those stacks, the third needs two more.
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = 2;
        world.stackSize = 4;
        List<BlockPos> crops = List.of(new BlockPos(-1, 64, 0), new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0));
        crops.forEach(c -> world.mature(c, WHEAT));
        List<BlockPos> positions = HarvestArea.positions(CENTER, 2);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertEquals(2, result.harvested());
        assertEquals(List.of("harvest " + crops.get(0), "harvest " + crops.get(1)), world.harvestCalls(),
                "the third and fourth crops are never harvested: they wait on the plant");
        assertTrue(result.containerFull(), "stopped for lack of room");
        assertEquals(positions.indexOf(crops.get(2)), result.nextIndex(), "the next cycle starts at the crop that did not fit");

        FakeCycleWorld oneSlot = new FakeCycleWorld();
        oneSlot.freeSlots = 1;
        crops.forEach(c -> oneSlot.mature(c, WHEAT));

        CycleResult none = HarvestCycle.run(oneSlot, CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertEquals(0, none.harvested(), "product and Life Essence need two new stacks; one slot is not enough");
        assertEquals(List.of(), oneSlot.harvestCalls());
        assertTrue(none.containerFull());
    }

    @Test
    void oneFreeSlotAndAPartialStackStillHarvest() {
        // The wheat goes into the partial stack, the essence into the free slot.
        FakeCycleWorld world = new FakeCycleWorld();
        world.freeSlots = 1;
        world.partialRoom.put("Wheat", 4);
        world.mature(new BlockPos(-1, 64, 0), WHEAT);
        world.mature(new BlockPos(1, 64, 0), WHEAT);
        world.mature(new BlockPos(2, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertEquals(2, result.harvested(), "4 wheat fit in the partial stack: two crops, then the wheat needs a slot");
        assertTrue(result.containerFull());
    }

    @Test
    void collectSkippedWhenContainerFull() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.full = true;
        world.freeSlots = 0;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertEquals(List.of(), world.collectCalls(), "nothing could go in: neither collect runs");
        assertEquals(List.of(), world.harvestCalls());
        assertEquals(0, result.collected());
        assertTrue(result.containerFull());
    }

    @Test
    void containerFullIsReadAfterTheSecondCollect() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.fullAfterCollects = 2;
        world.mature(new BlockPos(1, 64, 0), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT);

        assertTrue(result.containerFull(), "the second collect filled the container");
        assertEquals("containerFull", world.calls.getLast());
        assertFalse(HarvestCycle.run(new FakeCycleWorld(), CENTER, 2, MARGIN, new HashSet<>(), 0, NO_LIMIT).containerFull());
    }

    @Test
    void rotationStillCoversAreaDeterministically() {
        // Without a limit every start index harvests every mature crop once, in scan order from
        // the start index (wrapping around), and the next cycle starts at the same index.
        List<BlockPos> crops = List.of(new BlockPos(-2, 63, -2), new BlockPos(0, 64, 0), new BlockPos(2, 65, 2),
                new BlockPos(1, 64, -1));
        List<BlockPos> positions = HarvestArea.positions(CENTER, 2);
        for (int start : new int[] {0, 30, 74, 124, 200}) {
            FakeCycleWorld world = new FakeCycleWorld();
            crops.forEach(c -> world.mature(c, WHEAT));

            CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), start, NO_LIMIT);

            int from = Math.floorMod(start, positions.size());
            List<String> expected = new ArrayList<>();
            for (int i = 0; i < positions.size(); i++) {
                BlockPos p = positions.get((from + i) % positions.size());
                if (crops.contains(p)) {
                    expected.add("harvest " + p);
                }
            }
            assertEquals(expected, world.harvestCalls(), "scan order from start " + start);
            assertEquals(4, result.harvested());
            assertEquals(from, result.nextIndex(), "a full lap keeps the start index");
        }
    }

    @Test
    void maxHarvestsCapsTheCycleAndRotatesAcrossCycles() {
        // Nine crops that stay mature after each harvest (like Eternal crops under `dev grow`),
        // spread over the area including opposite corners, and MaxHarvestsPerCycle = 2. Without
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
            crops.forEach(c -> world.mature(c, WHEAT));

            CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), start, 2);

            assertEquals(2, result.harvested());
            assertEquals(2, world.harvestCalls().size(), "stops calling harvest once the limit is reached");
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
    void skipsNonCropsAndImmature() {
        // From index 0 the order is y, x, z ascending: the immature crop comes before the mature one.
        FakeCycleWorld world = new FakeCycleWorld();
        BlockPos immature = new BlockPos(-1, 64, 0);
        BlockPos mature = new BlockPos(1, 64, 0);
        world.immature(immature, WHEAT);
        world.mature(mature, WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 2, MARGIN, new HashSet<>(), 0, 1);

        assertEquals(1, result.harvested(), "an immature crop does not count towards the limit");
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
        first.mature(berryA, BERRY);
        first.mature(berryB, BERRY);
        first.mature(wheat, WHEAT);
        first.throwingTypes.add(BERRY);

        CycleResult firstResult = HarvestCycle.run(first, CENTER, 2, MARGIN, warnedTypes, 0, NO_LIMIT);

        assertEquals(List.of(BERRY), firstResult.newWarnings());
        assertEquals(List.of("harvest " + berryA, "harvest " + wheat), first.harvestCalls(),
                "other blocks of a failing type are skipped; other types keep being harvested");
        assertEquals(1, firstResult.harvested());
        assertEquals(Set.of(BERRY), warnedTypes);

        FakeCycleWorld second = new FakeCycleWorld();
        second.mature(berryA, BERRY);
        second.mature(berryB, BERRY);
        second.mature(wheat, WHEAT);
        second.throwingTypes.add(BERRY);

        CycleResult secondResult = HarvestCycle.run(second, CENTER, 2, MARGIN, warnedTypes, 0, NO_LIMIT);

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
        first.mature(busy, WHEAT);
        first.mature(other, WHEAT);
        first.transientPositions.add(busy);

        // With a limit of 1 the busy crop must not use it up: `other` is still harvested.
        CycleResult firstResult = HarvestCycle.run(first, CENTER, 2, MARGIN, warnedTypes, 0, 1);

        assertEquals(List.of("harvest " + busy, "harvest " + other), first.harvestCalls(),
                "a transient failure does not count towards the limit and does not skip the type");
        assertEquals(1, firstResult.harvested());
        assertEquals(1, firstResult.transientFailures());
        assertEquals(List.of(), firstResult.newWarnings());
        assertEquals(Set.of(), warnedTypes);

        FakeCycleWorld second = new FakeCycleWorld();
        second.mature(busy, WHEAT);

        // As HarvesterSystem does: the next cycle starts where this one stopped (after `other`),
        // so the busy crop is reached by wrapping around the area.
        CycleResult secondResult = HarvestCycle.run(second, CENTER, 2, MARGIN, warnedTypes, firstResult.nextIndex(), 1);

        assertEquals(List.of("harvest " + busy), second.harvestCalls());
        assertEquals(1, secondResult.harvested(), "the crop that was busy is harvested on the next cycle");
        assertEquals(0, secondResult.transientFailures());
    }

    @Test
    void nextIndexFollowsTheLastHarvestedPositionWhenCapped() {
        // Radius 1: 27 positions; (0, 63, 0) is index 4 and (1, 64, -1) is index 15.
        FakeCycleWorld world = new FakeCycleWorld();
        world.mature(new BlockPos(0, 63, 0), WHEAT);
        world.mature(new BlockPos(1, 64, -1), WHEAT);

        CycleResult result = HarvestCycle.run(world, CENTER, 1, MARGIN, new HashSet<>(), 10, 1);

        assertEquals(List.of("harvest " + new BlockPos(1, 64, -1)), world.harvestCalls(),
                "the scan starts at startIndex");
        assertEquals(16, result.nextIndex());
    }

    @Test
    void fullLapKeepsStartIndex() {
        FakeCycleWorld world = new FakeCycleWorld();
        world.mature(new BlockPos(0, 63, 0), WHEAT);
        world.mature(new BlockPos(1, 64, -1), WHEAT);

        // 37 is out of range for 27 positions and wraps to 10. A limit above the crop count
        // behaves like no limit.
        CycleResult result = HarvestCycle.run(world, CENTER, 1, MARGIN, new HashSet<>(), 37, 9);

        assertEquals(List.of("harvest " + new BlockPos(1, 64, -1), "harvest " + new BlockPos(0, 63, 0)),
                world.harvestCalls(), "the scan wraps around past the last position");
        assertEquals(2, result.harvested());
        assertEquals(10, result.nextIndex(), "after a full lap the next cycle starts where this one did");
    }

    @Test
    void slotHasRoomCountsPartialStacks() {
        assertTrue(HarvestCycle.slotHasRoom(0, 0), "an empty slot has room");
        assertTrue(HarvestCycle.slotHasRoom(5, 100));
        assertTrue(HarvestCycle.slotHasRoom(99, 100), "a stack one short of full has room");
        assertFalse(HarvestCycle.slotHasRoom(100, 100));
        assertFalse(HarvestCycle.slotHasRoom(7, 0), "an item without a known stack size counts as full");
    }

    /**
     * Records every call in order; crop layout, maturity, failures, the container's fullness and
     * room are configurable. A mature crop drops up to 2 wheat and 2 Life Essence (two stacks, like
     * an Eternal wheat); an immature one drops nothing.
     */
    private static final class FakeCycleWorld implements CycleWorld {
        static final List<ItemCount> CROP_DROPS = List.of(new ItemCount("Wheat", 2), new ItemCount("Essence", 2));

        final List<String> calls = new ArrayList<>();
        final Map<BlockPos, String> cropTypes = new HashMap<>();
        final Set<BlockPos> maturePositions = new HashSet<>();
        final Set<String> throwingTypes = new HashSet<>();
        final Set<BlockPos> transientPositions = new HashSet<>();
        boolean full;
        /** The container reports full once this many collects ran ({@code 0} = never by collecting). */
        int fullAfterCollects;
        int collectedOnCollect;
        /** Empty slots of the room snapshot; plenty by default. */
        int freeSlots = 1000;
        /** Room in partial stacks per item id, for the room snapshot. */
        final Map<String, Integer> partialRoom = new HashMap<>();
        int stackSize = 100;
        private int collects;

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

        List<String> collectCalls() {
            return calls.stream().filter(call -> call.startsWith("collect ")).toList();
        }

        @Override
        public String cropTypeAt(BlockPos p) {
            return cropTypes.get(p);
        }

        @Override
        public List<ItemCount> maxDrops(BlockPos p) {
            return maturePositions.contains(p) ? CROP_DROPS : List.of();
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
            collects++;
            return collectedOnCollect;
        }

        @Override
        public boolean containerFull() {
            calls.add("containerFull");
            return full || (fullAfterCollects > 0 && collects >= fullAfterCollects);
        }

        @Override
        public ContainerRoom room() {
            calls.add("room");
            return new ContainerRoom(freeSlots, partialRoom, id -> stackSize);
        }
    }
}
