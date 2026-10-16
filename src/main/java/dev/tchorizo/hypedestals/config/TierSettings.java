package dev.tchorizo.hypedestals.config;

import dev.tchorizo.hypedestals.block.HarvesterSettings;

/**
 * Radius and cycle interval of one tier.
 *
 * <p>The config accepts a narrower range than the block JSON: radius {@code 1..MAX_RADIUS} (as in
 * {@link HarvesterSettings}) and interval {@code MIN_INTERVAL..MAX_INTERVAL} seconds, so the
 * window or a typo in the file cannot make a Harvester cycle every tick.
 */
public record TierSettings(int radius, float intervalSeconds) {

    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = HarvesterSettings.MAX_RADIUS;
    public static final float MIN_INTERVAL = 0.5f;
    public static final float MAX_INTERVAL = 600f;

    public static boolean validRadius(int radius) {
        return radius >= MIN_RADIUS && radius <= MAX_RADIUS;
    }

    /** {@code false} for NaN too. */
    public static boolean validInterval(float seconds) {
        return seconds >= MIN_INTERVAL && seconds <= MAX_INTERVAL;
    }
}
