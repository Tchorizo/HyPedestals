package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One harvester cycle: collect last cycle's drops, then harvest mature crops within the slot budget. */
public final class HarvestCycle {

    private HarvestCycle() {}

    /**
     * Runs one cycle. Drops are collected first (radius {@code radius + 1}) because the game's item
     * index lags behind spawns, so drops from this cycle's harvests are picked up by the next one.
     * Harvesting is capped by a budget equal to the empty slots left after collecting; each
     * successful harvest spends one. With no empty slot the cycle pauses without harvesting.
     *
     * @param warnedTypes crop types whose harvest already threw; they are skipped, and types that
     *     throw during this cycle are added to it
     */
    public static CycleResult run(CycleWorld world, BlockPos center, int radius, Set<String> warnedTypes) {
        int collected = world.collectDroppedItems(center, radius + 1);
        int budget = world.freeSlots();
        if (budget == 0) {
            return new CycleResult(0, collected, true, List.of());
        }

        int harvested = 0;
        List<String> newWarnings = new ArrayList<>();
        for (BlockPos pos : HarvestArea.positions(center, radius)) {
            if (harvested == budget) {
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
        return new CycleResult(harvested, collected, false, List.copyOf(newWarnings));
    }
}
