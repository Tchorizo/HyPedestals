package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /hypedestals config}: shows the settings; {@code config show} and
 * {@code config set <Tier|global> <field> <value>} work from chat or the console. Uses the
 * engine's default command permission (operators).
 */
final class ConfigCommand extends AbstractAsyncCommand {

    private static final String USAGE = "usage: hypedestals config | config show | config set <Copper|Iron|Thorium|Cobalt> <Radius|IntervalSeconds> <value>"
            + " | config set global <CollectAnyItem|CollectMargin> <value>";

    ConfigCommand() {
        super("config", "Show or change the HyPedestals settings");
        addSubCommand(new ShowCommand());
        addSubCommand(new SetCommand());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        ShowCommand.show(context);
        HyPedestalsCommand.reply(context, "%s", USAGE);
        return CompletableFuture.completedFuture(null);
    }

    /** {@code config show}: {@code config <values> file=<path>}. */
    private static final class ShowCommand extends AbstractAsyncCommand {
        ShowCommand() {
            super("show", "Show the HyPedestals settings");
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
                withRequiredArg("field", "Radius or IntervalSeconds (tiers); CollectAnyItem or CollectMargin (global)", ArgTypes.STRING);
        private final RequiredArg<String> value = withRequiredArg("value", "New value", ArgTypes.STRING);

        SetCommand() {
            super("set", "Change one HyPedestals setting");
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
