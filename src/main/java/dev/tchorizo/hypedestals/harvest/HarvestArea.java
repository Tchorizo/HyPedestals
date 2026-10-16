package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;

/** The block positions a harvester scans: a square of side {@code 2R+1} over three layers. */
public final class HarvestArea {

    private HarvestArea() {}

    /**
     * Returns every position from {@code y-1} to {@code y+1} within {@code radius} on x and z,
     * center included, ordered by y, then x, then z (all ascending).
     */
    public static List<BlockPos> positions(BlockPos center, int radius) {
        int side = 2 * radius + 1;
        List<BlockPos> positions = new ArrayList<>(side * side * 3);
        for (int y = center.y() - 1; y <= center.y() + 1; y++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }
}
