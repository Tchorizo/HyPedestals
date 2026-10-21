package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.ItemCount;
import dev.tchorizo.hypedestals.lumber.Tree;
import dev.tchorizo.hypedestals.lumber.TreeLimits;
import dev.tchorizo.hypedestals.lumber.TreePart;
import dev.tchorizo.hypedestals.lumber.TreeScan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev diagnostics ({@code dev tree}): what a Lumberjack would make of the tree whose wood block is
 * at a position, without cutting anything. Runs on the world thread.
 */
public final class HytaleTreeProbe {

    private HytaleTreeProbe() {}

    /** One line: status, block counts, the lowest wood and what is under it, the roll of drops. */
    public static String report(World world, BlockPos start, int maxBlocks) {
        HytaleLumberWorld lumber = new HytaleLumberWorld(world, new SimpleItemContainer((short) 1), null);
        Tree tree = TreeScan.find(lumber, start, TreeLimits.of(maxBlocks));
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "tree %s: status=%s wood=%d leaves=%d hanging=%d visited=%d",
                start, tree.status(), tree.wood().size(), tree.leaves().size(), tree.hanging().size(), tree.visited()));
        BlockPos lowest = null;
        Map<String, Integer> ids = new TreeMap<>();
        for (BlockPos w : tree.wood()) {
            if (lowest == null || w.y() < lowest.y()) {
                lowest = w;
            }
            ids.merge(String.valueOf(lumber.blockId(w)), 1, Integer::sum);
        }
        out.append(" woodIds=").append(ids);
        if (lowest != null) {
            BlockPos under = new BlockPos(lowest.x(), lowest.y() - 1, lowest.z());
            out.append(String.format(Locale.ROOT, " lowestWood=%s under=%s soil=%b", lowest, lumber.blockId(under), lumber.soil(under)));
        }
        if (tree.status() == Tree.Status.CUTTABLE) {
            out.append(String.format(Locale.ROOT, " groundY=%d spot=%s buried=%d", tree.groundY(), tree.replantSpot(), tree.buried().size()));
        }
        Map<String, Integer> drops = new LinkedHashMap<>();
        Map<String, Integer> noDrop = new TreeMap<>();
        for (BlockPos b : tree.blocks()) {
            var rolled = lumber.drops(b);
            if (rolled.isEmpty()) {
                noDrop.merge(String.valueOf(lumber.blockId(b)), 1, Integer::sum);
            }
            for (ItemCount d : rolled) {
                drops.merge(d.itemId(), d.quantity(), Integer::sum);
            }
        }
        out.append(" drops=").append(drops).append(" blocksWithoutDrops=").append(noDrop);
        return out.toString();
    }

    /**
     * Every tree whose wood stands in the box {@code x±r, y0..y1, z±r}, scanned once each like a
     * Lumberjack would, one line per tree: status, wood and leaf counts, lowest wood and what is
     * under it. Trees not in memory are counted, not listed.
     */
    public static List<String> survey(World world, BlockPos center, int r, int y0, int y1, int maxBlocks) {
        HytaleLumberWorld lumber = new HytaleLumberWorld(world, new SimpleItemContainer((short) 1), null);
        TreeLimits limits = TreeLimits.of(maxBlocks);
        Set<BlockPos> met = new HashSet<>();
        Map<Tree.Status, Integer> counts = new TreeMap<>();
        List<String> lines = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            for (int x = center.x() - r; x <= center.x() + r; x++) {
                for (int z = center.z() - r; z <= center.z() + r; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (met.contains(p) || !lumber.loaded(p) || lumber.part(p) != TreePart.WOOD) {
                        continue;
                    }
                    Tree tree = TreeScan.find(lumber, p, limits);
                    met.addAll(tree.wood());
                    counts.merge(tree.status(), 1, Integer::sum);
                    BlockPos lowest = null;
                    for (BlockPos w : tree.wood()) {
                        if (lowest == null || w.y() < lowest.y()) {
                            lowest = w;
                        }
                    }
                    BlockPos under = lowest == null ? null : new BlockPos(lowest.x(), lowest.y() - 1, lowest.z());
                    int top = Integer.MIN_VALUE;
                    for (BlockPos w : tree.wood()) {
                        top = Math.max(top, w.y());
                    }
                    int high = 0;
                    for (BlockPos leaf : tree.leaves()) {
                        high += lowest != null && 2 * leaf.y() > lowest.y() + top ? 1 : 0;
                    }
                    lines.add(String.format(Locale.ROOT,
                            "%d %d %d %s wood=%d leaves=%d lowestY=%d groundY=%d above=%d/%d under=%s soil=%b",
                            x, y, z, tree.status(), tree.wood().size(), tree.leaves().size(),
                            lowest == null ? -1 : lowest.y(), tree.groundY(), high, tree.leaves().size(),
                            under == null ? null : lumber.blockId(under), under != null && lumber.soil(under)));
                }
            }
        }
        lines.add(0, "survey " + counts);
        return lines;
    }
}
