package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DropCalibrationTest {

    private static final String LIFE_CROP = "LifeCrop_Block_Eternal";
    /** Like the LifeCrops Eternal crop: 37 items reserved by the maximum, about 7 dropped. */
    private static final List<ItemCount> LIFE_CROP_MAX = List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 35));

    /** One cycle of {@code harvests} harvests, each putting {@code dropped} in the container; then the batch lapses. */
    private static void cycle(DropCalibration calibration, int harvests, List<ItemCount> max, List<ItemCount> dropped) {
        PendingDrops pending = new PendingDrops(calibration);
        pending.startCycle();
        for (int i = 0; i < harvests; i++) {
            pending.reserved(LIFE_CROP, max, calibration.reservation(LIFE_CROP, max));
            dropped.forEach(item -> pending.collected(item.itemId(), item.quantity()));
        }
        for (int i = 0; i <= PendingDrops.MAX_CYCLES; i++) {
            pending.startCycle();
        }
    }

    @Test
    void reservationShrinksToObservedDrops() {
        DropCalibration calibration = new DropCalibration();
        List<ItemCount> seven = List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 5));

        cycle(calibration, DropCalibration.MIN_SAMPLES - 1, LIFE_CROP_MAX, seven);
        assertEquals(LIFE_CROP_MAX, calibration.reservation(LIFE_CROP, LIFE_CROP_MAX), "fewer than 3 samples: the maximum");

        cycle(calibration, 1, LIFE_CROP_MAX, seven);
        assertEquals(List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 8)),
                calibration.reservation(LIFE_CROP, LIFE_CROP_MAX), "min(max, ceil(mean × 1.5)): 10 items instead of 37");

        List<ItemCount> otherList = List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 40));
        assertEquals(otherList, calibration.reservation(LIFE_CROP, otherList), "another drop list is not calibrated");
    }

    @Test
    void reservationNeverBelowOneStack() {
        DropCalibration calibration = new DropCalibration();
        List<ItemCount> max = List.of(new ItemCount("Wheat", 2), new ItemCount("Seed", 3));

        // The seed never dropped in the samples: it still keeps one item, so a slot (or a partial
        // stack) is held for it should it drop.
        cycle(calibration, DropCalibration.MIN_SAMPLES, max, List.of(new ItemCount("Wheat", 1)));

        List<ItemCount> reservation = calibration.reservation(LIFE_CROP, max);
        assertEquals(List.of(new ItemCount("Wheat", 2), new ItemCount("Seed", 1)), reservation);
        ContainerRoom oneSlot = new ContainerRoom(1, Map.of(), id -> 100);
        assertFalse(oneSlot.fits(reservation), "the seed needs a stack of its own, like the wheat");
    }

    @Test
    void reservationResetsAboveMax() {
        DropCalibration calibration = new DropCalibration();
        cycle(calibration, DropCalibration.MIN_SAMPLES, LIFE_CROP_MAX,
                List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 30)));
        assertEquals(LIFE_CROP_MAX, calibration.reservation(LIFE_CROP, LIFE_CROP_MAX),
                "mean × 1.5 above the maximum: the maximum, never more");

        calibration.clear();
        cycle(calibration, DropCalibration.MIN_SAMPLES, LIFE_CROP_MAX,
                List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 5)));
        assertEquals(new ItemCount("Essence", 8), calibration.reservation(LIFE_CROP, LIFE_CROP_MAX).get(1));

        // A harvest drops all 8 essence it reserved (maybe more): back to the maximum, and the
        // next MIN_SAMPLES harvests are needed to shrink again.
        cycle(calibration, 1, LIFE_CROP_MAX, List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 12)));
        assertEquals(LIFE_CROP_MAX, calibration.reservation(LIFE_CROP, LIFE_CROP_MAX));
        cycle(calibration, DropCalibration.MIN_SAMPLES - 1, LIFE_CROP_MAX,
                List.of(new ItemCount("LifeCrop", 2), new ItemCount("Essence", 5)));
        assertEquals(LIFE_CROP_MAX, calibration.reservation(LIFE_CROP, LIFE_CROP_MAX));
    }
}
