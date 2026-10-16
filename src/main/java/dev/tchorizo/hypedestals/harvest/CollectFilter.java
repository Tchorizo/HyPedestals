package dev.tchorizo.hypedestals.harvest;

/**
 * Which dropped items a Harvester pulls into its storage. With {@code CollectAnyItem} on (the
 * default) it takes everything in its collect box. With it off it leaves equipment on the
 * ground: items whose definition is a tool, weapon or armor (the {@code Tool}, {@code Weapon}
 * and {@code Armor} sections of the item asset), and items without a loaded definition. Crops,
 * seeds, Life Essence and other materials are still collected. The point is to not swallow the
 * gear a player drops (or dies with) next to the farm.
 */
public final class CollectFilter {

    private CollectFilter() {}

    /**
     * @param known whether the stack's item definition is loaded; the other flags are ignored when not
     */
    public static boolean accepts(boolean collectAnyItem, boolean known, boolean tool, boolean weapon, boolean armor) {
        if (collectAnyItem) {
            return true;
        }
        return known && !tool && !weapon && !armor;
    }
}
