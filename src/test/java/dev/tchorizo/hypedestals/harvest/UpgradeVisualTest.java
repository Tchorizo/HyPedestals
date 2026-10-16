package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UpgradeVisualTest {

    private static UpgradeSet of(UpgradeType... types) {
        UpgradeSet set = new UpgradeSet();
        for (UpgradeType type : types) {
            set.install(type);
        }
        return set;
    }

    @Test
    void noUpgradeIsTheDefaultState() {
        assertEquals(UpgradeVisual.DEFAULT, UpgradeVisual.stateFor(new UpgradeSet()));
        assertEquals("default", UpgradeVisual.DEFAULT);
    }

    @Test
    void singleUpgrades() {
        assertEquals("Watering", UpgradeVisual.stateFor(of(UpgradeType.WATERING)));
        assertEquals("Fertilizer", UpgradeVisual.stateFor(of(UpgradeType.FERTILIZER)));
    }

    @Test
    void bothInEitherOrder() {
        assertEquals("Both", UpgradeVisual.stateFor(of(UpgradeType.WATERING, UpgradeType.FERTILIZER)));
        assertEquals("Both", UpgradeVisual.stateFor(of(UpgradeType.FERTILIZER, UpgradeType.WATERING)));
    }

    @Test
    void followsRemoval() {
        UpgradeSet set = of(UpgradeType.WATERING, UpgradeType.FERTILIZER);
        set.remove(set.newest());
        assertEquals("Watering", UpgradeVisual.stateFor(set));
        set.remove(set.newest());
        assertEquals(UpgradeVisual.DEFAULT, UpgradeVisual.stateFor(set));
    }
}
