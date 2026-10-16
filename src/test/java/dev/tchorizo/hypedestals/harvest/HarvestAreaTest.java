package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HarvestAreaTest {

    @Test
    void countIsSideSquaredTimesThree() {
        assertEquals(75, HarvestArea.positions(new BlockPos(10, 64, -5), 2).size());
    }

    @Test
    void orderIsDeterministicAndStartsAtMinCorner() {
        List<BlockPos> positions = HarvestArea.positions(new BlockPos(10, 64, -5), 2);

        assertEquals(new BlockPos(8, 63, -7), positions.getFirst());
        assertEquals(new BlockPos(8, 63, -6), positions.get(1), "z varies fastest");
        assertEquals(new BlockPos(9, 63, -7), positions.get(5), "then x");
        assertEquals(new BlockPos(8, 64, -7), positions.get(25), "then y");
        assertEquals(new BlockPos(12, 65, -3), positions.getLast());
    }

    @Test
    void includesCenter() {
        BlockPos center = new BlockPos(10, 64, -5);

        assertTrue(HarvestArea.positions(center, 2).contains(center));
    }

    @Test
    void radiusOneIsTwentySeven() {
        assertEquals(27, HarvestArea.positions(new BlockPos(0, 0, 0), 1).size());
    }
}
