package dev.tchorizo.hypedestals.block;

import dev.tchorizo.hypedestals.HyPedestalsPlugin;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.builtin.adventure.farming.config.stages.PrefabFarmingStageData;
import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.protocol.BlockConditionInteraction;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.BlockIdMatcher;
import com.hypixel.hytale.protocol.BlockMatcher;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.PlaceBlockInteraction;
import com.hypixel.hytale.protocol.ReplaceInteraction;
import com.hypixel.hytale.protocol.SimpleInteraction;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the Planter upgrade (and the Lumberjack's replanting) can plant, read from the item and
 * interaction assets at runtime, with no table of ids (planter spec §0 S1–S3):
 *
 * <ul>
 *   <li><b>Seed:</b> an item whose {@code Secondary} interaction, walked as if its user clicked
 *       the top of a given soil block, ends in a {@code PlaceBlock} of a block with
 *       {@code Farming} and no {@code Prefab} stage. The walk reads the public network packets of
 *       the assets ({@link Interaction#toPacket()}): {@code BlockCondition} matchers (block id,
 *       state, tag; face up) choose {@code next} or {@code failed}, {@code Replace} follows the
 *       item's {@code InteractionVars} (the vanilla {@code SeedId}), {@code PlaceBlock} gives the
 *       block index. So the soil decides as in game: vanilla seeds on tilled soil, herbs on
 *       crystal soil, LifeCrops seeds on its essence soils (another block there).</li>
 *   <li><b>Sapling:</b> a block item ({@code Plant_Sapling_*}: {@code Secondary} is the generic
 *       {@code Block_Secondary}, no seed interaction) whose block has a {@code Prefab} farming
 *       stage. Vanilla saplings stand on {@code Type=Soil} ({@link #isSaplingSoil}).</li>
 * </ul>
 * Results are cached per item (and soil block) for the server's life: assets do not change while
 * it runs. Thread-safe.
 */
public final class HytalePlantables {

    private static final int MAX_DEPTH = 24;
    private static final String SOIL_TAG = "Type=Soil";
    private static final String TILLED_TAG = "SubType=Tilled";

    /** {@code itemId + '\0' + soil block id} → planted block id (empty: nothing). */
    private static final Map<String, Optional<String>> SEEDS = new ConcurrentHashMap<>();
    /** item id → sapling block id (empty: not a sapling). */
    private static final Map<String, Optional<String>> SAPLINGS = new ConcurrentHashMap<>();
    /** item id → whether it is a seed on some soil or a sapling (export stock). */
    private static final Map<String, Boolean> PLANTABLE = new ConcurrentHashMap<>();

    /** Item ids whose interaction walk threw; warned about once each. */
    private static final java.util.Set<String> RESOLVE_FAILED = ConcurrentHashMap.newKeySet();

    private HytalePlantables() {}

    /**
     * The block {@code itemId} plants on top of {@code soil}, or {@code null}: not a seed, does
     * not grow there, or grows a tree (tree seeds are creative-only in 0.7.2; saplings are planted
     * through {@link #saplingBlock} instead).
     */
    public static String seedBlockOn(String itemId, BlockType soil) {
        if (itemId == null || soil == null) {
            return null;
        }
        return SEEDS.computeIfAbsent(itemId + '\0' + soil.getId(), key -> {
            Item item = Item.getAssetMap().getAsset(itemId);
            String block;
            try {
                block = item == null ? null : resolve(item, soil, null);
            } catch (RuntimeException e) {
                warnOnce(itemId, e);
                block = null; // cached as "nothing": the walk is not retried every cycle
            }
            return Optional.ofNullable(isCrop(block) ? block : null);
        }).orElse(null);
    }

    /** The sapling block the item {@code itemId} places, or {@code null} when it is not a sapling. */
    public static String saplingBlock(String itemId) {
        if (itemId == null) {
            return null;
        }
        return SAPLINGS.computeIfAbsent(itemId, id -> {
            Item item = Item.getAssetMap().getAsset(id);
            String block = item == null || !item.hasBlockType() ? null : item.getBlockId();
            return Optional.ofNullable(isTree(block) ? block : null);
        }).orElse(null);
    }

    /** Whether {@code itemId} is a sapling or a seed that grows on at least one soil (any branch of its interaction). */
    public static boolean isPlantable(String itemId) {
        if (itemId == null) {
            return false;
        }
        return PLANTABLE.computeIfAbsent(itemId, id -> {
            if (saplingBlock(id) != null) {
                return true;
            }
            Item item = Item.getAssetMap().getAsset(id);
            if (item == null) {
                return false;
            }
            List<String> blocks = new ArrayList<>(2);
            try {
                for (Interaction i : rootInteractions(item)) {
                    collectAll(i, item, blocks, 0);
                }
            } catch (RuntimeException e) {
                warnOnce(id, e);
                return false;
            }
            return blocks.stream().anyMatch(HytalePlantables::isCrop);
        });
    }

    /** Logs, once per item id, that its interaction assets could not be walked. */
    private static void warnOnce(String itemId, RuntimeException e) {
        if (RESOLVE_FAILED.add(itemId)) {
            HyPedestalsPlugin.warn("planter: could not read the seed interaction of %s (treated as not plantable): %s",
                    itemId, e.toString());
        }
    }

    /** Whether a sapling can stand on {@code type} (the vanilla saplings' {@code Support.Down} tag). */
    public static boolean isSaplingSoil(BlockType type) {
        return hasTag(type, SOIL_TAG);
    }

    /** Whether {@code type} is farmland: it carries a {@code TilledSoil} component or the {@code SubType=Tilled} tag. */
    public static boolean isFarmland(BlockType type) {
        return type != null && (BlockEntity.declaresComponent(type, TilledSoilBlock.getComponentType()) || hasTag(type, TILLED_TAG));
    }

    /**
     * Walks {@code item}'s {@code Secondary} as if used on the top of {@code soil}; the block it
     * places or {@code null}. With {@code chain}, appends one line per step (dev diagnostics).
     */
    public static String resolve(Item item, BlockType soil, List<String> chain) {
        for (Interaction i : rootInteractions(item)) {
            String block = walk(i, item, soil, chain, 0);
            if (block != null) {
                return block;
            }
        }
        if (chain != null && chain.isEmpty()) {
            chain.add("no Secondary root interaction");
        }
        return null;
    }

    private static List<Interaction> rootInteractions(Item item) {
        String rootId = item.getInteractions() == null ? null : item.getInteractions().get(InteractionType.Secondary);
        RootInteraction root = rootId == null ? null : RootInteraction.getAssetMap().getAsset(rootId);
        if (root == null) {
            return List.of();
        }
        List<Interaction> out = new ArrayList<>();
        for (String id : root.getInteractionIds()) {
            Interaction i = Interaction.getAssetMap().getAsset(id);
            if (i != null) {
                out.add(i);
            }
        }
        return out;
    }

    private static String walk(Interaction interaction, Item item, BlockType soil, List<String> chain, int depth) {
        if (interaction == null || depth > MAX_DEPTH) {
            return null;
        }
        com.hypixel.hytale.protocol.Interaction packet = interaction.toPacket();
        if (packet instanceof PlaceBlockInteraction place) {
            String block = placed(place, item);
            note(chain, "place " + interaction.getId() + " -> " + block);
            return block;
        }
        if (packet instanceof BlockConditionInteraction condition) {
            boolean match = matches(condition.matchers, soil);
            note(chain, "condition " + interaction.getId() + " on " + soil.getId() + " -> " + match);
            return walk(child(match ? condition.next : condition.failed), item, soil, chain, depth + 1);
        }
        if (packet instanceof ReplaceInteraction replace) {
            note(chain, "replace " + interaction.getId() + " var=" + replace.variable);
            for (Interaction i : replaced(replace, item)) {
                String block = walk(i, item, soil, chain, depth + 1);
                if (block != null) {
                    return block;
                }
            }
            return null;
        }
        if (packet instanceof SimpleInteraction simple) {
            note(chain, "pass " + interaction.getId());
            return walk(child(simple.next), item, soil, chain, depth + 1);
        }
        note(chain, "stop " + interaction.getId() + " (" + packet.getClass().getSimpleName() + ")");
        return null;
    }

    /** Every block any branch of the chain can place, conditions taken both ways. */
    private static void collectAll(Interaction interaction, Item item, List<String> blocks, int depth) {
        if (interaction == null || depth > MAX_DEPTH) {
            return;
        }
        com.hypixel.hytale.protocol.Interaction packet = interaction.toPacket();
        if (packet instanceof PlaceBlockInteraction place) {
            String block = placed(place, item);
            if (block != null) {
                blocks.add(block);
            }
        } else if (packet instanceof BlockConditionInteraction condition) {
            collectAll(child(condition.next), item, blocks, depth + 1);
            collectAll(child(condition.failed), item, blocks, depth + 1);
        } else if (packet instanceof ReplaceInteraction replace) {
            for (Interaction i : replaced(replace, item)) {
                collectAll(i, item, blocks, depth + 1);
            }
        } else if (packet instanceof SimpleInteraction simple) {
            collectAll(child(simple.next), item, blocks, depth + 1);
        }
    }

    /** The block a {@code PlaceBlock} places: its own, or the held item's block when it names none. */
    private static String placed(PlaceBlockInteraction place, Item item) {
        if (place.blockId >= 0) {
            BlockType type = BlockType.getAssetMap().getAsset(place.blockId);
            return type == null ? null : type.getId();
        }
        return item.hasBlockType() ? item.getBlockId() : null;
    }

    /** The interactions a {@code Replace} runs: the item's variable, else its default root interaction. */
    private static List<Interaction> replaced(ReplaceInteraction replace, Item item) {
        Map<String, String> vars = item.getInteractionVars();
        String var = vars == null || replace.variable == null ? null : vars.get(replace.variable);
        RootInteraction target = var != null ? RootInteraction.getAssetMap().getAsset(var)
                : replace.defaultValue < 0 ? null : RootInteraction.getAssetMap().getAsset(replace.defaultValue);
        if (target == null) {
            return List.of();
        }
        List<Interaction> out = new ArrayList<>();
        for (String id : target.getInteractionIds()) {
            Interaction i = Interaction.getAssetMap().getAsset(id);
            if (i != null) {
                out.add(i);
            }
        }
        return out;
    }

    private static Interaction child(int index) {
        return index < 0 ? null : Interaction.getAssetMap().getAsset(index);
    }

    /**
     * A matcher of a seed condition against the clicked soil: face up (or unspecified); by id
     * (the base id matches any state of it unless the matcher names a state, a full state id only
     * itself), or by tag.
     */
    private static boolean matches(BlockMatcher[] matchers, BlockType soil) {
        if (matchers == null || soil == null) {
            return false;
        }
        String full = soil.getId();
        String base = HytaleIds.baseBlockId(full);
        String state = HytaleIds.stateName(full);
        for (BlockMatcher m : matchers) {
            if (m == null || m.block == null || (m.face != null && m.face != BlockFace.Up && m.face != BlockFace.None)) {
                continue;
            }
            BlockIdMatcher b = m.block;
            if (b.id != null) {
                if (b.id.equals(full) || (b.id.equals(base) && (b.state == null || b.state.equals(state)))) {
                    return true;
                }
            } else if (soil.getData() != null && soil.getData().getExpandedTagIndexes().contains(b.tagIndex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTag(BlockType type, String tag) {
        if (type == null || type.getData() == null) {
            return false;
        }
        int index = AssetRegistry.getTagIndex(tag);
        return index != AssetRegistry.TAG_NOT_FOUND && type.getData().getExpandedTagIndexes().contains(index);
    }

    private static boolean isCrop(String blockId) {
        FarmingData farming = farming(blockId);
        return farming != null && !hasPrefabStage(farming);
    }

    private static boolean isTree(String blockId) {
        FarmingData farming = farming(blockId);
        return farming != null && hasPrefabStage(farming);
    }

    private static FarmingData farming(String blockId) {
        BlockType type = blockId == null ? null : BlockType.getAssetMap().getAsset(blockId);
        return type == null ? null : type.getFarming();
    }

    private static boolean hasPrefabStage(FarmingData farming) {
        for (FarmingStageData[] stages : farming.getStages().values()) {
            for (FarmingStageData stage : stages) {
                if (stage instanceof PrefabFarmingStageData) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void note(List<String> chain, String line) {
        if (chain != null) {
            chain.add(line);
        }
    }
}
