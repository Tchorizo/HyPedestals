package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlantPassTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final String WHEAT_SEEDS = "Plant_Seeds_Wheat";
    private static final String CARROT_SEEDS = "Plant_Seeds_Carrot";
    private static final String HERB_SEEDS = "Plant_Seeds_Mana1";
    private static final String OAK = "Plant_Sapling_Oak";

    /** {@link SaplingRuleTest.FakeGround} plus seed rules: wheat and carrot grow on tilled soil, the herb on crystal soil. */
    static class FakePlantWorld extends SaplingRuleTest.FakeGround implements PlantPass.PlantWorld {
        final java.util.Set<BlockPos> crystal = new java.util.HashSet<>();
        final Map<BlockPos, String> placed = new LinkedHashMap<>();
        int seedLookups;

        FakePlantWorld crystalSoil(BlockPos p) {
            block(p);
            crystal.add(p);
            return this;
        }

        @Override
        public String seedBlockOn(String itemId, BlockPos soil) {
            seedLookups++;
            boolean tilled = farmland.contains(soil);
            return switch (itemId) {
                case WHEAT_SEEDS -> tilled ? "Plant_Crop_Wheat_Block" : null;
                case CARROT_SEEDS -> tilled ? "Plant_Crop_Carrot_Block" : null;
                case HERB_SEEDS -> crystal.contains(soil) ? "Plant_Crop_Mana1_Block" : null;
                default -> null;
            };
        }

        @Override
        public String saplingBlock(String itemId) {
            return OAK.equals(itemId) ? OAK : null;
        }

        @Override
        public boolean place(BlockPos p, String blockId) {
            placed.put(p, blockId);
            solid.add(p);
            return true;
        }
    }

    private static PlantPass.Stack stack(int slot, String id, int qty) {
        return new PlantPass.Stack(slot, id, qty);
    }

    @Test
    void plantsTheLowestSlotSeedOnEmptyTilledSoilInAreaOrder() {
        FakePlantWorld w = new FakePlantWorld();
        w.tilled(new BlockPos(1, 63, 0)).tilled(new BlockPos(-1, 63, 1)).tilled(new BlockPos(2, 63, 2));
        w.block(new BlockPos(2, 64, 2)); // a crop already there

        PlantPass.Result r = PlantPass.run(w, List.of(stack(3, CARROT_SEEDS, 5), stack(1, WHEAT_SEEDS, 9)), CENTER, 2, 0);

        assertEquals(2, r.seeds());
        assertEquals(0, r.saplings());
        assertEquals(Map.of(new BlockPos(-1, 64, 1), "Plant_Crop_Carrot_Block", new BlockPos(1, 64, 0), "Plant_Crop_Carrot_Block"),
                w.placed, "the stacks come in slot order from the caller: the first that grows there wins");
        assertEquals(List.of(new BlockPos(-1, 64, 1), new BlockPos(1, 64, 0)), List.copyOf(w.placed.keySet()), "area order");
        assertEquals(Map.of(3, 2), r.taken());
    }

    @Test
    void oneSeedPerBlockAndStopsWhenSeedsRunOut() {
        FakePlantWorld w = new FakePlantWorld();
        for (int x = -2; x <= 2; x++) {
            w.tilled(new BlockPos(x, 63, 0));
        }
        PlantPass.Result r = PlantPass.run(w, List.of(stack(0, WHEAT_SEEDS, 2), stack(4, CARROT_SEEDS, 1)), CENTER, 2, 0);
        assertEquals(3, r.seeds());
        assertEquals(Map.of(0, 2, 4, 1), r.taken());
        assertEquals(List.of("Plant_Crop_Wheat_Block", "Plant_Crop_Wheat_Block", "Plant_Crop_Carrot_Block"),
                List.copyOf(w.placed.values()));
    }

    @Test
    void theSeedsOwnSoilRuleDecides() {
        FakePlantWorld w = new FakePlantWorld();
        w.crystalSoil(new BlockPos(1, 63, 1)).tilled(new BlockPos(-1, 63, -1)).grass(new BlockPos(0, 63, 1));
        PlantPass.Result r = PlantPass.run(w, List.of(stack(0, HERB_SEEDS, 5), stack(1, WHEAT_SEEDS, 5)), CENTER, 2, 0);
        assertEquals(Map.of(new BlockPos(-1, 64, -1), "Plant_Crop_Wheat_Block", new BlockPos(1, 64, 1), "Plant_Crop_Mana1_Block"),
                new HashMap<>(w.placed), "nothing on grass");
    }

    @Test
    void nothingToPlantSkipsTheWorld() {
        FakePlantWorld w = new FakePlantWorld();
        w.tilled(new BlockPos(1, 63, 0));
        PlantPass.Result r = PlantPass.run(w, List.of(stack(0, "Rock_Stone_Cobble", 64)), CENTER, 2, 4);
        assertEquals(0, r.seeds() + r.saplings());
        assertTrue(r.taken().isEmpty());
    }

    @Test
    void saplingsGoOnTheGridOutsideFarmland() {
        FakePlantWorld w = new FakePlantWorld();
        w.floor(6);
        w.block(CENTER);
        w.tilled(new BlockPos(0, 63, 4));
        w.tilled(new BlockPos(1, 63, 1));
        PlantPass.Result r = PlantPass.run(w, List.of(stack(0, OAK, 3), stack(2, WHEAT_SEEDS, 1)), CENTER, 4, 4);
        assertEquals(1, r.seeds(), "one wheat seed: on the first tilled block in area order");
        assertEquals(3, r.saplings());
        assertEquals(List.of(new BlockPos(-4, 64, -4), new BlockPos(-4, 64, 0), new BlockPos(-4, 64, 4)),
                w.placed.entrySet().stream().filter(e -> e.getValue().equals(OAK)).map(Map.Entry::getKey).toList(),
                "grid points in area order, while saplings last");
        assertEquals("Plant_Crop_Wheat_Block", w.placed.get(new BlockPos(0, 64, 4)),
                "x=0 comes before x=1; the tilled grid point gets the seed, not a sapling");
        assertEquals(Map.of(0, 3, 2, 1), r.taken());
    }

    @Test
    void spacingZeroPlantsNoSaplings() {
        FakePlantWorld w = new FakePlantWorld();
        w.floor(6);
        assertEquals(0, PlantPass.run(w, List.of(stack(0, OAK, 3)), CENTER, 4, 0).saplings());
        assertTrue(w.placed.isEmpty());
    }

    @Test
    void aRefusedPlacementUsesNoSeed() {
        FakePlantWorld w = new FakePlantWorld() {
            @Override
            public boolean place(BlockPos p, String blockId) {
                return false;
            }
        };
        w.tilled(new BlockPos(1, 63, 0));
        PlantPass.Result r = PlantPass.run(w, List.of(stack(0, WHEAT_SEEDS, 1)), CENTER, 2, 0);
        assertEquals(0, r.seeds());
        assertTrue(r.taken().isEmpty());
    }

    @Test
    void airBelowIsNotLookedUp() {
        FakePlantWorld w = new FakePlantWorld();
        PlantPass.run(w, List.of(stack(0, WHEAT_SEEDS, 1)), CENTER, 2, 0);
        assertEquals(0, w.seedLookups);
    }
}
