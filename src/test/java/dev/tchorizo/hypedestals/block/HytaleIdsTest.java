package dev.tchorizo.hypedestals.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tchorizo.hypedestals.harvest.UpgradeType;
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

    private static String resource(String path) throws IOException {
        try (InputStream in = HytaleIdsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
