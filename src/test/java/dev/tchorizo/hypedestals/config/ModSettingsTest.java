package dev.tchorizo.hypedestals.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModSettingsTest {

    @Test
    void defaultsAreTheTierJsonValues() throws IOException {
        for (Tier tier : Tier.values()) {
            String json = resource("/Server/Item/Items/HyPedestals/Harvester/HyPedestals_Harvester_" + tier.displayName() + ".json");
            assertEquals(tier.number(), Integer.parseInt(field(json, "Tier")), tier + " Tier");
            assertEquals(tier.defaults().radius(), Integer.parseInt(field(json, "Radius")), tier + " Radius");
            assertEquals(tier.defaults().intervalSeconds(), Float.parseFloat(field(json, "IntervalSeconds")), tier + " IntervalSeconds");
            assertSame(tier.defaults(), ModSettings.DEFAULTS.tier(tier));
        }
        assertTrue(ModSettings.DEFAULTS.collectAnyItem());
        assertEquals(2, ModSettings.DEFAULTS.collectMargin());
        assertEquals(30, ModSettings.DEFAULTS.soilSweepSeconds());
        assertTrue(ModSettings.DEFAULTS.exportToAdjacent());
        assertTrue(ModSettings.DEFAULTS.harvestParticles());
        assertEquals(3f, ModSettings.DEFAULTS.postHarvestCollectSeconds());
        assertEquals(List.of(), ModSettings.DEFAULTS.problems(), "the defaults are valid");
    }

    @Test
    void wateringDefaultsFollowTheDesign() {
        assertEquals(21_600, Tier.COPPER.defaults().wateringSeconds());
        assertEquals(43_200, Tier.IRON.defaults().wateringSeconds());
        assertEquals(72_000, Tier.THORIUM.defaults().wateringSeconds());
        assertEquals(108_000, Tier.COBALT.defaults().wateringSeconds());
    }

    @Test
    void tiersByNumberAndName() {
        assertSame(Tier.COPPER, Tier.byNumber(1));
        assertSame(Tier.COBALT, Tier.byNumber(4));
        assertEquals(null, Tier.byNumber(0));
        assertEquals(null, Tier.byNumber(5));
        assertSame(Tier.THORIUM, Tier.byName("thorium"));
        assertSame(Tier.IRON, Tier.byName("IRON"));
        assertEquals(null, Tier.byName("Gold"));
    }

    @Test
    void effectiveUsesTheConfigForKnownTiersAndTheAssetOtherwise() {
        ModSettings s = ModSettings.DEFAULTS.withTier(Tier.COPPER, new TierSettings(6, 3f, 600));
        assertEquals(new TierSettings(6, 3f, 600), s.effective(1, 2, 12f), "config wins over the asset");
        assertEquals(new TierSettings(3, 8f, 43_200), s.effective(2, 7, 1f), "untouched tiers use their default, not the asset");
        assertEquals(new TierSettings(7, 1.5f, 21_600), s.effective(9, 7, 1.5f),
                "unknown tier falls back to the asset, watering to the Copper default");
        assertEquals(new TierSettings(0, 0f, 21_600), s.effective(0, 0, 0f), "a sanitized-invalid asset stays inactive");
    }

    @Test
    void withersAreImmutable() {
        ModSettings changed = ModSettings.DEFAULTS.withCollectAnyItem(false).withCollectMargin(0)
                .withSoilSweepSeconds(5).withExportToAdjacent(false).withHarvestParticles(false)
                .withPostHarvestCollectSeconds(0f);
        assertFalse(changed.collectAnyItem());
        assertEquals(0, changed.collectMargin());
        assertEquals(5, changed.soilSweepSeconds());
        assertFalse(changed.exportToAdjacent());
        assertFalse(changed.harvestParticles());
        assertEquals(0f, changed.postHarvestCollectSeconds());
        assertEquals(ModSettings.DEFAULTS.tiers(), changed.tiers(), "global withers keep the tiers");
        assertTrue(ModSettings.DEFAULTS.collectAnyItem());
        assertEquals(2, ModSettings.DEFAULTS.collectMargin());
        assertEquals(30, ModSettings.DEFAULTS.soilSweepSeconds());
        assertTrue(ModSettings.DEFAULTS.exportToAdjacent());
        assertTrue(ModSettings.DEFAULTS.harvestParticles());
        assertEquals(3f, ModSettings.DEFAULTS.postHarvestCollectSeconds());
        ModSettings tiered = changed.withTier(Tier.IRON, new TierSettings(1, 1f, 60));
        assertEquals(5, tiered.soilSweepSeconds(), "withTier keeps the globals");
        assertFalse(tiered.exportToAdjacent());
        assertFalse(tiered.harvestParticles());
        assertEquals(0f, tiered.postHarvestCollectSeconds());
        assertThrows(UnsupportedOperationException.class, () -> changed.tiers().put(Tier.COPPER, new TierSettings(1, 1f, 60)));
    }

    @Test
    void everyTierIsRequired() {
        Map<Tier, TierSettings> partial = new EnumMap<>(Tier.class);
        partial.put(Tier.COPPER, Tier.COPPER.defaults());
        assertThrows(IllegalArgumentException.class, () -> new ModSettings(partial, true, 2, 30, true, true, 3f));
    }

    @Test
    void sanitizedReplacesOutOfRangeValuesWithDefaults() {
        ModSettings raw = ModSettings.DEFAULTS
                .withTier(Tier.COPPER, new TierSettings(9, 0.25f, 59))
                .withTier(Tier.IRON, new TierSettings(0, 601f, 43_200))
                .withTier(Tier.THORIUM, new TierSettings(8, Float.NaN, 604_801))
                .withTier(Tier.COBALT, new TierSettings(1, 600f, 604_800))
                .withCollectMargin(5)
                .withSoilSweepSeconds(4)
                .withExportToAdjacent(false)
                .withHarvestParticles(false)
                .withPostHarvestCollectSeconds(10.5f);

        assertEquals(List.of(
                "Copper.Radius=9 (allowed 1..8)",
                "Copper.IntervalSeconds=0.25 (allowed 0.5..600.0)",
                "Copper.WateringSeconds=59 (allowed 60..604800)",
                "Iron.Radius=0 (allowed 1..8)",
                "Iron.IntervalSeconds=601.0 (allowed 0.5..600.0)",
                "Thorium.IntervalSeconds=NaN (allowed 0.5..600.0)",
                "Thorium.WateringSeconds=604801 (allowed 60..604800)",
                "CollectMargin=5 (allowed 0..4)",
                "SoilSweepSeconds=4 (allowed 5..600)",
                "PostHarvestCollectSeconds=10.5 (allowed 0.0..10.0)"), raw.problems());

        ModSettings fixed = raw.sanitized();
        assertEquals(Tier.COPPER.defaults(), fixed.tier(Tier.COPPER));
        assertEquals(Tier.IRON.defaults(), fixed.tier(Tier.IRON));
        assertEquals(new TierSettings(8, 5f, 72_000), fixed.tier(Tier.THORIUM), "only the invalid values are replaced");
        assertEquals(new TierSettings(1, 600f, 604_800), fixed.tier(Tier.COBALT), "the bounds are valid");
        assertEquals(2, fixed.collectMargin());
        assertEquals(30, fixed.soilSweepSeconds());
        assertFalse(fixed.exportToAdjacent(), "booleans are kept");
        assertFalse(fixed.harvestParticles());
        assertEquals(3f, fixed.postHarvestCollectSeconds());
        assertEquals(3f, ModSettings.DEFAULTS.withPostHarvestCollectSeconds(Float.NaN).sanitized().postHarvestCollectSeconds(),
                "NaN is out of range");
        assertEquals(List.of("PostHarvestCollectSeconds=-1.0 (allowed 0.0..10.0)"),
                ModSettings.DEFAULTS.withPostHarvestCollectSeconds(-1f).problems(), "negative is out of range");
        assertEquals(0f, ModSettings.DEFAULTS.withPostHarvestCollectSeconds(0f).sanitized().postHarvestCollectSeconds(),
                "0 (off) is valid");
        assertEquals(10f, ModSettings.DEFAULTS.withPostHarvestCollectSeconds(10f).sanitized().postHarvestCollectSeconds(),
                "upper bound is valid");
        assertEquals(5, ModSettings.DEFAULTS.withSoilSweepSeconds(5).sanitized().soilSweepSeconds(), "lower bound is valid");
        assertEquals(600, ModSettings.DEFAULTS.withSoilSweepSeconds(600).sanitized().soilSweepSeconds(), "upper bound is valid");
        assertEquals(List.of(), fixed.problems());
        assertEquals(fixed, fixed.sanitized(), "idempotent");
    }

    @Test
    void setChangesOneField() {
        ModSettings s = ModSettings.DEFAULTS;
        assertEquals(new TierSettings(2, 3f, 21_600), s.set("Copper", "IntervalSeconds", "3").tier(Tier.COPPER));
        assertEquals(new TierSettings(8, 8f, 43_200), s.set("iron", "radius", "8").tier(Tier.IRON), "names ignore case");
        assertEquals(new TierSettings(4, 0.5f, 72_000), s.set("Thorium", "IntervalSeconds", "0.5").tier(Tier.THORIUM));
        assertEquals(new TierSettings(5, 3f, 60), s.set("Cobalt", "WateringSeconds", "60").tier(Tier.COBALT));
        assertEquals(new TierSettings(2, 12f, 604_800), s.set("copper", "wateringseconds", "604800").tier(Tier.COPPER));
        assertEquals(5, s.set("global", "SoilSweepSeconds", "5").soilSweepSeconds());
        assertEquals(600, s.set("global", "soilsweepseconds", "600").soilSweepSeconds());
        assertFalse(s.set("global", "ExportToAdjacent", "false").exportToAdjacent());
        assertTrue(s.withExportToAdjacent(false).set("global", "exporttoadjacent", "true").exportToAdjacent());
        assertFalse(s.set("global", "HarvestParticles", "False").harvestParticles());
        assertFalse(s.set("global", "CollectAnyItem", "false").collectAnyItem());
        assertTrue(s.withCollectAnyItem(false).set("GLOBAL", "collectanyitem", "TRUE").collectAnyItem());
        assertEquals(0, s.set("global", "CollectMargin", "0").collectMargin());
        assertEquals(4, s.set("global", "CollectMargin", "4").collectMargin());
        assertEquals(0f, s.set("global", "PostHarvestCollectSeconds", "0").postHarvestCollectSeconds(), "0 turns it off");
        assertEquals(10f, s.set("global", "postharvestcollectseconds", "10").postHarvestCollectSeconds());
        assertEquals(1.5f, s.set("global", "PostHarvestCollectSeconds", "1.5").postHarvestCollectSeconds());
        assertEquals(ModSettings.DEFAULTS, s, "set returns a copy");
    }

    @Test
    void setRejectsBadInput() {
        ModSettings s = ModSettings.DEFAULTS;
        assertMessage("unknown target Gold", () -> s.set("Gold", "Radius", "2"));
        assertMessage("unknown tier field Margin", () -> s.set("Copper", "Margin", "2"));
        assertMessage("unknown global field Radius", () -> s.set("global", "Radius", "2"));
        assertMessage("Radius must be 1..8, got 9", () -> s.set("Copper", "Radius", "9"));
        assertMessage("Radius must be 1..8, got 0", () -> s.set("Copper", "Radius", "0"));
        assertMessage("not a whole number: 2.5", () -> s.set("Copper", "Radius", "2.5"));
        assertMessage("IntervalSeconds must be 0.5..600.0, got 0.1", () -> s.set("Copper", "IntervalSeconds", "0.1"));
        assertMessage("IntervalSeconds must be 0.5..600.0, got NaN", () -> s.set("Copper", "IntervalSeconds", "NaN"));
        assertMessage("not a number: fast", () -> s.set("Copper", "IntervalSeconds", "fast"));
        assertMessage("expected true or false, got yes", () -> s.set("global", "CollectAnyItem", "yes"));
        assertMessage("CollectMargin must be 0..4, got 5", () -> s.set("global", "CollectMargin", "5"));
        assertMessage("unknown tier field SoilSweepSeconds", () -> s.set("Copper", "SoilSweepSeconds", "30"));
        assertMessage("unknown global field WateringSeconds", () -> s.set("global", "WateringSeconds", "600"));
        assertMessage("WateringSeconds must be 60..604800, got 59", () -> s.set("Copper", "WateringSeconds", "59"));
        assertMessage("WateringSeconds must be 60..604800, got 604801", () -> s.set("Copper", "WateringSeconds", "604801"));
        assertMessage("not a whole number: 1.5", () -> s.set("Copper", "WateringSeconds", "1.5"));
        assertMessage("SoilSweepSeconds must be 5..600, got 4", () -> s.set("global", "SoilSweepSeconds", "4"));
        assertMessage("SoilSweepSeconds must be 5..600, got 601", () -> s.set("global", "SoilSweepSeconds", "601"));
        assertMessage("expected true or false, got on", () -> s.set("global", "ExportToAdjacent", "on"));
        assertMessage("expected true or false, got 1", () -> s.set("global", "HarvestParticles", "1"));
        assertMessage("PostHarvestCollectSeconds must be 0.0..10.0, got 10.5",
                () -> s.set("global", "PostHarvestCollectSeconds", "10.5"));
        assertMessage("PostHarvestCollectSeconds must be 0.0..10.0, got -1", () -> s.set("global", "PostHarvestCollectSeconds", "-1"));
        assertMessage("PostHarvestCollectSeconds must be 0.0..10.0, got NaN", () -> s.set("global", "PostHarvestCollectSeconds", "NaN"));
        assertMessage("not a number: soon", () -> s.set("global", "PostHarvestCollectSeconds", "soon"));
        assertMessage("unknown tier field PostHarvestCollectSeconds", () -> s.set("Copper", "PostHarvestCollectSeconds", "3"));
    }

    @Test
    void withChangesFromKeepsConcurrentChangesToUntouchedFields() {
        // The window opens with the defaults; meanwhile `config set Copper IntervalSeconds 3` and
        // `config set global CollectMargin 0`; the user only changes Iron's radius and the
        // particles checkbox, then saves.
        ModSettings opened = ModSettings.DEFAULTS;
        ModSettings current = opened.set("Copper", "IntervalSeconds", "3").set("global", "CollectMargin", "0");
        ModSettings entered = opened.set("Iron", "Radius", "6").set("global", "HarvestParticles", "false");

        ModSettings saved = current.withChangesFrom(opened, entered);

        assertEquals(new TierSettings(2, 3f, Tier.COPPER.defaults().wateringSeconds()), saved.tier(Tier.COPPER),
                "the concurrent command change survives");
        assertEquals(0, saved.collectMargin(), "the concurrent command change survives");
        assertEquals(6, saved.tier(Tier.IRON).radius(), "the field the user changed is applied");
        assertFalse(saved.harvestParticles(), "the field the user changed is applied");
        assertEquals(Tier.THORIUM.defaults(), saved.tier(Tier.THORIUM));
        assertEquals(current.withTier(Tier.IRON, new TierSettings(6, 8f, Tier.IRON.defaults().wateringSeconds()))
                .withHarvestParticles(false), saved, "nothing else changes");
    }

    @Test
    void withChangesFromAppliesTheUsersValueOverAConcurrentChangeOfTheSameField() {
        ModSettings opened = ModSettings.DEFAULTS;
        ModSettings current = opened.set("Copper", "IntervalSeconds", "3");
        ModSettings entered = opened.set("Copper", "IntervalSeconds", "6");
        assertEquals(6f, current.withChangesFrom(opened, entered).tier(Tier.COPPER).intervalSeconds(), "last writer wins");
    }

    @Test
    void withChangesFromIgnoresTheWindowsOneDecimalRounding() {
        // 0.75 set by command shows as 0.8 in the window; saving without touching it keeps 0.75.
        ModSettings opened = ModSettings.DEFAULTS.set("Copper", "IntervalSeconds", "0.75");
        ModSettings entered = ModSettings.DEFAULTS.set("Copper", "IntervalSeconds", "0.8");
        assertEquals(0.75f, opened.withChangesFrom(opened, entered).tier(Tier.COPPER).intervalSeconds());
        assertEquals(opened, opened.withChangesFrom(opened, opened), "no changes, same settings");
        ModSettings openedWindow = ModSettings.DEFAULTS.set("global", "PostHarvestCollectSeconds", "2.25");
        ModSettings enteredWindow = ModSettings.DEFAULTS.set("global", "PostHarvestCollectSeconds", "2.3");
        assertEquals(2.25f, openedWindow.withChangesFrom(openedWindow, enteredWindow).postHarvestCollectSeconds(),
                "the post-harvest window is compared in tenths too");
        assertEquals(0f, ModSettings.DEFAULTS.withChangesFrom(ModSettings.DEFAULTS,
                ModSettings.DEFAULTS.withPostHarvestCollectSeconds(0f)).postHarvestCollectSeconds(), "a real change is applied");
    }

    @Test
    void describeIsOneLine() {
        assertEquals("Copper Radius=2 IntervalSeconds=12.0 WateringSeconds=21600"
                        + " | Iron Radius=3 IntervalSeconds=8.0 WateringSeconds=43200"
                        + " | Thorium Radius=4 IntervalSeconds=5.0 WateringSeconds=72000"
                        + " | Cobalt Radius=5 IntervalSeconds=3.0 WateringSeconds=108000"
                        + " | CollectAnyItem=true CollectMargin=2 SoilSweepSeconds=30 ExportToAdjacent=true HarvestParticles=true"
                        + " PostHarvestCollectSeconds=3.0",
                ModSettings.DEFAULTS.describe());
    }

    private static void assertMessage(String prefix, org.junit.jupiter.api.function.Executable call) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
        assertTrue(e.getMessage().startsWith(prefix), () -> "message '" + e.getMessage() + "' should start with '" + prefix + "'");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ModSettingsTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Value of the first {@code "key": <number>} in the JSON text. */
    private static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)").matcher(json);
        if (!m.find()) {
            throw new AssertionError("no " + key + " in " + json);
        }
        return m.group(1);
    }
}
