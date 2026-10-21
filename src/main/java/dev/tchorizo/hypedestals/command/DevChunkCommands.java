package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonTicking;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import org.joml.Vector3i;

import java.util.concurrent.CompletableFuture;

/**
 * Section ticking state, for the {@code Duplicate block components} investigation (Task 40):
 * {@code chunkstate} reads it, {@code park} forces what the engine's {@code SectionUnloadingSystem}
 * does to a section nobody is near ({@code NonTicking}: the block entities of the section are
 * stored as {@code Holder}s in {@code BlockComponentSection}, not as live {@code Ref}s) and
 * {@code unpark} undoes it. None of them loads a section: they act on what is in memory, so the
 * state they read is the one the Harvester's cycle would see.
 */
final class DevChunkCommands {

    private DevChunkCommands() {}

    static void register(AbstractAsyncCommand parent) {
        parent.addSubCommand(new ChunkStateCommand());
        parent.addSubCommand(new ParkCommand());
        parent.addSubCommand(new UnparkCommand());
    }

    private static ComponentType<ChunkStore, NonTicking<ChunkStore>> nonTicking() {
        return ChunkStore.REGISTRY.getNonTickingComponentType();
    }

    /**
     * Runs {@code action} on the world thread with the section ref of {@code p} (or {@code null}).
     * {@code loadFlags >= 0} first asks the chunk store for the section with those flags (never
     * {@code SET_TICKING}: a section loaded that way comes up {@code NonTicking}); {@code -1} acts only
     * on what is already in memory.
     */
    private static CompletableFuture<Void> onSection(CommandContext context, Vector3i p, int loadFlags,
            java.util.function.BiConsumer<World, Ref<ChunkStore>> action) {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            HyPedestalsCommand.reply(context, "no default world loaded");
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?> loaded = loadFlags < 0 ? CompletableFuture.completedFuture(null)
                : world.getChunkStore().getChunkSectionReferenceAtBlockAsync(p.x, p.y, p.z, loadFlags);
        CompletableFuture<Void> result = new CompletableFuture<>();
        loaded.whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            world.execute(() -> {
                try {
                    Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(p.x, p.y, p.z);
                    action.accept(world, section != null && section.isValid() ? section : null);
                    result.complete(null);
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
        });
        return result;
    }

    /**
     * {@code chunkstate <x y z>}: {@code section=<loaded|not loaded> ticking=<bool> parkedHolders=<n>
     * block=<id> ref=<none|farming=… |other> holder=<none|farming=… |other>} for the block at the
     * position. {@code ref} is the live block entity ({@code BlockModule.getBlockEntity}), {@code holder}
     * the parked one ({@code BlockComponentSection.getBlockHolder}); {@code farming=<stageSet>/<progress>}
     * when it carries a {@code FarmingBlock}.
     */
    private static final class ChunkStateCommand extends HyPedestalsCommand.PositionCommand {
        ChunkStateCommand() {
            super("chunkstate", "Section ticking state and block entity (live ref or parked holder) at a position");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            return onSection(context, p, -1, (world, section) -> {
                if (section == null) {
                    HyPedestalsCommand.reply(context, "chunkstate at %s: section=not loaded", HyPedestalsCommand.format(p));
                    return;
                }
                Store<ChunkStore> store = world.getChunkStore().getStore();
                boolean ticking = !store.getArchetype(section).contains(nonTicking());
                BlockComponentSection components = store.getComponent(section, BlockComponentSection.getComponentType());
                int idx = ChunkUtil.indexBlock(p.x, p.y, p.z);
                Ref<ChunkStore> ref = BlockModule.getBlockEntity(store, section, p.x, p.y, p.z);
                String live = "none";
                if (ref != null) {
                    FarmingBlock farm = store.getComponent(ref, FarmingBlock.getComponentType());
                    live = farm == null ? "other" : farming(farm);
                }
                String parked = "none";
                if (components != null) {
                    Holder<ChunkStore> holder = components.getBlockHolder(idx);
                    if (holder != null) {
                        FarmingBlock farm = holder.getComponent(FarmingBlock.getComponentType());
                        parked = farm == null ? "other" : farming(farm);
                    }
                }
                BlockType block = BlockType.getAssetMap().getAsset(new SectionReader(world.getChunkStore()).getBlock(p.x, p.y, p.z));
                HyPedestalsCommand.reply(context, "chunkstate at %s: section=loaded ticking=%b parkedHolders=%d block=%s ref=%s holder=%s",
                        HyPedestalsCommand.format(p), ticking, components == null ? -1 : components.getBlockHolders().size(),
                        block == null ? "null" : block.getId(), live, parked);
            });
        }

        private static String farming(FarmingBlock farm) {
            return String.format(java.util.Locale.ROOT, "farming=%s/%.2f", farm.getCurrentStageSet(), farm.getGrowthProgress());
        }
    }

    /**
     * {@code park <x y z> [--keep true]}: puts {@code NonTicking} on the section (what the engine does
     * once nobody keeps it active): its live block entities become parked holders. {@code --keep}
     * (default true) also keeps the chunk loaded, so the engine does not unload it. A pinned chunk
     * ({@code dev pin}/{@code platform}) un-parks again within a second.
     */
    private static final class ParkCommand extends HyPedestalsCommand.PositionCommand {
        private final DefaultArg<Boolean> keep = withDefaultArg("keep", "Keep the chunk loaded", ArgTypes.BOOLEAN, true, "true");

        ParkCommand() {
            super("park", "Make a loaded section non-ticking (block entities become parked holders)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            boolean keepLoaded = context.get(keep);
            return onSection(context, p, GetChunkFlags.NO_GENERATE | GetChunkFlags.HIGH_PRIORITY, (world, section) -> {
                if (section == null) {
                    HyPedestalsCommand.reply(context, "park at %s: section not loaded", HyPedestalsCommand.format(p));
                    return;
                }
                if (keepLoaded) {
                    WorldChunk chunk = world.getChunkStore().getChunkComponent(
                            ChunkUtil.indexChunkFromBlock(p.x, p.z), WorldChunk.getComponentType());
                    if (chunk != null) {
                        chunk.addKeepLoaded();
                    }
                }
                world.getChunkStore().getStore().ensureComponent(section, nonTicking());
                HyPedestalsCommand.reply(context, "park at %s: non-ticking%s", HyPedestalsCommand.format(p),
                        keepLoaded ? " (chunk kept loaded)" : "");
            });
        }
    }

    /** {@code unpark <x y z>}: removes {@code NonTicking} (parked holders become live block entities). */
    private static final class UnparkCommand extends HyPedestalsCommand.PositionCommand {
        UnparkCommand() {
            super("unpark", "Make a loaded section ticking again (what SET_TICKING does)");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            Vector3i p = position(context);
            return onSection(context, p, -1, (world, section) -> {
                if (section == null) {
                    HyPedestalsCommand.reply(context, "unpark at %s: section not loaded", HyPedestalsCommand.format(p));
                    return;
                }
                world.getChunkStore().getStore().tryRemoveComponent(section, nonTicking());
                HyPedestalsCommand.reply(context, "unpark at %s: ticking", HyPedestalsCommand.format(p));
            });
        }
    }
}
