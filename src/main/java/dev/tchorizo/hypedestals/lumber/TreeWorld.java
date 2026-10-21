package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;

/** What {@link TreeScan} reads from the world. */
public interface TreeWorld {

    /** The block's role in a tree; {@link TreePart#NONE} for air and for a section not in memory. */
    TreePart part(BlockPos p);

    /** Whether the chunk section holding {@code p} is in memory (one that is not reads as air). */
    boolean loaded(BlockPos p);

    /** Whether the wood block at {@code p} carries a growing sapling's farming state (a tree still growing). */
    boolean growing(BlockPos p);

    /** Whether the block at {@code p} has support rules (it falls without what holds it). */
    boolean needsSupport(BlockPos p);

    /** Whether the block at {@code p} is solid (a block other things can rest on). */
    boolean solid(BlockPos p);
}
