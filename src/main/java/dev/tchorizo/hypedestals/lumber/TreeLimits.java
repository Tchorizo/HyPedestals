package dev.tchorizo.hypedestals.lumber;

/**
 * Bounds of one tree search ({@link TreeScan#find}), all measured from the block it starts at.
 *
 * @param maxBlocks most blocks (wood, leaves and hanging blocks) a tree may have
 * @param reach most blocks away on x and on z
 * @param below most blocks below
 * @param above most blocks above
 * @param leafReach most steps through leaves from the tree's wood
 */
public record TreeLimits(int maxBlocks, int reach, int below, int above, int leafReach) {

    /** Horizontal reach: Amber, the widest vanilla tree, spans 20 blocks from its trunk. */
    public static final int REACH = 24;
    /** Below the start: the start can be the top of a trunk whose roots go 5 blocks into the soil (Birch). */
    public static final int BELOW = 8;
    /** Above the start: the tallest vanilla trees are about 36 blocks. */
    public static final int ABOVE = 64;

    /** The Lumberjack's bounds for a given {@code LumberjackMaxTreeBlocks}. */
    public static TreeLimits of(int maxBlocks) {
        return new TreeLimits(maxBlocks, REACH, BELOW, ABOVE, TreeScan.LEAF_REACH);
    }
}
