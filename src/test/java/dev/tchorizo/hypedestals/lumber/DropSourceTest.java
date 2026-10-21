package dev.tchorizo.hypedestals.lumber;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropSourceTest {

    @Test
    void leavesRollTheFallDropNotTheOneForBreakingThemByHand() {
        // Tree_Leaves: Soft (50% fibre) and Physics (2% fibre).
        assertEquals(DropSource.PHYSICS, DropSource.pick(TreePart.LEAVES, false, true, false, true, true));
    }

    @Test
    void leavesWithoutAFallDropKeepTheHandOrder() {
        assertEquals(DropSource.SOFT, DropSource.pick(TreePart.LEAVES, false, true, false, false, false));
        assertEquals(DropSource.SOFT, DropSource.pick(TreePart.LEAVES, false, true, false, true, false));
        assertEquals(DropSource.HARVEST, DropSource.pick(TreePart.LEAVES, false, false, true, false, false));
        assertEquals(DropSource.NONE, DropSource.pick(TreePart.LEAVES, false, false, false, false, false));
    }

    @Test
    void woodKeepsBreakingThenSoftThenHarvestThenPhysics() {
        assertEquals(DropSource.BREAKING, DropSource.pick(TreePart.WOOD, true, true, true, true, true));
        assertEquals(DropSource.SOFT, DropSource.pick(TreePart.WOOD, false, true, true, true, true));
        assertEquals(DropSource.HARVEST, DropSource.pick(TreePart.WOOD, false, false, true, true, true));
        assertEquals(DropSource.PHYSICS, DropSource.pick(TreePart.WOOD, false, false, false, true, true));
        assertEquals(DropSource.NONE, DropSource.pick(TreePart.WOOD, false, false, false, false, false));
    }

    @Test
    void hangingBlocksThatAreNotLeavesKeepTheHandOrder() {
        assertEquals(DropSource.SOFT, DropSource.pick(TreePart.NONE, false, true, false, true, true));
    }
}
