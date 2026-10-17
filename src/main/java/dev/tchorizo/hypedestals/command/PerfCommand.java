package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.tchorizo.hypedestals.block.PerfRegistry;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /hypedestals perf}: the Harvesters' server-wide timings since the plugin started or the
 * last {@code perf reset} ({@link PerfRegistry}), one line per tier, a total and the worst
 * Harvester; {@code perf reset} starts a new measurement. Operator-only.
 */
final class PerfCommand extends AbstractAsyncCommand {

    private static final String PERMISSION = HyPedestalsCommand.PERMISSION + ".perf";

    PerfCommand() {
        super("perf", "Show how much server time the Harvesters use");
        requirePermission(PERMISSION);
        addSubCommand(new ResetCommand());
    }

    /**
     * Ticks per second of the default world (the tick budget the report compares against), or the
     * engine's default {@link TickingThread#TPS} when no world is loaded.
     */
    private static int tps() {
        World world = Universe.get().getDefaultWorld();
        int tps = world == null ? 0 : world.getTps();
        return tps > 0 ? tps : TickingThread.TPS;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        for (String line : PerfRegistry.global().report(tps())) {
            HyPedestalsCommand.reply(context, "%s", line);
        }
        return CompletableFuture.completedFuture(null);
    }

    /** {@code perf reset}: zeroes the server totals (each Harvester's own {@code inspect} counters are kept). */
    private static final class ResetCommand extends AbstractAsyncCommand {
        ResetCommand() {
            super("reset", "Start a new HyPedestals performance measurement");
            requirePermission(PERMISSION + ".reset");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            PerfRegistry.global().reset();
            HyPedestalsCommand.reply(context, "perf reset");
            return CompletableFuture.completedFuture(null);
        }
    }
}
