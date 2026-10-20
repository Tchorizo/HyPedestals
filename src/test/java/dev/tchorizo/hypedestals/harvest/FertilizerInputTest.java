package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tchorizo.hypedestals.harvest.FertilizerInput.InputStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FertilizerInputTest {

    private static InputStack bag(double durability) {
        return new InputStack("Tool_Fertilizer", durability, 5, 1);
    }

    @Test
    void acceptsOnlyTheVanillaBag() {
        assertTrue(FertilizerInput.accepts("Tool_Fertilizer"));
        assertFalse(FertilizerInput.accepts("Ingredient_Poop"));
        assertFalse(FertilizerInput.accepts("Tool_Fertilizer_Crystal"));
        assertFalse(FertilizerInput.accepts(null));
    }

    @Test
    void applicationsAreTheWholeDurabilityPoints() {
        assertEquals(5, FertilizerInput.applications(bag(5)));
        assertEquals(1, FertilizerInput.applications(bag(1)));
        assertEquals(2, FertilizerInput.applications(bag(2.7)));
    }

    @Test
    void nothingToApplyFromNoStackAnotherItemOrASpentBag() {
        assertEquals(0, FertilizerInput.applications(null));
        assertEquals(0, FertilizerInput.applications(new InputStack("Plant_Crop_Wheat_Item", 0, 0, 30)));
        assertEquals(0, FertilizerInput.applications(new InputStack("Tool_Fertilizer_Crystal", 5, 5, 1)));
        assertEquals(0, FertilizerInput.applications(bag(0.9)));
        assertEquals(0, FertilizerInput.applications(bag(0)));
    }

    @Test
    void eachUseCostsOneDurability() {
        assertEquals(bag(4), FertilizerInput.afterUses(bag(5), 1));
        assertEquals(bag(1), FertilizerInput.afterUses(bag(5), 4));
        assertEquals(bag(1.5), FertilizerInput.afterUses(bag(3.5), 2));
    }

    @Test
    void zeroUsesKeepsTheStack() {
        InputStack s = bag(3);
        assertSame(s, FertilizerInput.afterUses(s, 0));
    }

    @Test
    void usedUpBagLeavesTheSlot() {
        assertNull(FertilizerInput.afterUses(bag(5), 5), "durability 0: gone");
        assertNull(FertilizerInput.afterUses(bag(1), 1));
        assertNull(FertilizerInput.afterUses(bag(2.5), 2), "a fraction under 1 cannot fertilize: gone too");
    }

    @Test
    void moreUsesThanAvailableIsABug() {
        assertThrows(IllegalArgumentException.class, () -> FertilizerInput.afterUses(bag(2), 3));
        assertThrows(IllegalArgumentException.class, () -> FertilizerInput.afterUses(bag(2), -1));
        assertThrows(IllegalArgumentException.class, () -> FertilizerInput.afterUses(null, 1));
    }

    @Test
    void stackedBagsAreUsedOneAfterAnother() {
        InputStack two = new InputStack("Tool_Fertilizer", 3, 5, 2);
        assertEquals(8, FertilizerInput.applications(two));
        assertEquals(new InputStack("Tool_Fertilizer", 1, 5, 2), FertilizerInput.afterUses(two, 2));
        assertEquals(new InputStack("Tool_Fertilizer", 5, 5, 1), FertilizerInput.afterUses(two, 3), "first bag spent");
        assertEquals(new InputStack("Tool_Fertilizer", 2, 5, 1), FertilizerInput.afterUses(two, 6));
        assertNull(FertilizerInput.afterUses(two, 8));

        InputStack three = new InputStack("Tool_Fertilizer", 5, 5, 3);
        assertEquals(new InputStack("Tool_Fertilizer", 5, 5, 1), FertilizerInput.afterUses(three, 10));
        assertEquals(new InputStack("Tool_Fertilizer", 4, 5, 1), FertilizerInput.afterUses(three, 11));
    }

    private static final InputStack WHEAT = new InputStack("Plant_Crop_Wheat_Item", 0, 0, 12);
    /** The default {@code ManurePerApplication}. */
    private static final int K = 2;

    private static InputStack poop(int quantity) {
        return new InputStack("Ingredient_Poop", 0, 0, quantity);
    }

    @Test
    void fertilizerFoundInAnySlot() {
        for (int slot = 0; slot < 9; slot++) {
            List<InputStack> slots = new ArrayList<>(Collections.nCopies(9, (InputStack) null));
            slots.set(slot, bag(5));
            assertEquals(5, FertilizerInput.totalApplications(slots, K));
            assertEquals(List.of(new FertilizerInput.Draw(slot, bag(4))), FertilizerInput.draw(slots, 1, K));
        }
    }

    @Test
    void otherItemsAndSpentBagsAreNotFertilizer() {
        List<InputStack> slots = Arrays.asList(WHEAT, null, bag(0.5), new InputStack("Tool_Fertilizer_Crystal", 5, 5, 1));
        assertEquals(0, FertilizerInput.totalApplications(slots, K));
        assertEquals(List.of(), FertilizerInput.draw(slots, 0, K));
        assertEquals(0, FertilizerInput.totalApplications(List.of(), K));
    }

    @Test
    void theMostWornBagIsUsedFirst() {
        List<InputStack> slots = Arrays.asList(bag(5), WHEAT, bag(2), null, bag(2), bag(4));
        assertEquals(13, FertilizerInput.totalApplications(slots, K));
        assertEquals(List.of(new FertilizerInput.Draw(2, bag(1))), FertilizerInput.draw(slots, 1, K),
                "lowest durability; ties go to the lower slot");
    }

    @Test
    void drawingEmptiesOneBagBeforeTheNext() {
        List<InputStack> slots = Arrays.asList(bag(5), WHEAT, bag(2), null, bag(4));
        assertEquals(List.of(
                new FertilizerInput.Draw(2, null),
                new FertilizerInput.Draw(4, bag(3))), FertilizerInput.draw(slots, 3, K));
        assertEquals(List.of(
                new FertilizerInput.Draw(2, null),
                new FertilizerInput.Draw(4, null),
                new FertilizerInput.Draw(0, null)), FertilizerInput.draw(slots, 11, K));
    }

    @Test
    void drawingMoreThanTheBagsHoldIsABug() {
        List<InputStack> slots = Arrays.asList(bag(2), null, bag(1));
        assertThrows(IllegalArgumentException.class, () -> FertilizerInput.draw(slots, 4, K));
        assertThrows(IllegalArgumentException.class, () -> FertilizerInput.draw(slots, -1, K));
    }

    @Test
    void poopBeforeBags() {
        List<InputStack> slots = Arrays.asList(bag(3), WHEAT, poop(4));
        assertEquals(List.of(new FertilizerInput.Draw(2, poop(2))), FertilizerInput.draw(slots, 1, K),
                "the first application burns K poop, the bag stays intact");
        assertEquals(List.of(new FertilizerInput.Draw(2, null), new FertilizerInput.Draw(0, bag(2))),
                FertilizerInput.draw(slots, 3, K), "the bag only after the poop runs out");
    }

    @Test
    void poopLeftoverStaysWhenBelowK() {
        List<InputStack> slots = Arrays.asList(poop(5));
        assertEquals(2, FertilizerInput.poopApplications(slots, K));
        assertEquals(List.of(new FertilizerInput.Draw(0, poop(1))), FertilizerInput.draw(slots, 2, K),
                "5 poop with K=2: two applications, one poop left for later");
        assertEquals(0, FertilizerInput.totalApplications(List.of(poop(1)), K), "a single poop is not an application");
        assertEquals(List.of(), FertilizerInput.draw(List.of(poop(1)), 0, K));
    }

    @Test
    void poopAddsUpAcrossSlotsLowestSlotFirst() {
        List<InputStack> slots = Arrays.asList(null, poop(1), WHEAT, poop(1), poop(3));
        assertEquals(5, FertilizerInput.poop(slots));
        assertEquals(2, FertilizerInput.totalApplications(slots, K), "1 + 1 + 3 poop with K=2");
        assertEquals(List.of(
                new FertilizerInput.Draw(1, null),
                new FertilizerInput.Draw(3, null),
                new FertilizerInput.Draw(4, poop(1))), FertilizerInput.draw(slots, 2, K));
    }

    @Test
    void kZeroDisablesPoop() {
        List<InputStack> slots = Arrays.asList(poop(10), bag(5));
        assertEquals(0, FertilizerInput.poopApplications(slots, 0));
        assertEquals(5, FertilizerInput.totalApplications(slots, 0));
        assertEquals(List.of(new FertilizerInput.Draw(1, bag(4))), FertilizerInput.draw(slots, 1, 0),
                "K=0: the bag is used, the poop is left alone");
        assertFalse(FertilizerInput.isFuel("Ingredient_Poop", 0));
        assertTrue(FertilizerInput.isFuel("Ingredient_Poop", K));
        assertTrue(FertilizerInput.isFuel("Tool_Fertilizer", 0));
        assertFalse(FertilizerInput.isFuel("Plant_Crop_Wheat_Item", K));
    }

    @Test
    void mixedSourcesTotal() {
        List<InputStack> slots = Arrays.asList(poop(3), bag(2), poop(4), WHEAT, bag(5));
        assertEquals(3, FertilizerInput.poopApplications(slots, K), "7 poop with K=2");
        assertEquals(7, FertilizerInput.bagApplications(slots));
        assertEquals(10, FertilizerInput.totalApplications(slots, K));
        assertEquals(9, FertilizerInput.totalApplications(slots, 3), "7 poop with K=3: 2 applications");
        assertEquals(List.of(
                new FertilizerInput.Draw(0, null),
                new FertilizerInput.Draw(2, poop(1)),
                new FertilizerInput.Draw(1, null),
                new FertilizerInput.Draw(4, bag(4))), FertilizerInput.draw(slots, 6, K),
                "3 from the poop (one left), then the most worn bag, then the next");
        assertThrows(IllegalArgumentException.class, () -> FertilizerInput.draw(slots, 11, K));
    }
}
