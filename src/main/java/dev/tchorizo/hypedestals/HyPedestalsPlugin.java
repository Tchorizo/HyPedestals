package dev.tchorizo.hypedestals;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.BsonUtil;
import com.hypixel.hytale.server.core.util.Config;
import dev.tchorizo.hypedestals.block.HarvesterComponent;
import dev.tchorizo.hypedestals.block.HarvesterRemovalSystem;
import dev.tchorizo.hypedestals.block.HarvesterSystem;
import dev.tchorizo.hypedestals.block.InstallUpgradeInteraction;
import dev.tchorizo.hypedestals.block.LumberjackComponent;
import dev.tchorizo.hypedestals.block.LumberjackRemovalSystem;
import dev.tchorizo.hypedestals.block.LumberjackSystem;
import dev.tchorizo.hypedestals.block.RemoveUpgradeInteraction;
import dev.tchorizo.hypedestals.command.HyPedestalsCommand;
import dev.tchorizo.hypedestals.config.HyPedestalsConfig;
import dev.tchorizo.hypedestals.config.ModSettings;
import dev.tchorizo.hypedestals.harvest.DropCalibration;
import org.bson.BsonDocument;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

public final class HyPedestalsPlugin extends JavaPlugin {
    private static final String PREFIX = "[HyPedestals] ";
    /** Config name; the file is {@code <data directory>/HyPedestals.json}. */
    private static final String CONFIG_NAME = "HyPedestals";
    /** Guards the read-modify-write in {@link #updateSettings} and the write chain ({@link #lastWrite}). */
    private static final Object SETTINGS_LOCK = new Object();
    /** How long a reply (and the shutdown) waits for the config file write. */
    private static final long WRITE_TIMEOUT_SECONDS = 5;

    // The server instantiates the plugin exactly once; the static helpers route through it.
    // Server-only: calling them before the server constructs the plugin (e.g. in a plain JUnit test) throws NPE.
    private static volatile HyPedestalsPlugin instance;

    /** Must be created in the constructor: {@code withConfig} throws once {@code setup} has started. */
    private final Config<HyPedestalsConfig> config;
    /** Newest queued write of the config file; guarded by {@link #SETTINGS_LOCK}. */
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    public HyPedestalsPlugin(JavaPluginInit init) {
        super(init);
        config = withConfig(CONFIG_NAME, HyPedestalsConfig.CODEC);
        instance = this;
    }

    @Override
    protected void setup() {
        // The server loads the config before setup (PluginBase.preLoad).
        // Drop lists come with the assets, which a reload may have changed: learn them again.
        DropCalibration.global().clear();
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
        getChunkStoreRegistry().registerSystem(new HarvesterRemovalSystem());
        LumberjackComponent.TYPE = getChunkStoreRegistry().registerComponent(
                LumberjackComponent.class, "HyLumberjack", LumberjackComponent.CODEC);
        getChunkStoreRegistry().registerSystem(new LumberjackSystem());
        getChunkStoreRegistry().registerSystem(new LumberjackRemovalSystem());
        // Interaction types referenced by our Server/Item/Interactions JSON (upgrade item, Harvester Use).
        getCodecRegistry(Interaction.CODEC)
                .register(InstallUpgradeInteraction.TYPE_ID, InstallUpgradeInteraction.class, InstallUpgradeInteraction.CODEC)
                .register(RemoveUpgradeInteraction.TYPE_ID, RemoveUpgradeInteraction.class, RemoveUpgradeInteraction.CODEC);
        boolean dev = System.getProperty(HyPedestalsCommand.DEV_PROPERTY) != null;
        getCommandRegistry().registerCommand(new HyPedestalsCommand(dev));
        log("setup (dev commands %s); config %s: %s", dev ? "on" : "off", configFile(), settings().describe());
    }

    @Override
    protected void start() {
        log("started");
    }

    /**
     * The engine's document writer threads are daemons, so a write still queued when the JVM
     * exits is lost (e.g. {@code config set} followed right away by {@code stop}): wait for it.
     */
    @Override
    protected void shutdown() {
        CompletableFuture<Void> pending;
        synchronized (SETTINGS_LOCK) {
            pending = lastWrite;
        }
        try {
            pending.get(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            warn("config: pending write of %s did not finish: %s", configFile(), e);
        }
    }

    /** Current global settings; read by every Harvester cycle, so a change applies from the next cycle on. */
    public static ModSettings settings() {
        return instance.config.get().settings();
    }

    /**
     * Applies {@code change} to the current global settings and writes the config file. The
     * read-modify-write and the queuing of the write are serialized on {@link #SETTINGS_LOCK}, so
     * concurrent changes (two admins, window and command) do not overwrite each other and the
     * newest settings are the last written.
     *
     * @param change returns the new settings, already validated; an exception it throws leaves
     *     the settings unchanged and propagates
     * @return completes when the file is written; fails with the write error, or with a
     *     {@link TimeoutException} after {@value #WRITE_TIMEOUT_SECONDS} s
     */
    public static CompletableFuture<Void> updateSettings(UnaryOperator<ModSettings> change) {
        HyPedestalsConfig current = instance.config.get();
        synchronized (SETTINGS_LOCK) {
            current.setSettings(change.apply(current.settings()));
            return instance.save();
        }
    }

    /** Absolute path of the config file. */
    public static Path configFile() {
        return instance.getDataDirectory().resolve(CONFIG_NAME + ".json").toAbsolutePath();
    }

    /**
     * Queues a write of the current settings. Deliberately not {@code Config.save()}: in 0.7.2
     * {@code Config.load()} publishes its future only after starting the async read, so when the
     * read finishes first the future is never cleared, and from then on {@code save()} returns an
     * already-completed future without writing anything (every change of that session silently
     * lost on restart). This writes the same document, the same way ({@code BsonUtil.writeDocument}:
     * temp file, {@code .bak}, atomic move), chained after the previous write so two writes never
     * share the temp file and the newest snapshot lands last.
     */
    private CompletableFuture<Void> save() {
        synchronized (SETTINGS_LOCK) {
            BsonDocument document = HyPedestalsConfig.CODEC.encode(config.get(), new ExtraInfo());
            Path file = configFile();
            lastWrite = lastWrite.handle((ignored, error) -> null)
                    .thenCompose(ignored -> BsonUtil.writeDocument(file, document));
            // copy(): the timeout must fail only the caller's view, not the chain.
            return lastWrite.copy().orTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    public static void log(String fmt, Object... args) {
        instance.getLogger().atInfo().log("%s", PREFIX + String.format(Locale.ROOT, fmt, args));
    }

    public static void warn(String fmt, Object... args) {
        instance.getLogger().atWarning().log("%s", PREFIX + String.format(Locale.ROOT, fmt, args));
    }
}
