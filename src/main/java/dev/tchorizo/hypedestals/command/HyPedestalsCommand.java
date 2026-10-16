package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import org.joml.Vector3i;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** {@code /hypedestals}: {@code inspect} always; {@code dev …} only when the server enables it. */
public final class HyPedestalsCommand extends AbstractAsyncCommand {

    /** System property that enables the {@code dev} subcommands (e.g. {@code -Dhypedestals.dev=true}). */
    public static final String DEV_PROPERTY = "hypedestals.dev";

    private final boolean dev;

    public HyPedestalsCommand(boolean dev) {
        super("hypedestals", "HyPedestals commands");
        this.dev = dev;
        addSubCommand(new InspectCommand());
        if (dev) {
            addSubCommand(new DevCommands());
        }
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        reply(context, dev
                ? "usage: hypedestals inspect <x y z> | hypedestals dev place|plant|ripen|break|collect ..."
                : "usage: hypedestals inspect <x y z>");
        return CompletableFuture.completedFuture(null);
    }

    static void reply(CommandContext context, String fmt, Object... args) {
        context.sendMessage(Message.raw(String.format(Locale.ROOT, fmt, args)));
    }

    static String format(Vector3i p) {
        return p.x + " " + p.y + " " + p.z;
    }

    /** A subcommand whose first argument is a block position. */
    abstract static class PositionCommand extends AbstractAsyncCommand {
        private final RequiredArg<Vector3i> position =
                withRequiredArg("position", "Block coordinates x y z", ArgTypes.VECTOR3I);

        PositionCommand(String name, String description) {
            super(name, description);
        }

        Vector3i position(CommandContext context) {
            return context.get(position);
        }

        /**
         * Loads the sections holding {@code positions} (ticking, so a freshly placed harvester runs
         * without a player nearby), then runs {@code action} on the default world's thread. Failures
         * are reported to the sender instead of being dropped by the command future.
         */
        CompletableFuture<Void> runAt(CommandContext context, Consumer<World> action, Vector3i... positions) {
            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                reply(context, "no default world loaded");
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<?>[] loads = new CompletableFuture<?>[positions.length];
            for (int i = 0; i < positions.length; i++) {
                Vector3i p = positions[i];
                loads[i] = world.getChunkStore().getChunkSectionReferenceAtBlockAsync(
                        p.x, p.y, p.z, GetChunkFlags.SET_TICKING | GetChunkFlags.HIGH_PRIORITY);
            }
            CompletableFuture<Void> result = new CompletableFuture<>();
            CompletableFuture.allOf(loads).whenComplete((ignored, loadError) -> {
                if (loadError != null) {
                    result.completeExceptionally(loadError);
                    return;
                }
                try {
                    world.execute(() -> {
                        try {
                            action.accept(world);
                            result.complete(null);
                        } catch (Throwable e) {
                            result.completeExceptionally(e);
                        }
                    });
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
            return result.exceptionally(error -> {
                reply(context, "%s failed: %s", getName(), error);
                return null;
            });
        }
    }
}
