package dev.tchorizo.hypedestals.lumber;

/**
 * Which {@code BlockGathering} entry the Lumberjack rolls for a block of a felled tree. Pure.
 *
 * <p>Breaking a block by hand rolls {@code Breaking}, else {@code Soft}, else {@code Harvest},
 * else {@code Physics}. Leaves are different when a tree is felled: the game drops them through
 * {@code Physics} ({@code Tree_Leaves_Physics}: fibre at weight 2, ~2%) while {@code Soft}
 * ({@code Tree_Leaves}: fibre at weight 50, ~50%) is the by-hand drop. The Lumberjack fells trees,
 * so leaves roll {@code Physics} when they have one.
 */
public enum DropSource {
    BREAKING, SOFT, HARVEST, PHYSICS, NONE;

    /**
     * @param part      what the block is to the tree
     * @param physics   the block has a {@code Physics} entry (last in the by-hand order)
     * @param fallDrops that entry names an item or a drop list (what leaves need to use it first)
     */
    public static DropSource pick(TreePart part, boolean breaking, boolean soft, boolean harvest, boolean physics,
                                  boolean fallDrops) {
        if (part == TreePart.LEAVES && physics && fallDrops) {
            return PHYSICS;
        }
        if (breaking) {
            return BREAKING;
        }
        if (soft) {
            return SOFT;
        }
        if (harvest) {
            return HARVEST;
        }
        return physics ? PHYSICS : NONE;
    }
}
