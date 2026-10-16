package dev.tchorizo.hypedestals.block;

/** Pure helpers for Hytale asset ids; no server types, so they are unit-testable without the jar. */
public final class HytaleIds {

    private static final String STATE_PREFIX = "*";
    private static final String STATE_MARKER = "_State_Definitions_";

    private HytaleIds() {}

    /**
     * Base block id of a possibly state-variant id: once a crop reaches a named state the game
     * reports e.g. {@code *Plant_Crop_Wheat_Block_State_Definitions_StageFinal}; this returns
     * {@code Plant_Crop_Wheat_Block}. Base ids are returned unchanged; {@code null} stays {@code null}.
     */
    public static String baseBlockId(String blockId) {
        if (blockId == null) {
            return null;
        }
        String id = blockId.startsWith(STATE_PREFIX) ? blockId.substring(STATE_PREFIX.length()) : blockId;
        int marker = id.indexOf(STATE_MARKER);
        return marker >= 0 ? id.substring(0, marker) : id;
    }
}
