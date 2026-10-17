package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.config.ConfigPage;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /hypedestals config}: opens the settings window for a player; {@code config show} and
 * {@code config set <Tier|global> <field> <value>} do the same from chat or the console. All three
 * are operator-only (nodes under {@link HyPedestalsCommand#PERMISSION}).
 */
final class ConfigCommand extends AbstractAsyncCommand {

    private static final String PERMISSION = HyPedestalsCommand.PERMISSION + ".config";
    private static final String USAGE = "usage: hypedestals config | config show"
            + " | config set <Copper|Iron|Thorium|Cobalt> <Radius|IntervalSeconds|WateringSeconds> <value>"
            + " | config set global <CollectAnyItem|CollectMargin|SoilSweepSeconds|ExportToAdjacent|HarvestParticles"
            + "|PostHarvestCollectSeconds|MaxHarvestsPerCycle|UpgradeParticlesSeconds> <value>";

    ConfigCommand() {
        super("config", "Open the HyPedestals settings window");
        requirePermission(PERMISSION);
        addSubCommand(new ShowCommand());
        addSubCommand(new SetCommand());
    }

    /** Players get the window; the console gets the current values and the usage. */
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        if (!(context.sender() instanceof PlayerRef playerRef)) {
            ShowCommand.show(context);
            HyPedestalsCommand.reply(context, "%s", USAGE);
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            HyPedestalsCommand.reply(context, "config: you are not in a world");
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        // Pages are opened on the player's world thread; the player may have left (or changed
        // world) before the task runs.
        return CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new ConfigPage(playerRef));
            }
        }, world).exceptionally(error -> {
            HyPedestalsCommand.reply(context, "config failed: %s", error);
            return null;
        });
    }

    /** {@code config show}: {@code config <values> file=<path>}. */
    private static final class ShowCommand extends AbstractAsyncCommand {
        ShowCommand() {
            super("show", "Show the HyPedestals settings");
            requirePermission(PERMISSION + ".show");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            show(context);
            return CompletableFuture.completedFuture(null);
        }

        static void show(CommandContext context) {
            HyPedestalsCommand.reply(context, "config %s file=%s",
                    HyPedestalsPlugin.settings().describe(), HyPedestalsPlugin.configFile());
        }
    }

    /**
     * {@code config set <target> <field> <value>}: validates, applies right away and writes the
     * file; replies {@code config set <target> <field>=<value> saved} or {@code config set failed: <why>}.
     */
    private static final class SetCommand extends AbstractAsyncCommand {
        private final RequiredArg<String> target =
                withRequiredArg("target", "Copper, Iron, Thorium, Cobalt or global", ArgTypes.STRING);
        private final RequiredArg<String> field =
                withRequiredArg("field", "Radius, IntervalSeconds or WateringSeconds (tiers); CollectAnyItem, CollectMargin,"
                        + " SoilSweepSeconds, ExportToAdjacent, HarvestParticles, PostHarvestCollectSeconds,"
                        + " MaxHarvestsPerCycle or UpgradeParticlesSeconds (global)",
                        ArgTypes.STRING);
        private final RequiredArg<String> value = withRequiredArg("value", "New value", ArgTypes.STRING);

        SetCommand() {
            super("set", "Change one HyPedestals setting");
            requirePermission(PERMISSION + ".set");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            String t = context.get(target), f = context.get(field), v = context.get(value);
            CompletableFuture<Void> saved;
            try {
                saved = HyPedestalsPlugin.updateSettings(current -> current.set(t, f, v));
            } catch (IllegalArgumentException e) {
                HyPedestalsCommand.reply(context, "config set failed: %s", e.getMessage());
                return CompletableFuture.completedFuture(null);
            }
            return saved.handle((ignored, error) -> {
                if (error != null) {
                    HyPedestalsCommand.reply(context, "config set %s %s=%s applied, but writing %s failed: %s",
                            t, f, v, HyPedestalsPlugin.configFile(), error);
                } else {
                    HyPedestalsCommand.reply(context, "config set %s %s=%s saved", t, f, v);
                }
                return null;
            });
        }
    }
}
