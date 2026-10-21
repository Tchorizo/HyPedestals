package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Property tests (fixed seeds, 1000 cases each) over the pure reservation classes: {@link ContainerRoom},
 * {@link PendingDrops} and {@link HarvestCycle} against a container model that fills the way the game's
 * does. A failing case prints its index; the case's {@link Random} is {@code new Random(SEED + index)}.
 */
class ReservationPropertiesTest {

    private static final int CASES = 1000;
    private static final long SEED = 20261021L;
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final String[] ITEMS = {"Wheat", "Essence", "Carrot", "Pumpkin", "Junk"};

    // ---------------------------------------------------------------- ContainerRoom

    @Test
    void roomNeverReservesBeyondItsCapacity() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            int free = rnd.nextInt(8);
            Map<String, Integer> stacks = new HashMap<>();
            Map<String, Integer> partial = new HashMap<>();
            for (String id : ITEMS) {
                stacks.put(id, 1 + rnd.nextInt(100));
                if (rnd.nextInt(3) == 0) {
                    partial.put(id, rnd.nextInt(stacks.get(id)));
                }
            }
            ContainerRoom room = new ContainerRoom(free, partial, stacks::get);
            Map<String, Integer> reserved = new HashMap<>();
            for (int op = 0; op < 25; op++) {
                List<ItemCount> items = randomItems(rnd, 4, 60);
                List<ItemCount> probe = randomItems(rnd, 4, 60);
                boolean fitsBefore = room.fits(probe);
                boolean fits = room.fits(items);
                boolean ok = room.reserve(items);
                assertEquals(fits, ok, "case " + c + ": reserve agrees with fits");
                if (ok) {
                    for (ItemCount i : items) {
                        reserved.merge(i.itemId(), Math.max(0, i.quantity()), Integer::sum);
                    }
                    int slots = 0;
                    for (Map.Entry<String, Integer> e : reserved.entrySet()) {
                        int rest = e.getValue() - Math.min(e.getValue(), partial.getOrDefault(e.getKey(), 0));
                        slots += (rest + stacks.get(e.getKey()) - 1) / stacks.get(e.getKey());
                    }
                    assertTrue(slots <= free, "case " + c + ": reserved " + reserved + " needs " + slots
                            + " slots, only " + free + " free");
                } else {
                    assertEquals(fitsBefore, room.fits(probe), "case " + c + ": a refused reserve changes nothing");
                }
            }
        }
    }

    @Test
    void claimNeverGivesRoomBack() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            Map<String, Integer> partial = new HashMap<>();
            for (String id : ITEMS) {
                if (rnd.nextBoolean()) {
                    partial.put(id, rnd.nextInt(40));
                }
            }
            ContainerRoom room = new ContainerRoom(rnd.nextInt(6), partial, id -> 1 + id.length() * 9);
            for (int op = 0; op < 15; op++) {
                List<ItemCount> probe = randomItems(rnd, 3, 30);
                boolean before = room.fits(probe);
                room.claim(randomItems(rnd, 3, 80));
                assertTrue(!room.fits(probe) || before, "case " + c + ": claiming made room appear for " + probe);
            }
        }
    }

    // ---------------------------------------------------------------- HarvestCycle + PendingDrops

    @Test
    void dropsWithinTheReservationNeverStayOnTheGround() {
        long refusals = 0;
        long dropped = 0;
        for (int c = 0; c < CASES; c++) {
            Sim sim = new Sim(new Random(SEED + c), true);
            sim.runCycles(30);
            sim.flush();
            assertEquals(0, sim.lost, "case " + c + ": units that did not fit when collected\n" + sim.trace());
            assertEquals(0, sim.groundUnits(), "case " + c + ": drops left on the ground after the flush\n" + sim.trace());
            assertEquals(sim.generated, sim.inserted, "case " + c + ": every dropped item reached the container");
            assertEquals(sim.inserted, sim.reportedCollected, "case " + c + ": CycleResult.collected adds up");
            sim.assertWithinCapacity(c);
            refusals += sim.fullCycles;
            dropped += sim.generated;
        }
        // Not vacuous: containers do fill up and stop the scan, and plenty is dropped.
        assertTrue(refusals > 2000, "cycles that stopped for lack of room: " + refusals);
        assertTrue(dropped > 100_000, "items dropped over all cases: " + dropped);
    }

    @Test
    void dropsUpToTheMaximumConserveItemsAndNeverOverflow() {
        // Calibrated reservations may be below what a harvest drops (documented excess): the excess
        // waits on the ground, nothing is lost or created, and the container never holds more than it can.
        for (int c = 0; c < CASES; c++) {
            Sim sim = new Sim(new Random(SEED + c), false);
            sim.runCycles(30);
            assertEquals(sim.generated, sim.inserted + sim.groundUnits(), "case " + c + ": conservation\n" + sim.trace());
            assertEquals(sim.inserted, sim.reportedCollected, "case " + c + ": CycleResult.collected adds up");
            sim.assertWithinCapacity(c);
        }
    }

    @Test
    void cappedCyclesCoverEveryRipePositionInOrder() {
        for (int c = 0; c < CASES; c++) {
            Random rnd = new Random(SEED + c);
            int radius = 1 + rnd.nextInt(4);
            int cap = 1 + rnd.nextInt(6);
            List<BlockPos> positions = HarvestArea.positions(CENTER, radius);
            int size = positions.size();
            Set<BlockPos> ripe = new LinkedHashSet<>();
            Map<BlockPos, String> types = new HashMap<>();
            for (BlockPos p : positions) {
                if (rnd.nextInt(3) != 0) {
                    types.put(p, "Crop");
                    if (rnd.nextInt(3) != 0) {
                        ripe.add(p);
                    }
                }
            }
            if (ripe.isEmpty()) {
                continue;
            }
            List<BlockPos> harvestOrder = new ArrayList<>();
            CycleWorld world = new CycleWorld() {
                @Override public String cropTypeAt(BlockPos p) { return types.get(p); }
                @Override public boolean loaded(BlockPos p) { return true; }
                @Override public List<ItemCount> maxDrops(BlockPos p) { return List.of(new ItemCount("Wheat", 1)); }
                @Override public HarvestOutcome harvest(BlockPos p) {
                    if (!ripe.contains(p)) {
                        return HarvestOutcome.NOT_READY;
                    }
                    harvestOrder.add(p);
                    return HarvestOutcome.HARVESTED;
                }
                @Override public int collectDroppedItems(BlockPos center, double radius) { return 0; }
                @Override public boolean containerFull() { return false; }
                @Override public ContainerRoom room() { return new ContainerRoom(1_000_000, Map.of(), id -> 64); }
            };
            int start = rnd.nextInt(3 * size);
            int cycles = (ripe.size() + cap - 1) / cap;
            int next = start;
            Set<BlockPos> seen = new HashSet<>();
            for (int i = 0; i < cycles; i++) {
                int before = harvestOrder.size();
                CycleResult r = HarvestCycle.run(world, CENTER, radius, 2, new HashSet<>(), next, cap,
                        new PendingDrops(new DropCalibration()));
                assertTrue(r.harvested() <= cap, "case " + c + ": cap respected");
                assertEquals(harvestOrder.size() - before, r.harvested());
                seen.addAll(harvestOrder);
                next = r.nextIndex();
            }
            assertEquals(ripe, seen, "case " + c + ": " + cycles + " cycles of " + cap + " cover all " + ripe.size()
                    + " ripe positions (area " + size + ")");
            // Consecutive harvests follow the circular scan: the index never goes backwards by more than a lap.
            int previous = -1;
            for (BlockPos p : harvestOrder.subList(0, Math.min(harvestOrder.size(), ripe.size()))) {
                int idx = positions.indexOf(p);
                if (previous >= 0) {
                    assertTrue(Math.floorMod(idx - previous, size) > 0, "case " + c + ": scan moves forward");
                }
                previous = idx;
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static List<ItemCount> randomItems(Random rnd, int maxKinds, int maxQuantity) {
        int n = rnd.nextInt(maxKinds + 1);
        List<ItemCount> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(new ItemCount(ITEMS[rnd.nextInt(ITEMS.length)], rnd.nextInt(maxQuantity + 1)));
        }
        return items;
    }

    /** A container with real slots and a ground with drops that the item index shows after a lag. */
    private static final class Sim implements CycleWorld {
        static final Map<String, List<ItemCount>> MAX = Map.of(
                "CropA", List.of(new ItemCount("Wheat", 2), new ItemCount("Essence", 2)),
                "CropB", List.of(new ItemCount("Carrot", 3)),
                "CropC", List.of(new ItemCount("Pumpkin", 1), new ItemCount("Essence", 4), new ItemCount("Junk", 1)));
        static final String[] TYPES = {"CropA", "CropB", "CropC"};

        record Drop(String id, int[] qty, int visibleAt) {}

        final Random rnd;
        final boolean withinReservation;
        final int radius;
        final Map<String, Integer> stack = new HashMap<>();
        final String[] slotItem;
        final int[] slotQty;
        final Map<BlockPos, String> crops = new HashMap<>();
        final List<Drop> ground = new ArrayList<>();
        final PendingDrops pending = new PendingDrops(new DropCalibration());
        final StringBuilder log = new StringBuilder();
        int collects;
        long generated;
        long inserted;
        long lost;
        long reportedCollected;
        int fullCycles;
        int next;

        Sim(Random rnd, boolean withinReservation) {
            this.rnd = rnd;
            this.withinReservation = withinReservation;
            this.radius = 1 + rnd.nextInt(3);
            for (String id : ITEMS) {
                stack.put(id, new int[] {1, 16, 50, 100}[rnd.nextInt(4)]);
            }
            int slots = 1 + rnd.nextInt(14);
            slotItem = new String[slots];
            slotQty = new int[slots];
            for (int s = 0; s < slots; s++) {
                if (rnd.nextInt(3) == 0) {
                    String id = ITEMS[rnd.nextInt(ITEMS.length)];
                    slotItem[s] = id;
                    slotQty[s] = 1 + rnd.nextInt(stack.get(id));
                }
            }
            for (BlockPos p : HarvestArea.positions(CENTER, radius)) {
                if (rnd.nextInt(3) != 0) {
                    crops.put(p, TYPES[rnd.nextInt(TYPES.length)]);
                }
            }
            next = rnd.nextInt(100);
        }

        void runCycles(int n) {
            for (int i = 0; i < n; i++) {
                int[] caps = {0, 0, 4, 12};
                CycleResult r = HarvestCycle.run(this, CENTER, radius, 2, new HashSet<>(), next,
                        caps[rnd.nextInt(caps.length)], pending);
                reportedCollected += r.collected();
                fullCycles += r.cropsWaiting() ? 1 : 0;
                next = r.nextIndex();
                log.append("cycle ").append(i).append(": harvested=").append(r.harvested()).append(" collected=")
                        .append(r.collected()).append(" full=").append(r.containerFull()).append(" ground=")
                        .append(groundUnits()).append(" free=").append(freeSlots()).append('\n');
                if (rnd.nextInt(3) == 0) {
                    drain();
                }
            }
        }

        /** Collects with the index caught up and nothing left to wait for (the cycle's reservations are claimed already). */
        void flush() {
            collects += 10;
            reportedCollected += collectDroppedItems(CENTER, 100);
        }

        String trace() {
            return log.toString();
        }

        /** An export between cycles: some stacks leave. */
        void drain() {
            for (int s = 0; s < slotItem.length; s++) {
                if (slotItem[s] != null && rnd.nextInt(3) == 0) {
                    slotQty[s] -= 1 + rnd.nextInt(slotQty[s]);
                    if (slotQty[s] == 0) {
                        slotItem[s] = null;
                    }
                }
            }
        }

        long groundUnits() {
            long n = 0;
            for (Drop d : ground) {
                n += d.qty[0];
            }
            return n;
        }

        int freeSlots() {
            int n = 0;
            for (String s : slotItem) {
                n += s == null ? 1 : 0;
            }
            return n;
        }

        void assertWithinCapacity(int c) {
            for (int s = 0; s < slotItem.length; s++) {
                assertTrue(slotQty[s] >= 0 && (slotItem[s] == null ? slotQty[s] == 0 : slotQty[s] <= stack.get(slotItem[s])),
                        "case " + c + ": slot " + s + " holds " + slotQty[s] + " " + slotItem[s]);
            }
        }

        @Override
        public String cropTypeAt(BlockPos p) {
            return crops.get(p);
        }

        @Override
        public boolean loaded(BlockPos p) {
            return true;
        }

        @Override
        public List<ItemCount> maxDrops(BlockPos p) {
            return MAX.get(crops.get(p));
        }

        @Override
        public HarvestOutcome harvest(BlockPos p) {
            String type = crops.get(p);
            List<ItemCount> max = MAX.get(type);
            List<ItemCount> bound = withinReservation ? pending.calibration().reservation(type, max) : max;
            for (ItemCount item : bound) {
                int qty = rnd.nextInt(item.quantity() + 1);
                if (qty > 0) {
                    ground.add(new Drop(item.itemId(), new int[] {qty}, collects + rnd.nextInt(4)));
                    generated += qty;
                }
            }
            return HarvestOutcome.HARVESTED;
        }

        @Override
        public int collectDroppedItems(BlockPos center, double r) {
            collects++;
            int total = 0;
            boolean blocked = false;
            for (Drop d : new ArrayList<>(ground)) {
                if (d.visibleAt > collects) {
                    continue;
                }
                int put = insert(d.id, d.qty[0]);
                if (put > 0) {
                    pending.collected(d.id, put);
                    total += put;
                    inserted += put;
                }
                d.qty[0] -= put;
                if (d.qty[0] > 0) {
                    blocked = true;
                    lost += d.qty[0];
                } else {
                    ground.remove(d);
                }
            }
            if (blocked) {
                pending.collectBlocked();
            }
            return total;
        }

        /** Partial stacks of the item first, then empty slots, like the container. */
        private int insert(String id, int qty) {
            int left = qty;
            for (int s = 0; s < slotItem.length && left > 0; s++) {
                if (id.equals(slotItem[s]) && slotQty[s] < stack.get(id)) {
                    int n = Math.min(left, stack.get(id) - slotQty[s]);
                    slotQty[s] += n;
                    left -= n;
                }
            }
            for (int s = 0; s < slotItem.length && left > 0; s++) {
                if (slotItem[s] == null) {
                    int n = Math.min(left, stack.get(id));
                    slotItem[s] = id;
                    slotQty[s] = n;
                    left -= n;
                }
            }
            return qty - left;
        }

        @Override
        public boolean containerFull() {
            for (int s = 0; s < slotItem.length; s++) {
                if (slotItem[s] == null || slotQty[s] < stack.get(slotItem[s])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ContainerRoom room() {
            Map<String, Integer> partial = new HashMap<>();
            for (int s = 0; s < slotItem.length; s++) {
                if (slotItem[s] != null) {
                    partial.merge(slotItem[s], stack.get(slotItem[s]) - slotQty[s], Integer::sum);
                }
            }
            return new ContainerRoom(freeSlots(), partial, stack::get);
        }
    }
}
