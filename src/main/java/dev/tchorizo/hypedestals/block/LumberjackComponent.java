package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.config.LumberSettings;
import dev.tchorizo.hypedestals.harvest.BlockPos;
import dev.tchorizo.hypedestals.harvest.CycleStats;
import dev.tchorizo.hypedestals.harvest.ExportPlan;
import dev.tchorizo.hypedestals.harvest.UpgradeMachine;
import dev.tchorizo.hypedestals.harvest.UpgradeSet;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import dev.tchorizo.hypedestals.lumber.LumberResult;
import dev.tchorizo.hypedestals.lumber.TreeMemory;

import java.util.List;

/**
 * Lumberjack block state (Lumberjack spec §1). {@code Tier}, {@code Radius} and
 * {@code IntervalSeconds} come from the tier's block JSON and are the fallback for a tier the
 * global config does not know ({@link #settings()}); {@code Upgrades} (item ids, installation
 * order) is absent there and persisted with the block, like the Harvester's. Nothing else is
 * persisted: the area scan's rotation, timers and results start fresh after a load.
 */
public final class LumberjackComponent implements Component<ChunkStore>, UpgradeHost {

    /** Assigned once in {@code HyPedestalsPlugin.setup()} by the chunk-store registry. */
    public static ComponentType<ChunkStore, LumberjackComponent> TYPE;

    public static final BuilderCodec<LumberjackComponent> CODEC =
            BuilderCodec.builder(LumberjackComponent.class, LumberjackComponent::new)
                    .append(new KeyedCodec<>("Tier", Codec.INTEGER),
                            (c, v) -> c.tier = v, c -> c.tier)
                    .add()
                    .append(new KeyedCodec<>("Radius", Codec.INTEGER),
                            (c, v) -> c.radius = LumberSettings.sanitizeRadius(v), c -> c.radius)
                    .add()
                    .append(new KeyedCodec<>("IntervalSeconds", Codec.FLOAT),
                            (c, v) -> c.intervalSeconds = LumberSettings.sanitizeInterval(v), c -> c.intervalSeconds)
                    .add()
                    .append(new KeyedCodec<>("Upgrades", Codec.STRING_ARRAY),
                            LumberjackComponent::readUpgrades, c -> c.upgrades.toArray())
                    .add()
                    .build();

    // Persisted.
    int tier;
    /** Asset radius: within the config's range, or {@code 0} when the JSON value was invalid. */
    int radius;
    /** Asset interval: positive, or {@code 0} when the JSON value was not. */
    float intervalSeconds;
    /** Installed upgrades; mutate through {@link #upgrades()} and mark the block for saving. */
    UpgradeSet upgrades = new UpgradeSet(UpgradeMachine.LUMBERJACK);

    // Transient.
    /** Seconds on the cycle timer; set to the position's stagger offset on the first tick after a load. */
    float elapsed;
    /** Whether a cycle is queued on the world thread and has not run yet (one at a time). */
    volatile boolean queued;
    /** Where the next cycle's area scan starts. */
    int scanIndex;
    LumberResult lastResult;
    ExportPlan.Result lastExport = ExportPlan.Result.NONE;
    long lastCycleTick;
    /** Too-big trees skipped for a while ({@code TreeMemory}). */
    final TreeMemory memory = new TreeMemory();
    /** Trees cut since the load. */
    int treesTotal;
    boolean inactiveWarned;
    boolean missingContainerWarned;
    final CycleStats stats = new CycleStats();
    /** The block's position, once read on the first tick (a block does not move); {@code null} after a load. */
    BlockPos center;
    /**
     * Persisted upgrade ids the last decode dropped (unknown, repeated, or not accepted by a
     * Lumberjack); warned about once, with the block position, by {@code LumberjackSystem}.
     */
    List<String> ignoredUpgrades = List.of();
    boolean ignoredUpgradesWarned;
    /**
     * Seconds since the last spawn of the upgrade particles ({@code UpgradeParticlesSeconds});
     * infinite after a load, so a Lumberjack with the Planter shows them on its first tick.
     */
    float upgradeParticlesElapsed = Float.POSITIVE_INFINITY;
    /** Saplings the Planter's grid planted since the load. */
    int gridPlantedTotal;

    public LumberjackComponent() {}

    private static void readUpgrades(LumberjackComponent c, String[] ids) {
        c.upgrades = UpgradeSet.parse(ids, UpgradeMachine.LUMBERJACK);
        c.ignoredUpgrades = c.upgrades.ignored();
    }

    public int tier() {
        return tier;
    }

    /** Radius and interval this Lumberjack runs with (the config's entry for its tier, else the JSON values). */
    public LumberSettings.Lumber settings() {
        return HyPedestalsPlugin.settings().lumber().effective(tier, radius, intervalSeconds);
    }

    public LumberResult lastResult() {
        return lastResult;
    }

    public ExportPlan.Result lastExport() {
        return lastExport;
    }

    public long lastCycleTick() {
        return lastCycleTick;
    }

    public int treesTotal() {
        return treesTotal;
    }

    public int scanIndex() {
        return scanIndex;
    }

    public CycleStats stats() {
        return stats;
    }

    public float secondsToNextCycle() {
        return Math.max(0f, settings().intervalSeconds() - elapsed);
    }

    /** Installed upgrades (the Planter is the only one a Lumberjack accepts); see {@link UpgradeHost#upgrades()}. */
    @Override
    public UpgradeSet upgrades() {
        return upgrades;
    }

    public boolean hasUpgrade(UpgradeType type) {
        return upgrades.has(type);
    }

    /** Saplings the Planter's grid planted since the load. */
    public int gridPlantedTotal() {
        return gridPlantedTotal;
    }

    /** Puts the cycle timer back to 0 (dev only; on the world thread). */
    public void restartCycleTimer() {
        elapsed = 0f;
    }

    /** Copies the persisted fields and what the decode reported about them; runtime state starts fresh. */
    @Override
    public LumberjackComponent clone() {
        LumberjackComponent copy = new LumberjackComponent();
        copy.tier = tier;
        copy.radius = LumberSettings.sanitizeRadius(radius);
        copy.intervalSeconds = LumberSettings.sanitizeInterval(intervalSeconds);
        copy.upgrades = upgrades.copy();
        copy.ignoredUpgrades = ignoredUpgrades;
        copy.ignoredUpgradesWarned = ignoredUpgradesWarned;
        return copy;
    }
}
