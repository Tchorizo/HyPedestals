package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@code /hypedestals}: {@code inspect} and {@code config} always; {@code dev …} only when the server enables it.
 *
 * <p>Every node is operator-only: nobody holds them by default and {@code hytale:Admin} (ops) passes
 * through its {@code *} wildcard. A subcommand also needs its parents' nodes (the engine's
 * {@code AbstractCommand.hasPermission} walks up the chain), so granting {@code config set} to a
 * non-op means granting {@link #PERMISSION}, {@code .config} and {@code .config.set}. The nodes are
 * the ones the engine would generate from the manifest; declaring them keeps them stable.
 */
public final class HyPedestalsCommand extends AbstractAsyncCommand {

    /** System property that enables the {@code dev} subcommands (e.g. {@code -Dhypedestals.dev=true}). */
    public static final String DEV_PROPERTY = "hypedestals.dev";
    /** Node of {@code /hypedestals}; subcommands append {@code .<name>}. */
    static final String PERMISSION = "tchorizo.hypedestals.command.hypedestals";

    private final boolean dev;

    public HyPedestalsCommand(boolean dev) {
        super("hypedestals", "HyPedestals commands");
        requirePermission(PERMISSION);
        this.dev = dev;
        addSubCommand(new InspectCommand());
        addSubCommand(new ConfigCommand());
        if (dev) {
            addSubCommand(new DevCommands());
        }
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        reply(context, dev
                ? "usage: hypedestals inspect <x y z> | hypedestals config [show|set ...] | hypedestals dev place|plant|ripen|break|collect ..."
                : "usage: hypedestals inspect <x y z> | hypedestals config [show|set ...]");
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
         * Flags for loading the sections of the command's positions: ticking (so a freshly placed
         * harvester runs without a player nearby, and block entities are live rather than parked)
         * and high priority. Read-only subcommands add {@link GetChunkFlags#NO_GENERATE}.
         */
        int loadFlags() {
            return GetChunkFlags.SET_TICKING | GetChunkFlags.HIGH_PRIORITY;
        }

        /**
         * Loads the sections holding {@code positions} with {@link #loadFlags()}, then runs
         * {@code action} on the default world's thread. A section the flags could not supply (e.g.
         * never generated, with {@code NO_GENERATE}) is reported as {@code section not loaded at <x y z>}.
         * Failures are reported to the sender instead of being dropped by the command future.
         */
        CompletableFuture<Void> runAt(CommandContext context, Consumer<World> action, Vector3i... positions) {
            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                reply(context, "no default world loaded");
                return CompletableFuture.completedFuture(null);
            }
            int flags = loadFlags();
            List<CompletableFuture<Ref<ChunkStore>>> loads = new ArrayList<>(positions.length);
            for (Vector3i p : positions) {
                loads.add(world.getChunkStore().getChunkSectionReferenceAtBlockAsync(p.x, p.y, p.z, flags));
            }
            CompletableFuture<Void> result = new CompletableFuture<>();
            CompletableFuture.allOf(loads.toArray(CompletableFuture<?>[]::new)).whenComplete((ignored, loadError) -> {
                if (loadError != null) {
                    result.completeExceptionally(loadError);
                    return;
                }
                for (int i = 0; i < positions.length; i++) {
                    if (loads.get(i).join() == null) {
                        reply(context, "section not loaded at %s", format(positions[i]));
                        result.complete(null);
                        return;
                    }
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
