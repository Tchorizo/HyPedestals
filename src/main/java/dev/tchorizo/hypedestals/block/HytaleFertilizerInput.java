package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.FertilizerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The fertilizer upgrade's fuel in a Harvester's container (poop, then fertilizer bags) over the
 * game's {@link ItemContainer}: reading every slot and consuming from the fuel there. Any slot may
 * hold fuel; the rules (poop before bags, which bag goes first, how a bag wears) are in
 * {@link FertilizerInput}.
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
     * Takes {@code uses} applications from the fuel in {@code container}
     * ({@link FertilizerInput#draw}, {@code k} poop per application): writes each slot back with
     * less poop or a worn bag, or empties it when nothing is left. {@code slots} must be
     * {@link #read} from the same container in the same tick. A write the container refuses
     * leaves that slot as it was (the soil was fertilized for free); logged once per server run.
     * The caller marks the container for saving.
     */
    static void consume(ItemContainer container, List<FertilizerInput.InputStack> slots, int uses, int k) {
        for (FertilizerInput.Draw draw : FertilizerInput.draw(slots, uses, k)) {
            short slot = (short) draw.slot();
            FertilizerInput.InputStack after = draw.after();
            boolean written;
            if (after == null) {
                written = container.removeItemStackFromSlot(slot).succeeded();
            } else {
                ItemStack stack = container.getItemStack(slot).withQuantity(after.quantity());
                // Poop has no durability; only a bag wears.
                if (FertilizerInput.accepts(after.itemId())) {
                    stack = stack.withDurability(after.durability());
                }
                written = container.setItemStackForSlot(slot, stack).succeeded();
            }
            if (!written && WRITE_FAILURE_WARNED.compareAndSet(false, true)) {
                HyPedestalsPlugin.warn("container refused the used fertilizer fuel in slot %d (not charged); further failures are not logged",
                        slot);
            }
        }
    }

    private static FertilizerInput.InputStack toInput(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new FertilizerInput.InputStack(
                stack.getItemId(), stack.getDurability(), stack.getMaxDurability(), stack.getQuantity());
    }
}
