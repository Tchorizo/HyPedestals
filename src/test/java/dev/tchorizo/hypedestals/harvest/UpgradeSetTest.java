package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UpgradeSetTest {

    private static final String WATERING = "HyPedestals_Upgrade_Watering";
    private static final String FERTILIZER = "HyPedestals_Upgrade_Fertilizer";

    @Test
    void itemIdsRoundTrip() {
        assertEquals(WATERING, UpgradeType.WATERING.itemId());
        assertEquals(FERTILIZER, UpgradeType.FERTILIZER.itemId());
        assertEquals(UpgradeType.WATERING, UpgradeType.fromItemId(WATERING));
        assertEquals(UpgradeType.FERTILIZER, UpgradeType.fromItemId(FERTILIZER));
    }

    @Test
    void unknownOrNullItemIdIsNotAnUpgrade() {
        assertNull(UpgradeType.fromItemId("Tool_Fertilizer"));
        assertNull(UpgradeType.fromItemId(null));
    }

    @Test
    void displayNamesAreTitleCase() {
        assertEquals("Watering", UpgradeType.WATERING.displayName());
        assertEquals("Fertilizer", UpgradeType.FERTILIZER.displayName());
    }

    @Test
    void parseKeepsOrderAndSkipsUnknownAndDuplicates() {
        UpgradeSet set = UpgradeSet.parse(new String[] {FERTILIZER, "Nope", WATERING, FERTILIZER, null});
        assertArrayEquals(new String[] {FERTILIZER, WATERING}, set.toArray());
        assertEquals(List.of("Nope", FERTILIZER, "null"), set.ignored());
    }

    @Test
    void parseOfNullOrEmptyIsEmpty() {
        assertTrue(UpgradeSet.parse(null).isEmpty());
        assertArrayEquals(new String[0], UpgradeSet.parse(new String[0]).toArray());
        assertTrue(UpgradeSet.parse(null).ignored().isEmpty());
    }

    @Test
    void installRejectsRepeatedType() {
        UpgradeSet set = new UpgradeSet();
        assertTrue(set.install(UpgradeType.WATERING));
        assertFalse(set.install(UpgradeType.WATERING));
        assertTrue(set.has(UpgradeType.WATERING));
        assertFalse(set.has(UpgradeType.FERTILIZER));
        assertArrayEquals(new String[] {WATERING}, set.toArray());
    }

    @Test
    void removeLastIsLifo() {
        UpgradeSet set = new UpgradeSet();
        set.install(UpgradeType.FERTILIZER);
        set.install(UpgradeType.WATERING);
        assertEquals(UpgradeType.WATERING, set.removeLast());
        assertFalse(set.has(UpgradeType.WATERING));
        assertEquals(UpgradeType.FERTILIZER, set.removeLast());
        assertNull(set.removeLast());
        assertTrue(set.isEmpty());
    }

    @Test
    void removeSpecificType() {
        UpgradeSet set = UpgradeSet.parse(new String[] {WATERING, FERTILIZER});
        assertTrue(set.remove(UpgradeType.WATERING));
        assertFalse(set.remove(UpgradeType.WATERING));
        assertArrayEquals(new String[] {FERTILIZER}, set.toArray());
    }

    @Test
    void toArrayIsADetachedCopy() {
        UpgradeSet set = UpgradeSet.parse(new String[] {WATERING});
        String[] out = set.toArray();
        out[0] = "changed";
        assertArrayEquals(new String[] {WATERING}, set.toArray());
    }

    @Test
    void copyIsIndependent() {
        UpgradeSet set = UpgradeSet.parse(new String[] {WATERING});
        UpgradeSet copy = set.copy();
        copy.install(UpgradeType.FERTILIZER);
        assertArrayEquals(new String[] {WATERING}, set.toArray());
        assertArrayEquals(new String[] {WATERING, FERTILIZER}, copy.toArray());
        assertTrue(copy.ignored().isEmpty());
    }

    @Test
    void toStringListsDisplayNamesInOrder() {
        UpgradeSet set = UpgradeSet.parse(new String[] {WATERING, FERTILIZER});
        assertEquals("[Watering, Fertilizer]", set.toString());
        assertEquals("[]", new UpgradeSet().toString());
    }
}
