package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Property tests (fixed seed, 1000 cases) of {@link ExportPlan}: conservation, the keep ceilings, machines, "never above". */
class ExportPlanPropertiesTest {

    private static final int CASES = 1000;
    private static final long SEED = 20261021L + 1;
    private static final BlockPos CENTER = new BlockPos(10, 10, 10);
    private static final String[] IDS = {"Wheat", "Carrot", "Ingredient_Poop", "Tool_Fertilizer", "Seed_A", "Seed_B", "Sapling_A"};

    @Test
    void exportConservesItemsAndKeepsTheCeilings() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            Src src = new Src(1 + rnd.nextInt(20));
            for (int s = 0; s < src.capacity(); s++) {
                if (rnd.nextInt(4) != 0) {
                    src.id[s] = IDS[rnd.nextInt(IDS.length)];
                    src.qty[s] = 1 + rnd.nextInt(src.id[s].equals("Tool_Fertilizer") ? 1 : 120);
                }
            }
            boolean keepFertilizer = rnd.nextBoolean();
            int k = rnd.nextInt(4);
            int manureStock = rnd.nextInt(60) - 5;
            int seedStock = rnd.nextInt(30) - 3;
            boolean planter = rnd.nextBoolean();
            Predicate<String> plantable = planter ? id -> id.startsWith("Seed_") || id.startsWith("Sapling_") : null;

            // Targets around the Harvester: some containers, some machines, one reached from two sides, one above.
            List<Tgt> targets = new ArrayList<>();
            Map<BlockPos, Tgt> at = new HashMap<>();
            List<BlockPos> neighbours = ExportPlan.neighbors(CENTER);
            Tgt shared = null;
            for (BlockPos n : neighbours) {
                if (rnd.nextInt(3) == 0) {
                    continue;
                }
                Tgt t;
                if (shared != null && rnd.nextInt(4) == 0) {
                    t = shared; // the same container through another face
                } else {
                    t = new Tgt(n, rnd.nextInt(5) == 0, rnd.nextInt(400));
                    targets.add(t);
                    shared = t;
                }
                at.put(n, t);
            }
            Tgt above = new Tgt(new BlockPos(10, 11, 10), false, 1000);
            at.put(above.pos, above);
            List<BlockPos> asked = new ArrayList<>();
            ExportPlan.ExportWorld<Src> world = p -> {
                asked.add(p);
                return at.get(p);
            };

            Map<String, Integer> before = src.totals();
            ExportPlan.Result result = ExportPlan.run(world, src, CENTER, keepFertilizer, k, manureStock, plantable, seedStock);
            Map<String, Integer> after = src.totals();

