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
        assertEquals("Planter", UpgradeVisual.stateFor(of(UpgradeType.PLANTER)));
    }

    @Test
    void combinationsInAnyOrder() {
        assertEquals("Both", UpgradeVisual.stateFor(of(UpgradeType.WATERING, UpgradeType.FERTILIZER)));
        assertEquals("Both", UpgradeVisual.stateFor(of(UpgradeType.FERTILIZER, UpgradeType.WATERING)));
        assertEquals("Watering_Planter", UpgradeVisual.stateFor(of(UpgradeType.PLANTER, UpgradeType.WATERING)));
        assertEquals("Fertilizer_Planter", UpgradeVisual.stateFor(of(UpgradeType.FERTILIZER, UpgradeType.PLANTER)));
        assertEquals("All", UpgradeVisual.stateFor(of(UpgradeType.PLANTER, UpgradeType.FERTILIZER, UpgradeType.WATERING)));
    }

    @Test
    void everyCombinationHasItsOwnNamedState() {
        assertEquals(List.of("Watering", "Fertilizer", "Both", "Planter", "Watering_Planter", "Fertilizer_Planter", "All"),
                UpgradeVisual.STATES);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int mask = 1; mask < 8; mask++) {
            UpgradeSet set = new UpgradeSet();
            for (UpgradeType type : UpgradeType.values()) {
                if ((mask & (1 << type.ordinal())) != 0) {
                    set.install(type);
                }
            }
            String state = UpgradeVisual.stateFor(set);
            assertTrue(UpgradeVisual.STATES.contains(state), state);
            assertTrue(seen.add(state), "two combinations share " + state);
            assertEquals(UpgradeVisual.particlesFor(set), UpgradeVisual.particlesFor(UpgradeVisual.upgradesOf(state)));
        }
        assertTrue(UpgradeVisual.upgradesOf(UpgradeVisual.DEFAULT).isEmpty());
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
    void particlesFollowTheUpgradesWateringFertilizerPlanter() {
        assertTrue(UpgradeVisual.particlesFor(new UpgradeSet()).isEmpty());
        assertEquals(List.of(new UpgradeVisual.Particle(UpgradeVisual.PARTICLE_SYSTEM, UpgradeVisual.WATERING_COLOR, 1.1)),
                UpgradeVisual.particlesFor(of(UpgradeType.WATERING)));
        assertEquals(List.of(new UpgradeVisual.Particle(UpgradeVisual.PARTICLE_SYSTEM, UpgradeVisual.PLANTER_COLOR, 1.1)),
                UpgradeVisual.particlesFor(of(UpgradeType.PLANTER)));
        List<UpgradeVisual.Particle> both = List.of(
                new UpgradeVisual.Particle(UpgradeVisual.PARTICLE_SYSTEM, UpgradeVisual.WATERING_COLOR, 1.05),
                new UpgradeVisual.Particle(UpgradeVisual.PARTICLE_SYSTEM, UpgradeVisual.FERTILIZER_COLOR, 1.2));
        assertEquals(both, UpgradeVisual.particlesFor(of(UpgradeType.WATERING, UpgradeType.FERTILIZER)));
        assertEquals(both, UpgradeVisual.particlesFor(of(UpgradeType.FERTILIZER, UpgradeType.WATERING)));
        List<UpgradeVisual.Particle> all = UpgradeVisual.particlesFor(
                of(UpgradeType.PLANTER, UpgradeType.WATERING, UpgradeType.FERTILIZER));
        assertEquals(List.of(UpgradeVisual.WATERING_COLOR, UpgradeVisual.FERTILIZER_COLOR, UpgradeVisual.PLANTER_COLOR),
                all.stream().map(UpgradeVisual.Particle::color).toList());
    }

    @Test
    void particlesSitAboveTheCapAndApart() {
        // The cap ends at about 0.97 of the block (Harvester.blockymodel).
        for (String state : UpgradeVisual.STATES) {
            List<UpgradeVisual.Particle> particles = UpgradeVisual.particlesFor(UpgradeVisual.upgradesOf(state));
            for (int i = 0; i < particles.size(); i++) {
                assertTrue(particles.get(i).offsetY() > 0.97, state + " " + particles.get(i));
                if (i > 0) {
                    assertTrue(particles.get(i).offsetY() - particles.get(i - 1).offsetY() >= 0.1, state);
                }
            }
        }
    }

    @Test
    void wateringIsBlueFertilizerGreenPlanterAmber() {
        int[] blue = UpgradeVisual.rgb(UpgradeVisual.WATERING_COLOR);
        int[] green = UpgradeVisual.rgb(UpgradeVisual.FERTILIZER_COLOR);
        int[] amber = UpgradeVisual.rgb(UpgradeVisual.PLANTER_COLOR);
        assertTrue(blue[2] > blue[0] && blue[2] > blue[1], "watering is blue");
        assertTrue(green[1] > green[0] && green[1] > green[2], "fertilizer is green");
        assertTrue(amber[0] > amber[1] && amber[1] > amber[2], "planter is amber");
    }

    @Test
    void parsesHexColors() {
        assertArrayEquals(new int[] {0x64, 0xc1, 0xff}, UpgradeVisual.rgb("#64c1ff"));
        assertArrayEquals(new int[] {0x60, 0xd6, 0x82}, UpgradeVisual.rgb("60D682"));
        for (String bad : new String[] {null, "", "#fff", "#64c1fg", "-12345", "+12345", "#64c1ff00"}) {
            assertThrows(IllegalArgumentException.class, () -> UpgradeVisual.rgb(bad), String.valueOf(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new UpgradeVisual.Particle("X", "blue", 1.0));
    }
}
