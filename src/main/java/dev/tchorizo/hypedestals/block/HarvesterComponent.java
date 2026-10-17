package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.config.TierSettings;
import dev.tchorizo.hypedestals.harvest.CycleResult;
import dev.tchorizo.hypedestals.harvest.CycleStats;
import dev.tchorizo.hypedestals.harvest.ExportPlan;
import dev.tchorizo.hypedestals.harvest.PendingDrops;
import dev.tchorizo.hypedestals.harvest.SoilPass;
import dev.tchorizo.hypedestals.harvest.UpgradeSet;
import dev.tchorizo.hypedestals.harvest.UpgradeType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Harvester block state. {@code Tier}, {@code Radius} and {@code IntervalSeconds} come from the
 * tier's block JSON; {@code Upgrades} (item ids, installation order) is absent there, so a placed
 * Harvester starts without upgrades, and is persisted with the block. Everything else is
 * runtime-only and resets on load. The radius and interval a Harvester runs with come from the
 * global config for its {@code Tier} ({@link #settings()}); the JSON values are only the fallback
 * for a tier the config does not know.
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
                    .append(new KeyedCodec<>("Upgrades", Codec.STRING_ARRAY),
                            HarvesterComponent::readUpgrades, c -> c.upgrades.toArray())
                    .add()
                    .build();

    // Persisted.
    int tier;
    /** Asset radius: within {@code 1..HarvesterSettings.MAX_RADIUS}, or {@code 0} when the JSON value was invalid. */
    int radius;
    /** Asset interval: positive, or {@code 0} when the JSON value was not. */
    float intervalSeconds;
    /** Installed upgrades; mutate through {@link #upgrades()} and mark the block for saving. */
    UpgradeSet upgrades = new UpgradeSet();

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
    /** Whether the "crops still being updated" warning was logged since the last cycle without such failures. */
    boolean transientWarned;
    /**
     * Persisted upgrade ids the last decode dropped (unknown or repeated); warned about once, with
     * the block position, by {@code HarvesterSystem}. Carried over by {@link #clone()} in case the
     * engine clones the decoded component.
     */
    List<String> ignoredUpgrades = List.of();
    boolean ignoredUpgradesWarned;
    /**
     * Seconds since the last soil sweep; infinite after a load, so the first sweep runs on the
     * first tick. The sweep has its own timer, see {@code HarvesterSystem}.
     */
    float soilElapsed = Float.POSITIVE_INFINITY;
    /**
     * Seconds since the last spawn of the upgrade particles ({@code UpgradeParticlesSeconds});
     * infinite after a load, so a Harvester with upgrades shows them on its first tick.
     */
    float upgradeParticlesElapsed = Float.POSITIVE_INFINITY;
    /** Result of the last soil sweep (watering and fertilizer); {@code null} before the first one. */
    SoilPass.Result lastSoil;
    /** Soil blocks watered since the load. */
    int wateredTotal;
    /** Soil blocks fertilized since the load. */
    int fertilizedTotal;
    /** Result of the most recent export to an adjacent container; {@code NONE} before the first one or with export off. */
    ExportPlan.Result lastExport = ExportPlan.Result.NONE;
    /**
     * Seconds left of the post-harvest collect burst ({@code CollectBurst}): while positive,
     * {@code HarvesterSystem} collects drops on every tick between cycles. {@code 0} after a load.
     */
    float burstRemaining;
    /** Consecutive burst ticks that saw items in the box but put none in ({@code CollectBurst.ticksWithoutProgress}). */
    int burstTicksWithoutProgress;
    /** Timings and counters since the load ({@code inspect}'s {@code perf:}; {@code HarvesterSystem} fills them). */
    final CycleStats stats = new CycleStats();
    /**
     * Drops reserved by recent cycles that the collects have not put in yet, claimed in the next
     * cycles' container room ({@code HarvestCycle.run}). Empty after a load.
     */
    final PendingDrops pendingDrops = new PendingDrops();

    public HarvesterComponent() {}

    private static void readUpgrades(HarvesterComponent c, String[] ids) {
        c.upgrades = UpgradeSet.parse(ids);
        c.ignoredUpgrades = c.upgrades.ignored();
    }

    public int tier() {
        return tier;
    }

    /**
     * Radius and interval this Harvester runs with: the global config's entry for its tier, or the
     * block JSON values for a tier the config does not know. Read anew every call, so a config
     * change applies right away. A {@code 0} in the result means inactive (invalid JSON fallback).
     */
    public TierSettings settings() {
        return HyPedestalsPlugin.settings().effective(tier, radius, intervalSeconds);
    }

    /** Effective radius; see {@link #settings()}. */
    public int radius() {
        return settings().radius();
    }

    /** Effective interval; see {@link #settings()}. */
    public float intervalSeconds() {
        return settings().intervalSeconds();
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

    /**
     * Installed upgrades, live and mutable. After changing them outside the engine's own flows,
     * call {@code BlockModule.BlockStateInfo.markNeedsSaving} on the block entity, or the change
     * may not reach disk.
     */
    public UpgradeSet upgrades() {
        return upgrades;
    }

    public boolean hasUpgrade(UpgradeType type) {
        return upgrades.has(type);
    }

    /** Result of the most recent soil sweep, or {@code null} before the first one since the load. */
    public SoilPass.Result lastSoil() {
        return lastSoil;
    }

    /** Soil blocks watered since the load. */
    public int wateredTotal() {
        return wateredTotal;
    }

    /** Soil blocks fertilized since the load. */
    public int fertilizedTotal() {
        return fertilizedTotal;
    }

    /** Seconds since the last soil sweep; infinite before the first tick after the load. */
    public float secondsSinceSoilSweep() {
        return soilElapsed;
    }

    /** See {@link #lastExport}. */
    public ExportPlan.Result lastExport() {
        return lastExport;
    }

    /** Seconds left of the post-harvest collect burst; {@code 0} when none is running. */
    public float burstSecondsLeft() {
        return burstRemaining;
    }

    /** Performance counters since the load; see {@link CycleStats}. */
    public CycleStats stats() {
        return stats;
    }

    public float secondsToNextCycle() {
        return Math.max(0f, intervalSeconds() - elapsed);
    }

    /** Copies the persisted fields and what the decode reported about them; runtime state starts fresh. */
    @Override
    public HarvesterComponent clone() {
        HarvesterComponent copy = new HarvesterComponent();
        copy.tier = tier;
        copy.radius = HarvesterSettings.sanitizeRadius(radius);
        copy.intervalSeconds = HarvesterSettings.sanitizeInterval(intervalSeconds);
        copy.upgrades = upgrades.copy();
        copy.ignoredUpgrades = ignoredUpgrades;
        copy.ignoredUpgradesWarned = ignoredUpgradesWarned;
        return copy;
    }
}
