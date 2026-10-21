package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeScanTest {

    private static final TreeLimits LIMITS = TreeLimits.of(512);

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
    void touchingCanopiesAreSplitByTheNearestWood() {
        // Trunks at x=0 and x=4; the leaf row y=5 runs x=-1..5 without a gap.
        FakeForest world = new FakeForest();
        world.set(0, 0, 0, "Soil_Dirt").set(4, 0, 0, "Soil_Dirt");
        for (int y = 1; y <= 5; y++) {
            world.set(0, y, 0, "Wood_Oak_Trunk").set(4, y, 0, "Wood_Oak_Trunk");
        }
        for (int x = -1; x <= 5; x++) {
            world.set(x, 6, 0, "Plant_Leaves_Oak");
        }

        Tree tree = TreeScan.find(world, p(0, 1, 0), new TreeLimits(512, 24, 8, 64, 16));

        assertTrue(tree.leaves().contains(p(1, 6, 0)), "nearer to our trunk");
        assertFalse(tree.leaves().contains(p(2, 6, 0)), "a tie stays with the other tree");
        assertFalse(tree.leaves().contains(p(3, 6, 0)), "nearer to the other trunk");
        assertEquals(3, tree.leaves().size()); // x=-1, 0, 1
    }

    @Test
    void farLeavesOfASingleTreeAreAllTaken() {
        // A fir-like tip 10 leaf steps above the wood (vanilla prefabs reach 13).
        FakeForest world = new FakeForest().set(0, 0, 0, "Soil_Dirt").set(0, 1, 0, "Wood_Fir_Trunk");
        for (int y = 2; y <= 11; y++) {
            world.set(0, y, 0, "Plant_Leaves_Fir");
        }

        Tree tree = TreeScan.find(world, p(0, 1, 0), LIMITS);

        assertEquals(Tree.Status.CUTTABLE, tree.status());
        assertEquals(10, tree.leaves().size());
    }

    @Test
    void leavesBeyondTheReachStay() {
        FakeForest world = new FakeForest().set(0, 0, 0, "Soil_Dirt").set(0, 1, 0, "Wood_Oak_Trunk");
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
    void woodOfAnotherSectionThatIsNotTickingMakesTheTreeUnloaded() {
        // The sapling's origin block (growing, entity parked) sits across the section boundary.
        FakeForest world = new FakeForest().oak(0, 1, 0, 0, 5);
        world.unloaded.add(p(0, 5, 0));

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
    void theSpotIsTheColumnWithTheRootsNotTheOneNearestTheStart() {
        // 2x2 trunk base on grass at y=0: only (0,z=0) has roots down to y=-3, as a vanilla Oak.
        FakeForest world = new FakeForest().ground(0, 0, 4, 0, 5).oak(0, 1, 0, 4, 5);
        world.set(1, 0, 0, "Wood_Oak_Trunk_Full").set(1, 1, 0, "Wood_Oak_Trunk_Full")
                .set(0, 0, 1, "Wood_Oak_Trunk_Full").set(0, 1, 1, "Wood_Oak_Trunk_Full");

        Tree tree = TreeScan.find(world, p(1, 0, 0), LIMITS);

        assertEquals(1, tree.groundY());
        assertEquals(p(0, 1, 0), tree.replantSpot());
    }

    @Test
    void treeOnTopOfTheGroundHasNothingBuried() {
        FakeForest world = new FakeForest().set(0, 0, 0, "Soil_Dirt").oak(0, 1, 0, 0, 5);

        Tree tree = TreeScan.find(world, p(0, 3, 0), LIMITS);

        assertEquals(1, tree.groundY());
        assertEquals(p(0, 1, 0), tree.replantSpot());
        assertTrue(tree.buried().isEmpty());
    }

    @Test
    void logCabinWithoutLeavesIsNotATree() {
        FakeForest world = new FakeForest().ground(0, 0, 4, 0, 2);
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                world.set(x, y, -2, "Wood_Oak_Trunk").set(x, y, 2, "Wood_Oak_Trunk");
            }
        }

        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(world, p(0, 1, -2), LIMITS).status());
    }

    @Test
    void cabinWithALeafRoofOnAStoneFloorIsNotATree() {
        FakeForest world = new FakeForest();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.set(x, 0, z, "Rock_Stone").set(x, 4, z, "Plant_Leaves_Oak");
            }
        }
        for (int[] c : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            for (int y = 1; y <= 3; y++) {
                world.set(c[0], y, c[1], "Wood_Oak_Trunk");
            }
        }

        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(world, p(-2, 1, -2), LIMITS).status());
    }

    @Test
    void fewLeavesForTheWoodIsNotATree() {
        FakeForest world = new FakeForest().ground(0, 0, 2, 0, 2);
        for (int y = 1; y <= 12; y++) {
            world.set(0, y, 0, "Wood_Oak_Trunk");
        }
        world.set(0, 13, 0, "Plant_Leaves_Oak").set(1, 12, 0, "Plant_Leaves_Oak");

        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(world, p(0, 1, 0), LIMITS).status());
    }

    /** Bare rock terrain (4 layers up to y=0) with a trunk sunk {@code roots} blocks into it and a canopy of {@code layers} 5x5 leaf layers. */
    private static FakeForest rockTree(int roots, int height, int firstLeafY, int layers) {
        FakeForest world = new FakeForest();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -3; y <= 0; y++) {
                    world.set(x, y, z, "Rock_Stone");
                }
            }
        }
        for (int y = -roots + 1; y <= height; y++) {
            world.set(0, y, 0, "Wood_Beech_Trunk");
        }
        for (int y = firstLeafY; y < firstLeafY + layers; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x != 0 || z != 0) {
                        world.set(x, y, z, "Plant_Leaves_Beech");
                    }
                }
            }
        }
        return world;
    }

    @Test
    void treeWhoseRootsGrewIntoBareRockIsATree() {
        // Worldgen trees on a rock outcrop: the prefab sank 3 roots into the stone (Hybrothers, Beech and Birch: 8 of 9 mature trees).
        FakeForest world = rockTree(3, 8, 5, 4);

        Tree tree = TreeScan.find(world, p(0, 3, 0), LIMITS);

        assertEquals(Tree.Status.CUTTABLE, tree.status());
        assertEquals(1, tree.groundY());
        assertEquals(3, tree.buried().size());
    }

    @Test
    void rootedTreeOnRockWithALowCanopyIsATree() {
        // Firs carry leaves along the whole trunk: 41% of them above the middle of the wood in the vanilla prefabs.
        FakeForest world = rockTree(3, 14, 1, 7);

        assertEquals(Tree.Status.CUTTABLE, TreeScan.find(world, p(0, 3, 0), LIMITS).status());
    }

    @Test
    void trunkStandingOnTopOfRockIsNotATreeWhateverItsCanopy() {
        FakeForest world = rockTree(0, 8, 4, 4);

        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(world, p(0, 3, 0), LIMITS).status());
    }

    @Test
    void cabinWithAThickLeafRoofOnRockIsNotATree() {
        // 4 pillars of 3 logs under a 5x5x2 roof: 50 leaves for 12 logs, more than the 3x of the old review suggestion.
        FakeForest world = new FakeForest();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.set(x, 0, z, "Rock_Stone").set(x, 4, z, "Plant_Leaves_Oak").set(x, 5, z, "Plant_Leaves_Oak");
            }
        }
        for (int[] c : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            for (int y = 1; y <= 3; y++) {
                world.set(c[0], y, c[1], "Wood_Oak_Trunk");
            }
        }

        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(world, p(-2, 1, -2), LIMITS).status());
    }

    @Test
    void logLetOneBlockIntoRockIsNotATree() {
        // A pillar in a hole of the floor under a leaf roof (a build): the roots of a tree go at least 2 deep.
        assertEquals(Tree.Status.NOT_A_TREE, TreeScan.find(rockTree(1, 8, 5, 4), p(0, 3, 0), LIMITS).status());
        assertEquals(Tree.Status.CUTTABLE, TreeScan.find(rockTree(2, 8, 5, 4), p(0, 3, 0), LIMITS).status());
    }

    @Test
    void buriedIsOnlyWood() {
        FakeForest world = new FakeForest().ground(0, 0, 4, 0, 4).oak(0, 1, 0, 2, 5);
        world.set(1, 0, 0, "Plant_Leaves_Oak"); // a leaf in the ground next to the roots

        Tree tree = TreeScan.find(world, p(0, 1, 0), LIMITS);

        assertEquals(2, tree.buried().size());
        assertFalse(tree.buried().contains(p(1, 0, 0)));
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
