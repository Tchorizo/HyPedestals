package dev.tchorizo.hypedestals.harvest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The harvest areas of the Harvesters ticking in one world, so neighbours do not take each other's
 * drops. A collect box reaches {@code CollectMargin} past its area ({@link HarvestCycle#collectExtent}),
 * so two Harvesters a few blocks apart (two Iron 7 blocks apart: areas side by side, boxes 4
 * columns deep into each other) both reach the drops of the crops along their shared border, and
 * whichever collected first took them: the other one's reservation then waited for drops that
 * were already gone, and its own container got drops it had not reserved room for.
 *
 * <p>The rule ({@link #mayCollect}): an item inside the area of another active Harvester is left to
 * that one unless it is inside this Harvester's area too (overlapping areas share). Anything else
 * in the box (the margin outside every area, items a player dropped, from animals) is taken as
 * before. Areas are horizontal squares of the harvest radius, with the collect box's height. The
 * item's own position is all that is checked, the same position the item index finds it by, so a
 * drop that rolled over the border into a neighbour's area goes to the neighbour.
 *
 * <p>Each Harvester refreshes its area on every tick ({@link #refresh}); an area not refreshed for
 * {@link #TTL_TICKS} world ticks (section unloaded or no longer ticking) is no longer active and is
 * forgotten. A broken Harvester's area is forgotten right away ({@link #remove}). Runtime-only, one
 * instance per world, used by that world's thread only: not thread-safe.
 */
public final class HarvestAreas {

    /** World ticks an area stays active after its last refresh. */
    public static final long TTL_TICKS = 40;
    /** The collect box (and an area) reaches this far below the Harvester's block. */
    public static final double BELOW = 2.0;
    /** The collect box (and an area) reaches this far above the Harvester's block. */
    public static final double ABOVE = 3.0;

    private final Map<BlockPos, Area> byCenter = new HashMap<>();

    /** A Harvester's area, as of its last refresh. */
    public static final class Area {
        final BlockPos center;
        int radius;
        long tick;

        Area(BlockPos center) {
            this.center = center;
        }

        /** Whether the point (an item's position) lies in this area. */
        boolean contains(double x, double y, double z) {
            return HarvestAreas.contains(center, radius, x, y, z);
        }
    }

    /** The Harvester at {@code center}, harvesting {@code radius}, is active at world tick {@code tick}. */
    public void refresh(BlockPos center, int radius, long tick) {
        Area area = byCenter.get(center);
        if (area == null) {
            area = new Area(center);
            byCenter.put(center, area);
        }
        area.radius = radius;
        area.tick = tick;
    }

    /** The Harvester at {@code center} was broken: its neighbours may take the drops in its area now. */
    public void remove(BlockPos center) {
        byCenter.remove(center);
    }

    /**
     * Fills {@code out} (cleared first) with the active areas of the other Harvesters that reach
     * into the collect box of {@code self} ({@code halfExtent} horizontally, see
     * {@link HarvestCycle#collectExtent}); forgets the areas that are no longer active.
     *
     * @return {@code out}
     */
    public List<Area> neighbours(BlockPos self, double halfExtent, long tick, List<Area> out) {
        out.clear();
        double minX = self.x() + 0.5 - halfExtent, maxX = self.x() + 0.5 + halfExtent;
        double minZ = self.z() + 0.5 - halfExtent, maxZ = self.z() + 0.5 + halfExtent;
        double minY = self.y() - BELOW, maxY = self.y() + ABOVE;
        for (Iterator<Area> it = byCenter.values().iterator(); it.hasNext(); ) {
            Area area = it.next();
            if (tick - area.tick > TTL_TICKS) {
                it.remove();
                continue;
            }
            if (area.center.equals(self)) {
                continue;
            }
            BlockPos c = area.center;
            if (c.x() - area.radius < maxX && c.x() + area.radius + 1 > minX
                    && c.z() - area.radius < maxZ && c.z() + area.radius + 1 > minZ
                    && c.y() - BELOW < maxY && c.y() + ABOVE > minY) {
                out.add(area);
            }
        }
        return out;
    }

    /**
     * Whether the Harvester at {@code center} with {@code radius} may collect an item at
     * {@code (x, y, z)}: not when the item lies in one of {@code neighbours} (see
     * {@link #neighbours}) and outside its own area.
     */
    public static boolean mayCollect(BlockPos center, int radius, List<Area> neighbours, double x, double y, double z) {
        if (neighbours.isEmpty() || contains(center, radius, x, y, z)) {
            return true;
        }
        for (Area area : neighbours) {
            if (area.contains(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** The harvest columns {@code x±radius, z±radius} (blocks, so up to the far edge) at the collect box's height. */
    static boolean contains(BlockPos center, int radius, double x, double y, double z) {
        return x >= center.x() - radius && x < center.x() + radius + 1
                && z >= center.z() - radius && z < center.z() + radius + 1
                && y >= center.y() - BELOW && y < center.y() + ABOVE;
    }
}
