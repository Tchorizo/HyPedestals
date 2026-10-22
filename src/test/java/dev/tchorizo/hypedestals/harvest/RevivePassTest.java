package dev.tchorizo.hypedestals.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RevivePassTest {

    private static final BlockPos CENTER = new BlockPos(0, 10, 0);
    private static final ReviveRule.Stage EARLY = new ReviveRule.Stage("Harvested", 0, 3);
    private static final ReviveRule.Stage RIPE = new ReviveRule.Stage("Harvested", 3, 3);

    private static final class FakeWorld implements RevivePass.ReviveWorld {
        final Map<BlockPos, ReviveRule.Stage> stages = new HashMap<>();
        final Set<BlockPos> live = new HashSet<>();
        final Set<BlockPos> parked = new HashSet<>();
        final Set<BlockPos> notTicking = new HashSet<>();
        final Set<BlockPos> refuse = new HashSet<>();
        final List<BlockPos> revived = new ArrayList<>();

        @Override
        public ReviveRule.Stage stageAt(BlockPos p) {
            return stages.get(p);
        }

        @Override
        public boolean liveEntityAt(BlockPos p) {
            return live.contains(p);
        }

        @Override
        public boolean parkedEntityAt(BlockPos p) {
            return parked.contains(p);
        }

        @Override
        public boolean ticking(BlockPos p) {
            return !notTicking.contains(p);
        }

        @Override
        public boolean revive(BlockPos p, ReviveRule.Stage stage) {
            if (refuse.contains(p)) {
                return false;
            }
            revived.add(p);
            live.add(p);
            return true;
        }
    }

    @Test
    void revivesEveryDeadEarlyCropOfTheArea() {
        FakeWorld w = new FakeWorld();
        w.stages.put(new BlockPos(1, 10, 0), EARLY);
        w.stages.put(new BlockPos(-2, 10, 1), EARLY);
        w.stages.put(new BlockPos(0, 11, 0), EARLY);
        assertEquals(3, RevivePass.run(w, CENTER, 2));
        assertEquals(3, w.revived.size());
    }

    @Test
    void leavesAliveParkedRipeAndNonTickingAlone() {
        FakeWorld w = new FakeWorld();
        BlockPos alive = new BlockPos(1, 10, 0), parked = new BlockPos(2, 10, 0), ripe = new BlockPos(0, 10, 1),
                asleep = new BlockPos(0, 10, 2);
        for (BlockPos p : List.of(alive, parked, asleep)) {
            w.stages.put(p, EARLY);
        }
        w.stages.put(ripe, RIPE);
        w.live.add(alive);
        w.parked.add(parked);
        w.notTicking.add(asleep);
        assertEquals(0, RevivePass.run(w, CENTER, 2));
        assertTrue(w.revived.isEmpty());
    }

    @Test
    void ignoresWhatIsOutsideTheArea() {
        FakeWorld w = new FakeWorld();
        w.stages.put(new BlockPos(3, 10, 0), EARLY);
        w.stages.put(new BlockPos(0, 12, 0), EARLY);
        assertEquals(0, RevivePass.run(w, CENTER, 2));
    }

    @Test
    void aRefusedWriteIsNotCounted() {
        FakeWorld w = new FakeWorld();
        BlockPos p = new BlockPos(1, 10, 1);
        w.stages.put(p, EARLY);
        w.refuse.add(p);
        assertEquals(0, RevivePass.run(w, CENTER, 2));
        assertFalse(w.live.contains(p));
    }

    @Test
    void aSecondPassFindsNothingLeftToRevive() {
        FakeWorld w = new FakeWorld();
        w.stages.put(new BlockPos(1, 10, 1), EARLY);
        assertEquals(1, RevivePass.run(w, CENTER, 2));
        assertEquals(0, RevivePass.run(w, CENTER, 2), "once per block: the entity now exists");
    }
}
