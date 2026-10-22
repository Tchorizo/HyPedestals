package dev.tchorizo.hypedestals.harvest;

/** One revival sweep over the Harvester's area ({@link ReviveRule}). Pure. */
public final class RevivePass {

    private RevivePass() {}

    /** What the sweep reads and writes. */
    public interface ReviveWorld {
        /** Stage of the crop whose origin block is at {@code p}, or {@code null} if it is not an ordinary growing crop. */
        ReviveRule.Stage stageAt(BlockPos p);

        /** Whether the block has a live block entity. */
        boolean liveEntityAt(BlockPos p);

        /** Whether the block's entity sits parked ({@code Holder}) in a section that does not tick. */
        boolean parkedEntityAt(BlockPos p);

        /** Whether the section holding {@code p} is in memory and ticking. */
        boolean ticking(BlockPos p);

        /** Gives the crop at {@code p} its entity in {@code stage}; {@code false} when nothing was written. */
        boolean revive(BlockPos p, ReviveRule.Stage stage);
    }

    /** Revives every crop of {@link HarvestArea#positions} that {@link ReviveRule#shouldRevive}; returns how many. */
    public static int run(ReviveWorld world, BlockPos center, int radius) {
        int revived = 0;
        for (BlockPos p : HarvestArea.positions(center, radius)) {
            ReviveRule.Stage stage = world.stageAt(p);
            if (stage != null && ReviveRule.shouldRevive(stage, world.liveEntityAt(p), world.parkedEntityAt(p), world.ticking(p))
                    && world.revive(p, stage)) {
                revived++;
            }
        }
        return revived;
    }
}
