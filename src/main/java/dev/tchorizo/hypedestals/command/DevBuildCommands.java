package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.tchorizo.hypedestals.block.HytaleTrees;
import dev.tchorizo.hypedestals.lumber.TreePart;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builders for the manual test world's showcase ({@code devworld.sh showcase}), dev-gated with the
 * rest of {@link DevCommands}: {@code platform} (a grass mesa with clear air over a terrain
 * footprint), {@code field} (tilled soil and crops in a rectangle, optionally ripe and fenced),
 * {@code fillcontainer}, {@code npc} (spawn creatures: the vanilla {@code /npc spawn} is
 * player-only) and {@code npcs} (list or remove them).
 */
final class DevBuildCommands {

    private static final String TILLED_SOIL = "Soil_Dirt_Tilled";
    private static final String GRASS = "Soil_Grass";
    private static final String DIRT = "Soil_Dirt";
    private static final String FINAL_STAGE_STATE = "StageFinal";
    /** Terrain probe window above the hint {@code y} of {@code platform}. */
    private static final int PROBE_ABOVE = 64;
    /** Deepest hole {@code platform} fills under the grass. */
    private static final int MAX_FILL = 48;

    private DevBuildCommands() {}

    static void register(AbstractAsyncCommand parent) {
        parent.addSubCommand(new PlatformCommand());
        parent.addSubCommand(new FieldCommand());
        parent.addSubCommand(new FillContainerCommand());
        parent.addSubCommand(new NpcCommand());
        parent.addSubCommand(new NpcsCommand());
    }

    private static int blockId(SectionReader reader, int x, int y, int z) {
        return reader.canHoldY(y) ? reader.getBlock(x, y, z) : BlockType.EMPTY_ID;
    }

    private static boolean isTree(int id) {
        return id != BlockType.EMPTY_ID && HytaleTrees.part(BlockType.getAssetMap().getAsset(id)) != TreePart.NONE;
    }

    /** Highest block at or below {@code from} that is neither air nor part of a tree, or {@code -1}. */
    private static int terrainTop(SectionReader reader, int x, int z, int from) {
        for (int y = from; y >= 0; y--) {
            int id = blockId(reader, x, y, z);
            if (id != BlockType.EMPTY_ID && !isTree(id)) {
                return y;
            }
        }
        return -1;
    }

    /**
     * {@code platform <x y z> <x2 y2 z2> [--ground Y] [--air N] [--dry true]}: a grass layer at
     * {@code Y-1} over the whole rectangle {@code x..x2, z..z2}, dirt under it down to the first solid
     * block (holes, trees and leaves included), and air from {@code Y} up {@code N} layers (trees,
     * leaves and old builds included). {@code Y} is {@code --ground} when given (rebuilding in place), else
     * one above the highest terrain block of the rectangle (trees ignored) found from the hint
     * {@code y} + 64 down. {@code --dry true} only probes. Pins every chunk column of the rectangle
     * (see {@code DevCommands#pin}). Replies {@code platform x a..b z c..d ground=Y top=min..max
     * grass=<n> filled=<n> cleared=<n> pinned=<n>}.
     */
    private static final class PlatformCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Vector3i> other =
                withRequiredArg("other", "Opposite corner x y z (only x and z count)", ArgTypes.VECTOR3I);
        private final DefaultArg<Integer> ground = withDefaultArg("ground",
                "Ground level (y of the first air layer); negative = probe the terrain", ArgTypes.INTEGER, -1, "-1");
        private final DefaultArg<Integer> air = withDefaultArg("air", "Layers of air to clear above the ground",
                ArgTypes.INTEGER, 30, "30");
        private final DefaultArg<Boolean> dry = withDefaultArg("dry", "Only probe the terrain",
                ArgTypes.BOOLEAN, false, "false");

