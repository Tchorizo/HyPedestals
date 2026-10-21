package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds a whole tree from one of its wood blocks (Lumberjack spec §2, Rulings B, C, F). Pure.
 *
 * <ol>
 *   <li>Wood: breadth-first over the 26 neighbours, wood to wood (a diagonal branch belongs to the
 *       trunk).</li>
 *   <li>Leaves: the leaves connected to that wood through leaves (at most
 *       {@link TreeLimits#leafReach()} steps) that are nearer to it than to any other tree's wood.
 *       Leaves never lead to more wood, so two trees whose canopies touch stay two trees, and a
 *       neighbour keeps the leaves on its side of the canopy.</li>
 *   <li>Hanging blocks: non-tree blocks on a face of a tree block that have support rules and do
 *       not rest on a solid non-tree block (fruit under a leaf, sap on the trunk, snow on the
 *       canopy). Cutting the tree without them would leave them floating: removing blocks does not
 *       run the game's support physics (spike: branches and leaves floated for 65 s).</li>
 * </ol>
 *
 * Wood that does not look like a natural tree ({@link #natural}) is {@link Tree.Status#NOT_A_TREE}.
 * Anything beyond the limits makes the tree {@link Tree.Status#TOO_BIG} rather than cutting a
 * part of it (the rest would float). A neighbour in a section that is not in memory makes it
 * {@link Tree.Status#UNLOADED}; a growing sapling's state on any wood block,
 * {@link Tree.Status#GROWING}.
 */
public final class TreeScan {

    /**
     * Leaf steps from the wood a tree's leaves can be. Over the 1150 vanilla tree prefabs
     * ({@code Server/Prefabs/Trees}) the farthest leaf is 13 steps (26-neighbour, through leaves)
     * from its tree's wood (Oak Stage 7, Fir_Dead_Large); 28% of them have leaves farther than the
     * leaves' {@code MaxSupportDistance} of 4, which the game ignores for placed prefabs.
     */
    public static final int LEAF_REACH = 16;
    /** The leaf flood may cover this many times the block limit (other trees' leaves included). */
    static final int FLOOD_FACTOR = 4;
    /** Returned alone by {@link #leaves} when the flood met an unloaded section. */
    private static final BlockPos UNLOADED_MARK = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

    private static final int[][] AROUND = around();
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private TreeScan() {}

    public static Tree find(TreeWorld world, BlockPos start, TreeLimits limits) {
        if (world.part(start) != TreePart.WOOD) {
            return Tree.skipped(Tree.Status.NOT_A_TREE, List.of(), 1);
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
                return Tree.skipped(Tree.Status.TOO_BIG, wood, seen.size());
            }
            for (int[] d : AROUND) {
                BlockPos n = new BlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
                if (seen.contains(n)) {
                    continue;
                }
                TreePart part = world.part(n);
                if (part == TreePart.WOOD) {
                    if (!inBounds(start, n, limits)) {
                        return Tree.skipped(Tree.Status.TOO_BIG, wood, seen.size());
                    }
                    seen.add(n);
                    queue.add(n);
                } else if (part == TreePart.NONE && !world.loaded(n)) {
                    return Tree.skipped(Tree.Status.UNLOADED, wood, seen.size());
                }
            }
        }
        for (BlockPos w : wood) {
            if (world.growing(w)) {
                return Tree.skipped(Tree.Status.GROWING, wood, seen.size());
            }
        }

        List<BlockPos> leaves = leaves(world, start, limits, wood, seen);
        if (leaves == null) {
            return Tree.skipped(Tree.Status.TOO_BIG, wood, seen.size());
        }
        if (leaves.size() == 1 && leaves.getFirst() == UNLOADED_MARK) {
            return Tree.skipped(Tree.Status.UNLOADED, wood, seen.size());
        }

        if (!natural(world, wood, leaves)) {
            return Tree.skipped(Tree.Status.NOT_A_TREE, wood, seen.size());
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
            return Tree.skipped(Tree.Status.TOO_BIG, wood, seen.size());
        }

        int groundY = groundY(world, wood);
        // The sapling goes back on the column whose wood reaches deepest: a vanilla prefab is
        // anchored at the sapling and only that column carries the roots (itest: a 2x2 trunk base
        // put the sapling on a side column when the spot was the one nearest the scan's start).
        Map<Long, Integer> deepest = new HashMap<>();
        for (BlockPos w : wood) {
            deepest.merge(column(w), w.y(), Math::min);
        }
        BlockPos spot = null;
        for (BlockPos w : wood) {
            if (w.y() != groundY) {
                continue;
            }
            if (spot == null) {
                spot = w;
                continue;
            }
            int dw = deepest.get(column(w)), ds = deepest.get(column(spot));
            if (dw < ds || (dw == ds && closer(start, w, spot))) {
                spot = w;
            }
        }
        List<BlockPos> buried = new ArrayList<>();
        for (BlockPos p : wood) {
            if (p.y() < groundY) {
                buried.add(p);
            }
        }
        return new Tree(Tree.Status.CUTTABLE, List.copyOf(wood), List.copyOf(leaves), List.copyOf(hanging),
                groundY, spot, List.copyOf(buried), seen.size());
    }

    /**
     * The leaves of the tree whose wood is {@code wood}: flood through leaves from that wood for
     * {@link TreeLimits#leafReach()} steps, noting the wood of other trees the flood touches; then
     * every flooded leaf goes to the nearest wood (steps through flooded leaves), ours only when it
     * is strictly nearer than any other tree's. Along a leaf's shortest path to its own tree every
     * leaf belongs to that tree too, so what stays is still connected to its wood. {@code null}
     * when the tree is too big (a leaf of ours beyond the bounds, the flood above
     * {@link #FLOOD_FACTOR} × the block limit, or more blocks than the limit),
     * {@link #UNLOADED_MARK} alone when the flood met a section not in memory.
     */
    private static List<BlockPos> leaves(TreeWorld world, BlockPos start, TreeLimits limits, List<BlockPos> wood,
                                         Set<BlockPos> seen) {
        Map<BlockPos, Integer> fromOurs = new LinkedHashMap<>();
        List<BlockPos> foreign = new ArrayList<>();
        List<BlockPos> clipped = new ArrayList<>();
        int floodCap = limits.maxBlocks() * FLOOD_FACTOR;
        List<BlockPos> frontier = wood;
        for (int step = 1; step <= limits.leafReach() && !frontier.isEmpty(); step++) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos p : frontier) {
                boolean fromLeaf = step > 1;
                for (int[] d : AROUND) {
                    BlockPos n = new BlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
                    if (seen.contains(n)) {
                        continue;
                    }
                    TreePart part = world.part(n);
                    if (part == TreePart.LEAVES) {
                        if (!inBounds(start, n, limits)) {
                            if (!fromLeaf) {
                                return null; // beside our own wood: ours whatever else is around
                            }
                            clipped.add(p);
                            continue;
                        }
                        seen.add(n);
                        fromOurs.put(n, step);
                        next.add(n);
                        if (fromOurs.size() > floodCap) {
                            return null;
                        }
                    } else if (part == TreePart.WOOD) {
                        seen.add(n); // not ours (ours is all seen): another tree's wood
                        foreign.add(n);
                    } else if (part == TreePart.NONE && !world.loaded(n)) {
                        return List.of(UNLOADED_MARK);
                    }
                }
            }
            frontier = next;
        }

        Map<BlockPos, Integer> fromOthers = new HashMap<>();
        frontier = foreign;
        for (int step = 1; !frontier.isEmpty(); step++) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos p : frontier) {
                for (int[] d : AROUND) {
                    BlockPos n = new BlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
                    if (fromOurs.containsKey(n) && !fromOthers.containsKey(n)) {
                        fromOthers.put(n, step);
                        next.add(n);
                    }
                }
            }
            frontier = next;
        }

        List<BlockPos> leaves = new ArrayList<>();
        Set<BlockPos> ours = new HashSet<>();
        fromOurs.forEach((leaf, distance) -> {
            Integer other = fromOthers.get(leaf);
            if (other == null || distance < other) {
                leaves.add(leaf);
                ours.add(leaf);
            }
        });
        for (BlockPos p : clipped) {
            if (ours.contains(p)) {
                return null; // one of our leaves goes on beyond the bounds
            }
        }
        // The other trees' leaves stay: free them for the hanging pass.
        for (BlockPos leaf : fromOurs.keySet()) {
            if (!ours.contains(leaf)) {
                seen.remove(leaf);
            }
        }
        return wood.size() + leaves.size() > limits.maxBlocks() ? null : leaves;
    }

    /**
     * Ruling H (review, Main): wood the Lumberjack may cut looks like a natural tree: it has leaves,
     * at least a quarter as many leaves as wood blocks, and the lowest block of its deepest column
     * (the trunk with the roots) stands on soil. A log cabin (no leaves), or one with a leaf roof on
     * a stone floor, is left alone.
     */
    static boolean natural(TreeWorld world, List<BlockPos> wood, List<BlockPos> leaves) {
        if (leaves.isEmpty() || leaves.size() * 4 < wood.size()) {
            return false;
        }
        BlockPos lowest = null;
        for (BlockPos w : wood) {
            if (lowest == null || w.y() < lowest.y()) {
                lowest = w;
            }
        }
        return world.soil(new BlockPos(lowest.x(), lowest.y() - 1, lowest.z()));
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

    private static long column(BlockPos p) {
        return ((long) p.x() << 32) | (p.z() & 0xFFFFFFFFL);
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
