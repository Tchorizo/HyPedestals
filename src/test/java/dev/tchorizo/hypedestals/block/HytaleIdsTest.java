package dev.tchorizo.hypedestals.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HytaleIdsTest {

    @Test
    void stripsStatePrefixAndSuffix() {
        assertEquals("Plant_Crop_Wheat_Block",
                HytaleIds.baseBlockId("*Plant_Crop_Wheat_Block_State_Definitions_StageFinal"));
    }

    @Test
    void keepsBaseIdUnchanged() {
        assertEquals("Plant_Crop_Wheat_Block", HytaleIds.baseBlockId("Plant_Crop_Wheat_Block"));
    }

    @Test
    void stripsLonePrefix() {
        assertEquals("X", HytaleIds.baseBlockId("*X"));
    }

    @Test
    void nullStaysNull() {
        assertNull(HytaleIds.baseBlockId(null));
    }
}
