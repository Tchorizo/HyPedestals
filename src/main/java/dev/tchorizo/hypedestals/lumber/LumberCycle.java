package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.HarvestArea;
import dev.tchorizo.hypedestals.harvest.ItemCount;
import dev.tchorizo.hypedestals.harvest.SaplingRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One Lumberjack cycle (spec §2): cut the first whole tree whose wood stands in the area, store
 * its drops, fill the stump's hole with soil and plant a sapling back; with the Planter upgrade,
 * then plant the container's saplings on the {@link SaplingRule} grid of the area. Pure.
 */
public final class LumberCycle {

    private LumberCycle() {}

    /** {@link #run(LumberWorld, BlockPos, int, TreeLimits, int, int, TreeMemory, int)} without the sapling grid. */
    public static LumberResult run(LumberWorld world, BlockPos center, int radius, TreeLimits limits, int startIndex,
                                   int maxBlocksPerCycle, TreeMemory memory) {
        return run(world, center, radius, limits, startIndex, maxBlocksPerCycle, memory, 0);
    }

    /**
     * The tree search and cut ({@link #scan}), then the Planter's grid pass: every grid point of
     * {@code gridSpacing} ({@link SaplingRule#onGrid}, origin = {@code center}) in the area where
     * {@link SaplingRule#canPlantAt} holds counts as a spot and, while the container has saplings,
     * gets the one of the lowest slot ({@link LumberWorld#saplings()}), after clearing the grass
     * tufts of its footprint like the stump's replant. The grid runs after the cut and the stump's
     * replant (the stump gets the tree's own species first, and a point still occupied by the tree
     * is blocked by the rule), and also when the tree search found nothing or a tree waits for room.
     *
     * @param gridSpacing the config's {@code Lumberjack.SaplingSpacing}; {@code 0} = no grid (also the
     *     value for a Lumberjack without the Planter)
     */
    public static LumberResult run(LumberWorld world, BlockPos center, int radius, TreeLimits limits, int startIndex,
                                   int maxBlocksPerCycle, TreeMemory memory, int gridSpacing) {
        LumberResult result = scan(world, center, radius, limits, startIndex, maxBlocksPerCycle, memory);
        if (gridSpacing <= 0) {
            return result;
        }
        int spots = 0;
        int planted = 0;
        SaplingRule.Ground ground = new TuftsAsAir(world);
        for (BlockPos p : HarvestArea.positions(center, radius)) {
            if (!SaplingRule.onGrid(center, p, gridSpacing) || !SaplingRule.canPlantAt(ground, p)) {
                continue;
            }
            spots++;
            List<String> saplings = world.saplings();
            if (!saplings.isEmpty() && place(world, p, saplings.getFirst())) {
                planted++;
            }
        }
        return result.withGrid(spots, planted);
    }

    /**
     * Scans {@link HarvestArea#positions} circularly from {@code startIndex} (taken modulo the area
     * size). The first wood block of a tree not met yet in this cycle, and not in {@code memory},
     * starts a {@link TreeScan}; growing, too big (then remembered), not natural and unloaded trees
     * are counted and skipped. Once the searches of the cycle have read {@code maxBlocksPerCycle}
     * blocks the cycle stops and the next one starts at that position. The first cuttable tree
     * ends the scan: when one roll of its drops does not fit in the container it stays, and the
     * next cycle starts at it ({@link LumberResult#containerFull()}); otherwise all of its blocks
     * are removed top-down, the wood below its ground level becomes the soil beside it (air when
     * there is none), the drops are stored, and a sapling from the container is planted at the
     * stump when {@link SaplingRule#canPlantAt} ({@link SaplingChoice}), after clearing the grass tufts
     * of the sapling's footprint (flowers, mushrooms and anything with a block entity still block).
     */
    private static LumberResult scan(LumberWorld world, BlockPos center, int radius, TreeLimits limits, int startIndex,
                                     int maxBlocksPerCycle, TreeMemory memory) {
        memory.nextCycle();
        List<BlockPos> positions = HarvestArea.positions(center, radius);
        int size = positions.size();
        int start = Math.floorMod(startIndex, size);
        Set<BlockPos> met = new HashSet<>();
        int growing = 0;
        int tooBig = 0;
        int notTree = 0;
        int unloaded = 0;
        int read = 0;
        for (int i = 0; i < size; i++) {
            int index = (start + i) % size;
            BlockPos p = positions.get(index);
            if (met.contains(p) || memory.remembered(p)) {
                continue;
            }
            TreePart part = world.part(p);
            if (part == TreePart.NONE && !world.loaded(p)) {
                unloaded++;
                continue;
            }
            if (part != TreePart.WOOD) {
                continue;
            }
            if (read >= maxBlocksPerCycle) {
                return new LumberResult(0, 0, 0, LumberResult.Replant.NONE, null,
                        growing, tooBig, notTree, unloaded, false, true, index);
            }
            Tree tree = TreeScan.find(world, p, limits);
            read += tree.visited();
            met.addAll(tree.wood());
            switch (tree.status()) {
                case GROWING -> growing++;
                case TOO_BIG -> {
                    tooBig++;
                    memory.remember(tree.wood());
                }
                case UNLOADED -> unloaded++;
                case NOT_A_TREE -> notTree++;
                case CUTTABLE -> {
                    List<ItemCount> drops = rollDrops(world, tree);
                    if (!world.room().fits(drops)) {
                        return new LumberResult(0, 0, 0, LumberResult.Replant.NONE, null,
                                growing, tooBig, notTree, unloaded, true, false, index);
                    }
                    Planted planted = cut(world, tree, drops);
                    return new LumberResult(1, tree.size(), total(drops), planted.replant(), planted.sapling(),
                            growing, tooBig, notTree, unloaded, false, false, (index + 1) % size);
                }
            }
        }
        return new LumberResult(0, 0, 0, LumberResult.Replant.NONE, null, growing, tooBig, notTree, unloaded,
                false, false, start);
    }

