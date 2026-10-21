package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Property tests (fixed seed, 1000 cases) of {@link SaplingRule} and {@link PlantPass}: whatever the
 * terrain, a sapling is never planted within {@link SaplingRule#ROOT_CLEARANCE} of farmland nor with
 * its ring or column occupied, seeds only go on bare soil they grow on, and no stack is overdrawn.
 */
class SaplingRulePropertiesTest {

    private static final int CASES = 1000;
    private static final long SEED = 20261021L + 3;
    private static final BlockPos CENTER = new BlockPos(0, 1, 0);

    @Test
    void saplingsNeverGoNearFarmlandOrIntoAnOccupiedRing() {
        int saplingsTotal = 0;
        int seedsTotal = 0;
        int refusedByRule = 0;
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            Terrain world = new Terrain(rnd);
            int radius = 2 + rnd.nextInt(7);
            int spacing = rnd.nextInt(6);
            List<PlantPass.Stack> stacks = new ArrayList<>();
            stacks.add(new PlantPass.Stack(0, "Sapling_Oak", rnd.nextInt(30)));
            if (rnd.nextBoolean()) {
                stacks.add(new PlantPass.Stack(1, "Seed_Wheat", rnd.nextInt(60)));
            }
            if (rnd.nextBoolean()) {
                stacks.add(new PlantPass.Stack(2, "Seed_Herb", rnd.nextInt(60)));
            }
            stacks.removeIf(s -> s.quantity() == 0);

            List<BlockPos> spotsBefore = SaplingRule.spots(world, CENTER, radius, spacing);
            for (BlockPos p : spotsBefore) {
                assertTrue(SaplingRule.onGrid(CENTER, p, spacing) && spacing > 0 && spacing <= radius);
            }
            if (spacing <= 0 || spacing > radius) {
                assertTrue(spotsBefore.isEmpty(), "case " + c + ": no grid, no spots");
            }

            PlantPass.Result result = PlantPass.run(world, stacks, CENTER, radius, spacing);

            assertEquals(world.saplingsPlaced, result.saplings(), "case " + c);
            assertEquals(world.seedsPlaced, result.seeds(), "case " + c);
            int taken = result.taken().values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(result.planted(), taken, "case " + c + ": one item taken per planting");
            for (PlantPass.Stack s : stacks) {
                assertTrue(result.taken().getOrDefault(s.slot(), 0) <= s.quantity(),
                        "case " + c + ": overdrew slot " + s.slot());
            }
            if (spacing <= 0) {
                assertEquals(0, result.saplings(), "case " + c + ": spacing 0 plants no saplings");
            }
            saplingsTotal += result.saplings();
            seedsTotal += result.seeds();
            refusedByRule += spotsBefore.size() == 0 ? 1 : 0;
        }
        // The properties are not vacuous: the terrains do give saplings and seeds, and many are refused.
        assertTrue(saplingsTotal > 500, "saplings planted over all cases: " + saplingsTotal);
        assertTrue(seedsTotal > 500, "seeds planted over all cases: " + seedsTotal);
        assertTrue(refusedByRule > 20, "cases without any valid spot: " + refusedByRule);
    }

    /** A random field: soil layer at y=0 (dirt, tilled, stone, holes) and obstacles above it. */
    private static class Terrain implements PlantPass.PlantWorld {
        final Map<BlockPos, String> blocks = new HashMap<>();
        int saplingsPlaced;
        int seedsPlaced;
        final BlockPos[] all;

        Terrain(Random rnd) {
            List<BlockPos> everything = new ArrayList<>();
            int tilledPct = new int[] {0, 10, 40}[rnd.nextInt(3)];
            int obstaclePct = new int[] {0, 3, 12}[rnd.nextInt(3)];
            for (int x = -13; x <= 13; x++) {
                for (int z = -13; z <= 13; z++) {
                    int roll = rnd.nextInt(100);
                    String ground = roll < tilledPct ? "Soil_Dirt_Tilled" : roll > 95 ? "Rock_Stone" : roll > 92 ? null : "Soil_Dirt";
                    if (ground != null) {
                        blocks.put(new BlockPos(x, 0, z), ground);
                    }
                    for (int y = 1; y <= 5; y++) {
                        everything.add(new BlockPos(x, y, z));
                        if (rnd.nextInt(100) < obstaclePct) {
                            blocks.put(new BlockPos(x, y, z), rnd.nextBoolean() ? "Chest" : "Plant_Crop_Wheat");
                        }
                    }
                }
            }
            all = everything.toArray(BlockPos[]::new);
        }

        @Override public boolean isEmpty(BlockPos p) { return !blocks.containsKey(p); }
        @Override public boolean isSaplingSoil(BlockPos p) { String id = blocks.get(p); return id != null && id.startsWith("Soil_"); }
        @Override public boolean isFarmland(BlockPos p) { return "Soil_Dirt_Tilled".equals(blocks.get(p)); }

        @Override
        public String seedBlockOn(String itemId, BlockPos soil) {
            String ground = blocks.get(soil);
            if (ground == null) {
                return null;
            }
            return switch (itemId) {
                case "Seed_Wheat" -> ground.equals("Soil_Dirt_Tilled") ? "Plant_Crop_Wheat_Placed" : null;
                case "Seed_Herb" -> ground.startsWith("Soil_") ? "Plant_Herb" : null;
                default -> null;
            };
        }

        @Override
        public String saplingBlock(String itemId) {
            return itemId.startsWith("Sapling_") ? "Plant_" + itemId : null;
        }

        @Override
        public boolean place(BlockPos p, String blockId) {
            assertTrue(!blocks.containsKey(p), "placed over an existing block at " + p);
            BlockPos soil = new BlockPos(p.x(), p.y() - 1, p.z());
            assertTrue(blocks.containsKey(soil), "placed with nothing below at " + p);
            if (blockId.startsWith("Plant_Sapling_")) {
                assertTrue(blocks.get(soil).startsWith("Soil_"), "sapling on " + blocks.get(soil));
                // The rule's documented numbers, not its constants: 3 blocks of roots, a 4-high clear column.
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        assertTrue(!"Soil_Dirt_Tilled".equals(blocks.get(new BlockPos(p.x() + dx, soil.y(), p.z() + dz))),
                                "sapling at " + p + " within 3 of tilled soil");
                    }
                }
                for (int dy = 0; dy < 4; dy++) {
                    assertTrue(!blocks.containsKey(new BlockPos(p.x(), p.y() + dy, p.z())), "column not clear at " + p);
                }
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx != 0 || dz != 0) {
                                assertTrue(!blocks.containsKey(new BlockPos(p.x() + dx, p.y() + dy, p.z() + dz)),
                                        "ring occupied around " + p);
                            }
                        }
                    }
                }
                saplingsPlaced++;
            } else {
                assertTrue(seedBlockOn(blockId.startsWith("Plant_Herb") ? "Seed_Herb" : "Seed_Wheat", soil) != null,
                        "seed " + blockId + " on " + blocks.get(soil));
                seedsPlaced++;
            }
            blocks.put(p, blockId);
            return true;
        }
    }

    @Test
    void saplingsLandOnTheSpacingGrid() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + 9000 + c);
            int radius = 3 + rnd.nextInt(6);
            int spacing = 1 + rnd.nextInt(radius);
            List<BlockPos> placed = new ArrayList<>();
            Terrain world = new Terrain(rnd) {
                @Override
                public boolean place(BlockPos p, String blockId) {
                    boolean ok = super.place(p, blockId);
                    if (blockId.startsWith("Plant_Sapling_")) {
                        placed.add(p);
                    }
                    return ok;
                }
            };
            PlantPass.run(world, List.of(new PlantPass.Stack(0, "Sapling_Oak", 500)), CENTER, radius, spacing);
            for (BlockPos p : placed) {
                assertTrue(SaplingRule.onGrid(CENTER, p, spacing), "case " + c + ": " + p + " off the grid " + spacing);
                assertTrue(Math.abs(p.x() - CENTER.x()) <= radius && Math.abs(p.z() - CENTER.z()) <= radius
                        && Math.abs(p.y() - CENTER.y()) <= 1, "case " + c + ": outside the area");
            }
            // Saplings never closer than the ring lets them be: two saplings are at least 2 apart (Chebyshev).
            for (int i = 0; i < placed.size(); i++) {
                for (int j = i + 1; j < placed.size(); j++) {
                    BlockPos a = placed.get(i), b = placed.get(j);
                    if (Math.abs(a.y() - b.y()) <= 1) {
                        assertTrue(Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z())) >= 2,
                                "case " + c + ": saplings " + a + " and " + b + " touch");
                    }
                }
            }
        }
    }
}
