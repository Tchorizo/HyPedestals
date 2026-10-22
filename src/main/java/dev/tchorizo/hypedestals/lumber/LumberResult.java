package dev.tchorizo.hypedestals.lumber;

/**
 * What one Lumberjack cycle did.
 *
 * @param trees trees cut (0 or 1)
 * @param blocks blocks removed (wood, leaves, hanging)
 * @param items items put in the container
 * @param replant what happened at the stump; {@link Replant#NONE} when nothing was cut
 * @param sapling the sapling planted, or {@code null}
 * @param growing trees skipped because they are still growing
 * @param tooBig trees skipped for the limits ({@code Lumberjack.MaxTreeBlocks}, reach); remembered
 *     for {@link TreeMemory#CYCLES} cycles
 * @param notTree wood skipped because it does not look like a natural tree (Ruling H)
 * @param unloaded area positions or trees touching a chunk section that is not in memory
 * @param containerFull a tree's drops did not fit: it stays and the next cycle starts at it
 * @param budgetHit the cycle stopped at {@code Lumberjack.MaxBlocksPerCycle} blocks read; the next
 *     cycle goes on from there
 * @param nextIndex where the next cycle's area scan starts
 * @param gridSpots grid points of the Planter upgrade that could take a sapling this cycle (0 without
 *     the upgrade, with spacing 0, or when the grid has no point in the area)
 * @param gridPlanted saplings the grid planted this cycle (the stump's replant is {@code replant})
 */
public record LumberResult(int trees, int blocks, int items, Replant replant, String sapling,
                           int growing, int tooBig, int notTree, int unloaded, boolean containerFull,
                           boolean budgetHit, int nextIndex, int gridSpots, int gridPlanted) {

    /** A result of the tree search; the grid has not run. */
    public LumberResult(int trees, int blocks, int items, Replant replant, String sapling,
                        int growing, int tooBig, int notTree, int unloaded, boolean containerFull,
                        boolean budgetHit, int nextIndex) {
        this(trees, blocks, items, replant, sapling, growing, tooBig, notTree, unloaded, containerFull,
                budgetHit, nextIndex, 0, 0);
    }

    /** This result with the grid's outcome. */
    public LumberResult withGrid(int spots, int planted) {
        return new LumberResult(trees, blocks, items, replant, sapling, growing, tooBig, notTree, unloaded,
                containerFull, budgetHit, nextIndex, spots, planted);
    }

    public enum Replant {
        NONE("none"),
        PLANTED("planted"),
        NO_SAPLING("no-sapling"),
        NO_ROOM("no-room");

        private final String label;

        Replant(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
