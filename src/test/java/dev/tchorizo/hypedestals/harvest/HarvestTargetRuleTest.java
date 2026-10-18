package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HarvestTargetRuleTest {

    @Test
    void acceptsFarmingBlocksWithOrdinaryGrowthStages() {
        assertTrue(HarvestTargetRule.isTarget(true, true, false),
                "annual and Eternal crops, apple, sap and fishing traps keep their current behavior");
    }

    @Test
    void rejectsFarmingBlocksWithoutGrowthStages() {
        assertFalse(HarvestTargetRule.isTarget(true, false, false),
                "tilled soil is a farming block but not a harvest target");
    }

    @Test
    void rejectsPrefabAndSpreadGrowth() {
        assertFalse(HarvestTargetRule.isTarget(true, true, true),
                "saplings, cacti and spreading mushrooms must remain in the world to grow");
    }

    @Test
    void countsPrefabGrowersForSoilCareWithoutMakingThemHarvestTargets() {
        assertTrue(HarvestTargetRule.isGrowing(true, true),
                "a sapling still needs the Fertilizer upgrade below it");
        assertFalse(HarvestTargetRule.isTarget(true, true, true),
                "the same sapling must not be passed to the harvest operation");
    }

    @Test
    void rejectsBlocksWithoutFarmingData() {
        assertFalse(HarvestTargetRule.isTarget(false, true, false));
    }
}
