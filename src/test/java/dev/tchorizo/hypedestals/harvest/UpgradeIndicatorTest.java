package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UpgradeIndicatorTest {

    @Test
    void emptyHarvesterListsAllThreeAsMissing() {
        List<UpgradeIndicator.Entry> entries = UpgradeIndicator.entries(new UpgradeSet(UpgradeMachine.HARVESTER));
        assertEquals(List.of(
                new UpgradeIndicator.Entry(UpgradeType.WATERING, false),
                new UpgradeIndicator.Entry(UpgradeType.FERTILIZER, false),
                new UpgradeIndicator.Entry(UpgradeType.PLANTER, false)), entries);
    }

    @Test
    void installedOnesAreMarkedWhateverTheInstallationOrder() {
        UpgradeSet set = new UpgradeSet(UpgradeMachine.HARVESTER);
        set.install(UpgradeType.PLANTER);
        set.install(UpgradeType.WATERING);
        List<UpgradeIndicator.Entry> entries = UpgradeIndicator.entries(set);
        assertEquals(List.of(
                new UpgradeIndicator.Entry(UpgradeType.WATERING, true),
                new UpgradeIndicator.Entry(UpgradeType.FERTILIZER, false),
                new UpgradeIndicator.Entry(UpgradeType.PLANTER, true)), entries);
    }

    @Test
    void fullHarvesterHasNoMissingEntry() {
        UpgradeSet set = new UpgradeSet(UpgradeMachine.HARVESTER);
        for (UpgradeType type : UpgradeType.values()) {
            set.install(type);
        }
        assertEquals(3, UpgradeIndicator.entries(set).size());
        assertTrue(UpgradeIndicator.entries(set).stream().allMatch(UpgradeIndicator.Entry::installed));
    }

    @Test
    void lumberjackListsOnlyThePlanter() {
        UpgradeSet bare = new UpgradeSet(UpgradeMachine.LUMBERJACK);
        assertEquals(List.of(new UpgradeIndicator.Entry(UpgradeType.PLANTER, false)), UpgradeIndicator.entries(bare));

        UpgradeSet planted = new UpgradeSet(UpgradeMachine.LUMBERJACK);
        assertTrue(planted.install(UpgradeType.PLANTER));
        assertFalse(planted.install(UpgradeType.WATERING), "the Lumberjack never takes Watering");
        assertEquals(List.of(new UpgradeIndicator.Entry(UpgradeType.PLANTER, true)), UpgradeIndicator.entries(planted));
    }

    @Test
    void everyMachineAcceptsAtLeastOneEntryAndNoneIsRepeated() {
        for (UpgradeMachine machine : UpgradeMachine.values()) {
            List<UpgradeIndicator.Entry> entries = UpgradeIndicator.entries(new UpgradeSet(machine));
            assertFalse(entries.isEmpty(), machine.displayName());
            assertEquals(entries.size(), entries.stream().map(UpgradeIndicator.Entry::type).distinct().count());
            for (UpgradeIndicator.Entry entry : entries) {
                assertTrue(machine.accepts(entry.type()), machine.displayName() + " " + entry.type());
            }
        }
    }
}
