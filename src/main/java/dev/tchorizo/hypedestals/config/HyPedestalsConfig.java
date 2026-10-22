package dev.tchorizo.hypedestals.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.EnumMap;
import java.util.Map;

/**
 * The mod's config file ({@code HyPedestals.json} in the plugin's data directory), loaded through
 * the plugin's {@code withConfig}. Layout:
 *
 * <pre>{@code
 * {
 *   "Copper":  { "Radius": 2, "IntervalSeconds": 12.0, "WateringSeconds": 21600 },
 *   "Iron":    { "Radius": 3, "IntervalSeconds": 8.0,  "WateringSeconds": 43200 },
 *   "Thorium": { "Radius": 4, "IntervalSeconds": 5.0,  "WateringSeconds": 72000 },
 *   "Cobalt":  { "Radius": 5, "IntervalSeconds": 3.0,  "WateringSeconds": 108000 },
 *   "CollectAnyItem": true,
 *   "CollectMargin": 2,
 *   "SoilSweepSeconds": 30,
 *   "ExportToAdjacent": true,
 *   "HarvestParticles": true,
 *   "PostHarvestCollectSeconds": 3.0,
 *   "MaxHarvestsPerCycle": 0,
 *   "UpgradeParticlesSeconds": 0.0,
 *   "ManurePerApplication": 2,
 *   "ManureStock": 100,
 *   "PlanterSeedStock": 64,
 *   "SaplingSpacing": 4,
 *   "Lumberjack": {
 *     "Copper":  { "Radius": 3, "IntervalSeconds": 30.0 },
 *     "Iron":    { "Radius": 4, "IntervalSeconds": 20.0 },
 *     "Thorium": { "Radius": 6, "IntervalSeconds": 15.0 },
 *     "Cobalt":  { "Radius": 8, "IntervalSeconds": 10.0 },
 *     "MaxTreeBlocks": 2048,
 *     "MaxBlocksPerCycle": 4096,
 *     "SaplingSpacing": 4
 *   }
 * }
 * }</pre>
 *
 * Missing keys keep their defaults ({@link ModSettings#DEFAULTS}). Values are stored as decoded;
 * the plugin replaces out-of-range ones with {@link ModSettings#sanitized()} after loading.
 * The current values are one immutable {@link ModSettings} behind a volatile field, so world
 * threads reading it each cycle never see a half-applied change.
 */
public final class HyPedestalsConfig {

    public static final BuilderCodec<HyPedestalsConfig> CODEC = codec();

    private volatile ModSettings settings = ModSettings.DEFAULTS;

    public HyPedestalsConfig() {}

    public ModSettings settings() {
        return settings;
    }

    public void setSettings(ModSettings settings) {
        this.settings = settings;
    }

    private static BuilderCodec<HyPedestalsConfig> codec() {
        BuilderCodec.Builder<HyPedestalsConfig> builder =
                BuilderCodec.builder(HyPedestalsConfig.class, HyPedestalsConfig::new);
        for (Tier tier : Tier.values()) {
            builder = builder.append(new KeyedCodec<>(tier.displayName(), TierEntry.CODEC),
                            (c, e) -> c.settings = c.settings.withTier(tier, e.toSettings(c.settings.tier(tier))),
                            c -> TierEntry.of(c.settings.tier(tier)))
                    .add();
        }
        return builder
                .append(new KeyedCodec<>("CollectAnyItem", Codec.BOOLEAN),
                        (c, v) -> c.settings = c.settings.withCollectAnyItem(v),
                        c -> c.settings.collectAnyItem())
                .add()
                .append(new KeyedCodec<>("CollectMargin", Codec.INTEGER),
                        (c, v) -> c.settings = c.settings.withCollectMargin(v),
                        c -> c.settings.collectMargin())
                .add()
                .append(new KeyedCodec<>("SoilSweepSeconds", Codec.INTEGER),
                        (c, v) -> c.settings = c.settings.withSoilSweepSeconds(v),
                        c -> c.settings.soilSweepSeconds())
                .add()
                .append(new KeyedCodec<>("ExportToAdjacent", Codec.BOOLEAN),
                        (c, v) -> c.settings = c.settings.withExportToAdjacent(v),
                        c -> c.settings.exportToAdjacent())
                .add()
                .append(new KeyedCodec<>("HarvestParticles", Codec.BOOLEAN),
                        (c, v) -> c.settings = c.settings.withHarvestParticles(v),
                        c -> c.settings.harvestParticles())
                .add()
                .append(new KeyedCodec<>("PostHarvestCollectSeconds", Codec.FLOAT),
                        (c, v) -> c.settings = c.settings.withPostHarvestCollectSeconds(v),
                        c -> c.settings.postHarvestCollectSeconds())
                .add()
                .append(new KeyedCodec<>("MaxHarvestsPerCycle", Codec.INTEGER),
                        (c, v) -> c.settings = c.settings.withMaxHarvestsPerCycle(v),
                        c -> c.settings.maxHarvestsPerCycle())
                .add()
                .append(new KeyedCodec<>("UpgradeParticlesSeconds", Codec.FLOAT),
                        (c, v) -> c.settings = c.settings.withUpgradeParticlesSeconds(v),
                        c -> c.settings.upgradeParticlesSeconds())
                .add()
                .append(new KeyedCodec<>("ManurePerApplication", Codec.INTEGER),
                        (c, v) -> c.settings = c.settings.withManurePerApplication(v),
                        c -> c.settings.manurePerApplication())
                .add()
                .append(new KeyedCodec<>("ManureStock", Codec.INTEGER),
                        (c, v) -> c.settings = c.settings.withManureStock(v),
                        c -> c.settings.manureStock())
                .add()
                .append(new KeyedCodec<>("PlanterSeedStock", Codec.INTEGER),
                        (c, v) -> c.settings = c.settings.withPlanterSeedStock(v),
                        c -> c.settings.planterSeedStock())
                .add()
                .append(new KeyedCodec<>("SaplingSpacing", Codec.INTEGER),
                        (c, v) -> c.settings = c.settings.withSaplingSpacing(v),
                        c -> c.settings.saplingSpacing())
                .add()
                .append(new KeyedCodec<>(LumberSettings.TARGET, LumberEntry.CODEC),
                        (c, e) -> c.settings = c.settings.withLumber(e.toSettings(c.settings.lumber())),
                        c -> LumberEntry.of(c.settings.lumber()))
                .add()
                .build();
    }

