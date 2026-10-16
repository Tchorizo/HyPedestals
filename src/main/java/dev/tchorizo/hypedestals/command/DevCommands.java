package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import dev.tchorizo.hypedestals.block.HarvesterComponent;
import dev.tchorizo.hypedestals.block.HytaleIds;
import org.joml.Vector3i;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@code /hypedestals dev …}: world setup for testing the harvest cycle on a dedicated server with
 * no player. Registered only when {@link HyPedestalsCommand#DEV_PROPERTY} is set.
 */
final class DevCommands extends AbstractAsyncCommand {

    private static final List<String> TIERS = List.of("Copper", "Iron", "Thorium", "Cobalt");
    private static final String HARVESTER_PREFIX = "HyPedestals_Harvester_";
    private static final String TILLED_SOIL = "Soil_Dirt_Tilled";
    private static final String WHEAT = "Plant_Crop_Wheat_Block";
    private static final String FINAL_STAGE_STATE = "StageFinal";
    /** Positions pinned by {@link #pin}; one refresh loop each. */
    private static final Set<Vector3i> PINNED = ConcurrentHashMap.newKeySet();

    DevCommands() {
        super("dev", "HyPedestals development commands");
        addSubCommand(new PlaceCommand());
        addSubCommand(new PlantCommand());
        addSubCommand(new RipenCommand());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        HyPedestalsCommand.reply(context,
                "usage: hypedestals dev place <x y z> <%s> | plant <x y z> | ripen <x y z>",
                String.join("|", TIERS));
        return CompletableFuture.completedFuture(null);
    }

    /** {@code setBlock} at {@code p}; the block's section must already be loaded. */
    private static boolean setBlock(World world, Vector3i p, String blockId, BlockType type) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(p.x, p.y, p.z);
        return section != null && BlockOperations.setBlock(chunkStore, section, p.x, p.y, p.z,
                BlockType.getAssetMap().getIndex(blockId), type, 0, 0, SetBlockSettings.NONE);
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

    /** Tilled soil below and stage-zero wheat at the position. */
    private static final class PlantCommand extends HyPedestalsCommand.PositionCommand {
        PlantCommand() {
            super("plant", "Place tilled soil and stage-zero wheat");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i pos = position(context);
            Vector3i below = new Vector3i(pos.x, pos.y - 1, pos.z);
            return runAt(context, world -> {
                BlockType soil = BlockType.getAssetMap().getAsset(TILLED_SOIL);
                BlockType wheat = BlockType.getAssetMap().getAsset(WHEAT);
                if (soil == null || wheat == null) {
                    HyPedestalsCommand.reply(context, "plant: missing asset %s=%b %s=%b",
                            TILLED_SOIL, soil != null, WHEAT, wheat != null);
                    return;
                }
                boolean soilSet = setBlock(world, below, TILLED_SOIL, soil);
                boolean wheatSet = setBlock(world, pos, WHEAT, wheat);
                HyPedestalsCommand.reply(context, "plant at %s: soil=%b wheat=%b",
                        HyPedestalsCommand.format(pos), soilSet, wheatSet);
            }, pos, below);
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
    }
}
