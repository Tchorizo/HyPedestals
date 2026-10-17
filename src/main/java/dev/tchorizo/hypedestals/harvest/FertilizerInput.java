package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fertilizer bags in the Harvester's container, for the fertilizer upgrade: how many soil blocks
 * they can still fertilize, and what is left of each after using some. A bag can sit in any slot;
 * no slot is reserved. Pure.
 *
 * <p>Only the vanilla fertilizer bag counts ({@code Tool_Fertilizer}: {@code MaxStack 1},
 * {@code MaxDurability 5}). One application fertilizes one soil block and costs one durability
 * point, like one vanilla click. A bag below 1 durability is used up and leaves its slot, like the
 * vanilla {@code BrokenItem: "Empty"}. A stack of more than one bag (only possible if some other
 * code ignores {@code MaxStack}) uses the bags one after another, each starting at
 * {@code maxDurability}.
 *
 * <p>With several bags, the most worn one is used first (lowest durability of its bag in use;
 * ties go to the lower slot), so bags are emptied one by one instead of leaving several
 * half-used bags in the container.
 */
public final class FertilizerInput {

    /** The only item the pass consumes. */
    public static final String ITEM_ID = "Tool_Fertilizer";

    /** Use order: the most worn bag first, then the lower slot. */
    private static final Comparator<Indexed> USE_ORDER =
            Comparator.comparingDouble((Indexed i) -> i.stack().durability()).thenComparingInt(Indexed::slot);

    private FertilizerInput() {}

    /**
     * Item stack in one slot, abstracted from the game's {@code ItemStack}.
     *
     * @param durability durability of the bag in use (the first one of the stack)
     * @param maxDurability durability of a fresh bag
     */
    public record InputStack(String itemId, double durability, double maxDurability, int quantity) {}

    /**
     * What one slot holds after the applications taken from it.
     *
     * @param after the stack left, or {@code null} when the bag is used up and the slot is emptied
     */
    public record Draw(int slot, InputStack after) {}

    private record Indexed(int slot, InputStack stack) {}

    /** Whether {@code itemId} is the fertilizer bag. */
    public static boolean accepts(String itemId) {
        return ITEM_ID.equals(itemId);
    }

    /** Applications the stack can still give; {@code 0} for no stack, another item, or a bag under 1 durability. */
    public static int applications(InputStack stack) {
        if (stack == null || !accepts(stack.itemId()) || stack.quantity() < 1 || stack.durability() < 1) {
            return 0;
        }
        return whole(stack.durability()) + (stack.quantity() - 1) * whole(stack.maxDurability());
    }

    /**
     * Applications of every bag in the container.
     *
     * @param slots the container's slots in order; {@code null} for an empty slot
     */
    public static int totalApplications(List<InputStack> slots) {
        int total = 0;
        for (InputStack s : slots) {
            total += applications(s);
        }
        return total;
    }

    /**
     * The slot whose bag is used next ({@link FertilizerInput use order}), or {@code -1} when no
     * slot holds a usable bag.
     *
     * @param slots the container's slots in order; {@code null} for an empty slot
     */
    public static int nextSlot(List<InputStack> slots) {
        List<Indexed> usable = usable(slots);
        return usable.isEmpty() ? -1 : usable.getFirst().slot();
    }

    /**
     * Takes {@code uses} applications from the bags in {@code slots}, the most worn bag first,
     * and returns the slots that change, in use order.
     *
     * @param slots the container's slots in order; {@code null} for an empty slot
     * @param uses {@code 0..totalApplications(slots)}
     * @throws IllegalArgumentException if {@code uses} is outside that range
     */
    public static List<Draw> draw(List<InputStack> slots, int uses) {
        int available = totalApplications(slots);
        if (uses < 0 || uses > available) {
            throw new IllegalArgumentException("uses " + uses + " outside 0.." + available);
        }
        List<Draw> draws = new ArrayList<>();
        int left = uses;
        for (Indexed i : usable(slots)) {
            if (left == 0) {
                break;
            }
            int n = Math.min(left, applications(i.stack()));
            draws.add(new Draw(i.slot(), afterUses(i.stack(), n)));
            left -= n;
        }
        return draws;
    }

    /**
     * The stack after {@code uses} applications, or {@code null} when nothing usable is left (the
     * slot is emptied). A bag whose remaining durability drops below 1 counts as used up, even if
     * a fraction is left, so a bag that can no longer fertilize never lingers in the container.
     *
     * @param uses {@code 0..applications(stack)}
     * @throws IllegalArgumentException if {@code uses} is outside that range
     */
    public static InputStack afterUses(InputStack stack, int uses) {
        int available = applications(stack);
        if (uses < 0 || uses > available) {
            throw new IllegalArgumentException("uses " + uses + " outside 0.." + available);
        }
        if (uses == 0) {
            return stack;
        }
        int fromCurrent = whole(stack.durability());
        if (uses < fromCurrent) {
            return new InputStack(stack.itemId(), stack.durability() - uses, stack.maxDurability(), stack.quantity());
        }
        int perBag = whole(stack.maxDurability());
        int rest = uses - fromCurrent;
        int bags = stack.quantity() - 1;
        if (perBag > 0) {
            bags -= rest / perBag;
            rest %= perBag;
        }
        if (bags < 1 || perBag - rest < 1) {
            return null;
        }
        return new InputStack(stack.itemId(), stack.maxDurability() - rest, stack.maxDurability(), bags);
    }

    /** Slots with a usable bag, in use order. */
    private static List<Indexed> usable(List<InputStack> slots) {
        List<Indexed> usable = new ArrayList<>();
        for (int slot = 0; slot < slots.size(); slot++) {
            InputStack s = slots.get(slot);
            if (applications(s) > 0) {
                usable.add(new Indexed(slot, s));
            }
        }
        usable.sort(USE_ORDER);
        return usable;
    }

    private static int whole(double durability) {
        return durability < 1 ? 0 : (int) Math.floor(durability);
    }
}
