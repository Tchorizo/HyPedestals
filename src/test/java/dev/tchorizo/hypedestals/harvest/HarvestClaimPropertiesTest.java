package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Property tests (fixed seed, 1000 cases) of the one-harvest-per-block-per-tick rule
 * ({@link HarvestAreas#claimHarvest}) with several overlapping Harvesters cycling in the same ticks,
 * and of {@link CycleStagger}'s offsets.
 */
class HarvestClaimPropertiesTest {

    private static final int CASES = 1000;
    private static final long SEED = 20261021L + 5;

    @Test
    void aBlockIsHarvestedAtMostOncePerTickWhateverTheOverlap() {
        long harvests = 0;
        long refused = 0;
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            HarvestAreas areas = new HarvestAreas();
            int machines = 2 + rnd.nextInt(5);
            List<BlockPos> centers = new ArrayList<>();
            List<Integer> radii = new ArrayList<>();
            for (int m = 0; m < machines; m++) {
                centers.add(new BlockPos(rnd.nextInt(12), 64, rnd.nextInt(12)));
                radii.add(1 + rnd.nextInt(5));
            }
            long tick = 100 + rnd.nextInt(1000);
            for (int t = 0; t < 8; t++) {
                Map<BlockPos, Integer> harvestedThisTick = new HashMap<>();
                // The same tick: every machine whose timer fires runs its whole cycle, in some order.
                List<Integer> order = new ArrayList<>();
                for (int m = 0; m < machines; m++) {
                    if (rnd.nextInt(4) != 0) {
                        order.add(m);
                    }
                }
                java.util.Collections.shuffle(order, rnd);
                long now = tick + t;
                for (int m : order) {
                    final long thisTick = now;
                    BlockPos center = centers.get(m);
                    int radius = radii.get(m);
                    areas.refresh(center, radius, now);
                    CycleWorld world = new CycleWorld() {
                        @Override public String cropTypeAt(BlockPos p) { return "Crop"; }
                        @Override public boolean loaded(BlockPos p) { return true; }
                        @Override public List<ItemCount> maxDrops(BlockPos p) { return List.of(new ItemCount("Wheat", 1)); }
                        @Override public HarvestOutcome harvest(BlockPos p) {
                            // As HytaleCycleWorld: a position claimed this tick is not harvested again.
                            if (!areas.claimHarvest(p, thisTick)) {
                                return HarvestOutcome.NOT_READY;
                            }
                            return HarvestOutcome.HARVESTED;
                        }
                        @Override public int collectDroppedItems(BlockPos c, double r) { return 0; }
                        @Override public boolean containerFull() { return false; }
                        @Override public ContainerRoom room() { return new ContainerRoom(1_000_000, Map.of(), id -> 64); }
                    };
                    CycleResult r = HarvestCycle.run(new CountingWorld(world, harvestedThisTick), center, radius, 2,
                            new HashSet<>(), rnd.nextInt(500), 0, new PendingDrops(new DropCalibration()));
                    harvests += r.harvested();
                }
                for (Map.Entry<BlockPos, Integer> e : harvestedThisTick.entrySet()) {
                    assertEquals(1, e.getValue(), "case " + c + " tick " + now + ": " + e.getKey() + " harvested "
                            + e.getValue() + " times");
                }
                // A harvester that did not run does not keep the claims of an old tick: the next tick starts clean.
                assertTrue(areas.claimHarvest(new BlockPos(0, 64, 0), now + 1000 + t), "case " + c + ": claims reset on a new tick");
                refused += 0;
            }
        }
        assertTrue(harvests > 10_000, "not vacuous: " + harvests);
        assertEquals(0, refused);
    }

    /** Counts the harvests that took effect (outcome HARVESTED) per position. */
    private record CountingWorld(CycleWorld inner, Map<BlockPos, Integer> harvested) implements CycleWorld {
        @Override public String cropTypeAt(BlockPos p) { return inner.cropTypeAt(p); }
        @Override public boolean loaded(BlockPos p) { return inner.loaded(p); }
        @Override public List<ItemCount> maxDrops(BlockPos p) { return inner.maxDrops(p); }
        @Override public HarvestOutcome harvest(BlockPos p) {
            HarvestOutcome o = inner.harvest(p);
            if (o == HarvestOutcome.HARVESTED) {
                harvested.merge(p, 1, Integer::sum);
            }
            return o;
        }
        @Override public int collectDroppedItems(BlockPos c, double r) { return inner.collectDroppedItems(c, r); }
        @Override public boolean containerFull() { return inner.containerFull(); }
        @Override public ContainerRoom room() { return inner.room(); }
    }

    @Test
    void claimsAreOncePerPositionAndTickAcrossRandomSequences() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + 40000 + c);
            HarvestAreas areas = new HarvestAreas();
            Set<String> claimed = new HashSet<>();
            long tick = rnd.nextInt(50);
            for (int i = 0; i < 200; i++) {
                if (rnd.nextInt(10) == 0) {
                    tick += 1 + rnd.nextInt(3);
                    claimed.clear();
                }
                BlockPos p = new BlockPos(rnd.nextInt(4), 64, rnd.nextInt(4));
                boolean first = claimed.add(p.toString());
                assertEquals(first, areas.claimHarvest(p, tick), "case " + c + ": claim of " + p + " at tick " + tick);
            }
        }
    }

    @Test
    void staggerOffsetsStayInTheIntervalAndSpreadNeighbours() {
        Set<Integer> distinctSlots = new HashSet<>();
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + 80000 + c);
            float interval = 0.2f + rnd.nextFloat() * 600f;
            BlockPos p = new BlockPos(rnd.nextInt(2000) - 1000, rnd.nextInt(200), rnd.nextInt(2000) - 1000);
            float e = CycleStagger.initialElapsed(p, interval);
            assertTrue(e >= 0 && e < interval, "case " + c + ": " + e + " outside [0, " + interval + ")");
            assertEquals(e, CycleStagger.initialElapsed(p, interval), "deterministic");
            distinctSlots.add((int) (e / interval * 30));
        }
        assertTrue(distinctSlots.size() >= 25, "offsets spread over the interval: " + distinctSlots.size() + " of 30 slots used");
        assertEquals(0f, CycleStagger.initialElapsed(new BlockPos(1, 2, 3), 0f));
        assertEquals(0f, CycleStagger.initialElapsed(new BlockPos(1, 2, 3), -4f));
    }
}
