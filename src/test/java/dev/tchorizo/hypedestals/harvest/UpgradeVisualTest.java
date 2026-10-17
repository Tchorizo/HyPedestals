package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

    @Test
    void particlesFollowTheUpgradesWateringFirst() {
        assertTrue(UpgradeVisual.particlesFor(new UpgradeSet()).isEmpty());
        assertEquals(List.of(UpgradeVisual.WATERING_PARTICLE), UpgradeVisual.particlesFor(of(UpgradeType.WATERING)));
        assertEquals(List.of(UpgradeVisual.FERTILIZER_PARTICLE), UpgradeVisual.particlesFor(of(UpgradeType.FERTILIZER)));
        List<UpgradeVisual.Particle> both = List.of(UpgradeVisual.WATERING_PARTICLE, UpgradeVisual.FERTILIZER_PARTICLE);
        assertEquals(both, UpgradeVisual.particlesFor(of(UpgradeType.WATERING, UpgradeType.FERTILIZER)));
        assertEquals(both, UpgradeVisual.particlesFor(of(UpgradeType.FERTILIZER, UpgradeType.WATERING)));
    }

    @Test
    void wateringIsBlueAndFertilizerGreen() {
        int[] blue = UpgradeVisual.WATERING_PARTICLE.rgb();
        int[] green = UpgradeVisual.FERTILIZER_PARTICLE.rgb();
        assertTrue(blue[2] > blue[0] && blue[2] > blue[1], "watering is blue");
        assertTrue(green[1] > green[0] && green[1] > green[2], "fertilizer is green");
    }

    @Test
    void parsesHexColors() {
        assertArrayEquals(new int[] {0x64, 0xc1, 0xff}, UpgradeVisual.rgb("#64c1ff"));
        assertArrayEquals(new int[] {0x60, 0xd6, 0x82}, UpgradeVisual.rgb("60D682"));
        for (String bad : new String[] {null, "", "#fff", "#64c1fg", "-12345", "+12345", "#64c1ff00"}) {
            assertThrows(IllegalArgumentException.class, () -> UpgradeVisual.rgb(bad), String.valueOf(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new UpgradeVisual.Particle("X", "blue"));
    }
}
