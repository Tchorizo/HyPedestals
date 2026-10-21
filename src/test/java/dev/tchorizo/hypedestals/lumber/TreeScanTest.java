package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeScanTest {

    private static final TreeLimits LIMITS = new TreeLimits(512, 24, 8, 64, 4);

    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Test
    void findsTrunkAndLeavesOfOneTree() {
        FakeForest world = new FakeForest().ground(0, 0, 4, 0, 4).oak(0, 1, 0, 0, 5);

        Tree tree = TreeScan.find(world, p(0, 1, 0), LIMITS);

        assertEquals(Tree.Status.CUTTABLE, tree.status());
        assertEquals(5, tree.wood().size());
        assertEquals(17, tree.leaves().size()); // 2 rings of 8 + 1 on top
        assertTrue(tree.hanging().isEmpty());
    }

    @Test
    void startOnLeavesOrAirIsNotATree() {
        FakeForest world = new FakeForest().oak(0, 1, 0, 0, 5);

        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(world, p(1, 5, 0), LIMITS).status());
        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(world, p(3, 1, 3), LIMITS).status());
    }

    @Test
    void woodConnectsDiagonallyButLeavesDoNotJoinTwoTrees() {
        FakeForest world = new FakeForest().oak(0, 1, 0, 0, 5).oak(3, 1, 0, 0, 5);
        // The canopies touch (leaves at x=1 and x=2 are neighbours); only leaves connect them.
        world.set(1, 3, 1, "Wood_Oak_Branch_Long"); // 26-adjacent to the trunk block (0,2,0)

        Tree tree = TreeScan.find(world, p(0, 1, 0), LIMITS);

        assertEquals(Tree.Status.CUTTABLE, tree.status());
        assertTrue(tree.wood().contains(p(1, 3, 1)), "diagonal branch belongs to the tree");
        assertFalse(tree.wood().contains(p(3, 1, 0)), "the other trunk is not reached through leaves");
        assertFalse(tree.wood().contains(p(3, 5, 0)));
    }

    @Test
    void leavesBeyondTheReachStay() {
        FakeForest world = new FakeForest().set(0, 1, 0, "Wood_Oak_Trunk");
        for (int x = 1; x <= 6; x++) {
            world.set(x, 1, 0, "Plant_Leaves_Oak");
        }

        Tree tree = TreeScan.find(world, p(0, 1, 0), new TreeLimits(512, 24, 8, 64, 4));

        assertEquals(4, tree.leaves().size());
        assertTrue(tree.leaves().contains(p(4, 1, 0)));
        assertFalse(tree.leaves().contains(p(5, 1, 0)));
    }

    @Test
    void tooManyBlocksIsTooBig() {
        FakeForest world = new FakeForest().oak(0, 1, 0, 0, 5);

        Tree tree = TreeScan.find(world, p(0, 1, 0), new TreeLimits(10, 24, 8, 64, 4));

        assertEquals(Tree.Status.TOO_BIG, tree.status());
        assertTrue(tree.leaves().isEmpty());
    }

    @Test
    void woodOrLeavesOutsideTheReachIsTooBig() {
        FakeForest wide = new FakeForest().set(0, 1, 0, "Wood_Oak_Trunk");
        for (int x = 1; x <= 5; x++) {
            wide.set(x, 1, 0, "Wood_Oak_Branch_Long");
        }
        assertEquals(Tree.Status.TOO_BIG, TreeScan.find(wide, p(0, 1, 0), new TreeLimits(512, 4, 8, 64, 4)).status());

        // Trunk up to y=5, leaves up to y=6: 4 above the start leaves the top leaf out.
        FakeForest tall = new FakeForest().oak(0, 1, 0, 0, 5);
        assertEquals(Tree.Status.TOO_BIG, TreeScan.find(tall, p(0, 1, 0), new TreeLimits(512, 24, 8, 4, 4)).status());
    }

    @Test
    void growingWoodMakesTheTreeGrowing() {
        FakeForest world = new FakeForest().oak(0, 1, 0, 0, 5);
        world.growing.add(p(0, 1, 0));

        assertEquals(Tree.Status.GROWING, TreeScan.find(world, p(0, 3, 0), LIMITS).status());
    }

    @Test
    void unloadedNeighbourMakesTheTreeUnloaded() {
        FakeForest world = new FakeForest().oak(0, 1, 0, 0, 5);
        world.unloaded.add(p(2, 5, 0));

        assertEquals(Tree.Status.UNLOADED, TreeScan.find(world, p(0, 1, 0), LIMITS).status());
    }

    @Test
    void hangingBlocksAreCutButBlocksRestingOnTheGroundStay() {
        FakeForest world = new FakeForest().ground(0, 0, 4, 0, 2).oak(0, 1, 0, 0, 5);
        world.set(1, 3, 1, "Hang_Fruit");   // under the leaf (1,4,1), air below: hangs
        world.set(0, 2, 1, "Hang_Sap");     // on the trunk's side, air below: hangs
        world.set(1, 1, 0, "Hang_Pebbles"); // beside the trunk, on the soil: stays

        Tree tree = TreeScan.find(world, p(0, 1, 0), LIMITS);

        assertTrue(tree.hanging().contains(p(1, 3, 1)));
        assertTrue(tree.hanging().contains(p(0, 2, 1)));
        assertFalse(tree.hanging().contains(p(1, 1, 0)));
    }

    @Test
    void buriedTrunkIsBelowTheGroundAndTheSpotIsTheLowestExposedTrunk() {
        // Grass at y=0, dirt below; the trunk replaced the soil from y=-2 (roots) up.
        FakeForest world = new FakeForest().ground(0, 0, 4, 0, 4).oak(0, 1, 0, 3, 5);

        Tree tree = TreeScan.find(world, p(0, 1, 0), LIMITS);

        assertEquals(Tree.Status.CUTTABLE, tree.status());
        assertEquals(1, tree.groundY());
        assertEquals(p(0, 1, 0), tree.replantSpot());
        assertEquals(3, tree.buried().size());
        assertTrue(tree.buried().contains(p(0, -2, 0)));
        assertTrue(tree.buried().contains(p(0, 0, 0)));
    }

    @Test
    void treeOnStoneHasNothingBuried() {
        FakeForest world = new FakeForest().set(0, 0, 0, "Rock_Stone").oak(0, 1, 0, 0, 5);

        Tree tree = TreeScan.find(world, p(0, 3, 0), LIMITS);

        assertEquals(1, tree.groundY());
        assertEquals(p(0, 1, 0), tree.replantSpot());
        assertTrue(tree.buried().isEmpty());
    }

    @Test
    void blocksAreOrderedTopDown() {
        FakeForest world = new FakeForest().oak(0, 1, 0, 0, 5);

        Tree tree = TreeScan.find(world, p(0, 1, 0), LIMITS);

        int last = Integer.MAX_VALUE;
        for (BlockPos b : tree.blocks()) {
            assertTrue(b.y() <= last);
            last = b.y();
        }
        assertEquals(tree.wood().size() + tree.leaves().size() + tree.hanging().size(), tree.blocks().size());
    }
}
