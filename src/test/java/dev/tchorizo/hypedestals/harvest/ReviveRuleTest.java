package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviveRuleTest {

    /** The LifeCrops Eternal layout: Default = default,Stage1..3,StageFinal; Harvested = Stage1..3,StageFinal. */
    private static Map<String, List<String>> eternal() {
        Map<String, List<String>> sets = new LinkedHashMap<>();
        sets.put("Default", List.of("default", "Stage1", "Stage2", "Stage3", "StageFinal"));
        sets.put("Harvested", List.of("Stage1", "Stage2", "Stage3", "StageFinal"));
        return sets;
    }

    @Test
    void aStageOfTheAfterHarvestSetIsPlacedInThatSet() {
        ReviveRule.Stage stage = ReviveRule.stageOf(eternal(), "Harvested", "Default", "Stage1");
        assertEquals(new ReviveRule.Stage("Harvested", 0, 3), stage,
                "a dead Stage1 is what the harvest leaves behind: first stage of the regrowth set");
        assertEquals(new ReviveRule.Stage("Harvested", 2, 3), ReviveRule.stageOf(eternal(), "Harvested", "Default", "Stage3"));
    }

    @Test
    void theBaseStateIsTheDefaultStateOfTheStartingSet() {
        assertEquals(new ReviveRule.Stage("Default", 0, 4), ReviveRule.stageOf(eternal(), "Harvested", "Default", "default"),
                "a planted seed without entity: the 'default' state exists only in the starting set");
    }

    @Test
    void withoutRegrowthSetTheStartingSetIsUsed() {
        Map<String, List<String>> sets = new LinkedHashMap<>();
        sets.put("Default", List.of("default", "Stage1", "StageFinal"));
        assertEquals(new ReviveRule.Stage("Default", 1, 2), ReviveRule.stageOf(sets, null, "Default", "Stage1"));
    }

    @Test
    void anyOtherSetThatKnowsTheStateIsTheLastResort() {
        Map<String, List<String>> sets = new LinkedHashMap<>();
        sets.put("Wild", List.of("A", "B", "C"));
        sets.put("Default", List.of("default", "Stage1"));
        assertEquals(new ReviveRule.Stage("Wild", 1, 2), ReviveRule.stageOf(sets, "Harvested", "Default", "B"));
    }

    @Test
    void aStateNoSetKnowsHasNoStage() {
        assertNull(ReviveRule.stageOf(eternal(), "Harvested", "Default", "Stage9"));
        assertNull(ReviveRule.stageOf(Map.of(), null, null, "Stage1"));
    }

    @Test
    void aDeadCropInAnEarlyStageOfATickingSectionIsRevived() {
        ReviveRule.Stage stage = new ReviveRule.Stage("Harvested", 0, 3);
        assertTrue(ReviveRule.shouldRevive(stage, false, false, true));
    }

    @Test
    void theFinalStageWithoutEntityIsARipeCropAndIsLeftToTheHarvest() {
        assertFalse(ReviveRule.shouldRevive(new ReviveRule.Stage("Harvested", 3, 3), false, false, true),
                "FarmingUtil.harvest builds the entity of a ripe crop itself");
    }

    @Test
    void aCropWithALiveOrParkedEntityIsLeftAlone() {
        ReviveRule.Stage stage = new ReviveRule.Stage("Harvested", 1, 3);
        assertFalse(ReviveRule.shouldRevive(stage, true, false, true), "alive: it grows by itself");
        assertFalse(ReviveRule.shouldRevive(stage, false, true, true),
                "parked holder: adding another entity at that index throws Duplicate block components");
    }

    @Test
    void aSectionThatDoesNotTickIsNeverWrittenTo() {
        assertFalse(ReviveRule.shouldRevive(new ReviveRule.Stage("Harvested", 1, 3), false, false, false));
    }

    @Test
    void noStageNoRevival() {
        assertFalse(ReviveRule.shouldRevive(null, false, false, true));
    }
}
