package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PendingDropsTest {

    private static Map<String, Integer> outstanding(PendingDrops pending) {
        return pending.outstanding().stream().collect(Collectors.toMap(ItemCount::itemId, ItemCount::quantity));
    }

    @Test
    void collectsComeOffTheOldestBatchFirst() {
        PendingDrops pending = new PendingDrops();
        pending.startCycle();
        pending.reserved(List.of(new ItemCount("Wheat", 3), new ItemCount("Essence", 2)));
        pending.startCycle();
        pending.reserved(List.of(new ItemCount("Wheat", 3)));

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
        PendingDrops pending = new PendingDrops();
        pending.startCycle();
        pending.reserved(List.of(new ItemCount("Wheat", 2), new ItemCount("Wheat", 1), new ItemCount("Essence", 0)));
        pending.reserved(List.of(new ItemCount("Wheat", 2)));

        assertEquals(Map.of("Wheat", 5), outstanding(pending));
        pending.collected("Wheat", 5);
        assertEquals(Map.of(), outstanding(pending));
    }
}
