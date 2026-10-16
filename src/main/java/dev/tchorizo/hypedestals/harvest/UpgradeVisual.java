package dev.tchorizo.hypedestals.harvest;

/**
 * Which {@code State.Definitions} entry of the Harvester block shows the installed upgrades. The
 * entries are repeated in each tier's asset (an inherited {@code State} would point at the
 * Template's own variant blocks) and differ only in {@code Particles}: blue sparks for watering,
 * green spores for the fertilizer, both for both, none in the default state.
 */
public final class UpgradeVisual {

    /** The block's base form: Hytale's state name for "no named state" ({@code StateData.NULL_STATE_ID}). */
    public static final String DEFAULT = "default";
    public static final String WATERING = "Watering";
    public static final String FERTILIZER = "Fertilizer";
    public static final String BOTH = "Both";

    private UpgradeVisual() {}

    /** State name for {@code upgrades}, independent of installation order. */
    public static String stateFor(UpgradeSet upgrades) {
        boolean watering = upgrades.has(UpgradeType.WATERING);
        boolean fertilizer = upgrades.has(UpgradeType.FERTILIZER);
        if (watering && fertilizer) {
            return BOTH;
        }
        if (watering) {
            return WATERING;
        }
        return fertilizer ? FERTILIZER : DEFAULT;
    }
}
