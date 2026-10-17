package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One harvester cycle: collect last cycle's drops, then harvest every mature crop in the area. */
public final class HarvestCycle {

    private HarvestCycle() {}

    /**
     * Runs one cycle. Drops are collected first (box half-extent {@code radius + collectMargin}),
     * then every mature crop of the area is harvested, then drops are collected again to pick up
     * whatever the item index has already registered; the game's index lags behind spawns, so the
     * rest is picked up by the post-harvest burst or the next cycle. The container's free space
     * does not limit the harvest: what does not fit stays on the ground and is collected once room
     * frees up (e.g. by the export). {@link CycleResult#containerFull()} tells, after the second
     * collect, whether the container had no room left.
     *
     * <p>The scan is circular over {@link HarvestArea#positions}: it starts at {@code startIndex}
     * (taken modulo the area size, so a stale index after a radius change is harmless) and visits
     * every position once. With {@code maxHarvests > 0} it stops after that many harvests; the
     * returned {@link CycleResult#nextIndex()} is then the index after the last harvested position,
     * so consecutive capped cycles cover the area like one continuous scan instead of harvesting
     * the first positions over and over. After a full lap it is {@code startIndex} itself.
     *
     * @param collectMargin horizontal margin beyond the harvest area where drops that rolled away
     *     are still collected (the config's {@code CollectMargin})
     * @param warnedTypes crop types whose harvest failed with {@link HarvestOutcome#TYPE_ERROR}; they
     *     are skipped, and types that fail that way during this cycle are added to it. A
     *     {@link HarvestOutcome#TRANSIENT_ERROR} leaves the type eligible, does not count towards
     *     {@code maxHarvests} and is only counted in {@link CycleResult#transientFailures()}, so the
     *     crop is retried next cycle
     * @param maxHarvests the config's {@code MaxHarvestsPerCycle}; {@code 0} (or less) = no limit
     */
    public static CycleResult run(CycleWorld world, BlockPos center, int radius, int collectMargin,
                                  Set<String> warnedTypes, int startIndex, int maxHarvests) {
        double collectExtent = collectExtent(radius, collectMargin);
        List<BlockPos> positions = HarvestArea.positions(center, radius);
        int size = positions.size();
        int start = Math.floorMod(startIndex, size);
        int collected = world.collectDroppedItems(center, collectExtent);

        int harvested = 0;
        int transientFailures = 0;
        int next = start;
        List<String> newWarnings = new ArrayList<>();
        for (int i = 0; i < size && (maxHarvests <= 0 || harvested < maxHarvests); i++) {
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
        return new CycleResult(harvested, collected, world.containerFull(), List.copyOf(newWarnings),
                transientFailures, next);
    }

    /**
     * Half-extent of the collect box around the Harvester: the harvest radius plus
     * {@code collectMargin}. Also used by the post-harvest {@link CollectBurst}.
     */
    public static double collectExtent(int radius, int collectMargin) {
        return radius + collectMargin;
    }

    /**
     * Whether one container slot can still accept at least one item: empty, or a stack below its
     * {@code maxStack}. A container is full when no slot can ({@link CycleWorld#containerFull()}).
     *
     * @param quantity items in the slot, {@code 0} when empty
     * @param maxStack the item's stack size; {@code <= 0} (unknown item) counts as full
     */
    public static boolean slotHasRoom(int quantity, int maxStack) {
        return quantity <= 0 || quantity < maxStack;
    }
}
