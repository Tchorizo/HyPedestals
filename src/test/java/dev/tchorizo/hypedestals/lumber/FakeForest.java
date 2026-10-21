package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.ContainerRoom;
import dev.tchorizo.hypedestals.harvest.ItemCount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link LumberWorld} for the tests. Blocks are ids; the kind comes from the id:
 * {@code Wood_*} wood, {@code Plant_Leaves_*} leaves, {@code Soil_*} solid sapling soil,
 * {@code Rock_*} solid, {@code Hang_*} a non-solid block that needs support, anything else a
 * solid non-tree block. Missing = air. Each block drops one item of its own id.
 */
final class FakeForest implements LumberWorld {

    final Map<BlockPos, String> blocks = new HashMap<>();
    final Set<BlockPos> growing = new HashSet<>();
    final Set<BlockPos> unloaded = new HashSet<>();
    /** Free container slots; every item id stacks to 64. */
    int freeSlots = 10;
    final Map<String, Integer> stored = new HashMap<>();
    /** Sapling item ids in the container, slot order, with their counts. */
    final Map<String, Integer> saplings = new java.util.LinkedHashMap<>();
    final List<String> planted = new ArrayList<>();
    int removed;
    int partReads;

    FakeForest set(int x, int y, int z, String id) {
        blocks.put(new BlockPos(x, y, z), id);
        return this;
    }

    String at(int x, int y, int z) {
        return blocks.get(new BlockPos(x, y, z));
    }

    /** Soil layers {@code y0-depth+1..y0}, {@code Soil_Grass} on top and {@code Soil_Dirt} below, over the square. */
    FakeForest ground(int cx, int cz, int r, int y0, int depth) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = y0 - depth + 1; y <= y0; y++) {
                    set(x, y, z, y == y0 ? "Soil_Grass" : "Soil_Dirt");
                }
            }
        }
        return this;
    }

    /**
     * A small oak at {@code (x, y, z)} (the sapling spot): trunk from {@code y-roots} to
     * {@code y+height-1}, a 3x3 leaf layer around the top two trunk blocks and one on top.
     */
    FakeForest oak(int x, int y, int z, int roots, int height) {
        for (int ty = y - roots; ty < y + height; ty++) {
            set(x, ty, z, "Wood_Oak_Trunk");
        }
        blocks.putIfAbsent(new BlockPos(x, y - roots - 1, z), "Soil_Dirt");
        int top = y + height - 1;
        for (int ly = top - 1; ly <= top; ly++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        set(x + dx, ly, z + dz, "Plant_Leaves_Oak");
                    }
                }
            }
        }
        set(x, top + 1, z, "Plant_Leaves_Oak");
        return this;
    }

    int count(String prefix) {
        int n = 0;
        for (String id : blocks.values()) {
            n += id.startsWith(prefix) ? 1 : 0;
        }
        return n;
    }

    // ---------------------------------------------------------------- TreeWorld

    @Override
    public TreePart part(BlockPos p) {
        partReads++;
        String id = unloaded.contains(p) ? null : blocks.get(p);
        if (id == null) {
            return TreePart.NONE;
        }
        if (id.startsWith("Wood_")) {
            return TreePart.WOOD;
        }
        return id.startsWith("Plant_Leaves_") ? TreePart.LEAVES : TreePart.NONE;
    }

    @Override
    public boolean loaded(BlockPos p) {
        return !unloaded.contains(p);
    }

    @Override
    public boolean growing(BlockPos p) {
        return growing.contains(p);
    }

    @Override
    public boolean needsSupport(BlockPos p) {
        String id = blocks.get(p);
        return id != null && id.startsWith("Hang_");
    }

    @Override
    public boolean soil(BlockPos p) {
        String id = unloaded.contains(p) ? null : blocks.get(p);
        return id != null && id.startsWith("Soil_");
    }

    @Override
    public boolean solid(BlockPos p) {
        String id = unloaded.contains(p) ? null : blocks.get(p);
        return id != null && !id.startsWith("Hang_") && !id.startsWith("Plant_Leaves_");
    }

    // ---------------------------------------------------------------- LumberWorld

    @Override
    public String blockId(BlockPos p) {
        return blocks.get(p);
    }

    @Override
    public List<ItemCount> drops(BlockPos p) {
        String id = blocks.get(p);
        return id == null ? List.of() : List.of(new ItemCount(id, 1));
    }

    @Override
    public ContainerRoom room() {
        return new ContainerRoom(freeSlots, Map.of(), id -> 64);
    }

    @Override
    public String soilNextTo(BlockPos p) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            String id = blocks.get(new BlockPos(p.x() + d[0], p.y(), p.z() + d[1]));
            if (id != null && id.startsWith("Soil_")) {
                return id;
            }
        }
        return null;
    }

    @Override
    public void remove(BlockPos p) {
        if (blocks.remove(p) != null) {
            removed++;
        }
    }

    @Override
    public void setSoil(BlockPos p, String soilId) {
        blocks.put(p, soilId);
    }

    @Override
    public void store(List<ItemCount> items) {
        for (ItemCount item : items) {
            stored.merge(item.itemId(), item.quantity(), Integer::sum);
        }
    }

    @Override
    public List<String> saplings() {
        return List.copyOf(saplings.keySet());
    }

    @Override
    public boolean plant(BlockPos spot, String saplingItemId) {
        Integer left = saplings.get(saplingItemId);
        if (left == null) {
            return false;
        }
        if (left == 1) {
            saplings.remove(saplingItemId);
        } else {
            saplings.put(saplingItemId, left - 1);
        }
        blocks.put(spot, saplingItemId);
        planted.add(saplingItemId + "@" + spot.x() + " " + spot.y() + " " + spot.z());
        return true;
    }

    @Override
    public boolean isTuft(BlockPos p) {
        String id = blocks.get(p);
        return id != null && id.startsWith("Plant_Grass_");
    }

    // ---------------------------------------------------------------- SaplingRule.Ground

    @Override
    public boolean isEmpty(BlockPos p) {
        return !blocks.containsKey(p);
    }

    @Override
    public boolean isSaplingSoil(BlockPos p) {
        String id = blocks.get(p);
        return id != null && id.startsWith("Soil_");
    }

    @Override
    public boolean isFarmland(BlockPos p) {
        return "Soil_Dirt_Tilled".equals(blocks.get(p));
    }
}
