package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        int watered = SoilPass.run(world, CENTER, 2, NOW, true, D, 0).watered();

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

        assertEquals(1, water(world, CENTER, 2, NOW));
        assertEquals(List.of(top), world.watered);
        assertNull(world.soil.get(outsideX).wateredUntil());
        assertEquals(HarvestArea.size(2), world.lookups, "one lookup per area position");
    }

    @Test
    void secondPassRightAfterTheFirstWatersNothing() {
        FakeSoilWorld world = new FakeSoilWorld();
        world.soil.put(new BlockPos(0, 63, 0), new SoilWorld.Soil(null, false));
        world.soil.put(new BlockPos(1, 63, 1), new SoilWorld.Soil(NOW.minusSeconds(10), false));

        assertEquals(2, water(world, CENTER, 1, NOW));
        assertEquals(0, water(world, CENTER, 1, NOW.plusSeconds(900)),
                "a 30 s real sweep is ~900 game seconds, far below D/2");
        assertEquals(2, water(world, CENTER, 1, NOW.plusSeconds(D / 2 + 1)), "past half: rewater");
    }

    @Test
    void areaWithoutSoilWatersNothing() {
        assertEquals(0, water(new FakeSoilWorld(), CENTER, 3, NOW));
    }

    @Test
    void fertilizesUnfertilizedSoilUnderACropWhileApplicationsLast() {
        FakeSoilWorld world = new FakeSoilWorld();
        BlockPos a = new BlockPos(-1, 63, 0);
        BlockPos b = new BlockPos(0, 63, 0);
        BlockPos c = new BlockPos(1, 63, 0);
        for (BlockPos p : List.of(a, b, c)) {
            world.soil.put(p, new SoilWorld.Soil(null, false));
            world.crops.add(up(p));
        }

        SoilPass.Result result = SoilPass.run(world, CENTER, 1, NOW, false, D, 2);

        assertEquals(new SoilPass.Result(0, 2, 2), result);
        assertEquals(List.of(a, b), world.fertilized, "area order; stops when the applications run out");
        assertEquals(List.of(), world.watered, "no watering without the upgrade");
    }

    @Test
    void skipsSoilAlreadyFertilizedOrWithoutACrop() {
        FakeSoilWorld world = new FakeSoilWorld();
        BlockPos done = new BlockPos(0, 63, 0);
        BlockPos bare = new BlockPos(1, 63, 0);
        BlockPos cropBesideNotAbove = new BlockPos(-1, 63, 0);
        BlockPos wanted = new BlockPos(1, 63, 1);
        world.soil.put(done, new SoilWorld.Soil(null, true));
        world.crops.add(up(done));
        world.soil.put(bare, new SoilWorld.Soil(null, false));
        world.soil.put(cropBesideNotAbove, new SoilWorld.Soil(null, false));
        world.crops.add(new BlockPos(-1, 63, 1));
        world.soil.put(wanted, new SoilWorld.Soil(null, false));
        world.crops.add(up(wanted));

        assertEquals(new SoilPass.Result(0, 1, 1), SoilPass.run(world, CENTER, 1, NOW, false, D, 5));
        assertEquals(List.of(wanted), world.fertilized);
        assertEquals(0, SoilPass.run(world, CENTER, 1, NOW, false, D, 5).applicationsUsed(),
                "second pass: everything under a crop is fertilized already");
    }

    @Test
    void watersAndFertilizesInTheSamePass() {
        FakeSoilWorld world = new FakeSoilWorld();
        BlockPos p = new BlockPos(0, 63, 0);
        world.soil.put(p, new SoilWorld.Soil(null, false));
        world.crops.add(up(p));

        assertEquals(new SoilPass.Result(1, 1, 1), SoilPass.run(world, CENTER, 1, NOW, true, D, 5));
        assertEquals(new SoilWorld.Soil(NOW.plusSeconds(D), true), world.soil.get(p));
    }

    @Test
    void noApplicationsNeverLooksForCrops() {
        FakeSoilWorld world = new FakeSoilWorld();
        BlockPos p = new BlockPos(0, 63, 0);
        world.soil.put(p, new SoilWorld.Soil(null, false));
        world.crops.add(up(p));

        assertEquals(new SoilPass.Result(1, 0, 0), SoilPass.run(world, CENTER, 1, NOW, true, D, 0));
        assertEquals(0, world.cropLookups);
        assertEquals(false, world.soil.get(p).fertilized());
    }

    private static int water(FakeSoilWorld world, BlockPos center, int radius, Instant now) {
        return SoilPass.run(world, center, radius, now, true, D, 0).watered();
    }

    private static BlockPos up(BlockPos p) {
        return new BlockPos(p.x(), p.y() + 1, p.z());
    }

    private static final class FakeSoilWorld implements SoilWorld {
        final Map<BlockPos, Soil> soil = new HashMap<>();
        final Set<BlockPos> crops = new HashSet<>();
        final List<BlockPos> watered = new ArrayList<>();
        final List<BlockPos> fertilized = new ArrayList<>();
        int lookups;
        int cropLookups;

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

        @Override
        public boolean cropAt(BlockPos p) {
            cropLookups++;
            return crops.contains(p);
        }

        @Override
        public void fertilize(BlockPos p) {
            fertilized.add(p);
            soil.put(p, new Soil(soil.get(p).wateredUntil(), true));
        }
    }
}
