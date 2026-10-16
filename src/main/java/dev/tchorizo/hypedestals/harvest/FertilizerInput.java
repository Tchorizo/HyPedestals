package dev.tchorizo.hypedestals.harvest;

/**
 * The fertilizer input slot (the Harvester container's last slot) for the fertilizer upgrade:
 * how many soil blocks the stack there can still fertilize, and the stack left after using some.
 * Pure.
 *
 * <p>Only the vanilla fertilizer bag counts ({@code Tool_Fertilizer}: {@code MaxStack 1},
 * {@code MaxDurability 5}). One application fertilizes one soil block and costs one durability
 * point, like one vanilla click. A bag below 1 durability is used up and leaves the slot, like the
 * vanilla {@code BrokenItem: "Empty"}. A stack of more than one bag (only possible if some other
 * code ignores {@code MaxStack}) uses the bags one after another, each starting at
 * {@code maxDurability}.
 */
public final class FertilizerInput {

    /** The only item the input slot accepts and the pass consumes. */
    public static final String ITEM_ID = "Tool_Fertilizer";

    private FertilizerInput() {}

    /**
     * Item stack in the input slot, abstracted from the game's {@code ItemStack}.
     *
     * @param durability durability of the bag in use (the first one of the stack)
     * @param maxDurability durability of a fresh bag
     */
    public record InputStack(String itemId, double durability, double maxDurability, int quantity) {}

    /** Whether {@code itemId} may go into the input slot. */
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
     * The stack after {@code uses} applications, or {@code null} when nothing usable is left (the
     * slot is emptied). A bag whose remaining durability drops below 1 counts as used up, even if
     * a fraction is left, so a bag that can no longer fertilize never blocks the slot.
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

    private static int whole(double durability) {
        return durability < 1 ? 0 : (int) Math.floor(durability);
    }
}
