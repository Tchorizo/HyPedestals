package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.block.HytaleIds;
import dev.tchorizo.hypedestals.block.HytalePlantables;
import dev.tchorizo.hypedestals.block.HytaleSoilWorld;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import org.joml.Vector3i;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /hypedestals dev planter …}: diagnostics for the Planter upgrade (spike of 2026-10-21,
 * planter spec §0), dev-gated with the rest of {@link DevCommands}. {@code seeds} and
 * {@code resolve} show what {@link HytalePlantables} reads from the assets; {@code plant} plants
 * one item by {@code setBlock} like the upgrade; {@code soil}/{@code decay} show and force the
 * tilled soil's decay tick (where the vanilla tick sets {@code planted}); {@code stage} pushes a
 * sapling into a {@code Prefab} stage through the vanilla tick (which {@code dev grow} skips);
 * {@code snap}/{@code diff} record and compare a box of blocks around a growing tree.
 */
final class DevPlanterCommands extends AbstractAsyncCommand {

    private static final Map<String, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    DevPlanterCommands() {
        super("planter", "Planter upgrade diagnostics");
        addSubCommand(new SeedsCommand());
        addSubCommand(new ResolveCommand());
        addSubCommand(new PlantCommand());
        addSubCommand(new SoilCommand());
        addSubCommand(new DecayCommand());
        addSubCommand(new StageCommand());
        addSubCommand(new SnapCommand());
        addSubCommand(new DiffCommand());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        HyPedestalsCommand.reply(context, "usage: dev planter seeds | resolve <item> <soil> | plant <x y z> <item>"
                + " | soil <x y z> | decay <x y z> <s> | stage <x y z> <k> | snap <x y z> <name> <r> <down> <up> | diff <name>");
        return CompletableFuture.completedFuture(null);
    }

    /** The block an item plants on a soil block, and the steps of the walk. */
    record Resolution(String blockId, List<String> chain) {}

    /** {@link HytalePlantables}' walk with its steps, or the item's sapling block when it is one. */
    static Resolution resolve(Item item, BlockType soil) {
        List<String> chain = new ArrayList<>();
        String sapling = HytalePlantables.saplingBlock(item.getId());
        if (sapling != null) {
            boolean fits = HytalePlantables.isSaplingSoil(soil) && !HytalePlantables.isFarmland(soil);
            chain.add("sapling (block item) -> " + sapling + (fits ? "" : " (not on this soil)"));
            return new Resolution(fits ? sapling : null, chain);
        }
        String block = HytalePlantables.resolve(item, soil, chain);
        return new Resolution(HytalePlantables.seedBlockOn(item.getId(), soil) == null ? null : block, chain);
    }

    private static String category(String block) {
        if (block == null) {
            return "none";
        }
        BlockType type = BlockType.getAssetMap().getAsset(block);
        if (type == null || type.getFarming() == null) {
            return "no-farming";
        }
        boolean prefab = false;
        boolean spread = false;
        for (FarmingStageData[] stages : type.getFarming().getStages().values()) {
            for (FarmingStageData s : stages) {
                String n = s.getClass().getSimpleName();
                prefab |= n.startsWith("Prefab");
                spread |= n.startsWith("Spread");
            }
        }
        if (prefab) {
            return "tree";
        }
        if (spread) {
            return "spread";
        }
        return type.getFarming().getStages().containsKey("Harvested") ? "regrows" : "annual";
    }

    // ---------------------------------------------------------------- helpers

    private static Instant now(World world) {
        return HytaleSoilWorld.gameTime(world);
    }

    private static BlockType blockAt(World world, int x, int y, int z) {
        return BlockType.getAssetMap().getAsset(new SectionReader(world.getChunkStore()).getBlock(x, y, z));
    }

