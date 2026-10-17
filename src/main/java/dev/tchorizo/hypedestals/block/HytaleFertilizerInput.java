package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.FertilizerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The fertilizer bags in a Harvester's container over the game's {@link ItemContainer}: reading
 * every slot and consuming from the bags there. Any slot may hold a bag; the rules (which bag goes
 * first, how a bag wears) are in {@link FertilizerInput}.
 */
public final class HytaleFertilizerInput {

    /** Whether {@link #consume} already logged a refused write; once per server run. */
    private static final AtomicBoolean WRITE_FAILURE_WARNED = new AtomicBoolean();

    private HytaleFertilizerInput() {}

    /** Every slot of {@code container} in order, {@code null} for an empty one. */
    public static List<FertilizerInput.InputStack> read(ItemContainer container) {
        short capacity = container.getCapacity();
        List<FertilizerInput.InputStack> slots = new ArrayList<>(capacity);
        for (short slot = 0; slot < capacity; slot++) {
            slots.add(toInput(container.getItemStack(slot)));
        }
        return slots;
    }

    /**
     * Takes {@code uses} applications from the bags in {@code container} ({@link FertilizerInput#draw}):
     * writes each worn bag back, or empties its slot when it is used up. {@code slots} must be
     * {@link #read} from the same container in the same tick. A write the container refuses
     * leaves that bag as it was (the soil was fertilized for free); logged once per server run.
     */
    static void consume(ItemContainer container, List<FertilizerInput.InputStack> slots, int uses) {
        for (FertilizerInput.Draw draw : FertilizerInput.draw(slots, uses)) {
            short slot = (short) draw.slot();
            FertilizerInput.InputStack after = draw.after();
            boolean written;
            if (after == null) {
                written = container.removeItemStackFromSlot(slot).succeeded();
            } else {
                ItemStack bag = container.getItemStack(slot);
                written = container.setItemStackForSlot(slot,
                        bag.withQuantity(after.quantity()).withDurability(after.durability())).succeeded();
            }
            if (!written && WRITE_FAILURE_WARNED.compareAndSet(false, true)) {
                HyPedestalsPlugin.warn("container refused the worn fertilizer bag in slot %d (not charged); further failures are not logged",
                        slot);
            }
        }
    }

    private static FertilizerInput.InputStack toInput(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new FertilizerInput.InputStack(
                stack.getItemId(), stack.getDurability(), stack.getMaxDurability(), stack.getQuantity());
    }
}
