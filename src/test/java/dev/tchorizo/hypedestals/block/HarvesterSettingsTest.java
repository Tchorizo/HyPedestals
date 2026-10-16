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

    @Test
    void sanitizesInterval() {
        assertEquals(0f, HarvesterSettings.sanitizeInterval(0f));
        assertEquals(0f, HarvesterSettings.sanitizeInterval(-1f));
        assertEquals(0.5f, HarvesterSettings.sanitizeInterval(0.5f));
        assertEquals(3f, HarvesterSettings.sanitizeInterval(3f));
    }
}