    /** {@link SaplingRule} view of the world in which grass tufts count as air. */
    private record TuftsAsAir(LumberWorld world) implements SaplingRule.Ground {
        @Override
        public boolean isEmpty(BlockPos p) {
            return world.isEmpty(p) || world.isTuft(p);
        }

        @Override
        public boolean isSaplingSoil(BlockPos p) {
            return world.isSaplingSoil(p);
        }

        @Override
        public boolean isFarmland(BlockPos p) {
            return world.isFarmland(p);
        }
    }

    private record Planted(LumberResult.Replant replant, String sapling) {}

    private static Planted cut(LumberWorld world, Tree tree, List<ItemCount> drops) {
        String woodId = world.blockId(tree.replantSpot());
        List<String> soils = new ArrayList<>(tree.buried().size());
        for (BlockPos b : tree.buried()) {
            soils.add(world.soilNextTo(b));
        }
        for (BlockPos b : tree.blocks()) {
            world.remove(b);
        }
        for (int i = 0; i < soils.size(); i++) {
            if (soils.get(i) != null) {
                world.setSoil(tree.buried().get(i), soils.get(i));
            }
        }
        world.store(drops);

        String sapling = SaplingChoice.pick(woodId, world.saplings());
        if (sapling == null) {
            return new Planted(LumberResult.Replant.NO_SAPLING, null);
        }
        BlockPos spot = tree.replantSpot();
        return plant(world, spot, sapling)
                ? new Planted(LumberResult.Replant.PLANTED, sapling)
                : new Planted(LumberResult.Replant.NO_ROOM, null);
    }

    /**
     * Plants {@code sapling} at {@code spot} when {@link SaplingRule#canPlantAt} (grass tufts count
     * as air), after clearing the tufts of its footprint: grass drops nothing worth keeping, and
     * is only cleared once the sapling fits. {@code false} when it does not fit or the world refused.
     */
    private static boolean plant(LumberWorld world, BlockPos spot, String sapling) {
        return SaplingRule.canPlantAt(new TuftsAsAir(world), spot) && place(world, spot, sapling);
    }

    /** The clearing and placing half of {@link #plant}, for a spot already known to fit. */
    private static boolean place(LumberWorld world, BlockPos spot, String sapling) {
        for (BlockPos p : SaplingRule.footprint(spot)) {
            if (world.isTuft(p)) {
                world.remove(p);
            }
        }
        return world.plant(spot, sapling);
    }

    /** One roll of every block's drops, summed per item id (first-seen order). */
    static List<ItemCount> rollDrops(LumberWorld world, Tree tree) {
        Map<String, Integer> sum = new LinkedHashMap<>();
        for (BlockPos b : tree.blocks()) {
            for (ItemCount drop : world.drops(b)) {
                if (drop.quantity() > 0) {
                    sum.merge(drop.itemId(), drop.quantity(), Integer::sum);
                }
            }
        }
        List<ItemCount> out = new ArrayList<>(sum.size());
        sum.forEach((id, n) -> out.add(new ItemCount(id, n)));
        return out;
    }

    private static int total(List<ItemCount> items) {
        int n = 0;
        for (ItemCount item : items) {
            n += item.quantity();
        }
        return n;
    }
}
