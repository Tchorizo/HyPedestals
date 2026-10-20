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
 *   <li>With the fertilizer upgrade installed, its fuel ({@link FertilizerInput#isFuel}: the
 *       fertilizer bags, and poop while {@code ManurePerApplication > 0}) feeds the soil pass:
 *       the bags are never exported, whatever their slot, and of the poop (summed over its
 *       slots) only what is above {@code ManureStock} leaves, taken from the highest slot down,
 *       so poop collected without end does not fill the container. Without the upgrade (or
 *       poop with {@code ManurePerApplication = 0}) it moves like any other item.</li>
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

        /** Items in {@code slot}; only asked for a slot that is not empty. */
        int quantity(int slot);
    }

    /** A container next to the Harvester. */
    public interface ExportTarget<S extends ExportSource> {
        /** Origin of the container's block, the same for every neighbor position it covers. */
        BlockPos pos();

        boolean isHarvester();

        /** Whether at least part of the stack in {@code slot} of {@code from} fits. */
        boolean accepts(S from, int slot);

        /** Moves as much of {@code slot} as fits, at most {@code max} items; returns the number of items moved. */
        int moveFrom(S from, int slot, int max);
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
     * Whether a stack of {@code itemId} leaves the Harvester whole: everything, except the
     * fertilizer upgrade's fuel while the upgrade is installed (of which poop still leaves above
     * {@code ManureStock}, see {@link #exportLimits}).
     *
     * @param keepFertilizer whether the fertilizer upgrade is installed
     * @param manurePerApplication the config's {@code ManurePerApplication}; {@code 0} = poop is not fuel
     */
    public static boolean isExportable(String itemId, boolean keepFertilizer, int manurePerApplication) {
        return !(keepFertilizer && FertilizerInput.isFuel(itemId, manurePerApplication));
    }

    /**
     * Moves every exportable stack of {@code source} into the containers around {@code center}:
     * whole stacks of what {@link #isExportable}, and the poop the upgrade keeps above
     * {@code manureStock} ({@link #exportLimits}). The world is not consulted when nothing may leave.
     *
     * @param keepFertilizer whether the fertilizer upgrade is installed; see {@link #isExportable}
     * @param manurePerApplication the config's {@code ManurePerApplication}; see {@link #isExportable}
     * @param manureStock the config's {@code ManureStock}: most poop the upgrade keeps
     */
    public static <S extends ExportSource> Result run(ExportWorld<S> world, S source, BlockPos center,
                                                      boolean keepFertilizer, int manurePerApplication, int manureStock) {
        int[] limits = exportLimits(source, keepFertilizer, manurePerApplication, manureStock);
        if (limits == null) {
            return Result.NONE;
        }
        List<ExportTarget<S>> targets = targets(world, center);
        if (targets.isEmpty()) {
            return Result.NONE;
        }
        int moved = 0;
        BlockPos last = null;
        for (int slot = 0; slot < limits.length; slot++) {
            int left = limits[slot];
            for (ExportTarget<S> target : targets) {
                if (left <= 0 || source.isEmpty(slot)) {
                    break;
                }
                if (!target.accepts(source, slot)) {
                    continue;
                }
                int n = target.moveFrom(source, slot, left);
                if (n > 0) {
                    moved += n;
                    left -= n;
                    last = target.pos();
                }
            }
        }
        return moved == 0 ? Result.NONE : new Result(moved, last);
    }

    /**
     * Per slot, the most items that may leave: the whole stack when {@link #isExportable}, none
     * for the bags the upgrade keeps, and for poop the upgrade keeps, what the poop of all slots
     * has above {@code manureStock}, from the highest slot down (the lowest slots keep the stock).
     *
     * @return the limits, or {@code null} when nothing may leave
     */
    static int[] exportLimits(ExportSource source, boolean keepFertilizer, int manurePerApplication, int manureStock) {
        int capacity = source.capacity();
        int[] limits = new int[capacity];
        boolean poopKept = keepFertilizer && manurePerApplication > 0;
        boolean any = false;
        int poop = 0;
        for (int slot = 0; slot < capacity; slot++) {
            if (source.isEmpty(slot)) {
                continue;
            }
            String id = source.itemId(slot);
            if (poopKept && FertilizerInput.isPoop(id)) {
                poop += source.quantity(slot);
            } else if (isExportable(id, keepFertilizer, manurePerApplication)) {
                limits[slot] = source.quantity(slot);
                any = true;
            }
        }
        int excess = poop - Math.max(0, manureStock);
        for (int slot = capacity - 1; slot >= 0 && excess > 0; slot--) {
            if (!source.isEmpty(slot) && FertilizerInput.isPoop(source.itemId(slot))) {
                limits[slot] = Math.min(source.quantity(slot), excess);
                excess -= limits[slot];
                any = true;
            }
        }
        return any ? limits : null;
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