        PlatformCommand() {
            super("platform", "Grass platform with clear air over a rectangle (probes the terrain unless --ground)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i a = position(context);
            Vector3i b = context.get(other);
            int minX = Math.min(a.x, b.x), maxX = Math.max(a.x, b.x);
            int minZ = Math.min(a.z, b.z), maxZ = Math.max(a.z, b.z);
            int fixed = context.get(ground);
            int layers = context.get(air);
            boolean probeOnly = context.get(dry);
            int topY = (fixed >= 0 ? fixed : a.y + PROBE_ABOVE) + layers;
            List<Vector3i> loads = new ArrayList<>();
            for (int sx = Math.floorDiv(minX, ChunkUtil.SIZE); sx <= Math.floorDiv(maxX, ChunkUtil.SIZE); sx++) {
                for (int sz = Math.floorDiv(minZ, ChunkUtil.SIZE); sz <= Math.floorDiv(maxZ, ChunkUtil.SIZE); sz++) {
                    for (int sy = 0; sy <= Math.floorDiv(topY, ChunkUtil.SIZE); sy++) {
                        loads.add(new Vector3i(sx * ChunkUtil.SIZE, sy * ChunkUtil.SIZE, sz * ChunkUtil.SIZE));
                    }
                }
            }
            return runAt(context, world -> {
                SectionReader reader = new SectionReader(world.getChunkStore());
                int low = Integer.MAX_VALUE;
                int high = -1;
                if (fixed < 0 || probeOnly) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            int top = terrainTop(reader, x, z, a.y + PROBE_ABOVE);
                            low = Math.min(low, top);
                            high = Math.max(high, top);
                        }
                    }
                }
                int gy = fixed >= 0 ? fixed : high + 1;
                if (probeOnly) {
                    HyPedestalsCommand.reply(context, "platform dry x %d..%d z %d..%d: terrain top=%d..%d ground=%d",
                            minX, maxX, minZ, maxZ, low, high, gy);
                    return;
                }
                int grassId = BlockType.getAssetMap().getIndex(GRASS);
                int grass = 0, filled = 0, cleared = 0;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        DevCommands.setBlock(world, new Vector3i(x, gy - 1, z), GRASS);
                        grass += blockId(reader, x, gy - 1, z) == grassId ? 1 : 0;
                        for (int y = gy - 2; y >= Math.max(0, gy - 2 - MAX_FILL); y--) {
                            int id = blockId(reader, x, y, z);
                            if (id != BlockType.EMPTY_ID && !isTree(id)) {
                                break;
                            }
                            filled += DevCommands.setBlock(world, new Vector3i(x, y, z), DIRT) ? 1 : 0;
                        }
                        for (int y = gy; y < gy + layers; y++) {
                            if (blockId(reader, x, y, z) != BlockType.EMPTY_ID) {
                                DevCommands.setBlock(world, new Vector3i(x, y, z), BlockType.EMPTY_KEY, BlockType.EMPTY);
                                cleared++;
                            }
                        }
                    }
                }
                int pinned = 0;
                for (int cx = Math.floorDiv(minX, ChunkUtil.SIZE); cx <= Math.floorDiv(maxX, ChunkUtil.SIZE); cx++) {
                    for (int cz = Math.floorDiv(minZ, ChunkUtil.SIZE); cz <= Math.floorDiv(maxZ, ChunkUtil.SIZE); cz++) {
                        for (int y : new int[] {gy - 1, gy}) {
                            pinned += DevCommands.pin(world, new Vector3i(cx * ChunkUtil.SIZE, y, cz * ChunkUtil.SIZE)) ? 1 : 0;
                        }
                    }
                }
                HyPedestalsCommand.reply(context, "platform x %d..%d z %d..%d ground=%d top=%s grass=%d/%d filled=%d cleared=%d pinned=%d",
                        minX, maxX, minZ, maxZ, gy, fixed >= 0 ? "given" : low + ".." + high, grass,
                        (maxX - minX + 1) * (maxZ - minZ + 1), filled, cleared, pinned);
            }, loads.toArray(Vector3i[]::new));
        }
    }

    /**
     * {@code field <x y z> <rx> <rz> <crop|none> [--pattern full|half|checker] [--ripe true]
     * [--omit dx:dz/dx:dz] [--fence true]}: tilled soil at {@code y-1} over {@code x±rx, z±rz}
     * and {@code crop} (stage zero, or its final stage with {@code --ripe}) on top; {@code none} = soil
     * only; {@code half} leaves the rows {@code dz > 0} as bare soil, {@code checker} every other
     * cell; {@code --omit} lists offsets left alone (the Harvester, its chest); {@code --fence} puts the
     * fence ring one block outside the rectangle. Replies {@code field <x y z> … soil=<n> planted=<n>
     * ripe=<n> fence=<n>/<ring>}.
     */
    private static final class FieldCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> rx = withRequiredArg("rx", "Half side on x", ArgTypes.INTEGER);
        private final RequiredArg<Integer> rz = withRequiredArg("rz", "Half side on z", ArgTypes.INTEGER);
        private final RequiredArg<String> crop = withRequiredArg("crop", "Crop block id or none", ArgTypes.STRING);
        private final DefaultArg<String> pattern = withDefaultArg("pattern", "full, half or checker",
                ArgTypes.STRING, "full", "full");
        private final DefaultArg<Boolean> ripe = withDefaultArg("ripe", "Ripen each planted crop",
                ArgTypes.BOOLEAN, false, "false");
        private final DefaultArg<String> omit = withDefaultArg("omit", "Offsets dx:dz/dx:dz left untouched",
                ArgTypes.STRING, "", "");
        private final DefaultArg<Boolean> fence = withDefaultArg("fence", "Fence ring outside the rectangle",
                ArgTypes.BOOLEAN, false, "false");

        FieldCommand() {
            super("field", "Tilled soil and crops over a rectangle (optionally ripe, half empty, fenced)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            int sx = context.get(rx), sz = context.get(rz);
            String cropId = context.get(crop);
            String mode = context.get(pattern).toLowerCase(Locale.ROOT);
            boolean ripen = context.get(ripe);
            boolean ring = context.get(fence);
            Set<String> skipped = new HashSet<>();
            for (String pair : context.get(omit).split("[;/]")) {
                if (!pair.isBlank()) {
                    skipped.add(pair.trim());
                }
            }
            boolean soilOnly = cropId.equalsIgnoreCase("none");
            if (!soilOnly && (BlockType.getAssetMap().getAsset(cropId) == null
                    || BlockType.getAssetMap().getAsset(cropId).getFarming() == null)) {
                HyPedestalsCommand.reply(context, "field: %s is not a crop", cropId);
                return CompletableFuture.completedFuture(null);
            }
            if (!Set.of("full", "half", "checker").contains(mode)) {
                HyPedestalsCommand.reply(context, "field: pattern must be full, half or checker");
                return CompletableFuture.completedFuture(null);
            }
            int edge = ring ? 1 : 0;
            return runAt(context, world -> {
                int soil = 0, planted = 0, ripened = 0;
                for (int dx = -sx; dx <= sx; dx++) {
                    for (int dz = -sz; dz <= sz; dz++) {
                        if (skipped.contains(dx + ":" + dz)) {
                            continue;
                        }
                        Vector3i c = new Vector3i(p.x + dx, p.y, p.z + dz);
                        boolean bare = soilOnly || (mode.equals("half") && dz > 0) || (mode.equals("checker") && ((dx + dz) & 1) != 0);
                        if (bare) {
                            soil += DevCommands.setBlock(world, new Vector3i(c.x, c.y - 1, c.z), TILLED_SOIL) ? 1 : 0;
                            continue;
                        }
                        soil++;
                        planted += DevCommands.plant(world, c, cropId) ? 1 : 0;
                        if (ripen) {
                            ripened += DevCommands.ripen(world, c).contains(FINAL_STAGE_STATE) ? 1 : 0;
                        }
                    }
                }
                String fenced = ring ? fenceRect(world, p, sx + 1, sz + 1) + "/" + (4 * (sx + sz + 2)) : "off";
                HyPedestalsCommand.reply(context, "field %s rx=%d rz=%d %s pattern=%s: soil=%d planted=%d ripe=%d omitted=%d fence=%s",
                        HyPedestalsCommand.format(p), sx, sz, cropId, mode, soil, planted, ripened, skipped.size(), fenced);
            }, new Vector3i(p.x - sx - edge, p.y, p.z - sz - edge), new Vector3i(p.x + sx + edge, p.y, p.z + sz + edge),
                    new Vector3i(p.x - sx - edge, p.y, p.z + sz + edge), new Vector3i(p.x + sx + edge, p.y, p.z - sz - edge));
        }
    }

    /**
     * Fence ring on the layer of {@code center} at {@code dx} and {@code dz} blocks from it (the
     * scene's fence for a rectangle: straight pieces turned along their side, corner pieces turned
     * so their arms follow the ring); returns how many pieces end up as fence.
     */
    private static int fenceRect(World world, Vector3i center, int dx, int dz) {
        BlockType straight = BlockType.getAssetMap().getAsset(DevCommands.SceneCommand.FENCE);
        BlockType corner = BlockType.getAssetMap().getAsset(DevCommands.SceneCommand.FENCE_CORNER);
        if (straight == null || corner == null) {
            return 0;
        }
        int alongZ = RotationTuple.index(Rotation.Ninety, Rotation.None, Rotation.None);
        SectionReader reader = new SectionReader(world.getChunkStore());
        int placed = 0;
        for (int x = -dx; x <= dx; x++) {
            for (int z = -dz; z <= dz; z++) {
                boolean xEdge = Math.abs(x) == dx;
                boolean zEdge = Math.abs(z) == dz;
                if (!xEdge && !zEdge) {
                    continue;
                }
                Vector3i f = new Vector3i(center.x + x, center.y, center.z + z);
                if (xEdge && zEdge) {
                    DevCommands.setBlock(world, f, DevCommands.SceneCommand.FENCE_CORNER, corner,
                            DevCommands.SceneCommand.cornerRotation(-Integer.signum(x), -Integer.signum(z)));
                } else {
                    DevCommands.setBlock(world, f, DevCommands.SceneCommand.FENCE, straight,
                            xEdge ? alongZ : RotationTuple.NONE_INDEX);
                }
                BlockType here = BlockType.getAssetMap().getAsset(reader.getBlock(f.x, f.y, f.z));
                placed += here != null && here.getId().contains(DevCommands.SceneCommand.FENCE) ? 1 : 0;
            }
        }
        return placed;
    }

    /**
     * {@code fillcontainer <x y z> <item> [--qty N]}: every slot of the item container at the
     * position gets {@code N} of the item (default: its max stack), replacing what is there; for a
     * "container full" test without a client. Replies {@code fillcontainer <x y z>: <item> x<N> in
     * <filled>/<capacity> slots}.
     */
    private static final class FillContainerCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> item = withRequiredArg("item", "Item id", ArgTypes.STRING);
        private final DefaultArg<Integer> qty = withDefaultArg("qty", "Quantity per slot (0 = the item's max stack)",
                ArgTypes.INTEGER, 0, "0");

        FillContainerCommand() {
            super("fillcontainer", "Fill every slot of the container at a position with one item");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            String itemId = context.get(item);
            int wanted = context.get(qty);
            return runAt(context, world -> {
                String at = HyPedestalsCommand.format(p);
                Item asset = Item.getAssetMap().getAsset(itemId);
                Store<ChunkStore> store = world.getChunkStore().getStore();
                Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, p.x, p.y, p.z);
                ItemContainerBlock block = ref == null ? null : store.getComponent(ref, ItemContainerBlock.getComponentType());
                if (asset == null || block == null) {
                    HyPedestalsCommand.reply(context, "fillcontainer: item=%b container=%b at %s", asset != null, block != null, at);
                    return;
                }
                ItemContainer container = block.getItemContainer();
                int quantity = wanted > 0 ? wanted : asset.getMaxStack();
                int filled = 0;
                for (short s = 0; s < container.getCapacity(); s++) {
                    filled += container.setItemStackForSlot(s, new ItemStack(itemId, quantity)).succeeded() ? 1 : 0;
                }
                BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
                if (info != null) {
                    info.markNeedsSaving(store);
                }
                HyPedestalsCommand.reply(context, "fillcontainer %s: %s x%d in %d/%d slots", at, itemId, quantity, filled,
                        container.getCapacity());
            }, p);
        }
    }

    /**
     * {@code npc <x y z> <role> [--count N] [--spread S]}: spawns {@code N} creatures of an NPC role
     * (e.g. {@code Tamed_Cow}) with {@code NPCPlugin.spawnNPC}, on a grid of side {@code 2S+1} around
     * the block position (feet at its bottom). The vanilla {@code /npc spawn} needs a player.
     * Replies {@code npc <role> at <x y z>: spawned=<n>/<N>}.
     */
    private static final class NpcCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> role = withRequiredArg("role", "NPC role id, e.g. Tamed_Cow", ArgTypes.STRING);
        private final DefaultArg<Integer> count = withDefaultArg("count", "How many", ArgTypes.INTEGER, 1, "1");
        private final DefaultArg<Integer> spread = withDefaultArg("spread", "Grid half side around the position",
                ArgTypes.INTEGER, 1, "1");

        NpcCommand() {
            super("npc", "Spawn creatures of an NPC role around a position");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            String roleId = context.get(role);
            int n = context.get(count);
            int side = 2 * Math.max(0, context.get(spread)) + 1;
            return runAt(context, world -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                int spawned = 0;
                String failure = "";
                for (int i = 0; i < n; i++) {
                    Vector3d at = new Vector3d(p.x + 0.5 + (i % side) - side / 2, p.y, p.z + 0.5 + ((i / side) % side) - side / 2);
                    try {
                        Pair<?, ?> made = NPCPlugin.get().spawnNPC(store, roleId, null, at, Rotation3f.IDENTITY);
                        spawned += made == null ? 0 : 1;
                    } catch (RuntimeException e) {
                        failure = " (" + e + ")";
                        break;
                    }
                }
                HyPedestalsCommand.reply(context, "npc %s at %s: spawned=%d/%d%s", roleId, HyPedestalsCommand.format(p),
                        spawned, n, failure);
            }, p);
        }
    }

    /**
     * {@code npcs <x y z> <r> [--kill true]}: NPCs within {@code r} blocks (box) of the position, by
     * role; {@code --kill true} removes them (what a rebuild of the manure farm does before spawning
     * again). Replies {@code npcs near <x y z> r=<r>: <role>=<n> … removed=<k>}.
     */
    private static final class NpcsCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> radius = withRequiredArg("r", "Half side of the box", ArgTypes.INTEGER);
        private final DefaultArg<Boolean> kill = withDefaultArg("kill", "Remove them", ArgTypes.BOOLEAN, false, "false");

        NpcsCommand() {
            super("npcs", "List (or remove) the NPCs around a position");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            int r = context.get(radius);
            boolean remove = context.get(kill);
            return runAt(context, world -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Map<String, Integer> roles = new TreeMap<>();
                AtomicInteger removed = new AtomicInteger();
                store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                        NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                        if (transform == null || npc == null) {
                            continue;
                        }
                        Vector3d at = transform.getPosition();
                        if (Math.abs(at.x - p.x) > r || Math.abs(at.z - p.z) > r || Math.abs(at.y - p.y) > r) {
                            continue;
                        }
                        roles.merge(npc.getRoleName(), 1, Integer::sum);
                        if (remove) {
                            buffer.removeEntity(chunk.getReferenceTo(i), RemoveReason.REMOVE);
                            removed.incrementAndGet();
                        }
                    }
                });
                HyPedestalsCommand.reply(context, "npcs near %s r=%d: %s removed=%d", HyPedestalsCommand.format(p), r,
                        roles.isEmpty() ? "none" : roles.toString(), removed.get());
            }, p);
        }
    }
}
