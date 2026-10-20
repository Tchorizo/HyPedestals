package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Room reserved by past cycles for drops that have not reached the container yet. The game's item
 * index registers drops seconds after they spawn, so drops a cycle reserved room for may still be
 * on the ground when the next cycle reads its {@link ContainerRoom}; that snapshot does not count
 * them, and without this the next cycle could take their room and leave them on the ground.
 * {@link HarvestCycle#run} therefore claims {@link #outstanding()} in each cycle's room before it
 * reserves anything new.
 *
 * <p>Each cycle's reservations are one batch, per item id: what was reserved minus what the
 * collects (the cycle's own, the post-harvest burst and later cycles') have since put in the
 * container, oldest batch first. A batch is dropped once all of it was collected, or after it
 * was carried into {@link #MAX_CYCLES} later cycles: reservations are at least what a harvest
 * usually drops, often more, and drops a player picks up never arrive, so the leftover must not
 * hold room for ever.
 *
 * <p>A dropped batch tells the {@link DropCalibration} what its harvests put in: per item id, what
 * was collected, shared among the batch's crop types in proportion to the most each can drop
 * (exact for a batch of one type). A batch during which a collect could not put an item in, or was
 * skipped because the container was full, does not count: its drops may still be on the ground. A
 * batch that collected all of an item it reserved less than the maximum for may have dropped more
 * than that: its types go back to the maximum ({@link DropCalibration#saturated}).
 *
 * <p>Runtime-only (resets with the chunk). Pure and not thread-safe: one Harvester is only ticked
 * by its world's thread.
 */
public final class PendingDrops {

    /** Later cycles a batch's leftover is carried into before it is dropped. */
    public static final int MAX_CYCLES = 2;

    private final DropCalibration calibration;
    /** Oldest first; the last one is the running cycle's. */
    private final ArrayDeque<Batch> batches = new ArrayDeque<>();

    /** @param calibration told what each batch's harvests put in once the batch is dropped */
    public PendingDrops(DropCalibration calibration) {
        this.calibration = calibration;
    }

    /** The calibration the cycles reserve with ({@link DropCalibration#reservation}). */
    public DropCalibration calibration() {
        return calibration;
    }

    /**
     * Starts a cycle's batch: every older batch has been carried into one more cycle, and those
     * past {@link #MAX_CYCLES} or already collected are dropped (and reported to the calibration).
     * Call after the cycle's first collect (it still counts against them) and before
     * {@link #outstanding()}.
     */
    public void startCycle() {
        Iterator<Batch> it = batches.iterator();
        while (it.hasNext()) {
            Batch batch = it.next();
            batch.cycles++;
            if (batch.cycles > MAX_CYCLES || batch.leftover.isEmpty()) {
                it.remove();
                report(batch);
            }
        }
        batches.addLast(new Batch());
    }

    /**
     * A harvest of {@code cropType} reserved {@code reservation} ({@link DropCalibration#reservation}
     * of {@code max}, the most it can drop): added to the running cycle's batch.
     */
    public void reserved(String cropType, List<ItemCount> max, List<ItemCount> reservation) {
        if (batches.isEmpty()) {
            batches.addLast(new Batch());
        }
        Batch batch = batches.getLast();
        for (ItemCount item : reservation) {
            if (item.quantity() > 0) {
                batch.leftover.merge(item.itemId(), item.quantity(), Integer::sum);
                batch.reserved.merge(item.itemId(), item.quantity(), Integer::sum);
            }
        }
        for (ItemCount item : max) {
            if (item.quantity() > 0) {
                batch.maxTotal.merge(item.itemId(), item.quantity(), Integer::sum);
            }
        }
        batch.harvests.computeIfAbsent(cropType, type -> new TypeHarvests(max)).count++;
    }

    /**
     * {@code quantity} items of {@code itemId} went into the container: taken off the oldest
     * batches first. Items no batch is waiting for (e.g. dropped by a player) are ignored.
     */
    public void collected(String itemId, int quantity) {
        int left = quantity;
        for (Iterator<Batch> it = batches.iterator(); it.hasNext() && left > 0; ) {
            Batch batch = it.next();
            Integer waiting = batch.leftover.get(itemId);
            if (waiting == null) {
                continue;
            }
            int taken = Math.min(waiting, left);
            left -= taken;
            if (taken == waiting) {
                batch.leftover.remove(itemId);
            } else {
                batch.leftover.put(itemId, waiting - taken);
            }
        }
    }

    /**
     * A collect left items on the ground (one did not fit) or did not run because the container
     * was full: what the open batches collect no longer tells what their harvests dropped.
     */
    public void collectBlocked() {
        for (Batch batch : batches) {
            batch.blocked = true;
        }
    }

    /** What every batch is still waiting for, summed per item id; empty when nothing is. */
    public List<ItemCount> outstanding() {
        Map<String, Integer> sum = new HashMap<>();
        for (Batch batch : batches) {
            batch.leftover.forEach((id, quantity) -> sum.merge(id, quantity, Integer::sum));
        }
        List<ItemCount> items = new ArrayList<>(sum.size());
        sum.forEach((id, quantity) -> items.add(new ItemCount(id, quantity)));
        return items;
    }

    /** Tells the calibration what a dropped batch's harvests put in (see the class comment). */
    private void report(Batch batch) {
        for (Map.Entry<String, TypeHarvests> entry : batch.harvests.entrySet()) {
            TypeHarvests type = entry.getValue();
            boolean saturated = false;
            Map<String, Double> dropped = new HashMap<>();
            for (ItemCount item : type.max) {
                String id = item.itemId();
                int reserved = batch.reserved.getOrDefault(id, 0);
                int collected = reserved - batch.leftover.getOrDefault(id, 0);
                if (reserved > 0 && reserved < batch.maxTotal.getOrDefault(id, 0) && collected >= reserved) {
                    saturated = true;
                    break;
                }
                int weight = item.quantity() * type.count;
                int total = batch.maxTotal.getOrDefault(id, 0);
                dropped.put(id, total <= 0 ? 0.0 : (double) collected * weight / total);
            }
            if (saturated) {
                calibration.saturated(entry.getKey());
            } else if (!batch.blocked) {
                calibration.observed(entry.getKey(), type.max, type.count, dropped);
            }
        }
    }

    private static final class Batch {
        /** Item id → items reserved and not collected yet; ids drop out at {@code 0}. */
        final Map<String, Integer> leftover = new HashMap<>();
        /** Item id → items reserved. */
        final Map<String, Integer> reserved = new HashMap<>();
        /** Item id → the most the batch's harvests could drop. */
        final Map<String, Integer> maxTotal = new HashMap<>();
        /** Crop type → its harvests in this batch. */
        final Map<String, TypeHarvests> harvests = new HashMap<>();
        /** A collect left items on the ground while this batch was open; see {@link #collectBlocked}. */
        boolean blocked;
        /** Later cycles this batch has been carried into. */
        int cycles;
    }

    private static final class TypeHarvests {
        final List<ItemCount> max;
        int count;

        TypeHarvests(List<ItemCount> max) {
            this.max = max;
        }
    }
}
