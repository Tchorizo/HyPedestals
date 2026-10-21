package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SaplingRuleTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);

    /** Air everywhere except the blocks put in {@code solid}; {@code soil} and {@code farmland} are subsets of it. */
    static final class FakeGround implements SaplingRule.Ground {
        final Set<BlockPos> solid = new HashSet<>();
        final Set<BlockPos> soil = new HashSet<>();
        final Set<BlockPos> farmland = new HashSet<>();

        FakeGround grass(BlockPos p) {
            solid.add(p);
            soil.add(p);
            return this;
        }

        FakeGround tilled(BlockPos p) {
            grass(p);
            farmland.add(p);
            return this;
        }

        FakeGround block(BlockPos p) {
            solid.add(p);
            return this;
        }

        /** A grass floor at y=63 over the square of side 2r+1 around the center. */
        FakeGround floor(int r) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    grass(new BlockPos(x, 63, z));
                }
            }
            return this;
        }

        @Override
        public boolean isEmpty(BlockPos p) {
            return !solid.contains(p);
        }

        @Override
        public boolean isSaplingSoil(BlockPos p) {
            return soil.contains(p);
        }

        @Override
        public boolean isFarmland(BlockPos p) {
            return farmland.contains(p);
        }
    }

    @Test
    void plantsOnFreeGrass() {
        FakeGround g = new FakeGround().floor(6);
        assertTrue(SaplingRule.canPlantAt(g, new BlockPos(4, 64, 4)));
    }

    @Test
    void needsSaplingSoilBelowAndNotFarmland() {
        FakeGround g = new FakeGround().block(new BlockPos(0, 63, 0)).tilled(new BlockPos(2, 63, 0));
        assertFalse(SaplingRule.canPlantAt(g, new BlockPos(0, 64, 0)), "stone below");
        assertFalse(SaplingRule.canPlantAt(g, new BlockPos(2, 64, 0)), "tilled soil is for seeds");
        assertFalse(SaplingRule.canPlantAt(g, new BlockPos(5, 64, 5)), "air below");
    }

    @Test
    void needsTheSpotAndTheColumnAboveEmpty() {
        FakeGround g = new FakeGround().floor(6);
        BlockPos spot = new BlockPos(3, 64, 3);
        assertTrue(SaplingRule.canPlantAt(g, spot));
        g.block(new BlockPos(3, 64 + SaplingRule.CLEAR_HEIGHT - 1, 3));
        assertFalse(SaplingRule.canPlantAt(g, spot), "block in the column");
        FakeGround g2 = new FakeGround().floor(6).block(new BlockPos(3, 64 + SaplingRule.CLEAR_HEIGHT, 3));
        assertTrue(SaplingRule.canPlantAt(g2, spot), "above the checked column");
        FakeGround g3 = new FakeGround().floor(6).block(spot);
        assertFalse(SaplingRule.canPlantAt(g3, spot), "occupied spot");
    }

    @Test
    void needsTheRingAroundFreeAtTheSpotAndOneAbove() {
        BlockPos spot = new BlockPos(3, 64, 3);
        for (BlockPos obstacle : List.of(new BlockPos(4, 64, 3), new BlockPos(2, 65, 2), new BlockPos(4, 65, 4))) {
            FakeGround g = new FakeGround().floor(6).block(obstacle);
            assertFalse(SaplingRule.canPlantAt(g, spot), "obstacle at " + obstacle);
        }
        FakeGround far = new FakeGround().floor(6).block(new BlockPos(5, 64, 3)).block(new BlockPos(4, 66, 3));
        assertTrue(SaplingRule.canPlantAt(far, spot), "outside the ring, or above its two layers");
    }

    @Test
    void gridIsAlignedWithTheOriginAndSkipsItsColumn() {
        BlockPos o = new BlockPos(10, 64, -7);
        assertTrue(SaplingRule.onGrid(o, new BlockPos(14, 64, -7), 4));
        assertTrue(SaplingRule.onGrid(o, new BlockPos(6, 63, -11), 4));
        assertFalse(SaplingRule.onGrid(o, new BlockPos(13, 64, -7), 4));
        assertFalse(SaplingRule.onGrid(o, new BlockPos(10, 65, -7), 4), "the Harvester's column");
        assertFalse(SaplingRule.onGrid(o, new BlockPos(14, 64, -7), 0), "spacing 0 = no saplings");
    }

    @Test
    void spotsAreTheGridPointsOfTheAreaThatFitInScanOrder() {
        FakeGround g = new FakeGround().floor(6);
        g.block(CENTER); // the Harvester
        g.tilled(new BlockPos(-4, 63, 4));
        List<BlockPos> spots = SaplingRule.spots(g, CENTER, 4, 4);
        assertEquals(List.of(new BlockPos(-4, 64, -4), new BlockPos(-4, 64, 0),
                new BlockPos(0, 64, -4), new BlockPos(0, 64, 4),
                new BlockPos(4, 64, -4), new BlockPos(4, 64, 0), new BlockPos(4, 64, 4)), spots);
        assertEquals(List.of(), SaplingRule.spots(g, CENTER, 3, 4), "radius below the spacing: no grid point");
    }
}
