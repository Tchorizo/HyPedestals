package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SoilPassTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final Instant NOW = Instant.parse("0001-01-03T06:00:00Z");
    private static final long D = 21_600;

    @Test
    void watersDryAndHalfSpentSoilWithNowPlusDuration() {
        FakeSoilWorld world = new FakeSoilWorld();
        BlockPos dry = new BlockPos(1, 63, 0);
        BlockPos halfSpent = new BlockPos(-2, 63, 2);
        BlockPos fresh = new BlockPos(0, 63, 1);
        world.soil.put(dry, new SoilWorld.Soil(null, false));
        world.soil.put(halfSpent, new SoilWorld.Soil(NOW.plusSeconds(D / 2 - 1), true));
        world.soil.put(fresh, new SoilWorld.Soil(NOW.plusSeconds(D / 2), false));

        int watered = SoilPass.run(world, CENTER, 2, NOW, D);

        assertEquals(2, watered);
        assertEquals(List.of(halfSpent, dry), world.watered, "area order: y, then x, then z");
        assertEquals(NOW.plusSeconds(D), world.soil.get(dry).wateredUntil(), "set to now + D, not added");
        assertEquals(NOW.plusSeconds(D), world.soil.get(halfSpent).wateredUntil());
        assertEquals(true, world.soil.get(halfSpent).fertilized(), "fertilized state untouched");
        assertEquals(NOW.plusSeconds(D / 2), world.soil.get(fresh).wateredUntil());
    }

    @Test
    void scansTheHarvestAreaOnly() {
        FakeSoilWorld world = new FakeSoilWorld();
        BlockPos outsideX = new BlockPos(3, 63, 0);
        BlockPos belowArea = new BlockPos(0, 62, 0);
        BlockPos top = new BlockPos(2, 65, -2);
        world.soil.put(outsideX, new SoilWorld.Soil(null, false));
        world.soil.put(belowArea, new SoilWorld.Soil(null, false));
        world.soil.put(top, new SoilWorld.Soil(null, false));

        assertEquals(1, SoilPass.run(world, CENTER, 2, NOW, D));
        assertEquals(List.of(top), world.watered);
        assertNull(world.soil.get(outsideX).wateredUntil());
        assertEquals(HarvestArea.size(2), world.lookups, "one lookup per area position");
    }

    @Test
    void secondPassRightAfterTheFirstWatersNothing() {
        FakeSoilWorld world = new FakeSoilWorld();
        world.soil.put(new BlockPos(0, 63, 0), new SoilWorld.Soil(null, false));
        world.soil.put(new BlockPos(1, 63, 1), new SoilWorld.Soil(NOW.minusSeconds(10), false));

        assertEquals(2, SoilPass.run(world, CENTER, 1, NOW, D));
        assertEquals(0, SoilPass.run(world, CENTER, 1, NOW.plusSeconds(900), D),
                "a 30 s real sweep is ~900 game seconds, far below D/2");
        assertEquals(2, SoilPass.run(world, CENTER, 1, NOW.plusSeconds(D / 2 + 1), D), "past half: rewater");
    }

    @Test
    void areaWithoutSoilWatersNothing() {
        assertEquals(0, SoilPass.run(new FakeSoilWorld(), CENTER, 3, NOW, D));
    }

    private static final class FakeSoilWorld implements SoilWorld {
        final Map<BlockPos, Soil> soil = new HashMap<>();
        final List<BlockPos> watered = new ArrayList<>();
        int lookups;

        @Override
        public Soil soilAt(BlockPos p) {
            lookups++;
            return soil.get(p);
        }

        @Override
        public void water(BlockPos p, Instant until) {
            watered.add(p);
            soil.put(p, new Soil(until, soil.get(p).fertilized()));
        }
    }
}
