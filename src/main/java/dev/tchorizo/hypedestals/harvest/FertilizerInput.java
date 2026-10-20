package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fuel for the fertilizer upgrade in the Harvester's container: how many soil blocks it can still
 * fertilize, and what is left of each slot after using some. Fuel can sit in any slot; no slot is
 * reserved. Pure.
 *
 * <p>Two sources, used in this order (decision P23, manure spec §2.3):
 * <ol>
 *   <li><b>Poop</b> ({@code Ingredient_Poop}): {@code k} poop ({@code ManurePerApplication}) make
 *       one application. Poop of every slot adds up, and is taken from the lowest slot first; a
 *       rest below {@code k} stays for a later pass. {@code k = 0} turns poop off (it is then an
 *       ordinary item). Poop goes first because it is renewable and takes up slots; bags are
 *       crafted stock.</li>
 *   <li><b>Fertilizer bags</b>: only the vanilla bag ({@code Tool_Fertilizer}: {@code MaxStack 1},
 *       {@code MaxDurability 5}). One application costs one durability point, like one vanilla
 *       click. A bag below 1 durability is used up and leaves its slot, like the vanilla
 *       {@code BrokenItem: "Empty"}. A stack of more than one bag (only possible if some other
 *       code ignores {@code MaxStack}) uses the bags one after another, each starting at
 *       {@code maxDurability}. With several bags, the most worn one is used first (lowest
 *       durability of its bag in use; ties go to the lower slot), so bags are emptied one by one
 *       instead of leaving several half-used bags in the container.</li>
 * </ol>
 * One application fertilizes one soil block.
 */
public final class FertilizerInput {

    /** The fertilizer bag. */
    public static final String ITEM_ID = "Tool_Fertilizer";
    /** Animal manure, fuel when {@code k > 0}. */
    public static final String POOP_ID = "Ingredient_Poop";

    /** Use order: the most worn bag first, then the lower slot. */
    private static final Comparator<Indexed> USE_ORDER =
            Comparator.comparingDouble((Indexed i) -> i.stack().durability()).thenComparingInt(Indexed::slot);

    private FertilizerInput() {}

    /**
     * Item stack in one slot, abstracted from the game's {@code ItemStack}.
     *
     * @param durability durability of the bag in use (the first one of the stack); {@code 0} for poop
     * @param maxDurability durability of a fresh bag; {@code 0} for poop
     */
    public record InputStack(String itemId, double durability, double maxDurability, int quantity) {}

    /**
     * What one slot holds after the applications taken from it.
     *
     * @param after the stack left, or {@code null} when the slot is emptied (bag used up, or all its poop taken)
     */
    public record Draw(int slot, InputStack after) {}

    private record Indexed(int slot, InputStack stack) {}

    /** Whether {@code itemId} is the fertilizer bag. */
    public static boolean accepts(String itemId) {
        return ITEM_ID.equals(itemId);
    }

    /** Whether {@code itemId} is poop. */
    public static boolean isPoop(String itemId) {
        return POOP_ID.equals(itemId);
    }

    /**
     * Whether {@code itemId} feeds the fertilizer upgrade with {@code k} poop per application: the
     * bag always, poop only when {@code k > 0}.
     */
    public static boolean isFuel(String itemId, int k) {
        return accepts(itemId) || (k > 0 && isPoop(itemId));
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
    public static int bagApplications(List<InputStack> slots) {
        int total = 0;
        for (InputStack s : slots) {
            total += applications(s);
        }
        return total;
    }

    /**
     * Poop in every slot of the container, whatever {@code k}.
     *
     * @param slots the container's slots in order; {@code null} for an empty slot
     */
    public static int poop(List<InputStack> slots) {
        int total = 0;
        for (InputStack s : slots) {
            if (s != null && isPoop(s.itemId()) && s.quantity() > 0) {
                total += s.quantity();
            }
        }
        return total;
    }

    /**
     * Applications of the poop in the container: {@code floor(poop / k)}, or {@code 0} when
     * {@code k <= 0} (poop is off).
     *
     * @param slots the container's slots in order; {@code null} for an empty slot
     */
    public static int poopApplications(List<InputStack> slots, int k) {
        return k > 0 ? poop(slots) / k : 0;
    }

    /**
     * Applications of all the fuel in the container: poop ({@link #poopApplications}) plus bags
     * ({@link #bagApplications}).
     *
     * @param slots the container's slots in order; {@code null} for an empty slot
     * @param k poop per application; {@code 0} = poop is not fuel
     */
    public static int totalApplications(List<InputStack> slots, int k) {
        return poopApplications(slots, k) + bagApplications(slots);
    }

    /**
     * Takes {@code uses} applications from the fuel in {@code slots}: poop first ({@code k} per
     * application, the lowest slot first), then the bags (the most worn first). Returns the slots
     * that change, in use order; each slot at most once.
     *
     * @param slots the container's slots in order; {@code null} for an empty slot
     * @param uses {@code 0..totalApplications(slots, k)}
     * @param k poop per application; {@code 0} = poop is not fuel
     * @throws IllegalArgumentException if {@code uses} is outside that range
     */
    public static List<Draw> draw(List<InputStack> slots, int uses, int k) {
        int available = totalApplications(slots, k);
        if (uses < 0 || uses > available) {
            throw new IllegalArgumentException("uses " + uses + " outside 0.." + available);
        }
        List<Draw> draws = new ArrayList<>();
        int fromPoop = Math.min(uses, poopApplications(slots, k));
        int poopLeft = fromPoop * k;
        for (int slot = 0; slot < slots.size() && poopLeft > 0; slot++) {
            InputStack s = slots.get(slot);
            if (s == null || !isPoop(s.itemId()) || s.quantity() < 1) {
                continue;
            }
            int taken = Math.min(poopLeft, s.quantity());
            int rest = s.quantity() - taken;
            draws.add(new Draw(slot, rest == 0 ? null
                    : new InputStack(s.itemId(), s.durability(), s.maxDurability(), rest)));
            poopLeft -= taken;
        }
        int left = uses - fromPoop;
        for (Indexed i : usableBags(slots)) {
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
    private static List<Indexed> usableBags(List<InputStack> slots) {
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
