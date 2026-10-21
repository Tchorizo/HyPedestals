package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds a whole tree from one of its wood blocks (Lumberjack spec §2, Rulings B, C, F). Pure.
 *
 * <ol>
 *   <li>Wood: breadth-first over the 26 neighbours, wood to wood (a diagonal branch belongs to the
 *       trunk).</li>
 *   <li>Leaves: breadth-first from all of that wood through leaves, at most
 *       {@link TreeLimits#leafReach()} steps. Leaves never lead to more wood, so two trees whose
 *       canopies touch stay two trees.</li>
 *   <li>Hanging blocks: non-tree blocks on a face of a tree block that have support rules and do
 *       not rest on a solid non-tree block (fruit under a leaf, sap on the trunk, snow on the
 *       canopy). Cutting the tree without them would leave them floating: removing blocks does not
 *       run the game's support physics (spike: branches and leaves floated for 65 s).</li>
 * </ol>
 *
 * Anything beyond the limits makes the tree {@link Tree.Status#TOO_BIG} rather than cutting a
 * part of it (the rest would float). A neighbour in a section that is not in memory makes it
 * {@link Tree.Status#UNLOADED}; a growing sapling's state on any wood block,
 * {@link Tree.Status#GROWING}.
 */
public final class TreeScan {

    /**
     * Leaf steps from the wood. The vanilla leaves' {@code MaxSupportDistance} is 4; the spike's
     * grown Oak, Birch and Spruce and a natural Birch had every leaf within this reach.
     */
    public static final int LEAF_REACH = 4;

    private static final int[][] AROUND = around();
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private TreeScan() {}

    public static Tree find(TreeWorld world, BlockPos start, TreeLimits limits) {
        if (world.part(start) != TreePart.WOOD) {
            return Tree.skipped(Tree.Status.NOT_A_TREE, List.of());
        }
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start);
        List<BlockPos> wood = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            wood.add(p);
            if (wood.size() > limits.maxBlocks()) {
                return Tree.skipped(Tree.Status.TOO_BIG, wood);
            }
            for (int[] d : AROUND) {
                BlockPos n = new BlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
                if (seen.contains(n)) {
                    continue;
                }
                TreePart part = world.part(n);
                if (part == TreePart.WOOD) {
                    if (!inBounds(start, n, limits)) {
                        return Tree.skipped(Tree.Status.TOO_BIG, wood);
                    }
                    seen.add(n);
                    queue.add(n);
                } else if (part == TreePart.NONE && !world.loaded(n)) {
                    return Tree.skipped(Tree.Status.UNLOADED, wood);
                }
            }
        }
        for (BlockPos w : wood) {
            if (world.growing(w)) {
                return Tree.skipped(Tree.Status.GROWING, wood);
            }
        }

        List<BlockPos> leaves = new ArrayList<>();
        List<BlockPos> frontier = wood;
        for (int step = 1; step <= limits.leafReach() && !frontier.isEmpty(); step++) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos p : frontier) {
                for (int[] d : AROUND) {
                    BlockPos n = new BlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
                    if (seen.contains(n)) {
                        continue;
                    }
                    TreePart part = world.part(n);
                    if (part == TreePart.LEAVES) {
                        if (!inBounds(start, n, limits)) {
                            return Tree.skipped(Tree.Status.TOO_BIG, wood);
                        }
                        seen.add(n);
                        leaves.add(n);
                        next.add(n);
                        if (wood.size() + leaves.size() > limits.maxBlocks()) {
                            return Tree.skipped(Tree.Status.TOO_BIG, wood);
                        }
                    } else if (part == TreePart.NONE && !world.loaded(n)) {
                        return Tree.skipped(Tree.Status.UNLOADED, wood);
                    }
                }
            }
            frontier = next;
        }

        List<BlockPos> hanging = new ArrayList<>();
        for (List<BlockPos> list : List.of(wood, leaves)) {
            for (BlockPos p : list) {
                for (int[] d : FACES) {
                    BlockPos n = new BlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
                    if (!seen.contains(n) && hangs(world, n)) {
                        seen.add(n);
                        hanging.add(n);
                    }
                }
            }
        }
        if (wood.size() + leaves.size() + hanging.size() > limits.maxBlocks()) {
            return Tree.skipped(Tree.Status.TOO_BIG, wood);
        }

        int groundY = groundY(world, wood);
        BlockPos spot = null;
        for (BlockPos w : wood) {
            if (w.y() == groundY && (spot == null || closer(start, w, spot))) {
                spot = w;
            }
        }
        List<BlockPos> buried = new ArrayList<>();
        for (List<BlockPos> list : List.of(wood, leaves, hanging)) {
            for (BlockPos p : list) {
                if (p.y() < groundY) {
                    buried.add(p);
                }
            }
        }
        return new Tree(Tree.Status.CUTTABLE, List.copyOf(wood), List.copyOf(leaves), List.copyOf(hanging),
                groundY, spot, List.copyOf(buried));
    }

    /** A non-tree block with support rules that does not rest on a solid non-tree block. */
    private static boolean hangs(TreeWorld world, BlockPos n) {
        if (world.part(n) != TreePart.NONE || !world.needsSupport(n)) {
            return false;
        }
        BlockPos below = new BlockPos(n.x(), n.y() - 1, n.z());
        return !(world.solid(below) && world.part(below) == TreePart.NONE);
    }

    /** Lowest {@code y} of a wood block with an open side; the lowest wood when none has one. */
    private static int groundY(TreeWorld world, List<BlockPos> wood) {
        int lowestOpen = Integer.MAX_VALUE;
        int lowest = Integer.MAX_VALUE;
        for (BlockPos w : wood) {
            lowest = Math.min(lowest, w.y());
            if (w.y() >= lowestOpen) {
                continue;
            }
            for (int[] d : SIDES) {
                BlockPos n = new BlockPos(w.x() + d[0], w.y(), w.z() + d[2]);
                if (world.part(n) == TreePart.NONE && !world.solid(n)) {
                    lowestOpen = w.y();
                    break;
                }
            }
        }
        return lowestOpen == Integer.MAX_VALUE ? lowest : lowestOpen;
    }

    /** Whether {@code a} is horizontally closer to {@code start} than {@code b}; ties by x, then z. */
    private static boolean closer(BlockPos start, BlockPos a, BlockPos b) {
        long da = sq(a.x() - start.x()) + sq(a.z() - start.z());
        long db = sq(b.x() - start.x()) + sq(b.z() - start.z());
        if (da != db) {
            return da < db;
        }
        return a.x() != b.x() ? a.x() < b.x() : a.z() < b.z();
    }

    private static long sq(int v) {
        return (long) v * v;
    }

    private static boolean inBounds(BlockPos start, BlockPos n, TreeLimits limits) {
        return Math.abs(n.x() - start.x()) <= limits.reach() && Math.abs(n.z() - start.z()) <= limits.reach()
                && n.y() >= start.y() - limits.below() && n.y() <= start.y() + limits.above();
    }

    private static int[][] around() {
        int[][] out = new int[26][];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        out[i++] = new int[]{dx, dy, dz};
                    }
                }
            }
        }
        return out;
    }
}
