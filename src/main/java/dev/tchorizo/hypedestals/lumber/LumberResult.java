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
 * @param tooBig trees skipped for the limits ({@code LumberjackMaxTreeBlocks}, reach)
 * @param unloaded area positions or trees touching a chunk section that is not in memory
 * @param containerFull a tree's drops did not fit: it stays and the next cycle starts at it
 * @param nextIndex where the next cycle's area scan starts
 */
public record LumberResult(int trees, int blocks, int items, Replant replant, String sapling,
                           int growing, int tooBig, int unloaded, boolean containerFull, int nextIndex) {

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
