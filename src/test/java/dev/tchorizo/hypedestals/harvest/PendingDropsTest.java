package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PendingDropsTest {

    private static final List<ItemCount> WHEAT_MAX = List.of(new ItemCount("Wheat", 3), new ItemCount("Essence", 2));

    private static Map<String, Integer> outstanding(PendingDrops pending) {
        return pending.outstanding().stream().collect(Collectors.toMap(ItemCount::itemId, ItemCount::quantity));
    }

    @Test
    void collectsComeOffTheOldestBatchFirst() {
        PendingDrops pending = new PendingDrops(new DropCalibration());
        pending.startCycle();
        pending.reserved("Wheat_Block", WHEAT_MAX, WHEAT_MAX);
        pending.startCycle();
        pending.reserved("Wheat_Block", WHEAT_MAX, List.of(new ItemCount("Wheat", 3)));

        pending.collected("Wheat", 4);
        pending.collected("Seed", 9);

        assertEquals(Map.of("Wheat", 2, "Essence", 2), outstanding(pending), "items nobody waits for are ignored");

        // The first batch keeps its essence, the second its 2 wheat: after MAX_CYCLES more cycles
        // the first lapses one cycle before the second.
        for (int i = 1; i < PendingDrops.MAX_CYCLES; i++) {
            pending.startCycle();
        }
        assertEquals(Map.of("Wheat", 2, "Essence", 2), outstanding(pending));
        pending.startCycle();
        assertEquals(Map.of("Wheat", 2), outstanding(pending));
        pending.startCycle();
        assertEquals(Map.of(), outstanding(pending));
    }

    @Test
    void reservationsAddUpWithinACycle() {
        PendingDrops pending = new PendingDrops(new DropCalibration());
        pending.startCycle();
        pending.reserved("Wheat_Block", WHEAT_MAX,
                List.of(new ItemCount("Wheat", 2), new ItemCount("Wheat", 1), new ItemCount("Essence", 0)));
        pending.reserved("Wheat_Block", WHEAT_MAX, List.of(new ItemCount("Wheat", 2)));

        assertEquals(Map.of("Wheat", 5), outstanding(pending));
        pending.collected("Wheat", 5);
        assertEquals(Map.of(), outstanding(pending));
    }

    @Test
    void droppedBatchesTeachTheCalibrationPerCropType() {
        // One batch with two types sharing Essence: the wheat is all the wheat type's, the essence
        // is shared by the most each can drop (2 per wheat harvest, 6 per carrot harvest).
        DropCalibration calibration = new DropCalibration();
        PendingDrops pending = new PendingDrops(calibration);
        List<ItemCount> carrotMax = List.of(new ItemCount("Carrot", 1), new ItemCount("Essence", 6));
        pending.startCycle();
        for (int i = 0; i < 3; i++) {
            pending.reserved("Wheat_Block", WHEAT_MAX, WHEAT_MAX);
            pending.reserved("Carrot_Block", carrotMax, carrotMax);
        }
        pending.collected("Wheat", 3); // 1 per harvest
        pending.collected("Carrot", 3); // 1 per harvest
        pending.collected("Essence", 8); // weights 6 and 18: 2 for the wheat, 6 for the carrots

        for (int i = 0; i <= PendingDrops.MAX_CYCLES; i++) {
            pending.startCycle();
        }

        assertEquals(List.of(new ItemCount("Wheat", 2), new ItemCount("Essence", 1)),
                calibration.reservation("Wheat_Block", WHEAT_MAX), "wheat ceil(1 × 1.5), essence max(1, ceil(0.67 × 1.5))");
        assertEquals(List.of(new ItemCount("Carrot", 1), new ItemCount("Essence", 3)),
                calibration.reservation("Carrot_Block", carrotMax), "essence ceil(2 × 1.5)");
    }

    @Test
    void aBatchWhoseItemsWereLeftOnTheGroundTeachesNothing() {
        DropCalibration calibration = new DropCalibration();
        PendingDrops pending = new PendingDrops(calibration);
        pending.startCycle();
        for (int i = 0; i < 3; i++) {
            pending.reserved("Wheat_Block", WHEAT_MAX, WHEAT_MAX);
        }
        pending.collected("Wheat", 3);
        pending.collectBlocked(); // e.g. the container filled up: the rest may still be on the ground

        for (int i = 0; i <= PendingDrops.MAX_CYCLES; i++) {
            pending.startCycle();
        }

        assertEquals(WHEAT_MAX, calibration.reservation("Wheat_Block", WHEAT_MAX));
    }

    /** A calibration that learned 1 wheat and no essence per harvest: reserves (W2, E1) of (W3, E2). */
    private static DropCalibration calibratedWheat() {
        DropCalibration calibration = new DropCalibration();
        calibration.observed("Wheat_Block", WHEAT_MAX, 30, Map.of("Wheat", 30.0));
        return calibration;
    }

    private static final List<ItemCount> WHEAT_REDUCED = List.of(new ItemCount("Wheat", 2), new ItemCount("Essence", 1));

    @Test
    void aReducedLeftoverFilledByTheNextCyclesDropsDoesNotSendTheTypeBackToTheMaximum() {
        // Steady state: every cycle drops the mean (1 wheat) of a reservation of 2, so each batch
        // ends its cycle with 1 wheat left, and the next cycle's wheat (credited to the oldest
        // batch first) fills it. That fill says nothing about the older batch's harvest.
        DropCalibration calibration = calibratedWheat();
        PendingDrops pending = new PendingDrops(calibration);
        assertEquals(WHEAT_REDUCED, calibration.reservation("Wheat_Block", WHEAT_MAX));

        for (int cycle = 0; cycle < 3 + PendingDrops.MAX_CYCLES; cycle++) {
            pending.startCycle();
            pending.reserved("Wheat_Block", WHEAT_MAX, calibration.reservation("Wheat_Block", WHEAT_MAX));
            pending.collected("Wheat", 1);
            assertEquals(WHEAT_REDUCED, calibration.reservation("Wheat_Block", WHEAT_MAX), "cycle " + cycle);
        }
    }

    @Test
    void aReducedReservationFilledWhileItsBatchIsTheNewestSendsTheTypeBackToTheMaximum() {
        DropCalibration calibration = calibratedWheat();
        PendingDrops pending = new PendingDrops(calibration);
        pending.startCycle();
        pending.reserved("Wheat_Block", WHEAT_MAX, WHEAT_REDUCED);
        pending.collected("Wheat", 2); // the harvest dropped all it reserved, maybe more

        for (int i = 0; i <= PendingDrops.MAX_CYCLES; i++) {
            pending.startCycle();
        }

        assertEquals(WHEAT_MAX, calibration.reservation("Wheat_Block", WHEAT_MAX));
    }

    @Test
    void aSaturatedSharedItemLeavesTheTypesStillAtTheMaximumAlone() {
        // Wheat reserves 1 essence of 2, the carrot (2 harvests seen, still at its maximum) all 6;
        // the batch's 7 essence all came in while it was the newest.
        DropCalibration calibration = calibratedWheat();
        List<ItemCount> carrotMax = List.of(new ItemCount("Carrot", 1), new ItemCount("Essence", 6));
        calibration.observed("Carrot_Block", carrotMax, 2, Map.of("Carrot", 2.0, "Essence", 2.0));
        assertEquals(carrotMax, calibration.reservation("Carrot_Block", carrotMax));
        PendingDrops pending = new PendingDrops(calibration);
        pending.startCycle();
        pending.reserved("Wheat_Block", WHEAT_MAX, WHEAT_REDUCED);
        pending.reserved("Carrot_Block", carrotMax, carrotMax);
        pending.collected("Wheat", 1);
        pending.collected("Carrot", 1);
        pending.collected("Essence", 7);

        for (int i = 0; i <= PendingDrops.MAX_CYCLES; i++) {
            pending.startCycle();
        }

        assertEquals(WHEAT_MAX, calibration.reservation("Wheat_Block", WHEAT_MAX), "wheat reserved less essence than its maximum");
        assertEquals(List.of(new ItemCount("Carrot", 1), new ItemCount("Essence", 4)),
                calibration.reservation("Carrot_Block", carrotMax),
                "carrot observed (3rd sample): essence 7 × 6/8 = 5.25, mean (2 + 5.25) / 3, ceil(2.42 × 1.5)");
    }
}
