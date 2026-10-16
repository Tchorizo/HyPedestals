package dev.tchorizo.hypedestals.harvest;

import java.time.Duration;
import java.time.Instant;

/** Rules for the Harvester's care of tilled soil in its area. Pure; times are world game time. */
public final class SoilCare {

    private SoilCare() {}

    /**
     * Whether the watering upgrade should (re)water a soil block: when it was never watered, or
     * less than half of {@code durationSeconds} is left. Rewatering only below half keeps writes
     * rare, and the caller sets {@code now + duration} rather than adding to {@code until}, so the
     * watering never accumulates.
     *
     * @param until the soil's {@code wateredUntil}, or {@code null} if never watered
     * @param durationSeconds the tier's {@code WateringSeconds}, in game seconds
     */
    public static boolean decideWater(Instant now, Instant until, long durationSeconds) {
        return until == null
                || Duration.between(now, until).multipliedBy(2).compareTo(Duration.ofSeconds(durationSeconds)) < 0;
    }
}
