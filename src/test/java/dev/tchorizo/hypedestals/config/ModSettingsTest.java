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
        assertEquals(List.of(), ModSettings.DEFAULTS.problems(), "the defaults are valid");
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
        ModSettings s = ModSettings.DEFAULTS.withTier(Tier.COPPER, new TierSettings(6, 3f));
        assertEquals(new TierSettings(6, 3f), s.effective(1, 2, 12f), "config wins over the asset");
        assertEquals(new TierSettings(3, 8f), s.effective(2, 7, 1f), "untouched tiers use their default, not the asset");
        assertEquals(new TierSettings(7, 1.5f), s.effective(9, 7, 1.5f), "unknown tier falls back to the asset");
        assertEquals(new TierSettings(0, 0f), s.effective(0, 0, 0f), "a sanitized-invalid asset stays inactive");
    }

    @Test
    void withersAreImmutable() {
        ModSettings changed = ModSettings.DEFAULTS.withCollectAnyItem(false).withCollectMargin(0);
        assertFalse(changed.collectAnyItem());
        assertEquals(0, changed.collectMargin());
        assertTrue(ModSettings.DEFAULTS.collectAnyItem());
        assertEquals(2, ModSettings.DEFAULTS.collectMargin());
        assertThrows(UnsupportedOperationException.class, () -> changed.tiers().put(Tier.COPPER, new TierSettings(1, 1f)));
    }

    @Test
    void everyTierIsRequired() {
        Map<Tier, TierSettings> partial = new EnumMap<>(Tier.class);
        partial.put(Tier.COPPER, Tier.COPPER.defaults());
        assertThrows(IllegalArgumentException.class, () -> new ModSettings(partial, true, 2));
    }

    @Test
    void sanitizedReplacesOutOfRangeValuesWithDefaults() {
        ModSettings raw = ModSettings.DEFAULTS
                .withTier(Tier.COPPER, new TierSettings(9, 0.25f))
                .withTier(Tier.IRON, new TierSettings(0, 601f))
                .withTier(Tier.THORIUM, new TierSettings(8, Float.NaN))
                .withTier(Tier.COBALT, new TierSettings(1, 600f))
                .withCollectMargin(5);

        assertEquals(List.of(
                "Copper.Radius=9 (allowed 1..8)",
                "Copper.IntervalSeconds=0.25 (allowed 0.5..600.0)",
                "Iron.Radius=0 (allowed 1..8)",
                "Iron.IntervalSeconds=601.0 (allowed 0.5..600.0)",
                "Thorium.IntervalSeconds=NaN (allowed 0.5..600.0)",
                "CollectMargin=5 (allowed 0..4)"), raw.problems());

        ModSettings fixed = raw.sanitized();
        assertEquals(Tier.COPPER.defaults(), fixed.tier(Tier.COPPER));
        assertEquals(Tier.IRON.defaults(), fixed.tier(Tier.IRON));
        assertEquals(new TierSettings(8, 5f), fixed.tier(Tier.THORIUM), "only the invalid value is replaced");
        assertEquals(new TierSettings(1, 600f), fixed.tier(Tier.COBALT), "the bounds are valid");
        assertEquals(2, fixed.collectMargin());
        assertEquals(List.of(), fixed.problems());
        assertEquals(fixed, fixed.sanitized(), "idempotent");
    }

    @Test
    void setChangesOneField() {
        ModSettings s = ModSettings.DEFAULTS;
        assertEquals(new TierSettings(2, 3f), s.set("Copper", "IntervalSeconds", "3").tier(Tier.COPPER));
        assertEquals(new TierSettings(8, 8f), s.set("iron", "radius", "8").tier(Tier.IRON), "names ignore case");
        assertEquals(new TierSettings(4, 0.5f), s.set("Thorium", "IntervalSeconds", "0.5").tier(Tier.THORIUM));
        assertFalse(s.set("global", "CollectAnyItem", "false").collectAnyItem());
        assertTrue(s.withCollectAnyItem(false).set("GLOBAL", "collectanyitem", "TRUE").collectAnyItem());
        assertEquals(0, s.set("global", "CollectMargin", "0").collectMargin());
        assertEquals(4, s.set("global", "CollectMargin", "4").collectMargin());
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
    }

    @Test
    void describeIsOneLine() {
        assertEquals("Copper Radius=2 IntervalSeconds=12.0 | Iron Radius=3 IntervalSeconds=8.0"
                        + " | Thorium Radius=4 IntervalSeconds=5.0 | Cobalt Radius=5 IntervalSeconds=3.0"
                        + " | CollectAnyItem=true CollectMargin=2",
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
