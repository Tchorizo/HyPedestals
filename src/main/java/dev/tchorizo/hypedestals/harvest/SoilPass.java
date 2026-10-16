package dev.tchorizo.hypedestals.harvest;

import java.time.Instant;

/** One sweep of the Harvester's area over the tilled soil, for the watering upgrade. Pure. */
public final class SoilPass {

    private SoilPass() {}

    /**
     * Waters every tilled soil block in {@link HarvestArea#positions(BlockPos, int)} that
     * {@link SoilCare#decideWater} says needs it, setting it to {@code now + wateringSeconds}.
     *
     * @param now world game time
     * @param wateringSeconds the tier's {@code WateringSeconds}, in game seconds
     * @return how many soil blocks were watered
     */
    public static int run(SoilWorld world, BlockPos center, int radius, Instant now, long wateringSeconds) {
        Instant until = now.plusSeconds(wateringSeconds);
        int watered = 0;
        for (BlockPos p : HarvestArea.positions(center, radius)) {
            SoilWorld.Soil soil = world.soilAt(p);
            if (soil != null && SoilCare.decideWater(now, soil.wateredUntil(), wateringSeconds)) {
                world.water(p, until);
                watered++;
            }
        }
        return watered;
    }
}
