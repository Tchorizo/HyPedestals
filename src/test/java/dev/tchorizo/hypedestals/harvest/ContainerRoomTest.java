package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainerRoomTest {

    private static final List<ItemCount> WHEAT_CROP = List.of(new ItemCount("Wheat", 2), new ItemCount("Essence", 2));

    @Test
    void twoItemsNeedTwoEmptySlots() {
        assertFalse(new ContainerRoom(1, Map.of(), id -> 100).fits(WHEAT_CROP));
        assertTrue(new ContainerRoom(2, Map.of(), id -> 100).fits(WHEAT_CROP));
    }

    @Test
    void aPartialStackWithRoomSavesASlot() {
        assertTrue(new ContainerRoom(1, Map.of("Essence", 2), id -> 100).fits(WHEAT_CROP));
        assertFalse(new ContainerRoom(1, Map.of("Essence", 1), id -> 100).fits(WHEAT_CROP),
                "the essence that does not fit in the partial stack needs a slot of its own");
        assertFalse(new ContainerRoom(0, Map.of("Wheat", 50, "Essence", 1), id -> 100).fits(WHEAT_CROP));
    }

    @Test
    void reservationsFillReservedSlotsBeforeTakingNewOnes() {
        ContainerRoom room = new ContainerRoom(2, Map.of(), id -> 4);

        assertTrue(room.reserve(WHEAT_CROP), "two new stacks of 2 out of 4");
        assertTrue(room.reserve(WHEAT_CROP), "the same two stacks, now full");
        assertFalse(room.fits(WHEAT_CROP));
        assertFalse(room.reserve(WHEAT_CROP));
    }

    @Test
    void aFailedReservationTakesNothing() {
        ContainerRoom room = new ContainerRoom(1, Map.of(), id -> 100);

        assertFalse(room.reserve(WHEAT_CROP));
        assertTrue(room.reserve(List.of(new ItemCount("Wheat", 2))), "the slot is still free");
    }

    @Test
    void quantitiesBeyondAStackTakeSeveralSlots() {
        assertFalse(new ContainerRoom(2, Map.of(), id -> 1).fits(List.of(new ItemCount("Seed", 3))));
        assertTrue(new ContainerRoom(3, Map.of(), id -> 0).fits(List.of(new ItemCount("Seed", 3))),
                "an unknown stack size counts as 1");
        assertTrue(new ContainerRoom(0, Map.of(), id -> 1).fits(List.of()), "nothing to drop always fits");
    }
}
