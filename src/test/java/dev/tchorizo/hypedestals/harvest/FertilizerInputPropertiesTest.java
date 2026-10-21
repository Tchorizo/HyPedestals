package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tchorizo.hypedestals.harvest.FertilizerInput.Draw;
import dev.tchorizo.hypedestals.harvest.FertilizerInput.InputStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Property tests (fixed seed, 1000 cases) of {@link FertilizerInput}: no negative consumption, poop before bags. */
class FertilizerInputPropertiesTest {

    private static final int CASES = 1000;
    private static final long SEED = 20261021L + 2;

    private static List<InputStack> randomSlots(Random rnd) {
        int n = 1 + rnd.nextInt(12);
        List<InputStack> slots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            switch (rnd.nextInt(4)) {
                case 0 -> slots.add(null);
                case 1 -> slots.add(new InputStack(FertilizerInput.POOP_ID, 0, 0, 1 + rnd.nextInt(100)));
                case 2 -> slots.add(new InputStack(FertilizerInput.ITEM_ID, rnd.nextInt(6 * 4) / 4.0 + (rnd.nextBoolean() ? 0 : 0.25),
                        5, 1 + (rnd.nextInt(5) == 0 ? rnd.nextInt(3) : 0)));
                default -> slots.add(new InputStack("Wheat", 0, 0, 1 + rnd.nextInt(50)));
            }
        }
        return slots;
    }

    /** The slots after the draws (a draw's {@code after == null} empties its slot). */
    private static List<InputStack> apply(List<InputStack> slots, List<Draw> draws) {
        List<InputStack> out = new ArrayList<>(slots);
        for (Draw d : draws) {
            out.set(d.slot(), d.after());
        }
        return out;
    }

    @Test
    void drawNeverGoesNegativeAndUsesExactlyWhatWasAsked() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            List<InputStack> slots = randomSlots(rnd);
            int k = rnd.nextInt(5);
            int total = FertilizerInput.totalApplications(slots, k);
            int uses = total == 0 ? 0 : rnd.nextInt(total + 1);

            List<Draw> draws = FertilizerInput.draw(slots, uses, k);

            Set<Integer> touched = new HashSet<>();
            for (Draw d : draws) {
                assertTrue(touched.add(d.slot()), "case " + c + ": slot " + d.slot() + " drawn twice");
                InputStack was = slots.get(d.slot());
                assertTrue(was != null, "case " + c + ": drew from an empty slot");
                if (d.after() != null) {
                    assertTrue(d.after().quantity() >= 1, "case " + c + ": left a non-positive quantity");
                    assertTrue(d.after().quantity() <= was.quantity(), "case " + c + ": quantity grew");
                    assertTrue(d.after().durability() >= 0 && d.after().durability() <= was.maxDurability() + 1e-9,
                            "case " + c + ": durability out of range " + d.after());
                    assertEquals(was.itemId(), d.after().itemId());
                }
            }
            List<InputStack> after = apply(slots, draws);
            assertEquals(total - uses, FertilizerInput.totalApplications(after, k),
                    "case " + c + ": applications left = before - uses");
            if (uses > 0) {
                assertTrue(touched.stream().allMatch(s -> FertilizerInput.isFuel(slots.get(s).itemId(), k)),
                        "case " + c + ": a non-fuel slot was touched");
            }
            // Poop before bags: bags are only touched once all the poop applications are spent.
            int poopApps = FertilizerInput.poopApplications(slots, k);
            if (uses <= poopApps) {
                assertTrue(draws.stream().allMatch(d -> FertilizerInput.isPoop(slots.get(d.slot()).itemId())),
                        "case " + c + ": a bag was used while poop applications were left (uses=" + uses + ", poop=" + poopApps + ")");
                assertEquals(FertilizerInput.bagApplications(slots), FertilizerInput.bagApplications(after));
            } else {
                assertEquals(FertilizerInput.poop(slots) - poopApps * k, FertilizerInput.poop(after),
                        "case " + c + ": only the poop rest (< k) remains");
            }
            assertEquals(FertilizerInput.poop(slots) - Math.min(uses, poopApps) * k, FertilizerInput.poop(after),
                    "case " + c + ": poop used is k per application");
            // Poop comes off the lowest slots first: a slot left with poop is never below a slot emptied of it.
            int lastEmptiedPoop = -1;
            int firstKeptPoop = Integer.MAX_VALUE;
            for (int s = 0; s < slots.size(); s++) {
                InputStack was = slots.get(s);
                if (was != null && FertilizerInput.isPoop(was.itemId()) && k > 0) {
                    InputStack now = after.get(s);
                    if (now == null) {
                        lastEmptiedPoop = s;
                    } else if (now.quantity() == was.quantity()) {
                        firstKeptPoop = Math.min(firstKeptPoop, s);
                    }
                }
            }
            assertTrue(lastEmptiedPoop < firstKeptPoop, "case " + c + ": poop taken out of order");
        }
    }

    @Test
    void asksBeyondTheFuelAreRefused() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + 7000 + c);
            List<InputStack> slots = randomSlots(rnd);
            int k = rnd.nextInt(5);
            int total = FertilizerInput.totalApplications(slots, k);
            assertThrows(IllegalArgumentException.class, () -> FertilizerInput.draw(slots, total + 1, k));
            assertThrows(IllegalArgumentException.class, () -> FertilizerInput.draw(slots, -1, k));
            // Using it all leaves no fuel and no usable bag behind.
            List<InputStack> after = apply(slots, FertilizerInput.draw(slots, total, k));
            assertEquals(0, FertilizerInput.totalApplications(after, k), "case " + c);
        }
    }
}
