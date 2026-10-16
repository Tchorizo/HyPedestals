package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectFilterTest {

    @Test
    void collectAnyItemTakesEverything() {
        assertTrue(CollectFilter.accepts(true, true, false, false, false));
        assertTrue(CollectFilter.accepts(true, true, true, false, false), "tool");
        assertTrue(CollectFilter.accepts(true, true, false, true, false), "weapon");
        assertTrue(CollectFilter.accepts(true, true, false, false, true), "armor");
        assertTrue(CollectFilter.accepts(true, false, false, false, false), "unknown item");
    }

    @Test
    void withoutCollectAnyItemEquipmentStaysOnTheGround() {
        assertTrue(CollectFilter.accepts(false, true, false, false, false), "crops and materials are collected");
        assertFalse(CollectFilter.accepts(false, true, true, false, false), "tool");
        assertFalse(CollectFilter.accepts(false, true, false, true, false), "weapon");
        assertFalse(CollectFilter.accepts(false, true, false, false, true), "armor");
        assertFalse(CollectFilter.accepts(false, true, true, true, false), "tool and weapon");
        assertFalse(CollectFilter.accepts(false, false, false, false, false), "unknown item");
    }
}
