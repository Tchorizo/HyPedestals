package dev.tchorizo.hypedestals.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tchorizo.hypedestals.harvest.UpgradeType;
import dev.tchorizo.hypedestals.harvest.UpgradeVisual;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class HytaleIdsTest {

    private static final String ITEMS = "/Server/Item/Items/HyPedestals/";
    private static final String INTERACTIONS = "/Server/Item/Interactions/HyPedestals/";
    private static final String ROOT_INTERACTIONS = "/Server/Item/RootInteractions/HyPedestals/";

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

    @Test
    void stateNameOfVariantId() {
        assertEquals("Watering", HytaleIds.stateName("*HyPedestals_Harvester_Iron_State_Definitions_Watering"));
        assertEquals("StageFinal", HytaleIds.stateName("*Plant_Crop_Wheat_Block_State_Definitions_StageFinal"));
    }

    @Test
    void stateNameOfBaseIdIsNull() {
        assertNull(HytaleIds.stateName("HyPedestals_Harvester_Iron"));
        assertNull(HytaleIds.stateName(null));
    }

    @Test
    void itemTranslationKeyDropsModPrefix() {
        assertEquals("hypedestals.items.Upgrade_Watering.name",
                HytaleIds.itemTranslationKey("HyPedestals_Upgrade_Watering", "name"));
        assertEquals("hypedestals.items.Harvester_Copper.description",
                HytaleIds.itemTranslationKey("HyPedestals_Harvester_Copper", "description"));
    }

    /** Each upgrade id the code knows has its item asset, wired to our install interaction. */
    @ParameterizedTest
    @EnumSource(UpgradeType.class)
    void upgradeItemAssetMatchesItsId(UpgradeType type) throws IOException {
        String id = type.itemId();
        String json = resource(ITEMS + "Upgrades/" + id + ".json");
        assertTrue(json.contains("\"Name\": \"" + HytaleIds.itemTranslationKey(id, "name") + "\""), json);
        assertTrue(json.contains("\"Description\": \"" + HytaleIds.itemTranslationKey(id, "description") + "\""), json);
        assertTrue(json.contains("\"Icon\": \"Icons/ItemsGenerated/" + id + ".png\""), json);
        assertTrue(json.contains("\"Secondary\": \"HyPedestals_Upgrade_Install\""), json);
        assertNotNull(HytaleIdsTest.class.getResource("/Common/Icons/ItemsGenerated/" + id + ".png"), "icon of " + id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "pt-BR"})
    void langHasUpgradeKeys(String locale) throws IOException {
        String lang = resource("/Server/Languages/" + locale + "/hypedestals.lang");
        for (UpgradeType type : UpgradeType.values()) {
            for (String field : new String[] {"name", "description"}) {
                String key = HytaleIds.itemTranslationKey(type.itemId(), field).substring("hypedestals.".length());
                assertTrue(lang.contains("\n" + key + " = "), locale + " misses " + key);
            }
        }
        for (String key : new String[] {"installed", "alreadyInstalled", "removed", "none"}) {
            assertTrue(lang.contains("\nupgrade." + key + " = "), locale + " misses upgrade." + key);
        }
    }

    /** The JSON chain points at the interaction type ids registered in {@code HyPedestalsPlugin.setup}. */
    @Test
    void interactionAssetsReferenceRegisteredTypes() throws IOException {
        assertTrue(resource(INTERACTIONS + "HyPedestals_Upgrade_Install.json")
                .contains("\"Type\": \"HyPedestalsInstallUpgrade\""));
        String use = resource(INTERACTIONS + "HyPedestals_Harvester_Use.json");
        assertTrue(use.contains("\"Crouching\": true"), use);
        assertTrue(use.contains("\"Type\": \"HyPedestalsRemoveUpgrade\""), use);
        assertTrue(use.contains("\"Type\": \"OpenContainer\""), use);
        assertTrue(resource(ROOT_INTERACTIONS + "HyPedestals_Upgrade_Install.json")
                .contains("\"HyPedestals_Upgrade_Install\""));
        assertTrue(resource(ROOT_INTERACTIONS + "HyPedestals_Harvester_Use.json")
                .contains("\"HyPedestals_Harvester_Use\""));
        assertTrue(resource(ITEMS + "Harvester/HyPedestals_Harvester_Template.json")
                .contains("\"Use\": \"HyPedestals_Harvester_Use\""));
    }

    /**
     * Every non-default state {@link UpgradeVisual} can ask for is declared by each tier itself. A
     * {@code State} inherited through {@code Parent} keeps the parent's variant ids: with the states
     * in the Template, a Copper switched to {@code *HyPedestals_Harvester_Template_State_Definitions_Watering}
     * (itest 2026-10-16 10:25), i.e. the Template's block, texture and all, so the Template has none.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Copper", "Iron", "Thorium", "Cobalt"})
    void eachTierDeclaresUpgradeVisualStates(String tier) throws IOException {
        String json = resource(ITEMS + "Harvester/HyPedestals_Harvester_" + tier + ".json");
        for (String state : new String[] {UpgradeVisual.WATERING, UpgradeVisual.FERTILIZER, UpgradeVisual.BOTH}) {
            assertTrue(json.contains("\"" + state + "\": {"), tier + " misses state " + state);
        }
        assertFalse(resource(ITEMS + "Harvester/HyPedestals_Harvester_Template.json").contains("\"State\""),
                "states in the Template would be inherited with the Template's variant ids");
    }

    /**
     * The state particles are the effects {@link UpgradeVisual#particlesFor} names for the periodic
     * spawn (same system, colors, scale and height), so both paths show the same thing.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Copper", "Iron", "Thorium", "Cobalt"})
    void stateParticlesMatchUpgradeVisual(String tier) throws IOException {
        String json = resource(ITEMS + "Harvester/HyPedestals_Harvester_" + tier + ".json");
        String watering = between(json, "\"" + UpgradeVisual.WATERING + "\": {", "\"" + UpgradeVisual.FERTILIZER + "\": {");
        String fertilizer = between(json, "\"" + UpgradeVisual.FERTILIZER + "\": {", "\"" + UpgradeVisual.BOTH + "\": {");
        String both = between(json, "\"" + UpgradeVisual.BOTH + "\": {", "\"Recipe\"");
        String water = color(UpgradeVisual.WATERING_PARTICLE), fert = color(UpgradeVisual.FERTILIZER_PARTICLE);
        assertTrue(watering.contains(water) && !watering.contains(fert), tier + " Watering: " + watering);
        assertTrue(fertilizer.contains(fert) && !fertilizer.contains(water), tier + " Fertilizer: " + fertilizer);
        assertTrue(both.contains(water) && both.contains(fert), tier + " Both: " + both);
        for (String state : new String[] {watering, fertilizer, both}) {
            int entries = state.split("\"SystemId\"", -1).length - 1;
            assertEquals(entries, count(state, "\"SystemId\": \"" + UpgradeVisual.PARTICLE_SYSTEM + "\""), state);
            assertEquals(entries, count(state, "\"Scale\": " + UpgradeVisual.PARTICLE_SCALE), state);
            assertEquals(entries, count(state, "\"Y\": " + UpgradeVisual.PARTICLE_OFFSET_Y), state);
        }
    }

    private static String color(UpgradeVisual.Particle particle) {
        return "\"Color\": \"" + particle.color() + "\"";
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        int to = text.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from, "missing " + start + " … " + end);
        return text.substring(from, to);
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = HytaleIdsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
