package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One harvester cycle: collect last cycle's drops, then harvest every mature crop in the area that fits. */
public final class HarvestCycle {

    private HarvestCycle() {}

    /**
     * Runs one cycle. Drops are collected first (box half-extent {@code radius + collectMargin}),
     * then every mature crop of the area is harvested while the container has room for its drops,
     * then drops are collected again to pick up whatever the item index has already registered;
     * the game's index lags behind spawns, so the rest is picked up by the post-harvest burst or
     * the next cycle. Neither collect runs while the container is full (nothing could go in).
     *
     * <p>No item is dropped for lack of room: after the first collect the container's
     * {@link ContainerRoom} is read once, the drops earlier cycles reserved room for and the
     * collects have not put in yet ({@link PendingDrops#outstanding()}) are claimed in it, and
     * before each harvest the most that crop can drop ({@link CycleWorld#maxDrops}) must fit in
     * what is left; it is then reserved, in the room and in {@code pending}. The first crop whose
     * drops do not fit ends the scan, {@link CycleResult#containerFull()} is {@code true}, and that
     * crop and the rest stay ripe on the plant until room frees up (e.g. by the export); the next
     * cycle starts at that crop.
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
     * @param pending the Harvester's reservations still on their way; the collects through
     *     {@code world} must report what they put in to it ({@link PendingDrops#collected})
     */
    public static CycleResult run(CycleWorld world, BlockPos center, int radius, int collectMargin,
                                  Set<String> warnedTypes, int startIndex, int maxHarvests, PendingDrops pending) {
        double collectExtent = collectExtent(radius, collectMargin);
        List<BlockPos> positions = HarvestArea.positions(center, radius);
        int size = positions.size();
        int start = Math.floorMod(startIndex, size);
        int collected = world.containerFull() ? 0 : world.collectDroppedItems(center, collectExtent);
        pending.startCycle();
        ContainerRoom room = world.room();
        room.claim(pending.outstanding());

        int harvested = 0;
        int transientFailures = 0;
        int next = start;
        boolean noRoom = false;
        List<String> newWarnings = new ArrayList<>();
        for (int i = 0; i < size && (maxHarvests <= 0 || harvested < maxHarvests); i++) {
            int index = (start + i) % size;
            BlockPos pos = positions.get(index);
            String type = world.cropTypeAt(pos);
            if (type != null && !warnedTypes.contains(type)) {
                List<ItemCount> drops = world.maxDrops(pos);
                if (!room.fits(drops)) {
                    // Checked before knowing whether the crop is ripe: without room for its drops
                    // it could not be harvested anyway. This crop starts the next cycle.
                    noRoom = true;
                    next = index;
                    break;
                }
                HarvestOutcome outcome;
                try {
                    outcome = world.harvest(pos);
                } catch (RuntimeException e) {
                    outcome = HarvestOutcome.TYPE_ERROR;
                }
                switch (outcome) {
                    case HARVESTED -> {
                        harvested++;
                        room.reserve(drops);
                        pending.reserved(drops);
                    }
                    case NOT_READY -> { }
                    case TRANSIENT_ERROR -> transientFailures++;
                    case TYPE_ERROR -> {
                        warnedTypes.add(type);
                        newWarnings.add(type);
                    }
                }
            }
            next = (index + 1) % size;
        }
        boolean full = world.containerFull();
        if (!full) {
            collected += world.collectDroppedItems(center, collectExtent);
            full = world.containerFull();
        }
        return new CycleResult(harvested, collected, noRoom || full, List.copyOf(newWarnings),
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
