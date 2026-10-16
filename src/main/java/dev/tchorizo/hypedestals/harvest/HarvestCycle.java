package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One harvester cycle: collect last cycle's drops, then harvest mature crops within the slot budget. */
public final class HarvestCycle {

    private HarvestCycle() {}

    /** Horizontal margin beyond the harvest area where drops that rolled away are still collected. */
    public static final int COLLECT_MARGIN = 2;

    /**
     * Runs one cycle. Drops are collected first (box half-extent {@code radius + COLLECT_MARGIN})
     * so the slot budget reflects what is already on the ground, and again after harvesting to
     * pick up whatever the item index has already registered; the game's index lags behind spawns,
     * so the remainder is picked up by the next cycle. Harvesting is capped by a budget equal to
     * the empty slots left after the first collect; each successful harvest spends one. With no
     * empty slot the cycle pauses without harvesting.
     *
     * @param warnedTypes crop types whose harvest already threw; they are skipped, and types that
     *     throw during this cycle are added to it
     */
    public static CycleResult run(CycleWorld world, BlockPos center, int radius, Set<String> warnedTypes) {
        double collectExtent = radius + COLLECT_MARGIN;
        int collected = world.collectDroppedItems(center, collectExtent);
        int budget = world.freeSlots();
        if (budget <= 0) {
            return new CycleResult(0, collected, true, List.of());
        }

        int harvested = 0;
        List<String> newWarnings = new ArrayList<>();
        for (BlockPos pos : HarvestArea.positions(center, radius)) {
            if (harvested >= budget) {
                break;
            }
            String type = world.cropTypeAt(pos);
            if (type == null || warnedTypes.contains(type)) {
                continue;
            }
            try {
                if (world.harvest(pos)) {
                    harvested++;
                }
            } catch (RuntimeException e) {
                warnedTypes.add(type);
                newWarnings.add(type);
            }
        }
        collected += world.collectDroppedItems(center, collectExtent);
        return new CycleResult(harvested, collected, false, List.copyOf(newWarnings));
    }
}
