package dev.tchorizo.hypedestals.block;

/**
 * Pure validation of harvester tier settings. Kept apart from {@link HarvesterComponent} because
 * that class cannot be loaded without the server jar, which is compile-only in tests.
 */
public final class HarvesterSettings {

    public static final int MAX_RADIUS = 8;

    private HarvesterSettings() {}

    /** Valid radii are {@code 1..MAX_RADIUS}; anything else becomes {@code 0} (inactive harvester). */
    public static int sanitizeRadius(int r) {
        return r >= 1 && r <= MAX_RADIUS ? r : 0;
    }

    /** Positive intervals pass through; zero, negative or NaN become {@code 0} (inactive harvester). */
    public static float sanitizeInterval(float seconds) {
        return seconds > 0f ? seconds : 0f;
    }
}
