package dev.tchorizo.hypedestals.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

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
 *   "MaxHarvestsPerCycle": 0
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
}
