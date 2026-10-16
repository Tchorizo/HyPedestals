package dev.tchorizo.hypedestals.config;

import dev.tchorizo.hypedestals.block.HarvesterSettings;

/**
 * Radius, cycle interval and watering duration of one tier.
 *
 * <p>The config accepts a narrower range than the block JSON: radius {@code 1..MAX_RADIUS} (as in
 * {@link HarvesterSettings}) and interval {@code MIN_INTERVAL..MAX_INTERVAL} seconds, so the
 * window or a typo in the file cannot make a Harvester cycle every tick.
 *
 * @param wateringSeconds how long, in <em>game</em> seconds, the Watering upgrade keeps a soil
 *     block watered ({@code MIN_WATERING..MAX_WATERING}); not in the block JSON
 */
public record TierSettings(int radius, float intervalSeconds, int wateringSeconds) {

    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = HarvesterSettings.MAX_RADIUS;
    public static final float MIN_INTERVAL = 0.5f;
    public static final float MAX_INTERVAL = 600f;
    public static final int MIN_WATERING = 60;
    public static final int MAX_WATERING = 604_800;

    public static boolean validRadius(int radius) {
        return radius >= MIN_RADIUS && radius <= MAX_RADIUS;
    }

    /** {@code false} for NaN too. */
    public static boolean validInterval(float seconds) {
        return seconds >= MIN_INTERVAL && seconds <= MAX_INTERVAL;
    }

    public static boolean validWatering(int seconds) {
        return seconds >= MIN_WATERING && seconds <= MAX_WATERING;
    }
}
