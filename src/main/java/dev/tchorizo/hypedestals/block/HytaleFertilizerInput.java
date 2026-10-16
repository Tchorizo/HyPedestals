package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.FertilizerInput;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The fertilizer input slot of a Harvester's container (its last slot) over the game's
 * {@link ItemContainer}: the slot filter and reading and consuming the bag there. The rules are in
 * {@link FertilizerInput}.
 */
public final class HytaleFertilizerInput {

    /**
     * {@link FilterActionType#ADD} filter of the input slot: only {@link FertilizerInput#ITEM_ID}
     * goes in, whether from the player, another mod or the harvest's {@code addItemStack} (which
     * filters by default, {@code ItemContainer.DEFAULT_FILTER = true}). An empty stack passes, so
     * a filtered write of "nothing" is never refused.
     */
    static final SlotFilter FILTER = (action, container, slot, stack) ->
            stack == null || stack.isEmpty() || FertilizerInput.accepts(stack.getItemId());

    /** Whether {@link #consume} already logged a refused write; once per server run. */
    private static final AtomicBoolean WRITE_FAILURE_WARNED = new AtomicBoolean();

    private HytaleFertilizerInput() {}

    /** The input slot, or {@code -1} for a container too small to spare one (fewer than 2 slots). */
    public static short slot(ItemContainer container) {
        short capacity = container.getCapacity();
        return capacity < 2 ? -1 : (short) (capacity - 1);
    }

    /**
     * Applies the input filter to {@code container} when {@code installed}, or lifts it from the
     * container that had it ({@link SlotFilter#ALLOW}) when not; a no-op when already in that
     * state. Filters are runtime-only, so this runs on every tick and re-applies after a load.
     *
     * @param filtered the container that currently carries the filter, or {@code null}
     * @return the container that carries the filter afterwards, or {@code null}
     */
    static SimpleItemContainer sync(SimpleItemContainer container, SimpleItemContainer filtered, boolean installed) {
        boolean want = installed && container != null && slot(container) >= 0;
        if (want) {
            if (filtered != container) {
                container.setSlotFilter(FilterActionType.ADD, slot(container), FILTER);
            }
            return container;
        }
        if (filtered != null) {
            filtered.setSlotFilter(FilterActionType.ADD, slot(filtered), SlotFilter.ALLOW);
        }
        return null;
    }

    /** The stack in the input slot, or {@code null} when empty (or no input slot). */
    public static FertilizerInput.InputStack read(ItemContainer container) {
        short slot = slot(container);
        return slot < 0 ? null : toInput(container.getItemStack(slot));
    }

    /**
     * Takes {@code uses} applications from the bag in the input slot ({@link FertilizerInput#afterUses}):
     * writes the worn bag back, or empties the slot when it is used up. A write the container
     * refuses leaves the bag as it was (the soil was fertilized for free); logged once per server run.
     */
    static void consume(ItemContainer container, int uses) {
        short slot = slot(container);
        ItemStack bag = container.getItemStack(slot);
        FertilizerInput.InputStack after = FertilizerInput.afterUses(toInput(bag), uses);
        boolean written;
        if (after == null) {
            written = container.removeItemStackFromSlot(slot).succeeded();
        } else if (uses > 0) {
            written = container.setItemStackForSlot(slot,
                    bag.withQuantity(after.quantity()).withDurability(after.durability())).succeeded();
        } else {
            written = true;
        }
        if (!written && WRITE_FAILURE_WARNED.compareAndSet(false, true)) {
            HyPedestalsPlugin.warn("fertilizer input slot refused the worn bag (%d use(s) not charged); further failures are not logged",
                    uses);
        }
    }

    private static FertilizerInput.InputStack toInput(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new FertilizerInput.InputStack(
                stack.getItemId(), stack.getDurability(), stack.getMaxDurability(), stack.getQuantity());
    }
}
