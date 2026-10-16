package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.harvest.CycleResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Harvester block state. {@code Tier}, {@code Radius} and {@code IntervalSeconds} come from the
 * tier's block JSON; everything else is runtime-only and resets on load.
 */
public final class HarvesterComponent implements Component<ChunkStore> {

    /** Assigned once in {@code HyPedestalsPlugin.setup()} by the chunk-store registry. */
    public static ComponentType<ChunkStore, HarvesterComponent> TYPE;

    public static final BuilderCodec<HarvesterComponent> CODEC =
            BuilderCodec.builder(HarvesterComponent.class, HarvesterComponent::new)
                    .append(new KeyedCodec<>("Tier", Codec.INTEGER),
                            (c, v) -> c.tier = v, c -> c.tier)
                    .add()
                    .append(new KeyedCodec<>("Radius", Codec.INTEGER),
                            (c, v) -> c.radius = HarvesterSettings.sanitizeRadius(v), c -> c.radius)
                    .add()
                    .append(new KeyedCodec<>("IntervalSeconds", Codec.FLOAT),
                            (c, v) -> c.intervalSeconds = HarvesterSettings.sanitizeInterval(v), c -> c.intervalSeconds)
                    .add()
                    .build();

    // Persisted.
    int tier;
    /** Always within {@code 1..HarvesterSettings.MAX_RADIUS}, or {@code 0} when the configured radius was invalid (inactive). */
    int radius;
    /** Always positive, or {@code 0} when the configured interval was not (inactive). */
    float intervalSeconds;

    // Transient.
    float elapsed;
    CycleResult lastResult;
    long lastCycleTick;
    /** Where the next cycle's scan of {@code HarvestArea.positions} starts; see {@code HarvestCycle.run}. */
    int scanIndex;
    /** Crop types whose harvest threw at this block; skipped from then on. */
    final Set<String> warnedTypes = new HashSet<>();
    boolean inactiveWarned;
    boolean missingContainerWarned;

    public HarvesterComponent() {}

    public int tier() {
        return tier;
    }

    public int radius() {
        return radius;
    }

    public float intervalSeconds() {
        return intervalSeconds;
    }

    /** Result of the most recent cycle, or {@code null} before the first one. */
    public CycleResult lastResult() {
        return lastResult;
    }

    /** World tick of the most recent cycle; {@code 0} before the first one. */
    public long lastCycleTick() {
        return lastCycleTick;
    }

    /** Index in {@code HarvestArea.positions} where the next cycle's scan starts; {@code 0} after a load. */
    public int scanIndex() {
        return scanIndex;
    }

    public float secondsToNextCycle() {
        return Math.max(0f, intervalSeconds - elapsed);
    }

    /** Copies only the persisted fields; runtime state starts fresh. */
    @Override
    public HarvesterComponent clone() {
        HarvesterComponent copy = new HarvesterComponent();
        copy.tier = tier;
        copy.radius = HarvesterSettings.sanitizeRadius(radius);
        copy.intervalSeconds = HarvesterSettings.sanitizeInterval(intervalSeconds);
        return copy;
    }
}
