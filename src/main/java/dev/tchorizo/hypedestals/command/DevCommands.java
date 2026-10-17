package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.builtin.adventure.farming.config.stages.BlockStateFarmingStageData;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.block.HarvesterComponent;
import dev.tchorizo.hypedestals.block.HytaleIds;
import dev.tchorizo.hypedestals.block.HytaleSoilWorld;
import dev.tchorizo.hypedestals.config.Tier;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.HarvestArea;
import dev.tchorizo.hypedestals.harvest.UpgradeSet;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import dev.tchorizo.hypedestals.harvest.UpgradeVisual;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@code /hypedestals dev …}: world setup for testing the harvest cycle on a dedicated server with
 * no player. Registered only when {@link HyPedestalsCommand#DEV_PROPERTY} is set; operator-only
 * (each subcommand's node is generated below this one, e.g. {@code ….dev.place}).
 */
final class DevCommands extends AbstractAsyncCommand {

    private static final List<String> TIERS = List.of("Copper", "Iron", "Thorium", "Cobalt");
    private static final String HARVESTER_PREFIX = "HyPedestals_Harvester_";
    private static final String TILLED_SOIL = "Soil_Dirt_Tilled";
    private static final String WHEAT = "Plant_Crop_Wheat_Block";
    /** Vanilla crops with {@code StageSetAfterHarvest: "Harvested"}: they regrow after {@code harvest}. */
    private static final String WHEAT_ETERNAL = "Plant_Crop_Wheat_Block_Eternal";
    private static final String CARROT_ETERNAL = "Plant_Crop_Carrot_Block_Eternal";
    private static final String FINAL_STAGE_STATE = "StageFinal";
    /** Positions pinned by {@link #pin}; one refresh loop each. */
    private static final Set<Vector3i> PINNED = ConcurrentHashMap.newKeySet();
    /** Active {@code grow} areas by center; a loop runs while its {@link Grow} is the registered one. */
    private static final Map<Vector3i, Grow> GROWING = new ConcurrentHashMap<>();
    private static final long GROW_PERIOD_MS = 500;
    /** Growth factor the scene turns on; wheat goes from planted to ripe in about 15 s with it. */
    private static final double SCENE_GROW_FACTOR = 200;

    DevCommands() {
        super("dev", "HyPedestals development commands");
        requirePermission(HyPedestalsCommand.PERMISSION + ".dev");
        addSubCommand(new PlaceCommand());
        addSubCommand(new PlantCommand());
        addSubCommand(new RipenCommand());
        addSubCommand(new BreakCommand());
        addSubCommand(new CollectCommand());
        addSubCommand(new SceneCommand());
        addSubCommand(new GrowCommand());
        addSubCommand(new CropCommand());
        addSubCommand(new SetBlockCommand());
        addSubCommand(new ContentsCommand());
        addSubCommand(new UpgradeCommand());
        addSubCommand(new StateCommand());
        addSubCommand(new FenceCommand());
        addSubCommand(new WaterCommand());
        addSubCommand(new SoilCommand());
        addSubCommand(new SetItemCommand());
        addSubCommand(new PinCommand());
        addSubCommand(new StressCommand());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        HyPedestalsCommand.reply(context,
                "usage: hypedestals dev place <x y z> <%s> | plant <x y z> [--block <id>] | ripen <x y z>"
                        + " | break <x y z> | collect <x y z> <radius> | scene <x y z>"
                        + " | grow <x y z> <radius> <factor, 0 = off> | crop <x y z>"
                        + " | setblock <x y z> <block id|air> [--rotation <index>] | contents <x y z>"
                        + " | upgrade <x y z> <add|remove> <watering|fertilizer> | fence <x y z> <radius>"
                        + " | water <x y z> <game seconds> | soil <x y z> | pin <x y z> | stress <x y z> <count> [--factor F]"
                        + " | setitem <x y z> <slot|last> <item id|empty> [--qty N] [--durability D]",
                String.join("|", TIERS));
        return CompletableFuture.completedFuture(null);
    }

    /** {@code setBlock} at {@code p}, unrotated; the block's section must already be loaded. */
    private static boolean setBlock(World world, Vector3i p, String blockId, BlockType type) {
        return setBlock(world, p, blockId, type, RotationTuple.NONE_INDEX);
    }

    /** {@code setBlock} at {@code p} with a {@link RotationTuple} index; the section must already be loaded. */
    private static boolean setBlock(World world, Vector3i p, String blockId, BlockType type, int rotation) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x, p.y, p.z);
        return section != null && BlockOperations.setBlock(chunkStore, section, p.x, p.y, p.z,
                BlockType.getAssetMap().getIndex(blockId), type, rotation, 0, SetBlockSettings.NONE);
    }

    /** {@link #setBlock} by asset id; {@code false} when the asset is not loaded. */
    private static boolean setBlock(World world, Vector3i p, String blockId) {
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        return type != null && setBlock(world, p, blockId, type);
    }

    /** Tilled soil below and stage-zero {@code cropId} at the position; both sections must be loaded. */
    private static boolean plant(World world, Vector3i pos, String cropId) {
        boolean soil = setBlock(world, new Vector3i(pos.x, pos.y - 1, pos.z), TILLED_SOIL);
        return setBlock(world, pos, cropId) && soil;
    }

    /** Forces the crop at the position to its final growth stage; returns the reply line. */
    private static String ripen(World world, Vector3i pos) {
        String at = HyPedestalsCommand.format(pos);
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (section == null) {
            return "ripen at " + at + ": section not loaded";
        }
        BlockType current = BlockType.getAssetMap().getAsset(
                new SectionReader(chunkStore).getBlock(pos.x, pos.y, pos.z));
        String cropId = current == null ? null : HytaleIds.baseBlockId(current.getId());
        BlockType crop = cropId == null ? null : BlockType.getAssetMap().getAsset(cropId);
        if (crop == null || crop.getFarming() == null) {
            return "ripen at " + at + ": no crop (" + (current == null ? "null" : current.getId()) + ")";
        }

        Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
        FarmingBlock farm = ref == null ? null : store.getComponent(ref, FarmingBlock.getComponentType());
        String stageSet = farm == null || farm.getCurrentStageSet() == null
                ? crop.getFarming().getStartingStageSet()
                : farm.getCurrentStageSet();
        var stages = crop.getFarming().getStages().get(stageSet);
        if (stages == null || stages.length == 0) {
            return "ripen at " + at + ": " + cropId + " has no stages in set " + stageSet;
        }
        if (farm != null) {
            farm.setGrowthProgress(stages.length - 1);
        }
        BlockOperations.setBlockInteractionState(chunkStore, section, pos.x, pos.y, pos.z,
                crop, FINAL_STAGE_STATE, true);
        BlockType after = BlockType.getAssetMap().getAsset(
                new SectionReader(chunkStore).getBlock(pos.x, pos.y, pos.z));
        return "ripen at " + at + ": " + cropId + " -> " + (after == null ? "null" : after.getId());
    }

    /**
     * Keeps the harvester's chunk loaded and its section ticking. Artifact of a dedicated server
     * with no player: the chunk otherwise unloads after ~8 s and reloads with a fresh component
     * (timer and last result reset), and a loaded section barely ticks once the SET_TICKING
     * request expires. In game the player's presence keeps the section ticking, so production code
     * never pins anything. Idempotent per position; returns whether the chunk is now pinned.
     */
    private static boolean pin(World world, Vector3i pos) {
        WorldChunk chunk = world.getChunkStore().getChunkComponent(
                ChunkUtil.indexChunkFromBlock(pos.x, pos.z), WorldChunk.getComponentType());
        if (chunk == null) {
            return false;
        }
        if (PINNED.add(new Vector3i(pos))) {
            chunk.addKeepLoaded();
            keepTicking(world, new Vector3i(pos));
        }
        return true;
    }

    /** Re-requests the section as ticking every second for as long as the world lives. */
    private static void keepTicking(World world, Vector3i pos) {
        if (!world.isAlive()) {
            return;
        }
        world.getChunkStore().getChunkSectionReferenceAtBlockAsync(
                pos.x, pos.y, pos.z, GetChunkFlags.SET_TICKING | GetChunkFlags.HIGH_PRIORITY);
        world.scheduleAfter(() -> keepTicking(world, pos), 1, TimeUnit.SECONDS);
    }

    /**
     * One {@code grow} registration: crops in the {@link HarvestArea} of {@code center} gain
     * {@code factor - 1} extra seconds of growth per game second, on top of the vanilla tick.
     * {@code warned} keeps a failing crop from logging every step.
     */
    private record Grow(Vector3i center, int radius, double factor, Set<Vector3i> warned) {}

    /**
     * Turns {@code grow} on for the area (replacing any previous registration of the same center)
     * or off with {@code factor <= 0}; must run on the world thread. Returns the reply line.
     */
    private static String grow(World world, Vector3i center, int radius, double factor) {
        String at = HyPedestalsCommand.format(center);
        Vector3i key = new Vector3i(center);
        if (factor <= 0) {
            Grow old = GROWING.remove(key);
            return "grow at " + at + ": off" + (old == null ? " (was not on)" : "");
        }
        if (factor < 1 || radius < 0) {
            return "grow at " + at + ": factor must be 0 (off) or >= 1 and radius >= 0";
        }
        Grow grow = new Grow(key, radius, factor, ConcurrentHashMap.newKeySet());
        GROWING.put(key, grow);
        growStep(world, grow, null);
        return String.format(Locale.ROOT, "grow at %s r=%d factor=%.1f: on, every %d ms", at, radius, factor, GROW_PERIOD_MS);
    }

    /**
     * One step of a {@code grow} loop, on the world thread: advances the area's crops by the game
     * time elapsed since the previous step times {@code factor - 1}, then schedules the next step.
     * Game time (not wall time) so a paused world does not grow. Stops once the registration is
     * replaced or removed, or the world dies.
     */
    private static void growStep(World world, Grow grow, Instant previous) {
        if (!world.isAlive() || GROWING.get(grow.center()) != grow) {
            return;
        }
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        Instant now = time == null ? null : time.getGameTime();
        if (previous != null && now != null) {
            double elapsed = Duration.between(previous, now).toNanos() / 1e9;
            if (elapsed > 0) {
                growArea(world, grow, elapsed * (grow.factor() - 1));
            }
        }
        world.scheduleAfter(() -> world.execute(() -> growStep(world, grow, now)), GROW_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private static void growArea(World world, Grow grow, double seconds) {
        ChunkStore chunkStore = world.getChunkStore();
        SectionReader reader = new SectionReader(chunkStore);
        Vector3i c = grow.center();
        for (BlockPos p : HarvestArea.positions(new BlockPos(c.x, c.y, c.z), grow.radius())) {
            Vector3i pos = new Vector3i(p.x(), p.y(), p.z());
            try {
                advance(chunkStore, reader, pos, seconds);
            } catch (RuntimeException e) {
                if (grow.warned().add(pos)) {
                    HyPedestalsPlugin.warn("dev grow: crop at %s failed, skipped from now on: %s", pos, e);
                }
            }
        }
    }

    /**
     * Adds {@code seconds} of growth to the crop at {@code pos} (no-op unless it is a growing crop
     * with a {@link FarmingBlock} in a loaded section). {@code growthProgress} counts stages: its
     * integer part is the current stage of the stage set and its fraction the part of that stage's
     * duration already grown. Crossing into a later stage applies it as a block state, which every
     * vanilla crop uses; a crop whose next stage is of another type is left alone. Growth
     * modifiers (water, light, fertilizer) are not applied to the extra time. Returns whether the
     * crop reached a later stage.
     */
    private static boolean advance(ChunkStore chunkStore, SectionReader reader, Vector3i pos, double seconds) {
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (section == null) {
            return false;
        }
        int id = reader.getBlock(pos.x, pos.y, pos.z);
        if (id == BlockType.EMPTY_ID) {
            return false;
        }
        BlockType current = BlockType.getAssetMap().getAsset(id);
        BlockType crop = current == null ? null : BlockType.getAssetMap().getAsset(HytaleIds.baseBlockId(current.getId()));
        if (crop == null || crop.getFarming() == null) {
            return false;
        }
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
        FarmingBlock farm = ref == null ? null : store.getComponent(ref, FarmingBlock.getComponentType());
        if (farm == null) {
            return false;
        }
        String stageSet = farm.getCurrentStageSet() == null
                ? crop.getFarming().getStartingStageSet() : farm.getCurrentStageSet();
        FarmingStageData[] stages = crop.getFarming().getStages().get(stageSet);
        if (stages == null || stages.length == 0) {
            return false;
        }
        int last = stages.length - 1;
        float progress = farm.getGrowthProgress();
        int stage = (int) progress;
        if (stage >= last || stage < 0) {
            return false;
        }

        double next = progress;
        double left = seconds;
        while (left > 0 && (int) next < last) {
            int i = (int) next;
            double duration = duration(stages[i]);
            double need = (i + 1 - next) * duration;
            if (duration <= 0 || left >= need) {
                next = i + 1;
                left -= Math.max(need, 0);
            } else {
                next += left / duration;
                left = 0;
            }
        }
        int reached = (int) next;
        if (reached != stage) {
            if (!(stages[reached] instanceof BlockStateFarmingStageData blockState)) {
                return false;
            }
            BlockOperations.setBlockInteractionState(chunkStore, section, pos.x, pos.y, pos.z,
                    crop, blockState.getState(), true);
            // The state change may rebuild the block entity; write the progress on the current one.
            ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
            farm = ref == null ? null : store.getComponent(ref, FarmingBlock.getComponentType());
            if (farm == null) {
                return true;
            }
            farm.setCurrentStageSet(stageSet);
        }
        farm.setGrowthProgress((float) Math.min(next, last));
        BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving(store);
        }
        return reached != stage;
    }

    /** Mean of the stage's duration range in game seconds; 0 when it has none (final stages). */
    private static double duration(FarmingStageData stage) {
        return stage.getDuration() == null ? 0 : (stage.getDuration().min + stage.getDuration().max) / 2.0;
    }

    /** {@code id x qty} for each non-empty slot, comma separated. */
    private static String contents(ItemContainer container) {
        List<String> stacks = new ArrayList<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack.getItemId() + " x" + stack.getQuantity());
            }
        }
        return String.join(", ", stacks);
    }

    private static final class PlaceCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> tier =
                withRequiredArg("tier", "Copper, Iron, Thorium or Cobalt", ArgTypes.STRING);

        PlaceCommand() {
            super("place", "Place a Harvester block of the given tier");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            String tierName = context.get(tier);
            if (!TIERS.contains(tierName)) {
                HyPedestalsCommand.reply(context, "unknown tier %s; expected one of %s", tierName, TIERS);
                return CompletableFuture.completedFuture(null);
            }
            String blockId = HARVESTER_PREFIX + tierName;
            return runAt(context, world -> {
                BlockType type = BlockType.getAssetMap().getAsset(blockId);
                if (type == null) {
                    HyPedestalsCommand.reply(context, "block %s is not loaded", blockId);
                    return;
                }
                boolean set = setBlock(world, pos, blockId, type);
                // setBlock instantiates the BlockEntity components the block type declares.
                Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
                boolean hasHarvester = ref != null
                        && world.getChunkStore().getStore().getComponent(ref, HarvesterComponent.TYPE) != null;
                boolean pinned = pin(world, pos);
                HyPedestalsCommand.reply(context, "place %s at %s: setBlock=%b HyHarvester=%b pinned=%b",
                        blockId, HyPedestalsCommand.format(pos), set, hasHarvester, pinned);
            }, pos);
        }
    }

    /** Tilled soil below and a stage-zero crop (wheat unless {@code --block} says otherwise) at the position. */
    private static final class PlantCommand extends HyPedestalsCommand.PositionCommand {
        private final DefaultArg<String> block = withDefaultArg("block",
                "Crop block id, e.g. " + WHEAT_ETERNAL, ArgTypes.STRING, WHEAT, WHEAT);

        PlantCommand() {
            super("plant", "Place tilled soil and a stage-zero crop (default wheat)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            Vector3i below = new Vector3i(pos.x, pos.y - 1, pos.z);
            String cropId = context.get(block);
            return runAt(context, world -> {
                BlockType soil = BlockType.getAssetMap().getAsset(TILLED_SOIL);
                BlockType crop = BlockType.getAssetMap().getAsset(cropId);
                if (soil == null || crop == null || crop.getFarming() == null) {
                    HyPedestalsCommand.reply(context, "plant: missing asset or not a crop: %s=%b %s=%s",
                            TILLED_SOIL, soil != null, cropId, crop == null ? "missing" : "no Farming");
                    return;
                }
                boolean soilSet = setBlock(world, below, TILLED_SOIL, soil);
                boolean cropSet = setBlock(world, pos, cropId, crop);
                HyPedestalsCommand.reply(context, "plant at %s: soil=%b %s=%b",
                        HyPedestalsCommand.format(pos), soilSet, cropId, cropSet);
            }, pos, below);
        }
    }

    /**
     * {@code grow <x y z> <radius> <factor>}: crops in the harvest area of radius {@code radius}
     * around the position grow {@code factor} times as fast (see {@link #advance}); {@code 0}
     * turns it off. Lasts until turned off or the server stops (not persisted).
     */
    private static final class GrowCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> radius =
                withRequiredArg("radius", "Area radius on x and z (layers y-1..y+1, like a harvester)", ArgTypes.INTEGER);
        private final RequiredArg<Double> factor =
                withRequiredArg("factor", "Growth speed multiplier (>= 1); 0 turns it off", ArgTypes.DOUBLE);

        GrowCommand() {
            super("grow", "Make crops around a position grow faster (factor 0 = off)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            int r = context.get(radius);
            double f = context.get(factor);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", grow(world, pos, r, f)), pos);
        }
    }

    /** {@code crop <x y z>}: block id, stage set and growth progress of the crop at the position. */
    private static final class CropCommand extends HyPedestalsCommand.PositionCommand {
        CropCommand() {
            super("crop", "Show the block id, stage set and growth progress at a position");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", describe(world, pos)), pos);
        }

        private static String describe(World world, Vector3i pos) {
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> store = chunkStore.getStore();
            Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (section == null) {
                return "crop at " + HyPedestalsCommand.format(pos) + ": section not loaded";
            }
            SectionReader reader = new SectionReader(chunkStore);
            BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(pos.x, pos.y, pos.z));
            Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
            FarmingBlock farm = ref == null ? null : store.getComponent(ref, FarmingBlock.getComponentType());
            BlockType crop = type == null ? null : BlockType.getAssetMap().getAsset(HytaleIds.baseBlockId(type.getId()));
            String stages = "";
            if (farm != null && crop != null && crop.getFarming() != null) {
                String set = farm.getCurrentStageSet() == null ? crop.getFarming().getStartingStageSet() : farm.getCurrentStageSet();
                FarmingStageData[] data = crop.getFarming().getStages().get(set);
                stages = String.format(Locale.ROOT, " stageSet=%s progress=%.2f/%d", set, farm.getGrowthProgress(),
                        data == null ? -1 : data.length - 1);
            }
            return String.format(Locale.ROOT, "crop at %s: %s filler=%d farmingBlock=%b%s", HyPedestalsCommand.format(pos),
                    type == null ? "null" : type.getId(), reader.getFiller(pos.x, pos.y, pos.z), farm != null, stages);
        }
    }

    /**
     * {@code pin <x y z>}: what {@code place} does to keep a Harvester running on a server without
     * players ({@link #pin}), for a block that already exists (e.g. after a restart), without
     * touching the block.
     */
    private static final class PinCommand extends HyPedestalsCommand.PositionCommand {
        PinCommand() {
            super("pin", "Keep the chunk at a position loaded and its section ticking (no block change)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "pin at %s: pinned=%b",
                    HyPedestalsCommand.format(pos), pin(world, pos)), pos);
        }
    }

    /**
     * {@code stress <x y z> <count> [--factor F]}: performance scene for {@code /hypedestals perf}.
     * {@code count} Cobalt Harvesters in a row along +x from the position, {@code 2R+3} blocks
     * apart (the area plus its fence ring), at height {@code y}: grass platform at {@code y-1}
     * (tilled soil under the crops), air from {@code y} up 3 blocks, every cell of every area
     * planted with Eternal wheat and ripened, the fence ring at {@code R+1}, {@code grow} at
     * {@code F} (default 2000: a harvested crop is ripe again within about a second, so every
     * 3 s cycle finds the whole area mature), no chest (the containers fill up and the rest stays
     * on the ground), and every chunk under the row pinned. Rebuilds in place when run again.
     * Pick a free spot: the platform overwrites what is there.
     */
    private static final class StressCommand extends HyPedestalsCommand.PositionCommand {
        private static final String COBALT = "Cobalt";
        private final RequiredArg<Integer> count =
                withRequiredArg("count", "Number of Cobalt Harvesters (1..16)", ArgTypes.INTEGER);
        private final DefaultArg<Double> factor = withDefaultArg("factor",
                "grow factor of each area", ArgTypes.DOUBLE, 2000.0, "2000");

        StressCommand() {
            super("stress", "Build a row of Cobalt Harvesters over fully ripe Eternal wheat (performance test)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i origin = position(context);
            int n = context.get(count);
            double f = context.get(factor);
            if (n < 1 || n > 16) {
                HyPedestalsCommand.reply(context, "stress: count must be 1..16");
                return CompletableFuture.completedFuture(null);
            }
            int r = HyPedestalsPlugin.settings().tier(Tier.COBALT).radius();
            int step = 2 * (r + SceneCommand.FENCE_DISTANCE) + 1;
            int d = r + SceneCommand.FENCE_DISTANCE;
            SceneCommand.Platform platform = new SceneCommand.Platform(origin.x - d, origin.x + (n - 1) * step + d,
                    origin.z - d, origin.z + d, origin.y);
            Set<Vector3i> sections = new LinkedHashSet<>();
            SceneCommand.addSections(sections, platform.minX(), platform.maxX(), origin.y - 1,
                    origin.y + SceneCommand.AIR_ABOVE, platform.minZ(), platform.maxZ());
            Vector3i[] loads = sections.stream()
                    .map(k -> new Vector3i(k.x * ChunkUtil.SIZE, k.y * ChunkUtil.SIZE, k.z * ChunkUtil.SIZE))
                    .toArray(Vector3i[]::new);
            return runAt(context, world -> {
                BlockType type = BlockType.getAssetMap().getAsset(HARVESTER_PREFIX + COBALT);
                if (type == null) {
                    HyPedestalsCommand.reply(context, "stress: block %s is not loaded", HARVESTER_PREFIX + COBALT);
                    return;
                }
                int grass = SceneCommand.platform(world, new SectionReader(world.getChunkStore()), platform);
                int pinned = 0;
                for (int cx = Math.floorDiv(platform.minX(), ChunkUtil.SIZE); cx <= Math.floorDiv(platform.maxX(), ChunkUtil.SIZE); cx++) {
                    for (int cz = Math.floorDiv(platform.minZ(), ChunkUtil.SIZE); cz <= Math.floorDiv(platform.maxZ(), ChunkUtil.SIZE); cz++) {
                        pinned += pin(world, new Vector3i(cx * ChunkUtil.SIZE, origin.y, cz * ChunkUtil.SIZE)) ? 1 : 0;
                    }
                }
                HyPedestalsCommand.reply(context, "stress platform x %d..%d z %d..%d y=%d: grass=%d pinnedChunks=%d",
                        platform.minX(), platform.maxX(), platform.minZ(), platform.maxZ(), origin.y - 1, grass, pinned);
                for (int i = 0; i < n; i++) {
                    Vector3i p = new Vector3i(origin.x + i * step, origin.y, origin.z);
                    int cells = 0, planted = 0, ripe = 0;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            Vector3i c = new Vector3i(p.x + dx, p.y, p.z + dz);
                            cells++;
                            planted += plant(world, c, WHEAT_ETERNAL) ? 1 : 0;
                            ripe += ripen(world, c).contains(FINAL_STAGE_STATE) ? 1 : 0;
                        }
                    }
                    boolean set = setBlock(world, p, HARVESTER_PREFIX + COBALT, type);
                    pin(world, p);
                    int fence = SceneCommand.fence(world, p, r);
                    HyPedestalsCommand.reply(context, "stress Cobalt at %s r=%d: setBlock=%b planted=%d/%d ripe=%d fence=%d; %s",
                            HyPedestalsCommand.format(p), r, set, planted, cells, ripe, fence, grow(world, p, r, f));
                }
                HyPedestalsCommand.reply(context, "stress done");
            }, loads);
        }
    }

    /** Forces the crop at the position to its final growth stage. */
    private static final class RipenCommand extends HyPedestalsCommand.PositionCommand {
        RipenCommand() {
            super("ripen", "Force the crop at a position to its final stage");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", ripen(world, pos)), pos);
        }
    }

    /**
     * Breaks the block at the position the way automation would ({@code naturallyRemoveBlock}, no
     * instigator), using the drop configured in the block type's {@code Gathering.Breaking}. Reports
     * the container content just before, to compare with what {@code collect} finds afterwards.
     */
    private static final class BreakCommand extends HyPedestalsCommand.PositionCommand {
        BreakCommand() {
            super("break", "Break the block at a position as automation would (drops spawn as items)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", breakBlock(world, pos)), pos);
        }

        private static String breakBlock(World world, Vector3i pos) {
            String at = HyPedestalsCommand.format(pos);
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> store = chunkStore.getStore();
            Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (section == null) {
                return "break at " + at + ": section not loaded";
            }
            SectionReader reader = new SectionReader(chunkStore);
            BlockType block = BlockType.getAssetMap().getAsset(reader.getBlock(pos.x, pos.y, pos.z));
            if (block == null) {
                return "break at " + at + ": no block type";
            }
            Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
            ItemContainerBlock container = ref == null ? null : store.getComponent(ref, ItemContainerBlock.getComponentType());
            String contents = container == null ? "none" : contents(container.getItemContainer());

            var breaking = block.getGathering() == null ? null : block.getGathering().getBreaking();
            BlockHarvestUtils.naturallyRemoveBlock(pos, block, reader.getFiller(pos.x, pos.y, pos.z),
                    breaking == null ? 1 : breaking.getQuantity(),
                    breaking == null ? null : breaking.getItemId(),
                    breaking == null ? null : breaking.getDropListId(),
                    SetBlockSettings.NONE, section, world.getEntityStore().getStore(), store);

            BlockType after = BlockType.getAssetMap().getAsset(reader.getBlock(pos.x, pos.y, pos.z));
            return String.format(Locale.ROOT, "break at %s: %s (drop item=%s dropList=%s) container=[%s] -> %s",
                    at, block.getId(),
                    breaking == null ? null : breaking.getItemId(),
                    breaking == null ? null : breaking.getDropListId(),
                    contents, after == null ? "null" : after.getId());
        }
    }

    /**
     * Lists the item entities within {@code radius} of the block center (id and quantity) without
     * removing them. The item spatial index lags a few seconds behind spawns, so call it a few
     * seconds after the drop.
     */
    private static final class CollectCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Double> radius =
                withRequiredArg("radius", "Search radius in blocks", ArgTypes.DOUBLE);

        CollectCommand() {
            super("collect", "List item entities around a position (does not remove them)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            double r = context.get(radius);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", list(world, pos, r)), pos);
        }

        private static String list(World world, Vector3i pos, double r) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            var spatial = entityStore.getResource(EntityModule.get().getItemSpatialResourceType());
            if (spatial == null || spatial.getSpatialStructure() == null) {
                return "collect at " + HyPedestalsCommand.format(pos) + ": no item spatial index";
            }
            List<Ref<EntityStore>> refs = SpatialResource.getThreadLocalReferenceList();
            refs.clear();
            spatial.getSpatialStructure().collect(new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5), r, refs);
            List<String> items = new ArrayList<>();
            for (Ref<EntityStore> ref : refs) {
                ItemComponent item = ref == null || !ref.isValid()
                        ? null : entityStore.getComponent(ref, ItemComponent.getComponentType());
                if (item != null && item.getItemStack() != null) {
                    items.add(item.getItemStack().getItemId() + " x" + item.getItemStack().getQuantity());
                }
            }
            refs.clear();
            return String.format(Locale.ROOT, "collect at %s r=%.1f: %d item entities [%s]",
                    HyPedestalsCommand.format(pos), r, items.size(), String.join(", ", items));
        }
    }

    /**
     * {@code scene <x y z>}: the manual test scene, built in one world task. {@code x z} is the
     * Copper harvester; Iron, Thorium and Cobalt follow along +x with {@link #GAP} free blocks
     * between neighbouring harvest areas. The ground level is {@code y}, raised to one above the
     * highest block found in the four harvester columns (probed up to {@link #PROBE_ABOVE} blocks
     * above {@code y}, harvesters ignored so re-running the command on a built scene rebuilds it in
     * place) so the scene sits on the terrain surface instead of inside it. Everything
     * stands on a {@code Soil_Grass} layer one below the ground level with {@link #AIR_ABOVE}
     * blocks cleared to air above it (only that low, so builds of the player above the scene
     * survive a rebuild); the terrain below is left alone, so parts may float. Each
     * area is tilled and filled with ripe Eternal crops ({@link #cropFor}: carrot for Iron, wheat
     * elsewhere; they regrow after a harvest, so the scene never runs out) except its +z edge row
     * (tilled, left for the player), with a chest at x+1 of each harvester and a ripe berry bush in
     * the Copper area's -x/-z corner. Each area also gets {@code grow} at {@link #SCENE_GROW_FACTOR}
     * and a {@link #FENCE} ring right outside it, {@link #FENCE_DISTANCE} from the harvester (chest
     * and berry inside), so drops that roll away stop within reach of the collect pass.
     * An extra Copper stands at y={@link #ELEVATED_Y} (where the dev-server test saw
     * harvested crops without drops), or {@link #CLEARANCE} above the ground level if that is
     * higher, {@link #ELEVATED_DZ} blocks along z on a small platform. Nothing is pinned, unlike
     * {@code place}: the scene is meant for a player, and pinned harvesters would clear the crops
     * before anyone joins. Replies with the terrain tops, one line per harvester, the suggested
     * spawn, and finally {@code scene done}.
     */
    private static final class SceneCommand extends HyPedestalsCommand.PositionCommand {
        private static final String GRASS = "Soil_Grass";
        private static final String BERRY = "Plant_Crop_Berry_Block";
        private static final String CHEST = "Furniture_Crude_Chest_Small";
        /** Ring around each area that keeps drops from rolling off; see {@link #FENCE_DISTANCE}. */
        private static final String FENCE = "Wood_Greenwood_Fence";
        /** Corner state of {@link #FENCE}; {@code setBlock} never picks it by itself. */
        private static final String FENCE_CORNER = "*Wood_Greenwood_Fence_State_Definitions_Corner";
        /**
         * Chebyshev distance of the fence ring from the harvester, as an offset from the radius:
         * {@code radius + 1}, right against the area with no free block in between, so every drop
         * stops well inside the collect box ({@code radius} plus the collect margin).
         */
        private static final int FENCE_DISTANCE = 1;
        private static final int GAP = 6;
        private static final int MARGIN = 3;
        /** Height of the elevated Copper above the ground level, and of the sections loaded for it. */
        private static final int CLEARANCE = 16;
        /** Blocks cleared to air above the platform's grass: crops, harvester, chest and fence (ground level..+2). */
        private static final int AIR_ABOVE = 3;
        private static final int ELEVATED_Y = 140;
        private static final int ELEVATED_DZ = -16;
        /** Terrain probe window: each harvester column from y=0 up to this many blocks above {@code y}. */
        private static final int PROBE_ABOVE = 64;

        /** A harvester of the scene and its area radius, read from the tier's block entity template. */
        private record Site(String tier, BlockType type, Vector3i pos, int radius) {}

        /** Platform footprint: grass at {@code y - 1}, air from {@code y} up {@link #AIR_ABOVE} blocks. */
        private record Platform(int minX, int maxX, int minZ, int maxZ, int y) {}

        SceneCommand() {
            super("scene", "Build the manual test scene (x z = Copper harvester, y = minimum ground level)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i origin = position(context);
            if (origin.y < 1) {
                HyPedestalsCommand.reply(context, "scene: y must be at least 1");
                return CompletableFuture.completedFuture(null);
            }
            // The row at height origin.y; moved up to the ground level once the terrain is known.
            List<Site> row = new ArrayList<>(TIERS.size());
            int x = origin.x;
            for (String tier : TIERS) {
                BlockType type = BlockType.getAssetMap().getAsset(HARVESTER_PREFIX + tier);
                if (type == null) {
                    HyPedestalsCommand.reply(context, "scene: block %s is not loaded", HARVESTER_PREFIX + tier);
                    return CompletableFuture.completedFuture(null);
                }
                int radius = radiusOf(type);
                if (!row.isEmpty()) {
                    x += row.getLast().radius() + GAP + radius + 1;
                }
                row.add(new Site(tier, type, new Vector3i(x, origin.y, origin.z), radius));
            }
            Platform probe = ground(row, origin.y);
            Platform probeHigh = elevated(row.getFirst(), origin.y);
            Set<Vector3i> sections = new LinkedHashSet<>();
            int topY = Math.max(ELEVATED_Y, origin.y + PROBE_ABOVE + CLEARANCE) + CLEARANCE;
            for (Platform p : List.of(probe, probeHigh)) {
                addSections(sections, p.minX(), p.maxX(), 0, topY, p.minZ(), p.maxZ());
            }
            Vector3i[] loads = sections.stream()
                    .map(k -> new Vector3i(k.x * ChunkUtil.SIZE, k.y * ChunkUtil.SIZE, k.z * ChunkUtil.SIZE))
                    .toArray(Vector3i[]::new);

            return runAt(context, world -> {
                SectionReader reader = new SectionReader(world.getChunkStore());
                List<String> tops = new ArrayList<>(row.size());
                int groundY = origin.y;
                for (Site s : row) {
                    int top = terrainTop(reader, s.pos().x, origin.y + PROBE_ABOVE - 1, s.pos().z);
                    groundY = Math.max(groundY, top + 1);
                    tops.add(s.tier() + " " + s.pos().x + " " + s.pos().z + " -> y=" + top + " " + blockName(reader, s.pos().x, top, s.pos().z));
                }
                HyPedestalsCommand.reply(context, "scene terrain top (probed y<=%d): %s; ground level y=%d",
                        origin.y + PROBE_ABOVE - 1, String.join("; ", tops), groundY);

                List<Site> sites = new ArrayList<>(row.size() + 1);
                for (Site s : row) {
                    sites.add(new Site(s.tier(), s.type(), new Vector3i(s.pos().x, groundY, s.pos().z), s.radius()));
                }
                Site copper = sites.getFirst();
                Platform ground = ground(sites, groundY);
                Platform elevated = elevated(copper, groundY);
                sites.add(new Site(copper.tier(), copper.type(), new Vector3i(copper.pos().x, elevated.y(),
                        copper.pos().z + ELEVATED_DZ), copper.radius()));
                Vector3i berry = new Vector3i(copper.pos().x - copper.radius(), groundY, copper.pos().z - copper.radius());
                // Free columns between the Iron and Thorium areas, on the platform.
                Vector3i spawn = new Vector3i((sites.get(1).pos().x + sites.get(1).radius()
                        + sites.get(2).pos().x - sites.get(2).radius()) / 2, groundY, origin.z);

                int grass = platform(world, reader, ground) + platform(world, reader, elevated);
                HyPedestalsCommand.reply(context, "scene platform x %d..%d z %d..%d at y=%d and x %d..%d z %d..%d at y=%d: grass=%d",
                        ground.minX(), ground.maxX(), ground.minZ(), ground.maxZ(), ground.y() - 1,
                        elevated.minX(), elevated.maxX(), elevated.minZ(), elevated.maxZ(), elevated.y() - 1, grass);
                for (Site s : sites) {
                    HyPedestalsCommand.reply(context, "%s", build(world, s, s == copper ? berry : null));
                }
                HyPedestalsCommand.reply(context, "scene spawn %s", HyPedestalsCommand.format(spawn));
                HyPedestalsCommand.reply(context, "scene done");
            }, loads);
        }

        /** Main platform under the row of harvesters, with {@link #MARGIN} free blocks around the areas. */
        private static Platform ground(List<Site> row, int y) {
            Site first = row.getFirst();
            Site last = row.getLast();
            int maxRadius = row.stream().mapToInt(Site::radius).max().orElse(0);
            int z = first.pos().z;
            return new Platform(first.pos().x - first.radius() - MARGIN, last.pos().x + last.radius() + MARGIN,
                    z - maxRadius - MARGIN, z + maxRadius + MARGIN, y);
        }

        /**
         * Small platform for the elevated harvester: its area, the fence ring and one block more,
         * so rebuilding over a scene with a wider ring clears the old fence too.
         */
        private static Platform elevated(Site copper, int groundY) {
            int x = copper.pos().x;
            int z = copper.pos().z + ELEVATED_DZ;
            int r = copper.radius() + FENCE_DISTANCE + 1;
            return new Platform(x - r, x + r, z - r, z + r, Math.max(ELEVATED_Y, groundY + CLEARANCE));
        }

        private static int radiusOf(BlockType type) {
            HarvesterComponent template = type.getBlockEntity() == null
                    ? null : type.getBlockEntity().getComponent(HarvesterComponent.TYPE);
            return template == null ? 0 : template.radius();
        }

        /** Adds the section key (coordinates divided by the section size) of every section in the box. */
        private static void addSections(Set<Vector3i> keys, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            for (int sx = Math.floorDiv(minX, ChunkUtil.SIZE); sx <= Math.floorDiv(maxX, ChunkUtil.SIZE); sx++) {
                for (int sy = Math.floorDiv(minY, ChunkUtil.SIZE); sy <= Math.floorDiv(maxY, ChunkUtil.SIZE); sy++) {
                    for (int sz = Math.floorDiv(minZ, ChunkUtil.SIZE); sz <= Math.floorDiv(maxZ, ChunkUtil.SIZE); sz++) {
                        keys.add(new Vector3i(sx, sy, sz));
                    }
                }
            }
        }

        /**
         * Highest non-empty block of the column at or below {@code fromY}, harvesters excluded, or
         * {@code -1}.
         */
        private static int terrainTop(SectionReader reader, int x, int fromY, int z) {
            for (int y = fromY; y >= 0; y--) {
                if (!reader.canHoldY(y)) {
                    continue;
                }
                int id = reader.getBlock(x, y, z);
                if (id == BlockType.EMPTY_ID) {
                    continue;
                }
                BlockType type = BlockType.getAssetMap().getAsset(id);
                if (type == null || !HytaleIds.baseBlockId(type.getId()).startsWith(HARVESTER_PREFIX)) {
                    return y;
                }
            }
            return -1;
        }

        private static String blockName(SectionReader reader, int x, int y, int z) {
            if (y < 0) {
                return "none";
            }
            int id = reader.getBlock(x, y, z);
            BlockType type = BlockType.getAssetMap().getAsset(id);
            return type == null ? "#" + id : type.getId();
        }

        /** Grass layer and cleared air above it; returns how many columns end with grass in place. */
        private static int platform(World world, SectionReader reader, Platform p) {
            int grassId = BlockType.getAssetMap().getIndex(GRASS);
            int grass = 0;
            for (int x = p.minX(); x <= p.maxX(); x++) {
                for (int z = p.minZ(); z <= p.maxZ(); z++) {
                    // Count what is in place rather than setBlock's result (false on some no-ops).
                    setBlock(world, new Vector3i(x, p.y() - 1, z), GRASS);
                    grass += reader.getBlock(x, p.y() - 1, z) == grassId ? 1 : 0;
                    for (int y = p.y(); y < p.y() + AIR_ABOVE; y++) {
                        if (reader.getBlock(x, y, z) != BlockType.EMPTY_ID) {
                            setBlock(world, new Vector3i(x, y, z), BlockType.EMPTY_KEY, BlockType.EMPTY);
                        }
                    }
                }
            }
            return grass;
        }

        /** Eternal crop planted in a tier's area: carrot for Iron, so the scene has a second crop type. */
        private static String cropFor(String tier) {
            return tier.equals("Iron") ? CARROT_ETERNAL : WHEAT_ETERNAL;
        }

        /**
         * Fence ring at {@link #FENCE_DISTANCE} outside the area of the harvester at {@code p}, on the
         * harvester's layer (one above the soil); returns how many ring blocks end up as a fence.
         * {@code setBlock} does not run the fence's connection rules (every block stays the plain
         * straight fence), so each block is placed in its final shape: straight segments along x
         * unrotated, along z turned 90°, and the four corners as {@link #FENCE_CORNER} turned so its
         * two arms point along the ring.
         */
        private static int fence(World world, Vector3i p, int radius) {
            int d = radius + FENCE_DISTANCE;
            BlockType straight = BlockType.getAssetMap().getAsset(FENCE);
            BlockType corner = BlockType.getAssetMap().getAsset(FENCE_CORNER);
            if (straight == null || corner == null) {
                return 0;
            }
            int alongZ = RotationTuple.index(Rotation.Ninety, Rotation.None, Rotation.None);
            SectionReader reader = new SectionReader(world.getChunkStore());
            int placed = 0;
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    boolean xEdge = Math.abs(dx) == d;
                    boolean zEdge = Math.abs(dz) == d;
                    if (!xEdge && !zEdge) {
                        continue;
                    }
                    Vector3i f = new Vector3i(p.x + dx, p.y, p.z + dz);
                    if (xEdge && zEdge) {
                        // Arms toward the two ring neighbours, i.e. back toward the area on each axis.
                        setBlock(world, f, FENCE_CORNER, corner, cornerRotation(-Integer.signum(dx), -Integer.signum(dz)));
                    } else {
                        setBlock(world, f, FENCE, straight, xEdge ? alongZ : RotationTuple.NONE_INDEX);
                    }
                    placed += blockName(reader, f.x, f.y, f.z).contains(FENCE) ? 1 : 0;
                }
            }
            return placed;
        }

        /**
         * Rotation index (yaw only) that turns the unrotated corner, whose arms point to -x and +z
         * (hitbox {@code Fence_Corner_Thin}), so they point to {@code (armX, 0, 0)} and {@code (0, 0, armZ)}.
         */
        private static int cornerRotation(int armX, int armZ) {
            for (Rotation yaw : Rotation.VALUES) {
                RotationTuple rotation = RotationTuple.of(yaw, Rotation.None, Rotation.None);
                Vector3i a = new Vector3i(-1, 0, 0);
                Vector3i b = new Vector3i(0, 0, 1);
                rotation.applyRotationTo(a);
                rotation.applyRotationTo(b);
                Set<Vector3i> arms = Set.of(a, b);
                if (arms.equals(Set.of(new Vector3i(armX, 0, 0), new Vector3i(0, 0, armZ)))) {
                    return rotation.index();
                }
            }
            return RotationTuple.NONE_INDEX;
        }

        /**
         * Air at every {@link #FENCE} block (any state) on the square ring at Chebyshev distance
         * {@code d} around {@code p}, on {@code p}'s layer; other blocks are left alone. Returns how
         * many were removed.
         */
        private static int removeFenceRing(World world, Vector3i p, int d) {
            SectionReader reader = new SectionReader(world.getChunkStore());
            int removed = 0;
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.abs(dx) != d && Math.abs(dz) != d) {
                        continue;
                    }
                    int x = p.x + dx, z = p.z + dz;
                    if (blockName(reader, x, p.y, z).contains(FENCE)) {
                        setBlock(world, new Vector3i(x, p.y, z), BlockType.EMPTY_KEY, BlockType.EMPTY);
                        removed++;
                    }
                }
            }
            return removed;
        }

        /** Crops, harvester, chest, fence ring and {@code grow} of one site; returns its reply line. */
        private static String build(World world, Site site, Vector3i berry) {
            Vector3i p = site.pos();
            int r = site.radius();
            Vector3i chest = new Vector3i(p.x + 1, p.y, p.z);
            String cropId = cropFor(site.tier());
            int cells = 0;
            int planted = 0;
            int ripe = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Vector3i c = new Vector3i(p.x + dx, p.y, p.z + dz);
                    if (c.equals(p) || c.equals(chest) || c.equals(berry)) {
                        continue;
                    }
                    if (dz == r) {
                        setBlock(world, new Vector3i(c.x, c.y - 1, c.z), TILLED_SOIL);
                        continue;
                    }
                    cells++;
                    planted += plant(world, c, cropId) ? 1 : 0;
                    ripe += ripen(world, c).contains(FINAL_STAGE_STATE) ? 1 : 0;
                }
            }
            String berryResult = berry == null ? "" : String.format(Locale.ROOT, "; berry %s planted=%b %s",
                    HyPedestalsCommand.format(berry), plant(world, berry, BERRY), ripen(world, berry));
            String id = HARVESTER_PREFIX + site.tier();
            boolean set = setBlock(world, p, id, site.type());
            Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, p.x, p.y, p.z);
            boolean hasHarvester = ref != null
                    && world.getChunkStore().getStore().getComponent(ref, HarvesterComponent.TYPE) != null;
            boolean chestSet = setBlock(world, chest, CHEST);
            int fence = fence(world, p, r);
            int ring = 8 * (r + FENCE_DISTANCE);
            return String.format(Locale.ROOT,
                    "scene %s at %s r=%d: setBlock=%b HyHarvester=%b chest %s=%b %s planted=%d/%d ripe=%d"
                            + " unplanted row z=%d%s; %s fence=%d/%d; %s",
                    id, HyPedestalsCommand.format(p), r, set, hasHarvester, HyPedestalsCommand.format(chest),
                    chestSet, cropId, planted, cells, ripe, p.z + r, berryResult, FENCE, fence, ring,
                    grow(world, p, r, SCENE_GROW_FACTOR));
        }
    }

    /**
     * {@code fence <x y z> <radius>}: moves the fence ring of an existing scene area to the current
     * distance. Removes {@code Wood_Greenwood_Fence} blocks on the old ring ({@code radius + 2},
     * before {@code c63dca6}) and places the ring at {@code radius + 1}, both on the harvester's
     * layer; nothing else is touched. Replies {@code fence at <x y z> r=<r>: removed=<n> at R+2, fence=<placed>/<ring> at R+1}.
     */
    private static final class FenceCommand extends HyPedestalsCommand.PositionCommand {
        private static final int OLD_DISTANCE = 2;
        private final RequiredArg<Integer> radius =
                withRequiredArg("radius", "Harvest radius of the area", ArgTypes.INTEGER);

        FenceCommand() {
            super("fence", "Move a scene area's fence ring from radius+2 to radius+1");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            int r = context.get(radius);
            if (r < 1) {
                HyPedestalsCommand.reply(context, "fence: radius must be at least 1");
                return CompletableFuture.completedFuture(null);
            }
            int far = r + OLD_DISTANCE;
            return runAt(context, world -> {
                int removed = SceneCommand.removeFenceRing(world, p, far);
                int placed = SceneCommand.fence(world, p, r);
                HyPedestalsCommand.reply(context, "fence at %s r=%d: removed=%d at R+%d, fence=%d/%d at R+%d",
                        HyPedestalsCommand.format(p), r, removed, OLD_DISTANCE, placed,
                        8 * (r + SceneCommand.FENCE_DISTANCE), SceneCommand.FENCE_DISTANCE);
            }, new Vector3i(p.x - far, p.y, p.z - far), new Vector3i(p.x + far, p.y, p.z - far),
                    new Vector3i(p.x - far, p.y, p.z + far), new Vector3i(p.x + far, p.y, p.z + far));
        }
    }

    /**
     * {@code setblock <x y z> <id> [--rotation <index>]}: any block (or {@code air}) at the position
     * with a {@link RotationTuple} index, for building third-party setups (e.g. a storage network
     * next to a harvester) from the console. Replies with the block, rotation (yaw/pitch/roll) and
     * whether a block entity exists after the placement, read back from the world.
     */
    private static final class SetBlockCommand extends HyPedestalsCommand.PositionCommand {
        private static final String AIR = "air";
        private final RequiredArg<String> block =
                withRequiredArg("block", "Block id (e.g. Furniture_Crude_Chest_Small) or air", ArgTypes.STRING);
        private final DefaultArg<Integer> rotation = withDefaultArg("rotation",
                "RotationTuple index (0 = unrotated)", ArgTypes.INTEGER, RotationTuple.NONE_INDEX, "0");

        SetBlockCommand() {
            super("setblock", "Place any block (or air) at a position with a rotation index");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            String id = context.get(block);
            int rot = context.get(rotation);
            if (rot < 0 || rot >= RotationTuple.VALUES.length || RotationTuple.VALUES[rot] == null) {
                HyPedestalsCommand.reply(context, "setblock: rotation must be a RotationTuple index in 0..%d",
                        RotationTuple.VALUES.length - 1);
                return CompletableFuture.completedFuture(null);
            }
            return runAt(context, world -> {
                boolean air = id.equalsIgnoreCase(AIR);
                String blockId = air ? BlockType.EMPTY_KEY : id;
                BlockType type = air ? BlockType.EMPTY : BlockType.getAssetMap().getAsset(id);
                if (type == null) {
                    HyPedestalsCommand.reply(context, "setblock: block %s is not loaded", id);
                    return;
                }
                boolean set = setBlock(world, pos, blockId, type, rot);
                SectionReader reader = new SectionReader(world.getChunkStore());
                BlockType after = BlockType.getAssetMap().getAsset(reader.getBlock(pos.x, pos.y, pos.z));
                int afterRot = reader.getRotationIndex(pos.x, pos.y, pos.z);
                boolean entity = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z) != null;
                HyPedestalsCommand.reply(context, "setblock %s at %s rotation=%d: setBlock=%b -> %s rotation=%s blockEntity=%b",
                        id, HyPedestalsCommand.format(pos), rot, set, after == null ? "null" : after.getId(),
                        describe(afterRot), entity);
            }, pos);
        }

        private static String describe(int index) {
            RotationTuple r = index >= 0 && index < RotationTuple.VALUES.length ? RotationTuple.VALUES[index] : null;
            return r == null ? index + "(?)" : index + "(yaw=" + r.yaw() + " pitch=" + r.pitch() + " roll=" + r.roll() + ")";
        }
    }

    /**
     * {@code contents <x y z>}: block id and item container of the block at the position (slots in
     * use, item total, and each non-empty slot), e.g. to watch a chest that a third-party mod fills.
     */
    private static final class ContentsCommand extends HyPedestalsCommand.PositionCommand {
        ContentsCommand() {
            super("contents", "Show the item container of the block at a position");
        }

        @Override
        int loadFlags() {
            return super.loadFlags() | GetChunkFlags.NO_GENERATE;
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", describe(world, pos)), pos);
        }

        private static String describe(World world, Vector3i pos) {
            String at = HyPedestalsCommand.format(pos);
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> store = chunkStore.getStore();
            Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (section == null) {
                return "contents at " + at + ": section not loaded";
            }
            BlockType type = BlockType.getAssetMap().getAsset(new SectionReader(chunkStore).getBlock(pos.x, pos.y, pos.z));
            String id = type == null ? "null" : type.getId();
            Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
            ItemContainerBlock block = ref == null ? null : store.getComponent(ref, ItemContainerBlock.getComponentType());
            if (block == null) {
                return "contents at " + at + ": " + id + " has no item container";
            }
            ItemContainer container = block.getItemContainer();
            int used = 0;
            int total = 0;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack != null && !stack.isEmpty()) {
                    used++;
                    total += stack.getQuantity();
                }
            }
            return String.format(Locale.ROOT, "contents at %s: %s slots=%d/%d items=%d [%s]",
                    at, id, used, container.getCapacity(), total, contents(container));
        }
    }

    /**
     * {@code upgrade <x y z> <add|remove> <watering|fertilizer> [--save false]}: installs or removes
     * an upgrade on the Harvester at the position without a client, replying with the upgrades
     * afterwards. A change marks the block's section for saving, as the install interaction does;
     * {@code --save false} skips that, to show that without it the change is lost on restart.
     */
    private static final class UpgradeCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> action =
                withRequiredArg("action", "add or remove", ArgTypes.STRING);
        private final RequiredArg<String> upgrade =
                withRequiredArg("upgrade", "watering or fertilizer", ArgTypes.STRING);
        private final DefaultArg<Boolean> save = withDefaultArg("save",
                "Mark the block for saving after a change (false = persistence control)", ArgTypes.BOOLEAN, true, "true");

        UpgradeCommand() {
            super("upgrade", "Install or remove an upgrade on the Harvester at a position");
        }

        @Override
        int loadFlags() {
            return super.loadFlags() | GetChunkFlags.NO_GENERATE;
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            String act = context.get(action).toLowerCase(Locale.ROOT);
            UpgradeType type = parseType(context.get(upgrade));
            boolean mark = context.get(save);
            if (!(act.equals("add") || act.equals("remove")) || type == null) {
                HyPedestalsCommand.reply(context,
                        "usage: hypedestals dev upgrade <x y z> <add|remove> <watering|fertilizer> [--save false]");
                return CompletableFuture.completedFuture(null);
            }
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s",
                    apply(world, pos, act.equals("add"), type, mark)), pos);
        }

        private static UpgradeType parseType(String name) {
            for (UpgradeType type : UpgradeType.values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }

        private static String apply(World world, Vector3i pos, boolean add, UpgradeType type, boolean mark) {
            String at = HyPedestalsCommand.format(pos);
            Store<ChunkStore> store = world.getChunkStore().getStore();
            Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
            HarvesterComponent harvester = ref == null ? null : store.getComponent(ref, HarvesterComponent.TYPE);
            if (harvester == null) {
                return "upgrade: no Harvester at " + at;
            }
            UpgradeSet upgrades = harvester.upgrades();
            boolean changed = add ? upgrades.install(type) : upgrades.remove(type);
            boolean marked = false;
            if (changed && mark) {
                BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
                if (info != null) {
                    info.markNeedsSaving(store);
                    marked = true;
                }
            }
            return String.format(Locale.ROOT, "upgrade %s %s at %s: changed=%b markedForSave=%b upgrades=%s",
                    add ? "add" : "remove", type.displayName(), at, changed, marked, upgrades);
        }
    }

    /**
     * {@code state <x y z> <Default|Watering|Fertilizer|Both>}: puts the block at the position into
     * that {@code State.Definitions} entry with {@code setBlockInteractionState}, the call the
     * Harvester uses for its upgrades (upgrades spike S3). Replies with the id before and after,
     * whether the block entity and its Harvester component are the same objects afterwards, and the
     * texture and particles of the block type now in place. On a Harvester the next cycle puts back
     * the state of its upgrades.
     */
    private static final class StateCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> state = withRequiredArg("state",
                "Default, Watering, Fertilizer or Both (any state name of the block)", ArgTypes.STRING);

        StateCommand() {
            super("state", "Put the block at a position into a named block state (visual test)");
        }

        @Override
        int loadFlags() {
            return super.loadFlags() | GetChunkFlags.NO_GENERATE;
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            String name = context.get(state);
            String key = name.equalsIgnoreCase(UpgradeVisual.DEFAULT) ? UpgradeVisual.DEFAULT : name;
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", apply(world, pos, key)), pos);
        }

        private static String apply(World world, Vector3i pos, String key) {
            String at = HyPedestalsCommand.format(pos);
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> store = chunkStore.getStore();
            Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (section == null) {
                return "state at " + at + ": section not loaded";
            }
            BlockType before = BlockType.getAssetMap().getAsset(new SectionReader(chunkStore).getBlock(pos.x, pos.y, pos.z));
            if (before == null) {
                return "state at " + at + ": unknown block";
            }
            Ref<ChunkStore> entityBefore = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
            HarvesterComponent harvesterBefore = entityBefore == null ? null : store.getComponent(entityBefore, HarvesterComponent.TYPE);

            BlockOperations.setBlockInteractionState(chunkStore, section, pos.x, pos.y, pos.z, before, key, false);

            BlockType after = BlockType.getAssetMap().getAsset(new SectionReader(chunkStore).getBlock(pos.x, pos.y, pos.z));
            Ref<ChunkStore> entityAfter = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z);
            HarvesterComponent harvesterAfter = entityAfter == null ? null : store.getComponent(entityAfter, HarvesterComponent.TYPE);
            ItemContainerBlock container = entityAfter == null ? null : store.getComponent(entityAfter, ItemContainerBlock.getComponentType());
            StringBuilder textures = new StringBuilder();
            if (after != null && after.getCustomModelTexture() != null) {
                for (var texture : after.getCustomModelTexture()) {
                    textures.append(textures.isEmpty() ? "" : ",").append(texture.getTexture());
                }
            }
            StringBuilder particles = new StringBuilder();
            if (after != null && after.getParticles() != null) {
                for (var particle : after.getParticles()) {
                    particles.append(particles.isEmpty() ? "" : ",").append(particle.getSystemId());
                }
            }
            return String.format(Locale.ROOT,
                    "state %s at %s: %s -> %s sameEntity=%b sameHarvester=%b tier=%s upgrades=%s container=%s texture=[%s] particles=[%s]",
                    key, at, before.getId(), after == null ? "null" : after.getId(),
                    entityBefore != null && entityBefore.equals(entityAfter),
                    harvesterBefore != null && harvesterBefore == harvesterAfter,
                    harvesterAfter == null ? "-" : harvesterAfter.tier(),
                    harvesterAfter == null ? "-" : harvesterAfter.upgrades(),
                    container == null ? "none" : "[" + contents(container.getItemContainer()) + "]",
                    textures, particles);
        }
    }

    /**
     * {@code water <x y z> <seconds>}: waters the tilled soil at the position until
     * {@code now + seconds} of game time, through the same {@link HytaleSoilWorld} the watering
     * upgrade uses (setter, ticking, scheduled tick, mark for saving).
     */
    private static final class WaterCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<Integer> seconds =
                withRequiredArg("seconds", "Game seconds from now", ArgTypes.INTEGER);

        WaterCommand() {
            super("water", "Water the tilled soil at a position for some game seconds");
        }

        @Override
        int loadFlags() {
            return super.loadFlags() | GetChunkFlags.NO_GENERATE;
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            int s = context.get(seconds);
            return runAt(context, world -> {
                Instant now = HytaleSoilWorld.gameTime(world);
                if (now == null) {
                    HyPedestalsCommand.reply(context, "water: no world time");
                    return;
                }
                HytaleSoilWorld soil = new HytaleSoilWorld(world, world.getChunkStore().getStore());
                BlockPos p = new BlockPos(pos.x, pos.y, pos.z);
                if (soil.soilAt(p) == null) {
                    HyPedestalsCommand.reply(context, "water: no tilled soil at %s", HyPedestalsCommand.format(pos));
                    return;
                }
                soil.water(p, now.plusSeconds(s));
                HyPedestalsCommand.reply(context, "water: %s", SoilCommand.describe(world, pos));
            }, pos);
        }
    }

    /**
     * {@code soil <x y z>}: block id, ticking flag and {@code TilledSoilBlock} fields at the
     * position, with {@code wateredUntil} relative to the world's game time.
     */
    private static final class SoilCommand extends HyPedestalsCommand.PositionCommand {
        SoilCommand() {
            super("soil", "Show the tilled soil state at a position");
        }

        @Override
        int loadFlags() {
            return super.loadFlags() | GetChunkFlags.NO_GENERATE;
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", describe(world, pos)), pos);
        }

        static String describe(World world, Vector3i pos) {
            String at = HyPedestalsCommand.format(pos);
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> store = chunkStore.getStore();
            Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (section == null) {
                return "soil at " + at + ": section not loaded";
            }
            BlockType type = BlockType.getAssetMap().getAsset(new SectionReader(chunkStore).getBlock(pos.x, pos.y, pos.z));
            String id = type == null ? "null" : type.getId();
            TilledSoilBlock soil = new HytaleSoilWorld(world, store).component(new BlockPos(pos.x, pos.y, pos.z));
            if (soil == null) {
                return "soil at " + at + ": " + id + " has no TilledSoilBlock";
            }
            BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
            Instant now = HytaleSoilWorld.gameTime(world);
            Instant until = soil.getWateredUntil();
            String left = until == null || now == null ? "-"
                    : String.format(Locale.ROOT, "%+ds", Duration.between(now, until).toSeconds());
            return String.format(Locale.ROOT,
                    "soil at %s: %s ticking=%s planted=%b fertilized=%b externalWater=%b wateredUntil=%s left=%s now=%s",
                    at, id, blocks == null ? "?" : blocks.isTicking(pos.x, pos.y, pos.z), soil.isPlanted(),
                    soil.isFertilized(), soil.hasExternalWater(), until, left, now);
        }
    }

    /**
     * {@code setitem <x y z> <slot|last> <item id|empty> [--qty N] [--durability D]}: puts a stack
     * into one slot of the item container at the position (replacing what is there), e.g. a
     * fertilizer bag into any slot of a Harvester without a client. The write goes through the
     * container's slot filters like a player's, so it is refused ({@code succeeded=false}) where
     * a filter rejects the item; {@code empty} clears the slot. {@code --durability} defaults to the
     * item's maximum. A successful write marks the block for saving.
     */
    private static final class SetItemCommand extends HyPedestalsCommand.PositionCommand {
        private final RequiredArg<String> slot =
                withRequiredArg("slot", "Slot index from 0, or last", ArgTypes.STRING);
        private final RequiredArg<String> item =
                withRequiredArg("item", "Item id (e.g. Tool_Fertilizer) or empty", ArgTypes.STRING);
        private final DefaultArg<Integer> qty = withDefaultArg("qty", "Stack quantity", ArgTypes.INTEGER, 1, "1");
        private final DefaultArg<Double> durability = withDefaultArg("durability",
                "Durability (negative = the item's maximum)", ArgTypes.DOUBLE, -1.0, "-1");

        SetItemCommand() {
            super("setitem", "Put an item stack into one slot of the container at a position");
        }

        @Override
        int loadFlags() {
            return super.loadFlags() | GetChunkFlags.NO_GENERATE;
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            String slotArg = context.get(slot);
            String itemId = context.get(item);
            int quantity = context.get(qty);
            double d = context.get(durability);
            return runAt(context, world -> HyPedestalsCommand.reply(context, "%s",
                    apply(world, pos, slotArg, itemId, quantity, d)), pos);
        }

        private static String apply(World world, Vector3i pos, String slotArg, String itemId, int quantity, double d) {
            String at = HyPedestalsCommand.format(pos);
            Store<ChunkStore> store = world.getChunkStore().getStore();
            Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
            ItemContainerBlock block = ref == null ? null : store.getComponent(ref, ItemContainerBlock.getComponentType());
            if (block == null) {
                return "setitem: no item container at " + at;
            }
            ItemContainer container = block.getItemContainer();
            short capacity = container.getCapacity();
            short s;
            try {
                s = slotArg.equalsIgnoreCase("last") ? (short) (capacity - 1) : Short.parseShort(slotArg);
            } catch (NumberFormatException e) {
                s = -1;
            }
            if (s < 0 || s >= capacity) {
                return String.format(Locale.ROOT, "setitem: slot %s outside 0..%d at %s", slotArg, capacity - 1, at);
            }
            boolean clear = itemId.equalsIgnoreCase("empty");
            if (!clear && (Item.getAssetMap().getAsset(itemId) == null || quantity < 1)) {
                return "setitem: unknown item " + itemId + " or quantity < 1";
            }
            boolean succeeded;
            if (clear) {
                succeeded = container.removeItemStackFromSlot(s).succeeded();
            } else {
                ItemStack stack = new ItemStack(itemId, quantity);
                if (d >= 0) {
                    stack = stack.withDurability(d);
                }
                succeeded = container.setItemStackForSlot(s, stack).succeeded();
            }
            BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
            if (succeeded && info != null) {
                info.markNeedsSaving(store);
            }
            ItemStack now = container.getItemStack(s);
            return String.format(Locale.ROOT, "setitem %d at %s: succeeded=%b slot=%s", s, at, succeeded,
                    now == null || now.isEmpty() ? "empty" : String.format(Locale.ROOT, "%s x%d durability=%s/%s",
                            now.getItemId(), now.getQuantity(), now.getDurability(), now.getMaxDurability()));
        }
    }
}
