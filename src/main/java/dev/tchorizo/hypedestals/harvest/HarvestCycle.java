package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One harvester cycle: collect last cycle's drops, then harvest mature crops within the container's budget. */
public final class HarvestCycle {

    private HarvestCycle() {}

    /**
     * Runs one cycle. Drops are collected first (box half-extent {@code radius + collectMargin})
     * so the budget reflects what is already on the ground, and again after harvesting to pick up
     * whatever the item index has already registered; the game's index lags behind spawns, so the
     * remainder is picked up by the next cycle. Harvesting is capped by
     * {@link CycleWorld#harvestBudget()} read after the first collect; each successful harvest
     * spends one unit. With a budget of zero the cycle pauses without harvesting.
     *
     * <p>The scan is circular over {@link HarvestArea#positions}: it starts at {@code startIndex}
     * (taken modulo the area size, so a stale index after a radius change is harmless), visits
     * every position at most once and stops as soon as the budget is spent. The returned
     * {@link CycleResult#nextIndex()} is the index after the last visited position: after the
     * last harvest when the budget ran out, or {@code startIndex} itself after a full lap (or a
     * pause). Feeding it back as the next {@code startIndex} makes consecutive cycles cover the
     * area like one continuous scan, so every mature crop is reached within
     * {@code ceil(matureCrops / budget)} cycles instead of the first positions being harvested
     * over and over.
     *
     * @param collectMargin horizontal margin beyond the harvest area where drops that rolled away
     *     are still collected (the config's {@code CollectMargin})
     * @param warnedTypes crop types whose harvest failed with {@link HarvestOutcome#TYPE_ERROR}; they
     *     are skipped, and types that fail that way during this cycle are added to it. A
     *     {@link HarvestOutcome#TRANSIENT_ERROR} leaves the type eligible, spends no budget and is
     *     only counted in {@link CycleResult#transientFailures()}, so the crop is retried next cycle
     */
    public static CycleResult run(CycleWorld world, BlockPos center, int radius, int collectMargin,
                                  Set<String> warnedTypes, int startIndex) {
        double collectExtent = radius + collectMargin;
        List<BlockPos> positions = HarvestArea.positions(center, radius);
        int size = positions.size();
        int start = Math.floorMod(startIndex, size);
        int collected = world.collectDroppedItems(center, collectExtent);
        int budget = world.harvestBudget();
        if (budget <= 0) {
            return new CycleResult(0, collected, true, List.of(), 0, start);
        }

        int harvested = 0;
        int transientFailures = 0;
        int next = start;
        List<String> newWarnings = new ArrayList<>();
        for (int i = 0; i < size && harvested < budget; i++) {
            int index = (start + i) % size;
            next = (index + 1) % size;
            BlockPos pos = positions.get(index);
            String type = world.cropTypeAt(pos);
            if (type == null || warnedTypes.contains(type)) {
                continue;
            }
            HarvestOutcome outcome;
            try {
                outcome = world.harvest(pos);
            } catch (RuntimeException e) {
                outcome = HarvestOutcome.TYPE_ERROR;
            }
            switch (outcome) {
                case HARVESTED -> harvested++;
                case NOT_READY -> { }
                case TRANSIENT_ERROR -> transientFailures++;
                case TYPE_ERROR -> {
                    warnedTypes.add(type);
                    newWarnings.add(type);
                }
            }
        }
        collected += world.collectDroppedItems(center, collectExtent);
        return new CycleResult(harvested, collected, false, List.copyOf(newWarnings), transientFailures, next);
    }

    /**
     * Budget units one container slot contributes: {@code 1} if it can still accept at least one
     * item (empty, or a stack below its {@code maxStack}), else {@code 0}. An approximation: a
     * harvest yields a few items of one or two types (wheat: grain plus Life Essence), a partial
     * stack of the same item absorbs them, and one unit per slot keeps the container from being
     * reported full while its stacks still have room. It overestimates when the partial stacks
     * hold other items than the area drops; what does not fit stays on the ground (inserts are
     * all-or-nothing) and is collected once room frees up.
     *
     * @param quantity items in the slot, {@code 0} when empty
     * @param maxStack the item's stack size; {@code <= 0} (unknown item) counts as full
     */
    public static int slotBudget(int quantity, int maxStack) {
        if (quantity <= 0) {
            return 1;
        }
        return quantity < maxStack ? 1 : 0;
    }
}
