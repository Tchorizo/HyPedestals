package dev.tchorizo.hypedestals;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.tchorizo.hypedestals.block.HarvesterComponent;
import dev.tchorizo.hypedestals.block.HarvesterSystem;
import dev.tchorizo.hypedestals.command.HyPedestalsCommand;
import dev.tchorizo.hypedestals.config.HyPedestalsConfig;
import dev.tchorizo.hypedestals.config.ModSettings;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public final class HyPedestalsPlugin extends JavaPlugin {
    private static final String PREFIX = "[HyPedestals] ";
    /** Config name; the file is {@code <data directory>/HyPedestals.json}. */
    private static final String CONFIG_NAME = "HyPedestals";

    // The server instantiates the plugin exactly once; the static helpers route through it.
    // Server-only: calling them before the server constructs the plugin (e.g. in a plain JUnit test) throws NPE.
    private static volatile HyPedestalsPlugin instance;

    /** Must be created in the constructor: {@code withConfig} throws once {@code setup} has started. */
    private final Config<HyPedestalsConfig> config;

    public HyPedestalsPlugin(JavaPluginInit init) {
        super(init);
        config = withConfig(CONFIG_NAME, HyPedestalsConfig.CODEC);
        instance = this;
    }

    @Override
    protected void setup() {
        // The server loads the config before setup (PluginBase.preLoad).
        HyPedestalsConfig loaded = config.get();
        for (String problem : loaded.settings().problems()) {
            warn("config: %s is out of range; using the default", problem);
        }
        loaded.setSettings(loaded.settings().sanitized());
        // Writes the file on first start (and fills in keys missing from an older file).
        save().exceptionally(e -> {
            warn("config: could not write %s: %s", configFile(), e);
            return null;
        });

        // TYPE must be assigned before the system is registered: its query references it.
        HarvesterComponent.TYPE = getChunkStoreRegistry().registerComponent(
                HarvesterComponent.class, "HyHarvester", HarvesterComponent.CODEC);
        getChunkStoreRegistry().registerSystem(new HarvesterSystem());
        boolean dev = System.getProperty(HyPedestalsCommand.DEV_PROPERTY) != null;
        getCommandRegistry().registerCommand(new HyPedestalsCommand(dev));
        log("setup (dev commands %s); config %s: %s", dev ? "on" : "off", configFile(), settings().describe());
    }

    @Override
    protected void start() {
        log("started");
    }

    /** Current global settings; read by every Harvester cycle, so a change applies from the next cycle on. */
    public static ModSettings settings() {
        return instance.config.get().settings();
    }

    /**
     * Applies {@code change} to the current global settings and writes the config file. Serialized,
     * so concurrent changes (two admins, window and command) do not overwrite each other.
     *
     * @param change returns the new settings, already validated; an exception it throws leaves
     *     the settings unchanged and propagates
     */
    public static synchronized CompletableFuture<Void> updateSettings(UnaryOperator<ModSettings> change) {
        HyPedestalsConfig current = instance.config.get();
        current.setSettings(change.apply(current.settings()));
        return instance.save();
    }

    /** Absolute path of the config file. */
    public static Path configFile() {
        return instance.getDataDirectory().resolve(CONFIG_NAME + ".json").toAbsolutePath();
    }

    private CompletableFuture<Void> save() {
        return config.save();
    }

    public static void log(String fmt, Object... args) {
        instance.getLogger().atInfo().log("%s", PREFIX + String.format(Locale.ROOT, fmt, args));
    }

    public static void warn(String fmt, Object... args) {
        instance.getLogger().atWarning().log("%s", PREFIX + String.format(Locale.ROOT, fmt, args));
    }
}