    private static String soilLine(World world, Vector3i p) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();
        BlockType type = blockAt(world, p.x, p.y, p.z);
        TilledSoilBlock soil = new HytaleSoilWorld(world, store).component(new BlockPos(p.x, p.y, p.z));
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x, p.y, p.z);
        BlockSection blocks = section == null ? null : store.getComponent(section, BlockSection.getComponentType());
        Instant now = now(world);
        BlockType above = blockAt(world, p.x, p.y + 1, p.z);
        String base = String.format(Locale.ROOT, "soil %s: %s above=%s ticking=%s", HyPedestalsCommand.format(p),
                type == null ? "null" : type.getId(), above == null ? "null" : above.getId(),
                blocks == null ? "?" : blocks.isTicking(p.x, p.y, p.z));
        if (soil == null) {
            return base + " (no TilledSoilBlock)";
        }
        Instant decay = soil.getDecayTime();
        return base + String.format(Locale.ROOT, " planted=%b fertilized=%b wateredUntil=%s decayTime=%s decayIn=%s now=%s",
                soil.isPlanted(), soil.isFertilized(), soil.getWateredUntil(), decay,
                decay == null || now == null ? "-" : Duration.between(now, decay).toSeconds() + "s", now);
    }

    private abstract static class WorldCommand extends AbstractAsyncCommand {
        WorldCommand(String name, String description) {
            super(name, description);
        }

        CompletableFuture<Void> onWorld(CommandContext context, java.util.function.Consumer<World> action) {
            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                HyPedestalsCommand.reply(context, "no default world");
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> done = new CompletableFuture<>();
            world.execute(() -> {
                try {
                    action.accept(world);
                } catch (Throwable e) {
                    HyPedestalsCommand.reply(context, "%s failed: %s", getName(), e);
                }
                done.complete(null);
            });
            return done;
        }
    }

    // ---------------------------------------------------------------- commands

    /** {@code seeds}: every item asset whose Secondary resolves to a block, on tilled soil, grass and dirt. */
    private static final class SeedsCommand extends WorldCommand {
        SeedsCommand() {
            super("seeds", "Resolve every item's seed block on Soil_Dirt_Tilled, Soil_Grass, Soil_Dirt (and Soil_Dirt_Essence if loaded)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            return onWorld(context, world -> {
                List<BlockType> soils = new ArrayList<>();
                for (String id : List.of("Soil_Dirt_Tilled", "Soil_Grass", "Soil_Dirt", "Soil_Dirt_Essence", "Soil_Dirt_Crystal")) {
                    BlockType t = BlockType.getAssetMap().getAsset(id);
                    if (t != null) {
                        soils.add(t);
                    }
                }
                Map<String, Integer> counts = new TreeMap<>();
                int total = 0;
                for (Item item : Item.getAssetMap().getAssetMap().values()) {
                    if (!HytalePlantables.isPlantable(item.getId())) {
                        continue;
                    }
                    StringBuilder line = new StringBuilder("seed ").append(item.getId());
                    boolean any = false;
                    String cat = null;
                    for (BlockType soil : soils) {
                        Resolution r = resolve(item, soil);
                        line.append(" | ").append(soil.getId()).append("->").append(r.blockId() == null ? "-" : r.blockId());
                        if (r.blockId() != null) {
                            any = true;
                            cat = cat == null ? category(r.blockId()) : cat;
                        }
                    }
                    if (any) {
                        total++;
                        counts.merge(cat, 1, Integer::sum);
                        HyPedestalsCommand.reply(context, "%s [%s]", line, cat);
                    }
                }
                HyPedestalsCommand.reply(context, "seeds total=%d by category=%s", total, counts);
            });
        }
    }

    /** {@code resolve <item> <soil>}: the walk with every step. */
    private static final class ResolveCommand extends WorldCommand {
        private final RequiredArg<String> item = withRequiredArg("item", "Item id", ArgTypes.STRING);
        private final RequiredArg<String> soil = withRequiredArg("soil", "Soil block id", ArgTypes.STRING);

        ResolveCommand() {
            super("resolve", "Show how an item's Secondary resolves on a soil block");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            String itemId = context.get(item);
            String soilId = context.get(soil);
            return onWorld(context, world -> {
                Item i = Item.getAssetMap().getAsset(itemId);
                BlockType s = BlockType.getAssetMap().getAsset(soilId);
                if (i == null || s == null) {
                    HyPedestalsCommand.reply(context, "resolve: item=%b soil=%b", i != null, s != null);
                    return;
                }
                Resolution r = resolve(i, s);
                HyPedestalsCommand.reply(context, "resolve %s on %s -> %s (%s) vars=%s chain=%s", itemId, soilId,
                        r.blockId(), category(r.blockId()), i.getInteractionVars(), String.join(" ; ", r.chain()));
            });
        }
    }

    /** {@code plant <x y z> <item>}: resolves against the block below and {@code setBlock}s the result. */
    private static final class PlantCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> item = withRequiredArg("item", "Seed or sapling item id", ArgTypes.STRING);

        PlantCommand() {
            super("plant", "Plant an item by resolving its seed block on the soil below and setBlock");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            Vector3i below = new Vector3i(pos.x, pos.y - 1, pos.z);
            String itemId = context.get(item);
            return runAt(context, world -> {
                Item i = Item.getAssetMap().getAsset(itemId);
                BlockType soil = blockAt(world, below.x, below.y, below.z);
                BlockType here = blockAt(world, pos.x, pos.y, pos.z);
                if (i == null || soil == null) {
                    HyPedestalsCommand.reply(context, "plant: item=%b soil=%s", i != null, soil);
                    return;
                }
                if (here != null && here.getId() != null && !"Empty".equals(here.getId()) && here.getMaterial()
                        != com.hypixel.hytale.protocol.BlockMaterial.Empty) {
                    HyPedestalsCommand.reply(context, "plant: %s is occupied by %s", HyPedestalsCommand.format(pos), here.getId());
                    return;
                }
                Resolution r = resolve(i, soil);
                if (r.blockId() == null) {
                    HyPedestalsCommand.reply(context, "plant: %s does not resolve on %s (%s)", itemId, soil.getId(), String.join(" ; ", r.chain()));
                    return;
                }
                BlockType type = BlockType.getAssetMap().getAsset(r.blockId());
                ChunkStore chunkStore = world.getChunkStore();
                Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
                boolean set = section != null && BlockOperations.setBlock(chunkStore, section, pos.x, pos.y, pos.z,
                        BlockType.getAssetMap().getIndex(r.blockId()), type, RotationTuple.NONE_INDEX, 0, SetBlockSettings.NONE);
                Ref<ChunkStore> ref = BlockModule.getBlockEntity(chunkStore.getStore(), section, pos.x, pos.y, pos.z);
                FarmingBlock farm = ref == null ? null : chunkStore.getStore().getComponent(ref, FarmingBlock.getComponentType());
                HyPedestalsCommand.reply(context, "plant %s at %s on %s -> %s set=%b farmingBlock=%b category=%s | %s",
                        itemId, HyPedestalsCommand.format(pos), soil.getId(), r.blockId(), set, farm != null,
                        category(r.blockId()), soilLine(world, below));
            }, pos, below);
        }
    }

    /** {@code soil <x y z>}: TilledSoilBlock incl. planted and decayTime. */
    private static final class SoilCommand extends HyPedestalsCommand.PositionCommand {
        SoilCommand() {
            super("soil", "Show tilled soil state incl. planted and decay time");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", soilLine(world, pos)), pos);
        }
    }

    /** {@code decay <x y z> <s>}: moves the soil's decay time to now+s and schedules its tick then. */
    private static final class DecayCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> seconds = withRequiredArg("seconds", "Game seconds from now", ArgTypes.INTEGER);

        DecayCommand() {
            super("decay", "Set the tilled soil decay time to now + game seconds and schedule a tick");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            int s = context.get(seconds);
            return runAt(context, world -> {
                ChunkStore chunkStore = world.getChunkStore();
                Store<ChunkStore> store = chunkStore.getStore();
                TilledSoilBlock soil = new HytaleSoilWorld(world, store).component(new BlockPos(pos.x, pos.y, pos.z));
                Instant now = now(world);
                if (soil == null || now == null) {
                    HyPedestalsCommand.reply(context, "decay: no soil or time at %s", HyPedestalsCommand.format(pos));
                    return;
                }
                Instant at = now.plusSeconds(s);
                soil.setDecayTime(at);
                Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
                BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
                blocks.setTicking(pos.x, pos.y, pos.z, true);
                blocks.scheduleTick(ChunkUtil.indexBlock(pos.x, pos.y, pos.z), at);
                Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
                BlockModule.BlockStateInfo info = ref == null ? null : store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
                if (info != null) {
                    info.markNeedsSaving(store);
                }
                HyPedestalsCommand.reply(context, "decay: %s", soilLine(world, pos));
            }, pos);
        }
    }

    /**
     * {@code stage <x y z> <k>}: puts the farming block just before stage {@code k} and lets the
     * vanilla tick cross into it (growth progress k - 0.001, last tick one game hour ago, block
     * ticking with a tick scheduled now). Works for Prefab stages the {@code dev grow} loop skips.
     */
    private static final class StageCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> stage = withRequiredArg("stage", "Target stage index", ArgTypes.INTEGER);
        private final DefaultArg<Integer> back = withDefaultArg("back", "Game seconds to move lastTickGameTime back",
                ArgTypes.INTEGER, 3600, "3600");

        StageCommand() {
            super("stage", "Push a farming block to just before a stage and let the vanilla tick apply it");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            int k = context.get(stage);
            int b = context.get(back);
            return runAt(context, world -> {
                ChunkStore chunkStore = world.getChunkStore();
                Store<ChunkStore> store = chunkStore.getStore();
                Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
                Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
                FarmingBlock farm = ref == null ? null : store.getComponent(ref, FarmingBlock.getComponentType());
                BlockType here = blockAt(world, pos.x, pos.y, pos.z);
                if (farm == null) {
                    HyPedestalsCommand.reply(context, "stage: no FarmingBlock at %s (%s)", HyPedestalsCommand.format(pos),
                            here == null ? "null" : here.getId());
                    return;
                }
                BlockType farmType = here == null ? null : BlockType.getAssetMap().getAsset(HytaleIds.baseBlockId(here.getId()));
                if ((farmType == null || farmType.getFarming() == null) && farm.getPreviousBlockType() != null) {
                    farmType = BlockType.getAssetMap().getAsset(farm.getPreviousBlockType());
                }
                String set = farm.getCurrentStageSet() == null && farmType != null && farmType.getFarming() != null
                        ? farmType.getFarming().getStartingStageSet() : farm.getCurrentStageSet();
                FarmingStageData[] stages = farmType == null || farmType.getFarming() == null ? null
                        : farmType.getFarming().getStages().get(set);
                String before = String.format(Locale.ROOT, "block=%s progress=%.3f set=%s previous=%s generation=%d stages=%d",
                        here == null ? "null" : here.getId(), farm.getGrowthProgress(), set, farm.getPreviousBlockType(),
                        farm.getGeneration(), stages == null ? -1 : stages.length);
                Instant now = now(world);
                farm.setGrowthProgress(k - 0.001f);
                if (now != null) {
                    farm.setLastTickGameTime(now.minusSeconds(b));
                }
                BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
                blocks.setTicking(pos.x, pos.y, pos.z, true);
                if (now != null) {
                    blocks.scheduleTick(ChunkUtil.indexBlock(pos.x, pos.y, pos.z), now);
                }
                BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
                if (info != null) {
                    info.markNeedsSaving(store);
                }
                HyPedestalsCommand.reply(context, "stage %s -> %d: before %s; stage k=%s", HyPedestalsCommand.format(pos), k, before,
                        stages == null || k < 0 || k >= stages.length ? "?" : stages[k].getClass().getSimpleName());
            }, pos);
        }
    }

    /** A box of block ids around an origin. */
    private record Snapshot(Vector3i origin, int r, int down, int up, int[] ids) {
        int index(int dx, int dy, int dz) {
            int side = 2 * r + 1;
            return ((dy + down) * side + (dz + r)) * side + (dx + r);
        }
    }

    /** {@code snap <name> <x y z> <r> <down> <up>}: records block ids in the box. */
    private static final class SnapCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> name = withRequiredArg("name", "Snapshot name", ArgTypes.STRING);
        private final RequiredArg<Integer> radius = withRequiredArg("radius", "Half side on x/z", ArgTypes.INTEGER);
        private final RequiredArg<Integer> down = withRequiredArg("down", "Layers below", ArgTypes.INTEGER);
        private final RequiredArg<Integer> up = withRequiredArg("up", "Layers above", ArgTypes.INTEGER);

        SnapCommand() {
            super("snap", "Record block ids in a box for a later diff");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            String n = context.get(name);
            int r = context.get(radius);
            int d = context.get(down);
            int u = context.get(up);
            return runAt(context, world -> {
                Snapshot snap = read(world, new Vector3i(pos), r, d, u);
                SNAPSHOTS.put(n, snap);
                int nonAir = 0;
                for (int id : snap.ids()) {
                    nonAir += id == 0 ? 0 : 1;
                }
                HyPedestalsCommand.reply(context, "snap %s at %s r=%d y-%d..y+%d: %d blocks, %d non-air", n,
                        HyPedestalsCommand.format(pos), r, d, u, snap.ids().length, nonAir);
            }, pos);
        }

        static Snapshot read(World world, Vector3i o, int r, int d, int u) {
            SectionReader reader = new SectionReader(world.getChunkStore());
            int side = 2 * r + 1;
            int[] ids = new int[side * side * (d + u + 1)];
            Snapshot snap = new Snapshot(o, r, d, u, ids);
            for (int dy = -d; dy <= u; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        ids[snap.index(dx, dy, dz)] = reader.getBlock(o.x + dx, o.y + dy, o.z + dz);
                    }
                }
            }
            return snap;
        }
    }

    /** {@code diff <name>}: what changed in the snapshot's box since it was taken. */
    private static final class DiffCommand extends WorldCommand {
        private final RequiredArg<String> name = withRequiredArg("name", "Snapshot name", ArgTypes.STRING);

        DiffCommand() {
            super("diff", "Compare a box with its snapshot: changes, footprint, replaced non-air blocks");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            String n = context.get(name);
            return onWorld(context, world -> {
                Snapshot old = SNAPSHOTS.get(n);
                if (old == null) {
                    HyPedestalsCommand.reply(context, "diff: no snapshot %s", n);
                    return;
                }
                Snapshot now = SnapCommand.read(world, old.origin(), old.r(), old.down(), old.up());
                Map<String, Integer> transitions = new TreeMap<>();
                List<String> replaced = new ArrayList<>();
                int changed = 0;
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (int dy = -old.down(); dy <= old.up(); dy++) {
                    for (int dz = -old.r(); dz <= old.r(); dz++) {
                        for (int dx = -old.r(); dx <= old.r(); dx++) {
                            int i = old.index(dx, dy, dz);
                            int a = old.ids()[i];
                            int b = now.ids()[i];
                            if (a == b) {
                                continue;
                            }
                            changed++;
                            String from = name(a);
                            String to = name(b);
                            transitions.merge(from + "->" + to, 1, Integer::sum);
                            if (a != 0) {
                                replaced.add(String.format(Locale.ROOT, "(%+d,%+d,%+d) %s->%s", dx, dy, dz, from, to));
                            }
                            minX = Math.min(minX, dx);
                            maxX = Math.max(maxX, dx);
                            minY = Math.min(minY, dy);
                            maxY = Math.max(maxY, dy);
                            minZ = Math.min(minZ, dz);
                            maxZ = Math.max(maxZ, dz);
                        }
                    }
                }
                HyPedestalsCommand.reply(context, "diff %s: changed=%d box dx[%d,%d] dy[%d,%d] dz[%d,%d]", n, changed,
                        minX, maxX, minY, maxY, minZ, maxZ);
                HyPedestalsCommand.reply(context, "diff %s transitions: %s", n, transitions);
                HyPedestalsCommand.reply(context, "diff %s replaced non-air (%d): %s", n, replaced.size(),
                        replaced.size() > 60 ? replaced.subList(0, 60) + " …" : replaced);
            });
        }

        private static String name(int id) {
            if (id == 0) {
                return "air";
            }
            BlockType t = BlockType.getAssetMap().getAsset(id);
            return t == null ? "#" + id : t.getId();
        }
    }
}
