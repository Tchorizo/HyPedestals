package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a sapling may be planted, shared by the Planter upgrade and the Lumberjack's replanting.
 * Pure.
 *
 * <p>Why these checks (spike 2026-10-21, planter spec §2): a vanilla sapling grows by pasting
 * prefabs ({@code Farming} stages of type {@code Prefab}, {@code ReplaceMaskTags: ["Soil"]}). The
 * paste never replaces a block that is neither air nor {@code Type=Soil}: a chest, a crop, a
 * Harvester or stone in the way survive, but the tree then stops at the stage it had (an Oak
 * with a chest 2 blocks away stayed at its first prefab and lost its {@code FarmingBlock}). Roots
 * do replace soil below the trunk, tilled soil included. So the rule keeps the spot's
 * surroundings free when planting and leaves farmland to seeds; it cannot guarantee a full-grown
 * tree (later neighbours can still stunt it) but never makes one destroy anything.
 */
public final class SaplingRule {

    /** Horizontal radius (Chebyshev) kept free around the spot, on the spot's layer and the one above. */
    public static final int CLEARANCE = 1;
    /** Layers from the spot up that must be empty: the sapling and the first trunk blocks. */
    public static final int CLEAR_HEIGHT = 4;

    private SaplingRule() {}

    /** What the rule reads from the world. */
    public interface Ground {
        /** Whether {@code p} is air (nothing to replace, nothing in the way). */
        boolean isEmpty(BlockPos p);

        /**
         * Whether the block at {@code p} can hold a sapling: the vanilla saplings' support rule
         * ({@code Support.Down: [{TagId: "Type=Soil"}]}: grass, dirt, sand, snow, mud, tilled soil…).
         */
        boolean isSaplingSoil(BlockPos p);

        /** Whether the block at {@code p} is farmland (tilled soil), kept for seeds. */
        boolean isFarmland(BlockPos p);
    }

    /**
     * Whether a sapling fits at {@code spot} (the block the sapling would occupy): sapling soil
     * below that is not farmland, the spot and the {@link #CLEAR_HEIGHT} - 1 blocks above it
     * empty, and the ring of {@link #CLEARANCE} around the spot empty on the spot's layer and the
     * next one.
     */
    public static boolean canPlantAt(Ground ground, BlockPos spot) {
        BlockPos below = new BlockPos(spot.x(), spot.y() - 1, spot.z());
        if (!ground.isSaplingSoil(below) || ground.isFarmland(below)) {
            return false;
        }
        for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
            if (!ground.isEmpty(new BlockPos(spot.x(), spot.y() + dy, spot.z()))) {
                return false;
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -CLEARANCE; dx <= CLEARANCE; dx++) {
                for (int dz = -CLEARANCE; dz <= CLEARANCE; dz++) {
                    if ((dx != 0 || dz != 0)
                            && !ground.isEmpty(new BlockPos(spot.x() + dx, spot.y() + dy, spot.z() + dz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Whether {@code spot} is on the planting grid of a machine at {@code origin}: both horizontal
     * offsets multiples of {@code spacing}, and not the machine's own column. {@code spacing <= 0}
     * means no grid (no saplings).
     */
    public static boolean onGrid(BlockPos origin, BlockPos spot, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        int dx = spot.x() - origin.x();
        int dz = spot.z() - origin.z();
        return (dx != 0 || dz != 0) && Math.floorMod(dx, spacing) == 0 && Math.floorMod(dz, spacing) == 0;
    }

    /**
     * The positions of {@link HarvestArea#positions(BlockPos, int)} that are {@link #onGrid} and
     * {@link #canPlantAt}, in the area's order.
     */
    public static List<BlockPos> spots(Ground ground, BlockPos center, int radius, int spacing) {
        List<BlockPos> spots = new ArrayList<>();
        if (spacing <= 0 || spacing > radius) {
            return spots;
        }
        for (BlockPos p : HarvestArea.positions(center, radius)) {
            if (onGrid(center, p, spacing) && canPlantAt(ground, p)) {
                spots.add(p);
            }
        }
        return spots;
    }
}
