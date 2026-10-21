package dev.tchorizo.hypedestals.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumberSettingsTest {

    @Test
    void defaultsArePartOfTheModDefaults() {
        assertSame(LumberSettings.DEFAULTS, ModSettings.DEFAULTS.lumber());
        assertEquals(new LumberSettings.Lumber(3, 30f), LumberSettings.DEFAULTS.tier(Tier.COPPER));
        assertEquals(new LumberSettings.Lumber(8, 10f), LumberSettings.DEFAULTS.tier(Tier.COBALT));
        assertEquals(2048, LumberSettings.DEFAULTS.maxTreeBlocks());
        assertTrue(ModSettings.DEFAULTS.problems().isEmpty());
    }

    @Test
    void setThroughModSettings() {
        ModSettings s = ModSettings.DEFAULTS.set("lumberjack.iron", "radius", "7");
        assertEquals(7, s.lumber().tier(Tier.IRON).radius());
        assertEquals(20f, s.lumber().tier(Tier.IRON).intervalSeconds());
        assertEquals(3, s.tier(Tier.IRON).radius(), "the Harvester's Iron radius is untouched");

        s = s.set("Lumberjack.Cobalt", "IntervalSeconds", "12.5").set("Lumberjack", "MaxTreeBlocks", "500");
        assertEquals(12.5f, s.lumber().tier(Tier.COBALT).intervalSeconds());
        assertEquals(500, s.lumber().maxTreeBlocks());
    }

    @Test
    void setRejectsOutOfRangeAndUnknown() {
        ModSettings d = ModSettings.DEFAULTS;
        assertThrows(IllegalArgumentException.class, () -> d.set("Lumberjack.Copper", "Radius", "17"));
        assertThrows(IllegalArgumentException.class, () -> d.set("Lumberjack.Copper", "IntervalSeconds", "0.5"));
        assertThrows(IllegalArgumentException.class, () -> d.set("Lumberjack.Gold", "Radius", "3"));
        assertThrows(IllegalArgumentException.class, () -> d.set("Lumberjack", "MaxTreeBlocks", "10"));
        assertThrows(IllegalArgumentException.class, () -> d.set("Lumberjack", "Radius", "3"));
        assertThrows(IllegalArgumentException.class, () -> d.set("Lumberjack.Copper", "WateringSeconds", "60"));
    }

    @Test
    void problemsAndSanitized() {
        LumberSettings bad = LumberSettings.DEFAULTS.withTier(Tier.THORIUM, new LumberSettings.Lumber(0, 9999f))
                .withMaxTreeBlocks(1);
        assertEquals(3, bad.problems().size());
        assertTrue(bad.problems().getFirst().startsWith("Lumberjack.Thorium.Radius=0"));
        assertEquals(LumberSettings.DEFAULTS, bad.sanitized());
        ModSettings mod = ModSettings.DEFAULTS.withLumber(bad);
        assertEquals(3, mod.problems().size());
        assertEquals(LumberSettings.DEFAULTS, mod.sanitized().lumber());
    }

    @Test
    void windowChangesOnlyWhatWasEdited() {
        LumberSettings opened = LumberSettings.DEFAULTS;
        LumberSettings current = opened.withTier(Tier.COPPER, new LumberSettings.Lumber(5, 30f)); // set by command meanwhile
        LumberSettings entered = opened.withTier(Tier.IRON, new LumberSettings.Lumber(4, 25.04f)).withMaxTreeBlocks(1000);

        LumberSettings merged = current.withChangesFrom(opened, entered);

        assertEquals(5, merged.tier(Tier.COPPER).radius(), "the command's change survives");
        assertEquals(25.04f, merged.tier(Tier.IRON).intervalSeconds());
        assertEquals(1000, merged.maxTreeBlocks());
    }

    @Test
    void effectiveFallsBackToTheAssetForUnknownTiers() {
        assertEquals(new LumberSettings.Lumber(3, 30f), LumberSettings.DEFAULTS.effective(1, 9, 99f));
        assertEquals(new LumberSettings.Lumber(9, 99f), LumberSettings.DEFAULTS.effective(7, 9, 99f));
    }

    @Test
    void describeListsEveryTier() {
        String line = LumberSettings.DEFAULTS.describe();
        assertTrue(line.startsWith("Lumberjack: Copper Radius=3 IntervalSeconds=30.0"), line);
        assertTrue(line.endsWith("MaxTreeBlocks=2048"), line);
    }
}