            int received = 0;
            Map<String, Integer> receivedBy = new HashMap<>();
            for (Tgt t : targets) {
                received += t.total();
                t.got.forEach((id, n) -> receivedBy.merge(id, n, Integer::sum));
                assertTrue(t.got.values().stream().allMatch(n -> n > 0));
                assertTrue(t.total() <= t.capacityItems, "case " + c + ": a target took more than it had room for");
                if (t.machine) {
                    assertEquals(0, t.total(), "case " + c + ": a machine received items");
                }
            }
            assertEquals(0, above.total(), "case " + c + ": exported above the Harvester");
            assertTrue(asked.stream().noneMatch(p -> p.y() > CENTER.y()), "case " + c + ": looked above the Harvester");
            assertEquals(received, result.moved(), "case " + c + ": Result.moved is what the targets got");
            for (String id : IDS) {
                int b = before.getOrDefault(id, 0);
                int a = after.getOrDefault(id, 0);
                assertEquals(b, a + receivedBy.getOrDefault(id, 0), "case " + c + ": conservation of " + id);
                assertTrue(a >= 0);
                if (keepFertilizer && id.equals("Tool_Fertilizer")) {
                    assertEquals(b, a, "case " + c + ": the bags never leave with the upgrade");
                }
                if (keepFertilizer && k > 0 && id.equals("Ingredient_Poop")) {
                    assertTrue(a >= Math.min(b, Math.max(0, manureStock)),
                            "case " + c + ": poop " + a + " fell below the stock " + manureStock + " (had " + b + ")");
                }
                if (planter && (id.startsWith("Seed_") || id.startsWith("Sapling_"))) {
                    assertTrue(a >= Math.min(b, Math.max(0, seedStock)),
                            "case " + c + ": " + id + " " + a + " fell below the stock " + seedStock + " (had " + b + ")");
                }
            }
        }
    }

    @Test
    void exportingTwiceMovesNothingMoreThanTheLimitsAllow() {
        // With ample room everywhere, one export leaves exactly the keep ceilings behind and a second moves nothing.
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + 5000 + c);
            Src src = new Src(1 + rnd.nextInt(20));
            for (int s = 0; s < src.capacity(); s++) {
                if (rnd.nextInt(4) != 0) {
                    src.id[s] = IDS[rnd.nextInt(IDS.length)];
                    src.qty[s] = 1 + rnd.nextInt(src.id[s].equals("Tool_Fertilizer") ? 1 : 120);
                }
            }
            boolean keepFertilizer = rnd.nextBoolean();
            int k = 1 + rnd.nextInt(3);
            int manureStock = rnd.nextInt(60);
            int seedStock = rnd.nextInt(30);
            Predicate<String> plantable = id -> id.startsWith("Seed_") || id.startsWith("Sapling_");
            Tgt chest = new Tgt(new BlockPos(10, 9, 10), false, Integer.MAX_VALUE / 2);
            ExportPlan.ExportWorld<Src> world = p -> p.equals(chest.pos) ? chest : null;
            Map<String, Integer> before = src.totals();
            ExportPlan.run(world, src, CENTER, keepFertilizer, k, manureStock, plantable, seedStock);
            Map<String, Integer> after = src.totals();
            for (String id : IDS) {
                int b = before.getOrDefault(id, 0);
                int a = after.getOrDefault(id, 0);
                if (keepFertilizer && id.equals("Tool_Fertilizer")) {
                    assertEquals(b, a);
                } else if (keepFertilizer && id.equals("Ingredient_Poop")) {
                    assertEquals(Math.min(b, manureStock), a, "case " + c + ": poop kept");
                } else if (id.startsWith("Seed_") || id.startsWith("Sapling_")) {
                    assertEquals(Math.min(b, seedStock), a, "case " + c + ": " + id + " kept");
                } else {
                    assertEquals(0, a, "case " + c + ": " + id + " leaves entirely");
                }
            }
            assertEquals(0, ExportPlan.run(world, src, CENTER, keepFertilizer, k, manureStock, plantable, seedStock).moved(),
                    "case " + c + ": a second export moves nothing");
        }
    }

    private static final class Src implements ExportPlan.ExportSource {
        final String[] id;
        final int[] qty;

        Src(int capacity) {
            id = new String[capacity];
            qty = new int[capacity];
        }

        Map<String, Integer> totals() {
            Map<String, Integer> t = new HashMap<>();
            for (int s = 0; s < id.length; s++) {
                if (id[s] != null) {
                    t.merge(id[s], qty[s], Integer::sum);
                }
            }
            return t;
        }

        @Override public int capacity() { return id.length; }
        @Override public boolean isEmpty(int slot) { return id[slot] == null; }
        @Override public String itemId(int slot) { return id[slot]; }
        @Override public int quantity(int slot) { return qty[slot]; }
    }

    private static final class Tgt implements ExportPlan.ExportTarget<Src> {
        final BlockPos pos;
        final boolean machine;
        final int capacityItems;
        final Map<String, Integer> got = new HashMap<>();

        Tgt(BlockPos pos, boolean machine, int capacityItems) {
            this.pos = pos;
            this.machine = machine;
            this.capacityItems = capacityItems;
        }

        int total() {
            return got.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override public BlockPos pos() { return pos; }
        @Override public boolean isMachine() { return machine; }
        @Override public boolean accepts(Src from, int slot) { return total() < capacityItems; }

        @Override
        public int moveFrom(Src from, int slot, int max) {
            int n = Math.min(Math.min(max, from.qty[slot]), capacityItems - total());
            if (n <= 0) {
                return 0;
            }
            got.merge(from.id[slot], n, Integer::sum);
            from.qty[slot] -= n;
            if (from.qty[slot] == 0) {
                from.id[slot] = null;
            }
            return n;
        }
    }
}
