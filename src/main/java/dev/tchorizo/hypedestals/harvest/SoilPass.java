package dev.tchorizo.hypedestals.harvest;

import java.time.Instant;

/** One sweep of the Harvester's area over the tilled soil, for the watering and fertilizer upgrades. Pure. */
public final class SoilPass {

    private SoilPass() {}

    /**
     * @param watered soil blocks watered
     * @param fertilized soil blocks fertilized
     * @param applicationsUsed fertilizer applications to take from the container's fuel; equal to
     *     {@code fertilized} (one application per block, upgrades spec §3.3), kept apart so the
     *     caller's consumption does not depend on that rule
     */
    public record Result(int watered, int fertilized, int applicationsUsed) {}

    /**
     * Visits every position of {@link HarvestArea#positions(BlockPos, int)} with tilled soil:
     * <ul>
     *   <li>with {@code water}, waters it to {@code now + wateringSeconds} where
     *       {@link SoilCare#decideWater} says it needs it;</li>
     *   <li>while fertilizer applications are left, fertilizes it if it is not fertilized yet and a
     *       crop stands right above it (soil without a crop is not worth the fuel).</li>
     * </ul>
     *
     * @param now world game time
     * @param water whether the watering upgrade is installed
     * @param wateringSeconds the tier's {@code WateringSeconds}, in game seconds; unused without {@code water}
     * @param fertilizerApplications applications the container's fuel (poop, bags) can give ({@link FertilizerInput#totalApplications}); 0 = none
     */
    public static Result run(SoilWorld world, BlockPos center, int radius, Instant now,
                             boolean water, long wateringSeconds, int fertilizerApplications) {
        Instant until = now.plusSeconds(wateringSeconds);
        int watered = 0;
        int fertilized = 0;
        for (BlockPos p : HarvestArea.positions(center, radius)) {
            SoilWorld.Soil soil = world.soilAt(p);
            if (soil == null) {
                continue;
            }
            if (water && SoilCare.decideWater(now, soil.wateredUntil(), wateringSeconds)) {
                world.water(p, until);
                watered++;
            }
            if (fertilized < fertilizerApplications && !soil.fertilized()
                    && world.cropAt(new BlockPos(p.x(), p.y() + 1, p.z()))) {
                world.fertilize(p);
                fertilized++;
            }
        }
        return new Result(watered, fertilized, fertilized);
    }
}
