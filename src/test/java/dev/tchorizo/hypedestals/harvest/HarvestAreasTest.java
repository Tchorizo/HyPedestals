package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HarvestAreasTest {

    /** Two Iron (radius 3, CollectMargin 2) 7 blocks apart: areas x 97..103 and 104..110, boxes reach 4 columns into each other. */
    private static final BlockPos A = new BlockPos(100, 64, 100);
    private static final BlockPos B = new BlockPos(107, 64, 100);
    private static final int R = 3;
    private static final double EXTENT = HarvestCycle.collectExtent(R, 2);

    private static List<HarvestAreas.Area> neighbours(HarvestAreas areas, BlockPos self, long tick) {
        return areas.neighbours(self, EXTENT, tick, new ArrayList<>());
    }

    @Test
    void neighbourDropsAreLeftToTheirOwner() {
        HarvestAreas areas = new HarvestAreas();
        areas.refresh(A, R, 10);
        areas.refresh(B, R, 10);
        List<HarvestAreas.Area> ofA = neighbours(areas, A, 10);
        List<HarvestAreas.Area> ofB = neighbours(areas, B, 10);
        assertEquals(1, ofA.size());
        assertEquals(1, ofB.size());

        // A drop of B's border crop (x 104), in A's collect box but in B's area.
        assertFalse(HarvestAreas.mayCollect(A, R, ofA, 104.5, 64.2, 100.5));
        assertTrue(HarvestAreas.mayCollect(B, R, ofB, 104.5, 64.2, 100.5));
        // And the other way round: A's border crop (x 103) is A's.
        assertTrue(HarvestAreas.mayCollect(A, R, ofA, 103.9, 64.2, 100.5));
        assertFalse(HarvestAreas.mayCollect(B, R, ofB, 103.9, 64.2, 100.5));
    }

    @Test
    void dropsOutsideEveryOtherAreaFollowTheBox() {
        HarvestAreas areas = new HarvestAreas();
        areas.refresh(A, R, 10);
        areas.refresh(B, R, 10);
        List<HarvestAreas.Area> ofA = neighbours(areas, A, 10);

        assertTrue(HarvestAreas.mayCollect(A, R, ofA, 95.5, 64.2, 100.5), "A's own margin");
        assertTrue(HarvestAreas.mayCollect(A, R, ofA, 104.5, 68.5, 100.5), "above B's area: nobody's");
        assertTrue(HarvestAreas.mayCollect(A, R, List.of(), 104.5, 64.2, 100.5), "no active neighbour: the box as before");
    }

    @Test
    void overlappingAreasShare() {
        HarvestAreas areas = new HarvestAreas();
        BlockPos close = new BlockPos(105, 64, 100); // areas overlap at x 102..103
        areas.refresh(A, R, 10);
        areas.refresh(close, R, 10);

        assertTrue(HarvestAreas.mayCollect(A, R, neighbours(areas, A, 10), 102.5, 64.2, 100.5));
        assertTrue(HarvestAreas.mayCollect(close, R, neighbours(areas, close, 10), 102.5, 64.2, 100.5));
    }

    @Test
    void anAreaNotRefreshedStopsBeingActive() {
        HarvestAreas areas = new HarvestAreas();
        areas.refresh(A, R, 10);
        areas.refresh(B, R, 10);
        long later = 10 + HarvestAreas.TTL_TICKS + 1;
        areas.refresh(A, R, later);

        assertEquals(List.of(), neighbours(areas, A, later), "B was broken or unloaded");
        assertTrue(HarvestAreas.mayCollect(A, R, neighbours(areas, A, later), 104.5, 64.2, 100.5));
    }

    @Test
    void aRemovedAreaIsNoLongerActive() {
        HarvestAreas areas = new HarvestAreas();
        areas.refresh(A, R, 10);
        areas.refresh(B, R, 10);
        areas.remove(B);

        assertEquals(List.of(), neighbours(areas, A, 10), "B was broken: its drops are A's to take at once");
        assertTrue(HarvestAreas.mayCollect(A, R, neighbours(areas, A, 10), 104.5, 64.2, 100.5));
    }

    @Test
    void farHarvestersAreNotNeighbours() {
        HarvestAreas areas = new HarvestAreas();
        areas.refresh(A, R, 10);
        areas.refresh(new BlockPos(110, 64, 100), R, 10); // area from x 107, box of A ends at 105.5
        areas.refresh(new BlockPos(107, 80, 100), R, 10); // stacked well above

        assertEquals(List.of(), neighbours(areas, A, 10));
    }

    @Test
    void aBlockIsHarvestedByOneHarvesterPerTick() {
        // Task 37: two Iron with one shared column whose cycles fell on the same world tick both
        // harvested its crops; the second harvest queued a second block entity at the same block
        // and the engine crashed the world consuming the command buffer.
        HarvestAreas areas = new HarvestAreas();
        BlockPos shared = new BlockPos(103, 64, 100);

        assertTrue(areas.claimHarvest(shared, 10), "first Harvester this tick");
        assertFalse(areas.claimHarvest(shared, 10), "second Harvester, same tick");
        assertTrue(areas.claimHarvest(new BlockPos(103, 64, 101), 10), "another block, same tick");
        assertTrue(areas.claimHarvest(new BlockPos(103, 65, 100), 10), "the block above, same tick");
    }

    @Test
    void claimsLastOneTick() {
        HarvestAreas areas = new HarvestAreas();
        BlockPos shared = new BlockPos(103, 64, 100);
        assertTrue(areas.claimHarvest(shared, 10));

        assertTrue(areas.claimHarvest(shared, 11), "the next tick sees the block entity the first harvest added");
        assertFalse(areas.claimHarvest(shared, 11));
        assertTrue(areas.claimHarvest(shared, 30), "any later tick");
    }
}
