package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.block.HytaleIds;
import dev.tchorizo.hypedestals.block.HytaleTreeProbe;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code find <x y z> <r> <blockPrefix> [--down D] [--up U]}: loads the sections of the box
 * {@code x±r, y-down..y+up, z±r} (without generating terrain) and lists the blocks whose base id
 * starts with {@code blockPrefix} (e.g. {@code HyPedestals_Lumberjack}), as
 * {@code find … matches=<n> blocks=[x y z id, …]}. A Lumberjack placed by a player in a world
 * copy is found this way before the first inspect (a section nobody loaded reads as air).
 */
final class DevFindCommand extends HyPedestalsCommand.PositionCommand {

    private static final int LIST_LIMIT = 60;
    private static final int SECTION = 32;

    private final RequiredArg<Integer> radius = withRequiredArg("r", "Half side on x and z", ArgTypes.INTEGER);
    private final RequiredArg<String> prefix = withRequiredArg("prefix", "Base block id prefix", ArgTypes.STRING);
    private final DefaultArg<Integer> down = withDefaultArg("down", "Layers below y", ArgTypes.INTEGER, 40, "40");
    private final DefaultArg<Integer> up = withDefaultArg("up", "Layers above y", ArgTypes.INTEGER, 40, "40");

    DevFindCommand() {
        super("find", "List blocks by id prefix in a box, loading its sections first");
    }

    static void register(AbstractAsyncCommand parent) {
        parent.addSubCommand(new DevFindCommand());
        parent.addSubCommand(new Column());
        parent.addSubCommand(new TreeProbe());
        parent.addSubCommand(new Survey());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        Vector3i p = position(context);
        int r = context.get(radius), d = context.get(down), u = context.get(up);
        String wanted = context.get(prefix);
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            HyPedestalsCommand.reply(context, "no default world loaded");
            return CompletableFuture.completedFuture(null);
        }
        int flags = GetChunkFlags.SET_TICKING | GetChunkFlags.HIGH_PRIORITY | GetChunkFlags.NO_GENERATE;
        int y0 = p.y - d, y1 = p.y + u;
        List<CompletableFuture<Ref<ChunkStore>>> loads = new ArrayList<>();
        for (int x = Math.floorDiv(p.x - r, SECTION) * SECTION; x <= p.x + r; x += SECTION) {
            for (int z = Math.floorDiv(p.z - r, SECTION) * SECTION; z <= p.z + r; z += SECTION) {
                for (int y = Math.floorDiv(y0, SECTION) * SECTION; y <= y1; y += SECTION) {
                    loads.add(world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x, y, z, flags));
                }
            }
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.allOf(loads.toArray(CompletableFuture<?>[]::new)).whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            int notLoaded = (int) loads.stream().filter(f -> f.join() == null).count();
            world.execute(() -> {
                try {
                    scan(context, world, p, r, y0, y1, wanted, loads.size(), notLoaded);
                    result.complete(null);
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
        });
        return result.exceptionally(e -> {
            HyPedestalsCommand.reply(context, "find failed: %s", e);
            return null;
        });
    }

