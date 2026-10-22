package dev.tchorizo.hypedestals.harvest;

import java.util.List;
import java.util.Map;

/**
 * Which crops the Harvester brings back to life. A growing crop is a block with a
 * {@code FarmingBlock} entity; one that lost it (a world tick that failed after the harvest
 * swapped the block to its first regrowth stage but before the entity was added) stands there as a
 * young plant that never grows. Pure; the world-side reads are in {@code HytaleReviver}.
 */
public final class ReviveRule {

    private ReviveRule() {}

    /**
     * Where a block's state sits in the crop's stage sets.
     *
     * @param stageSet the set the entity is created with
     * @param index position of the state in that set (the integer part of {@code growthProgress})
     * @param last index of the set's final stage
     */
    public record Stage(String stageSet, int index, int last) {
        public boolean isFinal() {
            return index >= last;
        }
    }

    /**
     * Places {@code state} (the block's state name, {@code "default"} for the base id) in the sets,
     * given as set name → the state of each stage in order ({@code null} for a stage that is not a
     * block state). The set {@code afterHarvest} wins, since a young plant without entity is what a
     * harvest leaves behind, then {@code starting}, then any other set in map order; the first
     * stage of the set with that state. {@code null} when no set has it.
     */
    public static Stage stageOf(Map<String, List<String>> sets, String afterHarvest, String starting, String state) {
        Stage found = in(sets, afterHarvest, state);
        if (found == null) {
            found = in(sets, starting, state);
        }
        if (found == null) {
            for (String name : sets.keySet()) {
                found = in(sets, name, state);
                if (found != null) {
                    break;
                }
            }
        }
        return found;
    }

    private static Stage in(Map<String, List<String>> sets, String name, String state) {
        List<String> states = name == null ? null : sets.get(name);
        if (states == null) {
            return null;
        }
        int index = states.indexOf(state);
        return index < 0 ? null : new Stage(name, index, states.size() - 1);
    }

    /**
     * Whether a block in {@code stage} gets its entity back: it has none, live or parked (a parked
     * {@code Holder} at the index makes {@code addEntity} throw {@code Duplicate block components}),
     * it is not in the final stage (a ripe crop without entity is the normal state: the harvest
     * creates it) and its section ticks.
     */
    public static boolean shouldRevive(Stage stage, boolean liveEntity, boolean parkedEntity, boolean ticking) {
        return stage != null && !stage.isFinal() && !liveEntity && !parkedEntity && ticking;
    }
}
