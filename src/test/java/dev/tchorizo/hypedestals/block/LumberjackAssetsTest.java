package dev.tchorizo.hypedestals.block;

import dev.tchorizo.hypedestals.config.LumberSettings;
import dev.tchorizo.hypedestals.config.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Lumberjack's block JSON, textures, icons and translations agree with the code. */
class LumberjackAssetsTest {

    private static final String ITEMS = "/Server/Item/Items/HyPedestals/Lumberjack/";

    @ParameterizedTest
    @EnumSource(Tier.class)
    void tierJsonMatchesTheConfigDefaults(Tier tier) throws IOException {
        String name = tier.displayName();
        String json = resource(ITEMS + "HyPedestals_Lumberjack_" + name + ".json");
        LumberSettings.Lumber d = LumberSettings.defaults(tier);
        String component = String.format(Locale.ROOT, "\"HyLumberjack\":{\"Tier\":%d,\"Radius\":%d,\"IntervalSeconds\":%.1f}",
                tier.number(), d.radius(), d.intervalSeconds());
        assertTrue(json.replaceAll("\\s", "").contains(component), json);
        assertTrue(json.contains("\"Parent\": \"HyPedestals_Lumberjack_Template\""), json);
        assertTrue(json.contains("\"ItemId\": \"HyPedestals_Lumberjack_" + name + "\""), "breaking drops the block itself");
        assertTrue(json.contains("\"Texture\": \"Blocks/HyPedestals/Lumberjack_Textures/" + name + ".png\""), json);
        assertNotNull(getClass().getResource("/Common/Blocks/HyPedestals/Lumberjack_Textures/" + name + ".png"), "atlas " + name);
        assertNotNull(getClass().getResource("/Common/Icons/ItemsGenerated/HyPedestals_Lumberjack_" + name + ".png"), "icon " + name);
        Tier previous = Tier.byNumber(tier.number() - 1);
        assertTrue(json.contains(previous == null ? "\"ItemId\": \"Tool_Hatchet_Copper\""
                : "\"ItemId\": \"HyPedestals_Lumberjack_" + previous.displayName() + "\""), "recipe of " + name);
    }

    @Test
    void templateOpensTheContainerAndUsesThePedestalModel() throws IOException {
        String json = resource(ITEMS + "HyPedestals_Lumberjack_Template.json");
        assertTrue(json.contains("\"Use\": \"Open_Container\""), json);
        assertTrue(json.contains("\"CustomModel\": \"Blocks/HyPedestals/Harvester.blockymodel\""), json);
        assertTrue(json.contains("\"Quality\": \"Template\""), json);
        assertFalse(json.contains("\"Recipe\""), "templates have no recipe");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "pt-BR"})
    void langHasLumberjackKeys(String locale) throws IOException {
        String lang = resource("/Server/Languages/" + locale + "/hypedestals.lang");
        for (Tier tier : Tier.values()) {
            for (String key : new String[]{"name", "description"}) {
                assertTrue(lang.contains("\nitems.Lumberjack_" + tier.displayName() + "." + key + " = "),
                        locale + " misses Lumberjack_" + tier.displayName() + "." + key);
            }
        }
        for (String key : new String[]{"lumberRadius", "lumberInterval", "lumberMaxTreeBlocks"}) {
            assertTrue(lang.contains("\nui.config." + key + " = "), locale + " misses ui.config." + key);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = LumberjackAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
