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
 * its drops, fill the stump's hole with soil and plant a sapling back. Pure.
 */
public final class LumberCycle {

    /** Soil put back where the tree had replaced soil and no soil block is beside the hole. */
    public static final String DEFAULT_SOIL = "Soil_Dirt";

    private LumberCycle() {}

    /**
     * Scans {@link HarvestArea#positions} circularly from {@code startIndex} (taken modulo the area
     * size). The first wood block of a tree not met yet in this cycle starts a {@link TreeScan};
     * growing, too big and unloaded trees are counted and skipped. The first cuttable tree ends
     * the scan: when one roll of its drops does not fit in the container it stays, and the next
     * cycle starts at it ({@link LumberResult#containerFull()}); otherwise all of its blocks are
     * removed top-down, the blocks below its ground level become soil (the id beside each one,
     * else {@link #DEFAULT_SOIL}), the drops are stored, and a sapling from the container is
     * planted at the stump when {@link SaplingRule#canPlantAt} ({@link SaplingChoice}).
     */
    public static LumberResult run(LumberWorld world, BlockPos center, int radius, TreeLimits limits, int startIndex) {
        List<BlockPos> positions = HarvestArea.positions(center, radius);
        int size = positions.size();
        int start = Math.floorMod(startIndex, size);
        Set<BlockPos> met = new HashSet<>();
        int growing = 0;
        int tooBig = 0;
        int unloaded = 0;
        for (int i = 0; i < size; i++) {
            int index = (start + i) % size;
            BlockPos p = positions.get(index);
            if (met.contains(p)) {
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
            Tree tree = TreeScan.find(world, p, limits);
            met.addAll(tree.wood());
            switch (tree.status()) {
                case GROWING -> growing++;
                case TOO_BIG -> tooBig++;
                case UNLOADED -> unloaded++;
                case NOT_A_TREE -> { }
                case CUTTABLE -> {
                    List<ItemCount> drops = rollDrops(world, tree);
                    if (!world.room().fits(drops)) {
                        return new LumberResult(0, 0, 0, LumberResult.Replant.NONE, null,
                                growing, tooBig, unloaded, true, index);
                    }
                    Planted planted = cut(world, tree, drops);
                    return new LumberResult(1, tree.size(), total(drops), planted.replant(), planted.sapling(),
                            growing, tooBig, unloaded, false, (index + 1) % size);
                }
            }
        }
        return new LumberResult(0, 0, 0, LumberResult.Replant.NONE, null, growing, tooBig, unloaded, false, start);
    }

    private record Planted(LumberResult.Replant replant, String sapling) {}

    private static Planted cut(LumberWorld world, Tree tree, List<ItemCount> drops) {
        String woodId = world.blockId(tree.replantSpot());
        List<String> soils = new ArrayList<>(tree.buried().size());
        for (BlockPos b : tree.buried()) {
            String soil = world.soilNextTo(b);
            soils.add(soil == null ? DEFAULT_SOIL : soil);
        }
        for (BlockPos b : tree.blocks()) {
            world.remove(b);
        }
        for (int i = 0; i < soils.size(); i++) {
            world.setSoil(tree.buried().get(i), soils.get(i));
        }
        world.store(drops);

        String sapling = SaplingChoice.pick(woodId, world.saplings());
        if (sapling == null) {
            return new Planted(LumberResult.Replant.NO_SAPLING, null);
        }
        if (!SaplingRule.canPlantAt(world, tree.replantSpot()) || !world.plant(tree.replantSpot(), sapling)) {
            return new Planted(LumberResult.Replant.NO_ROOM, null);
        }
        return new Planted(LumberResult.Replant.PLANTED, sapling);
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
