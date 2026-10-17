package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Export to an adjacent container ({@code ExportToAdjacent}): after a cycle, the Harvester moves
 * its items into the containers touching it, each stack to the first one with room. Pure, so the
 * order, the exclusions and "first that fits" are unit-tested with fakes.
 *
 * <ul>
 *   <li>Neighbors in a fixed order: below, then {@code +X}, {@code -X}, {@code +Z}, {@code -Z};
 *       never above (where the crops and the plate are).</li>
 *   <li>A neighbor that is a Harvester is skipped, so two Harvesters side by side never pass
 *       items back and forth. A container reached from two sides is used once.</li>
 *   <li>With the fertilizer upgrade installed, fertilizer bags ({@link FertilizerInput#ITEM_ID})
 *       are never exported, whatever their slot: they feed the soil pass. Without it they move
 *       like any other item.</li>
 *   <li>Partial moves are fine: what does not fit stays for the next cycle.</li>
 * </ul>
 */
public final class ExportPlan {

    /** Neighbor offsets in export order. */
    private static final List<BlockPos> OFFSETS = List.of(
            new BlockPos(0, -1, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, -1));

    private ExportPlan() {}

    /** The Harvester's slots, read by the plan; moving is up to the targets. */
    public interface ExportSource {
        int capacity();

        boolean isEmpty(int slot);

        /** Item id of the stack in {@code slot}; only asked for a slot that is not empty. */
        String itemId(int slot);
    }

    /** A container next to the Harvester. */
    public interface ExportTarget<S extends ExportSource> {
        /** Origin of the container's block, the same for every neighbor position it covers. */
        BlockPos pos();

        boolean isHarvester();

        /** Whether at least part of the stack in {@code slot} of {@code from} fits. */
        boolean accepts(S from, int slot);

        /** Moves as much of {@code slot} as fits; returns the number of items moved. */
        int moveFrom(S from, int slot);
    }

    public interface ExportWorld<S extends ExportSource> {
        /** Container at {@code p} (through a filler block, if any), or {@code null}. */
        ExportTarget<S> targetAt(BlockPos p);
    }

    /**
     * @param moved items moved this cycle
     * @param target origin of the last container that received items, or {@code null}
     */
    public record Result(int moved, BlockPos target) {
        public static final Result NONE = new Result(0, null);
    }

    /** The five neighbors of {@code center}, in export order. */
    public static List<BlockPos> neighbors(BlockPos center) {
        List<BlockPos> out = new ArrayList<>(OFFSETS.size());
        for (BlockPos o : OFFSETS) {
            out.add(new BlockPos(center.x() + o.x(), center.y() + o.y(), center.z() + o.z()));
        }
        return out;
    }

    /**
     * Whether a stack of {@code itemId} leaves the Harvester: everything, except fertilizer bags
     * while the fertilizer upgrade is installed.
     *
     * @param keepFertilizer whether the fertilizer upgrade is installed
     */
    public static boolean isExportable(String itemId, boolean keepFertilizer) {
        return !(keepFertilizer && FertilizerInput.accepts(itemId));
    }

    /**
     * Moves every exportable stack of {@code source} into the containers around {@code center}.
     * The world is not consulted when no slot holds an exportable stack.
     *
     * @param keepFertilizer whether the fertilizer upgrade is installed; see {@link #isExportable}
     */
    public static <S extends ExportSource> Result run(ExportWorld<S> world, S source, BlockPos center, boolean keepFertilizer) {
        if (!hasExportable(source, keepFertilizer)) {
            return Result.NONE;
        }
        List<ExportTarget<S>> targets = targets(world, center);
        if (targets.isEmpty()) {
            return Result.NONE;
        }
        int moved = 0;
        BlockPos last = null;
        int capacity = source.capacity();
        for (int slot = 0; slot < capacity; slot++) {
            if (!exportable(source, slot, keepFertilizer)) {
                continue;
            }
            for (ExportTarget<S> target : targets) {
                if (source.isEmpty(slot)) {
                    break;
                }
                if (!target.accepts(source, slot)) {
                    continue;
                }
                int n = target.moveFrom(source, slot);
                if (n > 0) {
                    moved += n;
                    last = target.pos();
                }
            }
        }
        return moved == 0 ? Result.NONE : new Result(moved, last);
    }

    private static boolean hasExportable(ExportSource source, boolean keepFertilizer) {
        int capacity = source.capacity();
        for (int slot = 0; slot < capacity; slot++) {
            if (exportable(source, slot, keepFertilizer)) {
                return true;
            }
        }
        return false;
    }

    private static boolean exportable(ExportSource source, int slot, boolean keepFertilizer) {
        return !source.isEmpty(slot) && isExportable(source.itemId(slot), keepFertilizer);
    }

    private static <S extends ExportSource> List<ExportTarget<S>> targets(ExportWorld<S> world, BlockPos center) {
        List<ExportTarget<S>> targets = new ArrayList<>(OFFSETS.size());
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : neighbors(center)) {
            ExportTarget<S> target = world.targetAt(p);
            if (target != null && !target.isHarvester() && seen.add(target.pos())) {
                targets.add(target);
            }
        }
        return targets;
    }
}
