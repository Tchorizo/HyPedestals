package dev.tchorizo.hypedestals.harvest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One planting sweep of the Planter upgrade over the Harvester's area (planter spec §1–§2). Pure.
 *
 * <ul>
 *   <li><b>Seeds:</b> every empty position of {@link HarvestArea#positions} whose block below a
 *       seed of the container grows on ({@link PlantWorld#seedBlockOn}: the seed's own soil
 *       rule, tilled soil for vanilla crops, other soils for mods and herbs) gets one seed, of
 *       the first stack in the given order (the caller passes them by slot: lowest slot first)
 *       that grows there.</li>
 *   <li><b>Saplings:</b> where no seed was planted, a grid point of {@code saplingSpacing}
 *       ({@link SaplingRule#onGrid}) that {@link SaplingRule#canPlantAt} gets one sapling of the
 *       first sapling stack.</li>
 * </ul>
 * The sweep stops once every stack is used up. Placement goes through the world, so a block
 * planted earlier in the sweep counts for the later checks.
 */
public final class PlantPass {

    private PlantPass() {}

    /** What the sweep reads and writes. */
    public interface PlantWorld extends SaplingRule.Ground {
        /**
         * The block {@code itemId} plants when used on the top of the block at {@code soil}, or
         * {@code null} when it is not a seed, does not grow on that block, or would grow a tree
         * (saplings go through {@link #saplingBlock}).
         */
        String seedBlockOn(String itemId, BlockPos soil);

        /** The block the sapling item {@code itemId} places, or {@code null} when it is not a sapling. */
        String saplingBlock(String itemId);

        /** Puts {@code blockId} at {@code p}; {@code false} when the world refused (nothing changed). */
        boolean place(BlockPos p, String blockId);
    }

    /** One container slot: its index, item id and quantity. */
    public record Stack(int slot, String itemId, int quantity) {}

    /**
     * @param seeds seeds planted
     * @param saplings saplings planted
     * @param taken items to take from each slot, by slot index, in planting order of first use
     */
    public record Result(int seeds, int saplings, Map<Integer, Integer> taken) {
        public static final Result NONE = new Result(0, 0, Map.of());

        public int planted() {
            return seeds + saplings;
        }
    }

    /**
     * @param stacks the container's non-empty slots, in the order seeds are preferred (lowest slot first)
     * @param saplingSpacing the config's {@code SaplingSpacing}; {@code 0} = no saplings
     */
    public static Result run(PlantWorld world, List<Stack> stacks, BlockPos center, int radius, int saplingSpacing) {
        int n = stacks.size();
        int[] left = new int[n];
        boolean[] sapling = new boolean[n];
        String[] saplingBlocks = new String[n];
        int seedsLeft = 0;
        int saplingsLeft = 0;
        for (int i = 0; i < n; i++) {
            Stack s = stacks.get(i);
            left[i] = Math.max(0, s.quantity());
            saplingBlocks[i] = world.saplingBlock(s.itemId());
            sapling[i] = saplingBlocks[i] != null;
            if (sapling[i]) {
                saplingsLeft += left[i];
            } else {
                seedsLeft += left[i];
            }
        }
        boolean saplings = saplingSpacing > 0 && saplingsLeft > 0;
        if (seedsLeft == 0 && !saplings) {
            return Result.NONE;
        }
        Map<Integer, Integer> taken = new LinkedHashMap<>();
        int seeds = 0;
        int planted = 0;
        for (BlockPos p : HarvestArea.positions(center, radius)) {
            if (seedsLeft == 0 && (!saplings || saplingsLeft == 0)) {
                break;
            }
            if (!world.isEmpty(p)) {
                continue;
            }
            BlockPos soil = new BlockPos(p.x(), p.y() - 1, p.z());
            if (world.isEmpty(soil)) {
                continue;
            }
            int used = seedsLeft == 0 ? -1 : seedAt(world, stacks, left, sapling, p, soil);
            if (used >= 0) {
                seedsLeft--;
                seeds++;
            } else if (saplings && saplingsLeft > 0 && SaplingRule.onGrid(center, p, saplingSpacing)
                    && SaplingRule.canPlantAt(world, p)) {
                used = saplingAt(world, left, sapling, saplingBlocks, p);
                if (used >= 0) {
                    saplingsLeft--;
                    planted++;
                }
            }
            if (used >= 0) {
                taken.merge(stacks.get(used).slot(), 1, Integer::sum);
            }
        }
        return taken.isEmpty() ? Result.NONE : new Result(seeds, planted, taken);
    }

    /** Plants the first seed stack that grows on {@code soil}; its index, or -1 when none did. */
    private static int seedAt(PlantWorld world, List<Stack> stacks, int[] left, boolean[] sapling, BlockPos p, BlockPos soil) {
        for (int i = 0; i < left.length; i++) {
            if (sapling[i] || left[i] == 0) {
                continue;
            }
            String block = world.seedBlockOn(stacks.get(i).itemId(), soil);
            if (block != null) {
                // One try per position: a refused placement means the position, not the seed, is the problem.
                if (!world.place(p, block)) {
                    return -1;
                }
                left[i]--;
                return i;
            }
        }
        return -1;
    }

    private static int saplingAt(PlantWorld world, int[] left, boolean[] sapling, String[] blocks, BlockPos p) {
        for (int i = 0; i < left.length; i++) {
            if (sapling[i] && left[i] > 0) {
                if (!world.place(p, blocks[i])) {
                    return -1;
                }
                left[i]--;
                return i;
            }
        }
        return -1;
    }
}