    /** One tier's object in the file; a missing key keeps the current value. */
    private static final class TierEntry {
        static final BuilderCodec<TierEntry> CODEC = BuilderCodec.builder(TierEntry.class, TierEntry::new)
                .append(new KeyedCodec<>("Radius", Codec.INTEGER), (e, v) -> e.radius = v, e -> e.radius)
                .add()
                .append(new KeyedCodec<>("IntervalSeconds", Codec.FLOAT), (e, v) -> e.intervalSeconds = v, e -> e.intervalSeconds)
                .add()
                .append(new KeyedCodec<>("WateringSeconds", Codec.INTEGER), (e, v) -> e.wateringSeconds = v, e -> e.wateringSeconds)
                .add()
                .build();

        Integer radius;
        Float intervalSeconds;
        Integer wateringSeconds;

        static TierEntry of(TierSettings s) {
            TierEntry e = new TierEntry();
            e.radius = s.radius();
            e.intervalSeconds = s.intervalSeconds();
            e.wateringSeconds = s.wateringSeconds();
            return e;
        }

        TierSettings toSettings(TierSettings current) {
            return new TierSettings(radius != null ? radius : current.radius(),
                    intervalSeconds != null ? intervalSeconds : current.intervalSeconds(),
                    wateringSeconds != null ? wateringSeconds : current.wateringSeconds());
        }
    }

    /** The {@code Lumberjack} object in the file; a missing key keeps the current value. */
    private static final class LumberEntry {
        static final BuilderCodec<LumberEntry> CODEC = codec();

        final Map<Tier, LumberTierEntry> tiers = new EnumMap<>(Tier.class);
        Integer maxTreeBlocks;
        Integer maxBlocksPerCycle;
        Integer saplingSpacing;

        private static BuilderCodec<LumberEntry> codec() {
            BuilderCodec.Builder<LumberEntry> builder = BuilderCodec.builder(LumberEntry.class, LumberEntry::new);
            for (Tier tier : Tier.values()) {
                builder = builder.append(new KeyedCodec<>(tier.displayName(), LumberTierEntry.CODEC),
                                (e, v) -> e.tiers.put(tier, v), e -> e.tiers.get(tier))
                        .add();
            }
            return builder
                    .append(new KeyedCodec<>("MaxTreeBlocks", Codec.INTEGER), (e, v) -> e.maxTreeBlocks = v, e -> e.maxTreeBlocks)
                    .add()
                    .append(new KeyedCodec<>("MaxBlocksPerCycle", Codec.INTEGER),
                            (e, v) -> e.maxBlocksPerCycle = v, e -> e.maxBlocksPerCycle)
                    .add()
                    .append(new KeyedCodec<>("SaplingSpacing", Codec.INTEGER),
                            (e, v) -> e.saplingSpacing = v, e -> e.saplingSpacing)
                    .add()
                    .build();
        }

        static LumberEntry of(LumberSettings s) {
            LumberEntry e = new LumberEntry();
            for (Tier tier : Tier.values()) {
                LumberTierEntry t = new LumberTierEntry();
                t.radius = s.tier(tier).radius();
                t.intervalSeconds = s.tier(tier).intervalSeconds();
                e.tiers.put(tier, t);
            }
            e.maxTreeBlocks = s.maxTreeBlocks();
            e.maxBlocksPerCycle = s.maxBlocksPerCycle();
            e.saplingSpacing = s.saplingSpacing();
            return e;
        }

        LumberSettings toSettings(LumberSettings current) {
            LumberSettings out = current;
            for (Tier tier : Tier.values()) {
                LumberTierEntry t = tiers.get(tier);
                if (t != null) {
                    LumberSettings.Lumber c = current.tier(tier);
                    out = out.withTier(tier, new LumberSettings.Lumber(t.radius != null ? t.radius : c.radius(),
                            t.intervalSeconds != null ? t.intervalSeconds : c.intervalSeconds()));
                }
            }
            out = maxTreeBlocks != null ? out.withMaxTreeBlocks(maxTreeBlocks) : out;
            out = maxBlocksPerCycle != null ? out.withMaxBlocksPerCycle(maxBlocksPerCycle) : out;
            return saplingSpacing != null ? out.withSaplingSpacing(saplingSpacing) : out;
        }
    }

    /** One Lumberjack tier's object in the file. */
    private static final class LumberTierEntry {
        static final BuilderCodec<LumberTierEntry> CODEC = BuilderCodec.builder(LumberTierEntry.class, LumberTierEntry::new)
                .append(new KeyedCodec<>("Radius", Codec.INTEGER), (e, v) -> e.radius = v, e -> e.radius)
                .add()
                .append(new KeyedCodec<>("IntervalSeconds", Codec.FLOAT), (e, v) -> e.intervalSeconds = v, e -> e.intervalSeconds)
                .add()
                .build();

        Integer radius;
        Float intervalSeconds;
    }
}
