package dev.tchorizo.hypedestals.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HarvesterSettingsTest {

    @Test
    void sanitizesRadius() {
        assertEquals(0, HarvesterSettings.sanitizeRadius(0));
        assertEquals(1, HarvesterSettings.sanitizeRadius(1));
        assertEquals(8, HarvesterSettings.sanitizeRadius(8));
        assertEquals(0, HarvesterSettings.sanitizeRadius(9));
        assertEquals(0, HarvesterSettings.sanitizeRadius(-3));
    }
}
