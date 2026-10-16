package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tchorizo.hypedestals.harvest.FertilizerInput.InputStack;
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
}
