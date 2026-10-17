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
 * was carried into {@link #MAX_CYCLES} later cycles: reservations are the most a harvest can drop,
 * usually more than it does, and drops a player picks up never arrive, so the leftover must not
 * hold room for ever. Runtime-only (resets with the chunk). Pure and not thread-safe: one Harvester
 * is only ticked by its world's thread.
 */
public final class PendingDrops {

    /** Later cycles a batch's leftover is carried into before it is dropped. */
    public static final int MAX_CYCLES = 2;

    /** Oldest first; the last one is the running cycle's. */
    private final ArrayDeque<Batch> batches = new ArrayDeque<>();

    /**
     * Starts a cycle's batch: every older batch has been carried into one more cycle, and those
     * past {@link #MAX_CYCLES} or already collected are dropped. Call after the cycle's first
     * collect (it still counts against them) and before {@link #outstanding()}.
     */
    public void startCycle() {
        Iterator<Batch> it = batches.iterator();
        while (it.hasNext()) {
            Batch batch = it.next();
            batch.cycles++;
            if (batch.cycles > MAX_CYCLES || batch.items.isEmpty()) {
                it.remove();
            }
        }
        batches.addLast(new Batch());
    }

    /** Adds {@code items} (a harvest's reservation) to the running cycle's batch. */
    public void reserved(List<ItemCount> items) {
        if (batches.isEmpty()) {
            batches.addLast(new Batch());
        }
        Map<String, Integer> current = batches.getLast().items;
        for (ItemCount item : items) {
            if (item.quantity() > 0) {
                current.merge(item.itemId(), item.quantity(), Integer::sum);
            }
        }
    }

    /**
     * {@code quantity} items of {@code itemId} went into the container: taken off the oldest
     * batches first. Items no batch is waiting for (e.g. dropped by a player) are ignored.
     */
    public void collected(String itemId, int quantity) {
        int left = quantity;
        for (Iterator<Batch> it = batches.iterator(); it.hasNext() && left > 0; ) {
            Batch batch = it.next();
            Integer waiting = batch.items.get(itemId);
            if (waiting == null) {
                continue;
            }
            int taken = Math.min(waiting, left);
            left -= taken;
            if (taken == waiting) {
                batch.items.remove(itemId);
            } else {
                batch.items.put(itemId, waiting - taken);
            }
        }
    }

    /** What every batch is still waiting for, summed per item id; empty when nothing is. */
    public List<ItemCount> outstanding() {
        Map<String, Integer> sum = new HashMap<>();
        for (Batch batch : batches) {
            batch.items.forEach((id, quantity) -> sum.merge(id, quantity, Integer::sum));
        }
        List<ItemCount> items = new ArrayList<>(sum.size());
        sum.forEach((id, quantity) -> items.add(new ItemCount(id, quantity)));
        return items;
    }

    private static final class Batch {
        /** Item id → items reserved and not collected yet; ids drop out at {@code 0}. */
        final Map<String, Integer> items = new HashMap<>();
        /** Later cycles this batch has been carried into. */
        int cycles;
    }
}
