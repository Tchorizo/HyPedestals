package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a {@link TreeScan}: a tree found from one of its wood blocks, or why it is not cut.
 * Only a {@link Status#CUTTABLE} tree is cut; for a skipped one, {@code wood} holds the wood
 * found before the search stopped (all of it for {@link Status#GROWING}), so a cycle does not
 * search the same tree again from its other blocks, and the other lists are empty.
 *
 * @param wood trunk, branch and root blocks
 * @param leaves leaf blocks within the leaf reach of {@code wood}
 * @param hanging non-tree blocks that hang from the tree (fruit, sap, snow…), cut with it
 * @param groundY lowest {@code y} where a wood block has an open side (air or a non-solid
 *     block): the ground level; below it the tree replaced soil
 * @param replantSpot the wood block at {@code groundY} nearest the start (where a sapling goes back)
 * @param buried cut blocks below {@code groundY}, filled back with soil after the cut
 */
public record Tree(Status status, List<BlockPos> wood, List<BlockPos> leaves, List<BlockPos> hanging,
                   int groundY, BlockPos replantSpot, List<BlockPos> buried) {

    public enum Status {
        /** Whole tree found within the limits, not growing. */
        CUTTABLE,
        /** The start is not a wood block. */
        NOT_A_TREE,
        /** A wood block carries a growing sapling's state. */
        GROWING,
        /** More blocks than the limit, or a block beyond the reach. */
        TOO_BIG,
        /** The tree touches a chunk section that is not in memory. */
        UNLOADED
    }

    static Tree skipped(Status status, List<BlockPos> woodSoFar) {
        return new Tree(status, List.copyOf(woodSoFar), List.of(), List.of(), 0, null, List.of());
    }

    /** Every block to cut, top-down (the order they are removed in). */
    public List<BlockPos> blocks() {
        List<BlockPos> all = new ArrayList<>(wood.size() + leaves.size() + hanging.size());
        all.addAll(wood);
        all.addAll(leaves);
        all.addAll(hanging);
        all.sort((a, b) -> Integer.compare(b.y(), a.y()));
        return all;
    }

    public int size() {
        return wood.size() + leaves.size() + hanging.size();
    }
}
