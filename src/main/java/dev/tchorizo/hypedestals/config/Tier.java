package dev.tchorizo.hypedestals.config;

import java.util.Locale;

/**
 * The four Harvester tiers. {@link #number()} is the {@code Tier} of the {@code HyHarvester}
 * component; the radius and interval defaults are the values of the tier's block JSON (pinned by
 * a test) and the watering default is the design's per-tier duration, used when the config file
 * does not override them.
 */
public enum Tier {
    COPPER(1, "Copper", new TierSettings(2, 12f, 21_600)),
    IRON(2, "Iron", new TierSettings(3, 8f, 43_200)),
    THORIUM(3, "Thorium", new TierSettings(4, 5f, 72_000)),
    COBALT(4, "Cobalt", new TierSettings(5, 3f, 108_000));

    private final int number;
    private final String displayName;
    private final TierSettings defaults;

    Tier(int number, String displayName, TierSettings defaults) {
        this.number = number;
        this.displayName = displayName;
        this.defaults = defaults;
    }

    public int number() {
        return number;
    }

    /** Capitalized name, also the tier's key in the config file and in {@code config set}. */
    public String displayName() {
        return displayName;
    }

    public TierSettings defaults() {
        return defaults;
    }

    /** Tier with that component number, or {@code null}. */
    public static Tier byNumber(int number) {
        for (Tier t : values()) {
            if (t.number == number) {
                return t;
            }
        }
        return null;
    }

    /** Tier with that name, ignoring case, or {@code null}. */
    public static Tier byName(String name) {
        for (Tier t : values()) {
            if (t.displayName.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        return null;
    }
}
