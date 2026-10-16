package dev.tchorizo.hypedestals.harvest;

import java.time.Instant;

/** What a soil pass needs from the game world: the tilled soil blocks in the Harvester's area. */
public interface SoilWorld {

    /**
     * State of one tilled soil block.
     *
     * @param wateredUntil game time the watering lasts until, or {@code null} if never watered
     */
    record Soil(Instant wateredUntil, boolean fertilized) {}

    /** The tilled soil at {@code p}, or {@code null} if there is none (other block, or not loaded). */
    Soil soilAt(BlockPos p);

    /** Sets the soil at {@code p} watered until {@code until} (game time); only called where {@link #soilAt} found soil. */
    void water(BlockPos p, Instant until);

    /** Whether a crop stands at {@code p}, by the same detection the harvest uses ({@link CycleWorld#cropTypeAt}). */
    boolean cropAt(BlockPos p);

    /** Marks the soil at {@code p} fertilized; only called where {@link #soilAt} found unfertilized soil. */
    void fertilize(BlockPos p);
}
