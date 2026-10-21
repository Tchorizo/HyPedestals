package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import dev.tchorizo.hypedestals.lumber.TreePart;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Which blocks are trees to the Lumberjack (spec Ruling A): wood = the vanilla block type list
 * {@code TreeWood} (trunks, branches, roots; base id), leaves = the list {@code TreeLeaves} or the
 * tag {@code Family=Leaves}. The lists are assets read on every call (a pack can extend them); the
 * tags alone would also take {@code Debug_MusicEmitter_*} and the fern trunks, the lists alone
 * would miss the fir tips. Apple and bamboo wood are in neither list: never cut.
 */
public final class HytaleTrees {

    static final String WOOD_LIST = "TreeWood";
    static final String LEAVES_LIST = "TreeLeaves";

    private HytaleTrees() {}

    /** The current {@code TreeWood} and {@code TreeLeaves} lists, for a caller classifying many blocks. */
    record Lists(Set<String> wood, Set<String> leaves) {
        static Lists current() {
            return new Lists(list(WOOD_LIST), list(LEAVES_LIST));
        }

        TreePart part(BlockType type) {
            if (type == null || type.getId() == null) {
                return TreePart.NONE;
            }
            String base = HytaleIds.baseBlockId(type.getId());
            if (wood.contains(base)) {
                return TreePart.WOOD;
            }
            return leaves.contains(base) || hasTag(type, "Family", "Leaves") ? TreePart.LEAVES : TreePart.NONE;
        }
    }

    /** The tree part of {@code type} ({@link TreePart#NONE} for {@code null}). */
    public static TreePart part(BlockType type) {
        return Lists.current().part(type);
    }

    private static Set<String> list(String id) {
        BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(id);
        return asset == null ? Set.of() : asset.getBlockTypeKeys();
    }

    static boolean hasTag(BlockType type, String key, String value) {
        Map<String, String[]> tags = type.getData() == null ? null : type.getData().getRawTags();
        String[] values = tags == null ? null : tags.get(key);
        return values != null && Arrays.asList(values).contains(value);
    }
}
