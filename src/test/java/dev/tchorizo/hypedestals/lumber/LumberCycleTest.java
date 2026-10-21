package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.HarvestArea;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumberCycleTest {

    private static final TreeLimits LIMITS = TreeLimits.of(512);
    /** The Lumberjack stands on the grass at y=1, like the saplings. */
    private static final BlockPos CENTER = new BlockPos(0, 1, 0);

    private static FakeForest field() {
        return new FakeForest().ground(0, 0, 8, 0, 5);
    }

    @Test
    void cutsTheWholeTreeStoresTheDropsFillsTheHoleAndReplants() {
        FakeForest world = field().oak(3, 1, 0, 3, 5);
        world.saplings.put("Plant_Sapling_Oak", 2);

        LumberResult r = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 4096, new TreeMemory());

        assertEquals(1, r.trees());
        assertEquals(8 + 17, r.blocks()); // trunk y=-2..5, 17 leaves
        assertEquals(25, r.items());
        assertEquals(8, world.stored.get("Wood_Oak_Trunk"));
        assertEquals(17, world.stored.get("Plant_Leaves_Oak"));
        assertEquals(0, world.count("Wood_"));
        assertEquals(0, world.count("Plant_Leaves_"));
        // Roots replaced the grass at y=0 and the dirt below: back as the soil beside them.
        assertEquals("Soil_Grass", world.at(3, 0, 0));
        assertEquals("Soil_Dirt", world.at(3, -2, 0));
        assertEquals(LumberResult.Replant.PLANTED, r.replant());
        assertEquals("Plant_Sapling_Oak", r.sapling());
        assertEquals("Plant_Sapling_Oak", world.at(3, 1, 0));
        assertEquals(1, world.saplings.get("Plant_Sapling_Oak"));
        assertFalse(r.containerFull());
    }

    @Test
    void oneTreePerCycleAndTheNextCycleGoesOn() {
        FakeForest world = field().oak(-3, 1, 0, 0, 5).oak(3, 1, 0, 0, 5);

        LumberResult first = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 4096, new TreeMemory());
        assertEquals(1, first.trees());
        assertEquals(5, world.count("Wood_"));

        LumberResult second = LumberCycle.run(world, CENTER, 4, LIMITS, first.nextIndex(), 4096, new TreeMemory());
        assertEquals(1, second.trees());
        assertEquals(0, world.count("Wood_"));
        assertEquals(LumberResult.Replant.NO_SAPLING, second.replant());
    }

    @Test
    void dropsThatDoNotFitLeaveTheTreeStandingAndTheNextCycleStartsAtIt() {
        FakeForest world = field().oak(3, 1, 0, 0, 5);
        world.freeSlots = 1; // trunk and leaves need two slots

        LumberResult r = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 4096, new TreeMemory());

        assertTrue(r.containerFull());
        assertEquals(0, r.trees());
        assertEquals(5, world.count("Wood_"));
        assertEquals(TreePart.WOOD, world.part(HarvestArea.positions(CENTER, 4).get(r.nextIndex())),
                "the next cycle starts at the waiting tree");
        assertTrue(world.stored.isEmpty());
    }

    @Test
    void growingAndTooBigTreesAreSkippedOncePerTree() {
        FakeForest world = field().oak(-3, 1, 0, 0, 5).oak(3, 1, 0, 0, 5);
        world.growing.add(new BlockPos(-3, 1, 0));

        LumberResult r = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 4096, new TreeMemory());

        assertEquals(1, r.growing(), "the trunk crosses two area layers but is one tree");
        assertEquals(1, r.trees());
        assertEquals(5, world.count("Wood_"), "the growing tree stays");

        FakeForest other = field().oak(3, 1, 0, 0, 5);
        LumberResult big = LumberCycle.run(other, CENTER, 4, new TreeLimits(10, 24, 8, 64, 4), 0, 4096, new TreeMemory());
        assertEquals(0, big.trees());
        assertEquals(1, big.tooBig());
        assertEquals(5, other.count("Wood_"));
    }

    @Test
    void noRoomAroundTheStumpMeansNoSapling() {
        FakeForest world = field().oak(3, 1, 0, 0, 5);
        world.set(4, 1, 0, "Rock_Stone"); // in the sapling's clearance ring
        world.saplings.put("Plant_Sapling_Oak", 1);

        LumberResult r = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 4096, new TreeMemory());

        assertEquals(1, r.trees());
        assertEquals(LumberResult.Replant.NO_ROOM, r.replant());
        assertNull(r.sapling());
        assertEquals(1, world.saplings.get("Plant_Sapling_Oak"));
    }

    @Test
    void nothingToCutKeepsTheStartIndex() {
        FakeForest world = field();

        LumberResult r = LumberCycle.run(world, CENTER, 2, LIMITS, 7, 4096, new TreeMemory());

        assertEquals(0, r.trees());
        assertEquals(7, r.nextIndex());
        assertEquals(LumberResult.Replant.NONE, r.replant());
    }

    @Test
    void unloadedAreaPositionsAreCounted() {
        FakeForest world = field();
        world.unloaded.add(new BlockPos(2, 1, 2));

        assertEquals(1, LumberCycle.run(world, CENTER, 2, LIMITS, 0, 4096, new TreeMemory()).unloaded());
    }

    @Test
    void woodWithoutSoilBesideItBecomesAir() {
        // A trunk rooted 2 blocks into stone, on a dirt pocket: no soil beside the roots.
        FakeForest world = new FakeForest();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                for (int y = -2; y <= 0; y++) {
                    world.set(x, y, z, "Rock_Stone");
                }
            }
        }
        world.set(3, -3, 0, "Soil_Dirt").oak(3, 1, 0, 3, 5);

        LumberResult r = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 4096, new TreeMemory());

        assertEquals(1, r.trees());
        assertNull(world.at(3, 0, 0));
        assertNull(world.at(3, -2, 0));
    }

    @Test
    void notATreeIsCountedAndLeftStanding() {
        FakeForest world = field();
        for (int y = 1; y <= 3; y++) {
            world.set(2, y, 0, "Wood_Oak_Trunk");
        }

        LumberResult r = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 4096, new TreeMemory());

        assertEquals(0, r.trees());
        assertEquals(1, r.notTree());
        assertEquals(3, world.count("Wood_"));
    }

    @Test
    void theBlockBudgetStopsTheCycleAndTheNextGoesOn() {
        FakeForest world = field().oak(-3, 1, 0, 0, 5).oak(3, 1, 0, 0, 5);
        world.growing.add(new BlockPos(-3, 1, 0));
        TreeMemory memory = new TreeMemory();

        LumberResult first = LumberCycle.run(world, CENTER, 4, LIMITS, 0, 1, memory);
        assertEquals(0, first.trees());
        assertEquals(1, first.growing());
        assertTrue(first.budgetHit());

        LumberResult second = LumberCycle.run(world, CENTER, 4, LIMITS, first.nextIndex(), 1, memory);
        assertEquals(1, second.trees(), "the next cycle resumes at the other tree");
    }

    @Test
    void tooBigTreesAreRememberedForSomeCycles() {
        FakeForest world = field().oak(3, 1, 0, 0, 5);
        TreeMemory memory = new TreeMemory();
        TreeLimits small = new TreeLimits(10, 24, 8, 64, 16);

        assertEquals(1, LumberCycle.run(world, CENTER, 4, small, 0, 4096, memory).tooBig());
        int reads = world.partReads;
        LumberResult again = LumberCycle.run(world, CENTER, 4, small, 0, 4096, memory);
        assertEquals(0, again.tooBig(), "skipped without searching it again");
        assertTrue(world.partReads - reads < 300, "only the area scan ran");
        int searchedAgainAfter = 0;
        for (int i = 2; i <= TreeMemory.CYCLES + 1 && searchedAgainAfter == 0; i++) {
            if (LumberCycle.run(world, CENTER, 4, small, 0, 4096, memory).tooBig() == 1) {
                searchedAgainAfter = i;
            }
        }
        assertEquals(TreeMemory.CYCLES, searchedAgainAfter, "searched again once the memory expires");
    }
}
