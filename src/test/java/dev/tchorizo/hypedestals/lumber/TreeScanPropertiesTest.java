package dev.tchorizo.hypedestals.lumber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tchorizo.hypedestals.harvest.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Property tests (fixed seed, 1000 cases) of {@link TreeScan} and {@link LumberCycle} over random forests
 * ({@link FakeForest}): a cuttable tree holds only what the lists say it holds, structures that are not
 * trees survive every cycle, trees still growing are left alone, and a cycle's scans stay within its
 * block budget.
 */
class TreeScanPropertiesTest {

    private static final int CASES = 1000;
    private static final long SEED = 20261021L + 4;
    private static final BlockPos CENTER = new BlockPos(0, 1, 0);
    private static final int RADIUS = 16;

    /** A forest in the west half, buildings in the east half, junk everywhere. */
    private static final class Forest {
        final FakeForest world = new FakeForest().ground(0, 0, 20, 0, 4);
        final Set<BlockPos> structure = new HashSet<>();
        final Set<BlockPos> junk = new HashSet<>();
        final Set<BlockPos> growing = new HashSet<>();
        final List<BlockPos> wood = new ArrayList<>();
        int trees;

        Forest(Random rnd) {
            int n = rnd.nextInt(7);
            for (int i = 0; i < n; i++) {
                int x = -14 + rnd.nextInt(14);
                int z = -14 + rnd.nextInt(29);
                world.oak(x, 1, z, rnd.nextInt(4), 3 + rnd.nextInt(6));
                trees++;
            }
            world.blocks.forEach((p, id) -> {
                if (id.startsWith("Wood_")) {
                    wood.add(p);
                }
            });
            // Fruit hanging under some leaves, sap beside some trunks.
            List<BlockPos> all = new ArrayList<>(world.blocks.keySet());
            all.sort((a, b) -> a.x() != b.x() ? a.x() - b.x() : a.y() != b.y() ? a.y() - b.y() : a.z() - b.z());
            for (BlockPos p : all) {
                String id = world.blocks.get(p);
                if (id.startsWith("Plant_Leaves_") && rnd.nextInt(8) == 0) {
                    BlockPos below = new BlockPos(p.x(), p.y() - 1, p.z());
                    if (world.isEmpty(below)) {
                        world.blocks.put(below, "Hang_Fruit");
                    }
                }
            }
            int cabins = rnd.nextInt(3) + 1;
            for (int i = 0; i < cabins; i++) {
                int x = 3 + i * 6; // 2 blocks apart: buildings never merge into one structure
                int z = -14 + rnd.nextInt(24);
                int kind = rnd.nextInt(3);
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 4; dz++) {
                        for (int dy = 1; dy <= 4; dy++) {
                            boolean wall = dx == 0 || dz == 0 || dx == 3 || dz == 3;
                            BlockPos p = new BlockPos(x + dx, dy, z + dz);
                            if (wall && world.isEmpty(p)) {
                                world.blocks.put(p, "Wood_Plank");
                                structure.add(p);
                            }
                        }
                        BlockPos floor = new BlockPos(x + dx, 0, z + dz);
                        if (kind == 1) {
                            world.blocks.put(floor, "Rock_Stone"); // a full leaf roof, but on a stone floor
                        }
                        // kind 1: leaf roof on stone (not a natural tree); kind 2: two leaves on soil (too few).
                        if (kind == 1 || (kind == 2 && dx == 1 && dz < 2)) {
                            BlockPos roof = new BlockPos(x + dx, 5, z + dz);
                            world.blocks.put(roof, "Plant_Leaves_Oak");
                            structure.add(roof);
                        }
                    }
                }
            }
            int noise = rnd.nextInt(30);
            for (int i = 0; i < noise; i++) {
                BlockPos p = new BlockPos(-16 + rnd.nextInt(33), 1 + rnd.nextInt(5), -16 + rnd.nextInt(33));
                if (world.isEmpty(p)) {
                    world.blocks.put(p, rnd.nextBoolean() ? "Chest" : "Rock_Pillar");
                    junk.add(p);
                }
            }
            List<BlockPos> woodOfTrees = new ArrayList<>(wood);
            woodOfTrees.removeIf(structure::contains);
            woodOfTrees.sort((a, b) -> a.x() != b.x() ? a.x() - b.x() : a.y() != b.y() ? a.y() - b.y() : a.z() - b.z());
            if (!woodOfTrees.isEmpty() && rnd.nextInt(3) == 0) {
                growing.add(woodOfTrees.get(rnd.nextInt(woodOfTrees.size())));
                world.growing.addAll(growing);
            }
            world.freeSlots = rnd.nextInt(3) == 0 ? rnd.nextInt(30) : 1000;
            if (rnd.nextBoolean()) {
                world.saplings.put("Plant_Sapling_Oak", 1 + rnd.nextInt(10));
            }
        }
    }

    @Test
    void aCuttableTreeHoldsOnlyWhatTheListsSay() {
        int cuttable = 0;
        int notCuttable = 0;
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            Forest f = new Forest(rnd);
            TreeLimits limits = TreeLimits.of(new int[] {40, 120, 512}[rnd.nextInt(3)]);
            List<BlockPos> starts = new ArrayList<>(f.wood);
            starts.sort((a, b) -> a.x() != b.x() ? a.x() - b.x() : a.y() != b.y() ? a.y() - b.y() : a.z() - b.z());
            for (int i = 0; i < 6 && !starts.isEmpty(); i++) {
                BlockPos start = starts.get(rnd.nextInt(starts.size()));
                Tree tree = TreeScan.find(f.world, start, limits);
                if (f.structure.contains(start)) {
                    assertNotEquals(Tree.Status.CUTTABLE, tree.status(), "case " + c + ": a building at " + start + " is cuttable");
                }
                if (tree.status() != Tree.Status.CUTTABLE) {
                    notCuttable++;
                    assertTrue(tree.hanging().isEmpty() && (tree.status() == Tree.Status.NOT_A_TREE || tree.leaves().isEmpty()),
                            "case " + c + ": skipped tree lists leaves");
                    continue;
                }
                cuttable++;
                Set<BlockPos> seen = new HashSet<>();
                for (BlockPos w : tree.wood()) {
                    assertEquals(TreePart.WOOD, f.world.part(w), "case " + c + ": wood list holds " + f.world.blockId(w));
                    assertTrue(seen.add(w), "case " + c + ": duplicate " + w);
                    assertTrue(!f.structure.contains(w), "case " + c + ": a building block is in a tree");
                }
                for (BlockPos l : tree.leaves()) {
                    assertEquals(TreePart.LEAVES, f.world.part(l), "case " + c + ": leaves list holds " + f.world.blockId(l));
                    assertTrue(seen.add(l), "case " + c + ": duplicate " + l);
                    assertTrue(!f.structure.contains(l), "case " + c + ": a building block is in a tree");
                }
                for (BlockPos h : tree.hanging()) {
                    assertEquals(TreePart.NONE, f.world.part(h), "case " + c + ": hanging list holds a tree block at " + h);
                    assertTrue(f.world.needsSupport(h), "case " + c + ": hanging block without support rules " + h);
                    assertTrue(seen.add(h), "case " + c + ": duplicate " + h);
                    assertTrue(!f.junk.contains(h));
                }
                assertTrue(tree.size() <= limits.maxBlocks(), "case " + c + ": " + tree.size() + " blocks over the limit");
                assertTrue(!tree.leaves().isEmpty() && tree.leaves().size() * 4 >= tree.wood().size(),
                        "case " + c + ": cuttable but not natural");
                assertTrue(tree.wood().stream().noneMatch(f.growing::contains) && !f.growing.contains(start),
                        "case " + c + ": a growing tree is cuttable");
                assertTrue(tree.blocks().stream().allMatch(b -> f.world.blockId(b) != null), "case " + c + ": blocks that do not exist");
                assertTrue(tree.visited() >= tree.size(), "case " + c + ": visited counts every block of the tree");
            }
        }
        assertTrue(cuttable > 1000 && notCuttable > 300, "not vacuous: cuttable=" + cuttable + " other=" + notCuttable);
    }

    @Test
    void cyclesNeverTouchBuildingsJunkOrGrowingTreesAndRespectTheBudget() {
        int cuts = 0;
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + 20000 + c);
            Forest f = new Forest(rnd);
            Map<BlockPos, String> protectedBefore = new HashMap<>();
            for (BlockPos p : f.structure) {
                protectedBefore.put(p, f.world.blockId(p));
            }
            for (BlockPos p : f.junk) {
                protectedBefore.put(p, f.world.blockId(p));
            }
            // Wood and leaves of a tree with a growing block: untouched whole.
            Set<BlockPos> guarded = new HashSet<>();
            for (BlockPos g : f.growing) {
                Tree whole = TreeScan.find(f.world, g, TreeLimits.of(4096));
                guarded.addAll(whole.wood());
            }
            for (BlockPos p : guarded) {
                protectedBefore.put(p, f.world.blockId(p));
            }
            int budget = new int[] {1, 5, 40, 4096}[rnd.nextInt(4)];
            TreeLimits limits = TreeLimits.of(new int[] {60, 512}[rnd.nextInt(2)]);
            TreeMemory memory = new TreeMemory();
            int next = rnd.nextInt(2000);
            for (int i = 0; i < 30; i++) {
                int removedBefore = f.world.removed;
                LumberResult r = LumberCycle.run(f.world, CENTER, RADIUS, limits, next, budget, memory);
                next = r.nextIndex();
                cuts += r.trees();
                int scans = r.growing() + r.tooBig() + r.notTree() + r.trees();
                assertTrue(scans <= budget, "case " + c + ": " + scans + " scans in a cycle with a budget of " + budget + " blocks");
                assertTrue(r.trees() <= 1);
                assertEquals(r.blocks(), f.world.removed - removedBefore, "case " + c + ": blocks removed = reported");
            }
            for (Map.Entry<BlockPos, String> e : protectedBefore.entrySet()) {
                assertEquals(e.getValue(), f.world.blockId(e.getKey()),
                        "case " + c + ": " + e.getValue() + " at " + e.getKey() + " was changed by a cycle");
            }
            int stored = f.world.stored.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(f.world.removed, stored, "case " + c + ": each removed block dropped exactly one item into the container");
        }
        assertTrue(cuts > 500, "not vacuous: trees cut over all cases = " + cuts);
    }
}
