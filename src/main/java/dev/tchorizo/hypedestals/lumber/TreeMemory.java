package dev.tchorizo.hypedestals.lumber;

import dev.tchorizo.hypedestals.harvest.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Wood of trees a Lumberjack found too big, remembered for {@link #CYCLES} cycles so the area
 * scan does not search the same giant again every cycle (review, Main). Per Lumberjack, transient.
 * Pure, not thread-safe.
 */
public final class TreeMemory {

    /** Cycles a too-big tree is skipped before it is searched again (it may have been cut by hand). */
    public static final int CYCLES = 20;

    private final Map<BlockPos, Integer> until = new HashMap<>();
    private int cycle;

    /** Starts a cycle and forgets what has expired. */
    public void nextCycle() {
        cycle++;
        Iterator<Integer> it = until.values().iterator();
        while (it.hasNext()) {
            if (it.next() <= cycle) {
                it.remove();
            }
        }
    }

    public boolean remembered(BlockPos p) {
        Integer u = until.get(p);
        return u != null && u > cycle;
    }

    public void remember(Iterable<BlockPos> wood) {
        for (BlockPos p : wood) {
            until.put(p, cycle + CYCLES);
        }
    }

    public int size() {
        return until.size();
    }
}
