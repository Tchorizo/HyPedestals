package dev.tchorizo.hypedestals.harvest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Room left in a container, in items, from a snapshot of its slots. The harvest cycle reserves in
 * it the most each harvest can drop ({@link HarvestCycle#run}), so a crop is only harvested when
 * all of its drops are sure to fit. The drops reach the container seconds later (the game's item
 * index lags behind spawns), so the container itself does not change during the cycle and is not
 * re-read: the reservations stand for the drops still on their way. Reservations follow the way
 * the container fills: partial stacks of the same item first, then empty slots, each new slot
 * holding up to the item's stack size. A crop dropping its product and Life Essence therefore
 * needs two empty slots, or one plus a partial stack of either item with room. Not thread-safe.
 */
public final class ContainerRoom {

    private int freeSlots;
    /** Item id → items that still fit in the partial stacks of that item (and in its reserved new slots). */
    private final Map<String, Integer> partial;
    private final ToIntFunction<String> maxStack;

    /**
     * @param freeSlots empty slots
     * @param partialRoom per item id, how many more items its partial stacks can take (stacks that
     *     a fresh stack of that item would not merge with must be left out)
     * @param maxStack stack size of an item id; {@code <= 0} (unknown item) counts as {@code 1}
     */
    public ContainerRoom(int freeSlots, Map<String, Integer> partialRoom, ToIntFunction<String> maxStack) {
        this.freeSlots = Math.max(0, freeSlots);
        this.partial = new HashMap<>(partialRoom);
        this.maxStack = maxStack;
    }

    /** Whether all of {@code items} fit in what is left (nothing is reserved). */
    public boolean fits(List<ItemCount> items) {
        return slotsNeeded(merge(items)) <= freeSlots;
    }

    /**
     * Reserves room for all of {@code items}, or for none of them when they do not all fit.
     *
     * @return whether they fit (and are now reserved)
     */
    public boolean reserve(List<ItemCount> items) {
        Map<String, Integer> merged = merge(items);
        int slots = slotsNeeded(merged);
        if (slots > freeSlots) {
            return false;
        }
        freeSlots -= slots;
        merged.forEach((id, quantity) -> {
            int room = partial.getOrDefault(id, 0);
            int intoPartial = Math.min(quantity, room);
            int rest = quantity - intoPartial;
            int stack = stackSize(id);
            // What is left in the partial stacks plus what the new slots can still take.
            partial.put(id, room - intoPartial + ceilDiv(rest, stack) * stack - rest);
        });
        return true;
    }

    private int slotsNeeded(Map<String, Integer> merged) {
        int slots = 0;
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            int rest = e.getValue() - Math.min(e.getValue(), partial.getOrDefault(e.getKey(), 0));
            slots += ceilDiv(rest, stackSize(e.getKey()));
        }
        return slots;
    }

    private int stackSize(String itemId) {
        return Math.max(1, maxStack.applyAsInt(itemId));
    }

    /** Sums the quantities per item id, skipping non-positive ones. */
    private static Map<String, Integer> merge(List<ItemCount> items) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (ItemCount item : items) {
            if (item.quantity() > 0) {
                merged.merge(item.itemId(), item.quantity(), Integer::sum);
            }
        }
        return merged;
    }

    private static int ceilDiv(int a, int b) {
        return a <= 0 ? 0 : (a + b - 1) / b;
    }
}