    private static void scan(CommandContext context, World world, Vector3i p, int r, int y0, int y1, String wanted,
            int sections, int notLoaded) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        Map<Integer, String> matches = new HashMap<>();
        Map<Integer, Boolean> known = new HashMap<>();
        List<String> found = new ArrayList<>();
        int count = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = p.x - r; x <= p.x + r; x++) {
                for (int z = p.z - r; z <= p.z + r; z++) {
                    if (!reader.hasStorageInMemory(x, y, z)) {
                        continue;
                    }
                    int id = reader.getBlock(x, y, z);
                    if (id == BlockType.EMPTY_ID) {
                        continue;
                    }
                    Boolean hit = known.get(id);
                    if (hit == null) {
                        BlockType type = BlockType.getAssetMap().getAsset(id);
                        String base = type == null || type.getId() == null ? null : HytaleIds.baseBlockId(type.getId());
                        hit = base != null && base.startsWith(wanted);
                        known.put(id, hit);
                        if (hit) {
                            matches.put(id, base);
                        }
                    }
                    if (hit) {
                        count++;
                        if (found.size() < LIST_LIMIT) {
                            found.add(x + " " + y + " " + z + " " + matches.get(id));
                        }
                    }
                }
            }
        }
        HyPedestalsCommand.reply(context, "find %s r=%d y%d..%d %s: sections=%d notLoaded=%d matches=%d blocks=%s",
                HyPedestalsCommand.format(p), r, y0, y1, wanted, sections, notLoaded, count, found);
    }

    /**
     * {@code column <x y z> <up> [--down D]}: the blocks of the column {@code x, z} from
     * {@code y+up} down to {@code y-down}, as runs {@code y0..y1 id} (top first, {@code -} = air),
     * with the filler byte of non-zero fillers.
     */
    private static final class Column extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> above = withRequiredArg("up", "Layers above y", ArgTypes.INTEGER);
        private final DefaultArg<Integer> below = withDefaultArg("down", "Layers below y", ArgTypes.INTEGER, 8, "8");

        Column() {
            super("column", "Run-length list of the blocks of a column");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            int u = context.get(above), d = context.get(below);
            return runAt(context, world -> {
                SectionReader reader = new SectionReader(world.getChunkStore());
                List<String> runs = new ArrayList<>();
                String current = null;
                int top = 0;
                for (int y = p.y + u; y >= p.y - d; y--) {
                    int id = reader.getBlock(p.x, y, p.z);
                    BlockType type = id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
                    String name = type == null ? "-" : type.getId();
                    if (!name.equals(current)) {
                        if (current != null) {
                            runs.add((y + 1) + ".." + top + " " + current);
                        }
                        current = name;
                        top = y;
                    }
                }
                runs.add((p.y - d) + ".." + top + " " + current);
                HyPedestalsCommand.reply(context, "column %d %d y%d..%d: %s", p.x, p.z, p.y - d, p.y + u, runs);
            }, p);
        }
    }

    /** {@code tree <x y z> [--max N]}: what a Lumberjack would make of the tree whose wood block is at the position. */
    private static final class TreeProbe extends HyPedestalsCommand.PositionCommand {
        private final DefaultArg<Integer> max = withDefaultArg("max", "Block limit", ArgTypes.INTEGER, 2048, "2048");

        TreeProbe() {
            super("tree", "Scan the tree at a wood block like the Lumberjack, without cutting");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            int limit = context.get(max);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s",
                    HytaleTreeProbe.report(world, new BlockPos(p.x, p.y, p.z), limit)), p);
        }
    }

    /** {@code survey <x y z> <r> [--down D] [--up U]}: a dry Lumberjack scan of every tree in the box (loads its sections first). */
    private static final class Survey extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> radius = withRequiredArg("r", "Half side on x and z", ArgTypes.INTEGER);
        private final DefaultArg<Integer> down = withDefaultArg("down", "Layers below y", ArgTypes.INTEGER, 10, "10");
        private final DefaultArg<Integer> up = withDefaultArg("up", "Layers above y", ArgTypes.INTEGER, 40, "40");

        Survey() {
            super("survey", "List every tree in a box with the status a Lumberjack would give it");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            int r = context.get(radius), d = context.get(down), u = context.get(up);
            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                HyPedestalsCommand.reply(context, "no default world loaded");
                return CompletableFuture.completedFuture(null);
            }
            int flags = GetChunkFlags.SET_TICKING | GetChunkFlags.HIGH_PRIORITY | GetChunkFlags.NO_GENERATE;
            List<CompletableFuture<Ref<ChunkStore>>> loads = new ArrayList<>();
            for (int x = Math.floorDiv(p.x - r, SECTION) * SECTION; x <= p.x + r; x += SECTION) {
                for (int z = Math.floorDiv(p.z - r, SECTION) * SECTION; z <= p.z + r; z += SECTION) {
                    for (int y = Math.floorDiv(p.y - d, SECTION) * SECTION; y <= p.y + u; y += SECTION) {
                        loads.add(world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x, y, z, flags));
                    }
                }
            }
            CompletableFuture<Void> result = new CompletableFuture<>();
            CompletableFuture.allOf(loads.toArray(CompletableFuture<?>[]::new)).whenComplete((ignored, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                    return;
                }
                world.execute(() -> {
                    try {
                        for (String line : HytaleTreeProbe.survey(world, new BlockPos(p.x, p.y, p.z), r, p.y - d, p.y + u, 2048)) {
                            HyPedestalsCommand.reply(context, "%s", line);
                        }
                        result.complete(null);
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
            });
            return result.exceptionally(e -> {
                HyPedestalsCommand.reply(context, "survey failed: %s", e);
                return null;
            });
        }
    }
}
