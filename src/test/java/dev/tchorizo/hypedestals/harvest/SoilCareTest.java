package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SoilCareTest {

    private static final Instant NOW = Instant.parse("0001-01-03T06:00:00Z");
    private static final long D = 21_600;

    @Test
    void neverWateredSoilIsWatered() {
        assertTrue(SoilCare.decideWater(NOW, null, D));
    }

    @Test
    void expiredWateringIsRenewed() {
        assertTrue(SoilCare.decideWater(NOW, NOW.minusSeconds(1), D));
        assertTrue(SoilCare.decideWater(NOW, NOW, D));
    }

    @Test
    void rewatersOnlyBelowHalfTheDuration() {
        assertTrue(SoilCare.decideWater(NOW, NOW.plusSeconds(D / 2 - 1), D));
        assertFalse(SoilCare.decideWater(NOW, NOW.plusSeconds(D / 2), D), "exactly half left is enough");
        assertFalse(SoilCare.decideWater(NOW, NOW.plusSeconds(D), D));
    }

    @Test
    void halfOfAnOddDurationIsExact() {
        // D = 61: half is 30.5 s, so 30 s left rewaters and 31 s does not.
        assertTrue(SoilCare.decideWater(NOW, NOW.plusSeconds(30), 61));
        assertFalse(SoilCare.decideWater(NOW, NOW.plusSeconds(31), 61));
    }

    @Test
    void longerWateringThanTheTierIsLeftAlone() {
        // e.g. a vanilla watering can (86 400 s) on a Copper area: never shortened.
        assertFalse(SoilCare.decideWater(NOW, NOW.plusSeconds(86_400), D));
    }
}
