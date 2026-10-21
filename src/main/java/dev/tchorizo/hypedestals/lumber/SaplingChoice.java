package dev.tchorizo.hypedestals.lumber;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which sapling the Lumberjack plants back (spec Ruling G): the one of the cut tree's species
 * when the container has it, else the first one in slot order. The species comes from the wood's
 * id ({@code Wood_<Species>_Trunk|Branch…|Roots}) and names the sapling
 * {@code Plant_Sapling_<Species>}; species whose names differ (Fir wood, Spruce sapling) fall back
 * to the first sapling. Pure.
 */
public final class SaplingChoice {

    private static final Pattern WOOD = Pattern.compile("^Wood_(.+?)_(?:Trunk|Branch|Roots)(?:_.*)?$");

    private SaplingChoice() {}

    /** {@code Plant_Sapling_<Species>} for a vanilla wood id, or {@code null} when the id does not follow the pattern. */
    public static String saplingOf(String woodId) {
        if (woodId == null) {
            return null;
        }
        Matcher m = WOOD.matcher(woodId);
        return m.matches() ? "Plant_Sapling_" + m.group(1) : null;
    }

    /** The sapling to plant from {@code saplings} (slot order), or {@code null} when there is none. */
    public static String pick(String woodId, List<String> saplings) {
        if (saplings.isEmpty()) {
            return null;
        }
        String same = saplingOf(woodId);
        return same != null && saplings.contains(same) ? same : saplings.getFirst();
    }
}
