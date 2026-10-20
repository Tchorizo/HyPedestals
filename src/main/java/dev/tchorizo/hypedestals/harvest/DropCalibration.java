package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How much room a harvest reserves, learned per crop type from what its harvests actually put in
 * the container. Until a type has {@link #MIN_SAMPLES} observed harvests it reserves the most its
 * drop list can roll ({@link CycleWorld#maxDrops}, P17'); after that, per item id,
 * {@code min(max, max(1, ceil(mean observed × MARGIN)))}. Some drop lists hold far more than they
 * usually give (a LifeCrops Eternal crop reserved about 37 items and dropped about 7), and every
 * reserved item holds room for {@link PendingDrops#MAX_CYCLES} cycles, so reserving the maximum
 * left a near-empty container harvesting nothing.
 *
 * <p>Trade-off: below the maximum, one harvest can drop more than it reserved. A cycle's harvests
 * reserve {@code MARGIN} times their mean together, so their sum rarely goes over; when it does,
 * the excess waits on the ground for the post-harvest burst or the next cycle's collect, which
 * claims everything still outstanding before reserving anything new. The floor of one item keeps
 * every item the list can drop (even one never seen yet) a stack to go into. A batch whose drops
 * reached the reduced reservation (the harvests may have dropped more than it, see
 * {@link PendingDrops}) sends the type back to the maximum and the learning starts over.
 *
 * <p>Shared by every Harvester ({@link #global()}): the drop list belongs to the crop type, not to
 * the Harvester. Runtime-only, so a server start or plugin reload starts from the maximum again.
 * Thread-safe: worlds tick on their own threads.
 */
public final class DropCalibration {

    /** Observed harvests of a type before its reservation leaves the maximum. */
    public static final int MIN_SAMPLES = 3;
    /** Reservation over the mean observed drop. */
    public static final double MARGIN = 1.5;

    private static final DropCalibration GLOBAL = new DropCalibration();

    /** Crop type → what its harvests were seen dropping. */
    private final Map<String, Learned> byType = new HashMap<>();

    /** The server-wide instance every Harvester reserves with. */
    public static DropCalibration global() {
        return GLOBAL;
    }

    /**
     * What a harvest of {@code cropType} reserves, given the most its drop list can roll; {@code max}
     * itself until the type has {@link #MIN_SAMPLES} observed harvests under that same list. Never
     * above {@code max}, item by item.
     */
    public synchronized List<ItemCount> reservation(String cropType, List<ItemCount> max) {
        Learned learned = byType.get(cropType);
        if (learned == null || learned.harvests < MIN_SAMPLES || !learned.max.equals(max)) {
            return max;
        }
        if (learned.reservation == null) {
            List<ItemCount> reservation = new ArrayList<>(max.size());
            for (ItemCount item : max) {
                double mean = learned.dropped.getOrDefault(item.itemId(), 0.0) / learned.harvests;
                int calibrated = Math.max(1, (int) Math.ceil(mean * MARGIN));
                reservation.add(new ItemCount(item.itemId(), Math.min(item.quantity(), calibrated)));
            }
            learned.reservation = List.copyOf(reservation);
        }
        return learned.reservation;
    }

    /**
     * {@code harvests} harvests of {@code cropType} (whose drop list rolls at most {@code max})
     * put {@code dropped} in the container, per item id (ids of {@code max} missing from it count
     * as {@code 0}). A {@code max} other than the one learned so far (the asset changed) starts
     * the type over.
     */
    public synchronized void observed(String cropType, List<ItemCount> max, int harvests, Map<String, Double> dropped) {
        if (harvests <= 0) {
            return;
        }
        Learned learned = byType.get(cropType);
        if (learned == null || !learned.max.equals(max)) {
            learned = new Learned(max);
            byType.put(cropType, learned);
        }
        for (ItemCount item : max) {
            learned.dropped.merge(item.itemId(), dropped.getOrDefault(item.itemId(), 0.0), Double::sum);
        }
        learned.harvests += harvests;
        learned.reservation = null;
    }

    /**
     * Harvests of {@code cropType} dropped at least the reduced reservation, maybe more: back to
     * the maximum until {@link #MIN_SAMPLES} new observations.
     */
    public synchronized void saturated(String cropType) {
        byType.remove(cropType);
    }

    /** Forgets every type (plugin setup: the drop lists may have changed with the assets). */
    public synchronized void clear() {
        byType.clear();
    }

    private static final class Learned {
        /** The drop list's maximum the observations were made under. */
        final List<ItemCount> max;
        /** Item id → items put in by {@link #harvests} harvests. */
        final Map<String, Double> dropped = new HashMap<>();
        int harvests;
        /** {@link #reservation} computed since the last observation; {@code null} = stale. */
        List<ItemCount> reservation;

        Learned(List<ItemCount> max) {
            this.max = List.copyOf(max);
        }
    }
}
