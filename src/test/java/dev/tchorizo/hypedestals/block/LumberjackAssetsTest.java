package dev.tchorizo.hypedestals.block;

import dev.tchorizo.hypedestals.config.LumberSettings;
import dev.tchorizo.hypedestals.config.Tier;
import dev.tchorizo.hypedestals.harvest.UpgradeMachine;
import dev.tchorizo.hypedestals.harvest.UpgradeSet;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import dev.tchorizo.hypedestals.harvest.UpgradeVisual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void templateUsesTheUpgradeAwareUseAndThePedestalModel() throws IOException {
        String json = resource(ITEMS + "HyPedestals_Lumberjack_Template.json");
        assertTrue(json.contains("\"Use\": \"HyPedestals_Harvester_Use\""),
                "the Harvester's Use opens the container and removes the last upgrade when crouching: " + json);
        assertTrue(json.contains("\"CustomModel\": \"Blocks/HyPedestals/Harvester.blockymodel\""), json);
        assertTrue(json.contains("\"Quality\": \"Template\""), json);
        assertFalse(json.contains("\"Recipe\""), "templates have no recipe");
        assertFalse(json.contains("\"State\""), "states in the Template would be inherited with the Template's variant ids");
    }

    /**
     * The Lumberjack takes the Planter only, so each tier declares just that state (a {@code State}
     * inherited through {@code Parent} keeps the parent's variant ids), with the effect
     * {@link UpgradeVisual#particlesFor} names for the periodic spawn.
     */
    @ParameterizedTest
    @EnumSource(Tier.class)
    void eachTierDeclaresOnlyThePlanterState(Tier tier) throws IOException {
        String json = resource(ITEMS + "HyPedestals_Lumberjack_" + tier.displayName() + ".json");
        assertEquals(UpgradeVisual.PLANTER, UpgradeVisual.stateFor(planterOnly()),
                "the state a Lumberjack with the Planter asks for");
        String state = json.substring(json.indexOf("\"" + UpgradeVisual.PLANTER + "\": {"), json.indexOf("\"Recipe\""));
        List<UpgradeVisual.Particle> expected = UpgradeVisual.particlesFor(planterOnly());
        assertEquals(1, expected.size());
        assertEquals(expected.size(), state.split("\"SystemId\"", -1).length - 1, state);
        UpgradeVisual.Particle p = expected.getFirst();
        assertTrue(Pattern.compile("\"SystemId\":\\s*\"" + p.systemId() + "\",\\s*\"Color\":\\s*\"" + p.color()
                + "\",\\s*\"Scale\":\\s*" + String.valueOf(UpgradeVisual.PARTICLE_SCALE).replace(".", "\\.")
                + ",\\s*\"PositionOffset\":\\s*\\{\\s*\"Y\":\\s*" + String.valueOf(p.offsetY()).replace(".", "\\.") + "\\s*}")
                .matcher(state).find(), state);
        for (String other : new String[] {"Watering", "Fertilizer", "Both", "Watering_Planter", "Fertilizer_Planter", "All"}) {
            assertFalse(json.contains("\"" + other + "\": {"), tier.displayName() + " declares " + other);
        }
    }

    private static UpgradeSet planterOnly() {
        UpgradeSet set = new UpgradeSet(UpgradeMachine.LUMBERJACK);
        set.install(UpgradeType.PLANTER);
        return set;
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
        for (String key : new String[]{"lumberRadius", "lumberInterval", "lumberMaxTreeBlocks", "lumberMaxBlocksPerCycle",
                "lumberSaplingSpacing"}) {
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
