package dev.tchorizo.hypedestals.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Immutable snapshot of the mod's global settings: radius, interval and watering duration per
 * {@link Tier}, plus the global switches. Pure (no server classes), so the rules are unit-tested;
 * {@link HyPedestalsConfig} persists it.
 *
 * @param tiers one entry for every tier
 * @param collectAnyItem {@code true}: the Harvester collects every dropped item in its collect
 *     box; {@code false}: it leaves tools, weapons and armor on the ground (see
 *     {@link dev.tchorizo.hypedestals.harvest.CollectFilter})
 * @param collectMargin blocks beyond the harvest area (horizontally) where drops are still collected
 * @param soilSweepSeconds real seconds between two soil passes (watering) of a Harvester
 * @param exportToAdjacent {@code true}: after each cycle the Harvester moves its items into a
 *     container touching it (below or on the four sides)
 * @param harvestParticles {@code true}: a short particle effect when a cycle harvests something
 */
public record ModSettings(Map<Tier, TierSettings> tiers, boolean collectAnyItem, int collectMargin,
                          int soilSweepSeconds, boolean exportToAdjacent, boolean harvestParticles) {

    public static final int MIN_MARGIN = 0;
    public static final int MAX_MARGIN = 4;
    public static final int MIN_SOIL_SWEEP = 5;
    public static final int MAX_SOIL_SWEEP = 600;
    public static final boolean DEFAULT_COLLECT_ANY_ITEM = true;
    public static final int DEFAULT_COLLECT_MARGIN = 2;
    public static final int DEFAULT_SOIL_SWEEP = 30;
    public static final boolean DEFAULT_EXPORT_TO_ADJACENT = true;
    public static final boolean DEFAULT_HARVEST_PARTICLES = true;
    /** {@code config set} target of the fields that are not per tier. */
    public static final String GLOBAL = "global";

    public static final ModSettings DEFAULTS = defaults();

    public ModSettings {
        EnumMap<Tier, TierSettings> copy = new EnumMap<>(Tier.class);
        copy.putAll(tiers);
        for (Tier t : Tier.values()) {
            if (!copy.containsKey(t)) {
                throw new IllegalArgumentException("missing tier " + t.displayName());
            }
        }
        tiers = Collections.unmodifiableMap(copy);
    }

    private static ModSettings defaults() {
        EnumMap<Tier, TierSettings> tiers = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            tiers.put(t, t.defaults());
        }
        return new ModSettings(tiers, DEFAULT_COLLECT_ANY_ITEM, DEFAULT_COLLECT_MARGIN,
                DEFAULT_SOIL_SWEEP, DEFAULT_EXPORT_TO_ADJACENT, DEFAULT_HARVEST_PARTICLES);
    }

    public static boolean validMargin(int margin) {
        return margin >= MIN_MARGIN && margin <= MAX_MARGIN;
    }

    public static boolean validSoilSweep(int seconds) {
        return seconds >= MIN_SOIL_SWEEP && seconds <= MAX_SOIL_SWEEP;
    }

    public TierSettings tier(Tier tier) {
        return tiers.get(tier);
    }

    /**
     * Settings a Harvester runs with: this config's entry for its tier number, or the values of
     * its block JSON ({@code assetRadius}, {@code assetInterval}) when the number is not one of
     * the four tiers (e.g. a custom tier added by an asset pack). Such a tier waters for the
     * Copper default, since the block JSON has no watering duration.
     */
    public TierSettings effective(int tierNumber, int assetRadius, float assetInterval) {
        Tier tier = Tier.byNumber(tierNumber);
        return tier == null
                ? new TierSettings(assetRadius, assetInterval, Tier.COPPER.defaults().wateringSeconds())
                : tier(tier);
    }

    public ModSettings withTier(Tier tier, TierSettings settings) {
        EnumMap<Tier, TierSettings> copy = new EnumMap<>(tiers);
        copy.put(tier, settings);
        return new ModSettings(copy, collectAnyItem, collectMargin, soilSweepSeconds, exportToAdjacent, harvestParticles);
    }

    public ModSettings withCollectAnyItem(boolean value) {
        return new ModSettings(tiers, value, collectMargin, soilSweepSeconds, exportToAdjacent, harvestParticles);
    }

    public ModSettings withCollectMargin(int value) {
        return new ModSettings(tiers, collectAnyItem, value, soilSweepSeconds, exportToAdjacent, harvestParticles);
    }

    public ModSettings withSoilSweepSeconds(int value) {
        return new ModSettings(tiers, collectAnyItem, collectMargin, value, exportToAdjacent, harvestParticles);
    }

    public ModSettings withExportToAdjacent(boolean value) {
        return new ModSettings(tiers, collectAnyItem, collectMargin, soilSweepSeconds, value, harvestParticles);
    }

    public ModSettings withHarvestParticles(boolean value) {
        return new ModSettings(tiers, collectAnyItem, collectMargin, soilSweepSeconds, exportToAdjacent, value);
    }

    /**
     * Applies to these (current) settings only the fields where {@code entered} differs from
     * {@code opened}, the snapshot the window was filled with. Fields the user did not touch keep
     * their current value, so a {@code config set} made while the window was open survives its Save.
     * Intervals are compared in tenths of a second, the window's precision: a {@code 0.75} set by
     * command shows (and comes back) as {@code 0.8} and is not a change.
     */
    public ModSettings withChangesFrom(ModSettings opened, ModSettings entered) {
        EnumMap<Tier, TierSettings> merged = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            TierSettings c = tier(t), o = opened.tier(t), e = entered.tier(t);
            merged.put(t, new TierSettings(
                    e.radius() != o.radius() ? e.radius() : c.radius(),
                    tenths(e.intervalSeconds()) != tenths(o.intervalSeconds()) ? e.intervalSeconds() : c.intervalSeconds(),
                    e.wateringSeconds() != o.wateringSeconds() ? e.wateringSeconds() : c.wateringSeconds()));
        }
        return new ModSettings(merged,
                entered.collectAnyItem != opened.collectAnyItem ? entered.collectAnyItem : collectAnyItem,
                entered.collectMargin != opened.collectMargin ? entered.collectMargin : collectMargin,
                entered.soilSweepSeconds != opened.soilSweepSeconds ? entered.soilSweepSeconds : soilSweepSeconds,
                entered.exportToAdjacent != opened.exportToAdjacent ? entered.exportToAdjacent : exportToAdjacent,
                entered.harvestParticles != opened.harvestParticles ? entered.harvestParticles : harvestParticles);
    }

    private static long tenths(float seconds) {
        return Math.round(seconds * 10.0);
    }

    /** One message per out-of-range value (e.g. {@code Copper.Radius=9 (allowed 1..8)}); empty when valid. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (Tier t : Tier.values()) {
            TierSettings s = tier(t);
            if (!TierSettings.validRadius(s.radius())) {
                problems.add(String.format(Locale.ROOT, "%s.Radius=%d (allowed %d..%d)",
                        t.displayName(), s.radius(), TierSettings.MIN_RADIUS, TierSettings.MAX_RADIUS));
            }
            if (!TierSettings.validInterval(s.intervalSeconds())) {
                problems.add(String.format(Locale.ROOT, "%s.IntervalSeconds=%s (allowed %s..%s)",
                        t.displayName(), s.intervalSeconds(), TierSettings.MIN_INTERVAL, TierSettings.MAX_INTERVAL));
            }
            if (!TierSettings.validWatering(s.wateringSeconds())) {
                problems.add(String.format(Locale.ROOT, "%s.WateringSeconds=%d (allowed %d..%d)",
                        t.displayName(), s.wateringSeconds(), TierSettings.MIN_WATERING, TierSettings.MAX_WATERING));
            }
        }
        if (!validMargin(collectMargin)) {
            problems.add(String.format(Locale.ROOT, "CollectMargin=%d (allowed %d..%d)", collectMargin, MIN_MARGIN, MAX_MARGIN));
        }
        if (!validSoilSweep(soilSweepSeconds)) {
            problems.add(String.format(Locale.ROOT, "SoilSweepSeconds=%d (allowed %d..%d)",
                    soilSweepSeconds, MIN_SOIL_SWEEP, MAX_SOIL_SWEEP));
        }
        return problems;
    }

    /** Copy with every out-of-range value (see {@link #problems()}) replaced by its default. */
    public ModSettings sanitized() {
        EnumMap<Tier, TierSettings> fixed = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            TierSettings s = tier(t);
            TierSettings d = t.defaults();
            fixed.put(t, new TierSettings(
                    TierSettings.validRadius(s.radius()) ? s.radius() : d.radius(),
                    TierSettings.validInterval(s.intervalSeconds()) ? s.intervalSeconds() : d.intervalSeconds(),
                    TierSettings.validWatering(s.wateringSeconds()) ? s.wateringSeconds() : d.wateringSeconds()));
        }
        return new ModSettings(fixed, collectAnyItem,
                validMargin(collectMargin) ? collectMargin : DEFAULT_COLLECT_MARGIN,
                validSoilSweep(soilSweepSeconds) ? soilSweepSeconds : DEFAULT_SOIL_SWEEP,
                exportToAdjacent, harvestParticles);
    }

    /**
     * Copy with one field changed, for {@code /hypedestals config set <target> <field> <value>}.
     * {@code target} is a tier name (fields {@code Radius}, {@code IntervalSeconds},
     * {@code WateringSeconds}) or {@link #GLOBAL} (fields {@code CollectAnyItem},
     * {@code CollectMargin}, {@code SoilSweepSeconds}, {@code ExportToAdjacent},
     * {@code HarvestParticles}); names ignore case.
     *
     * @throws IllegalArgumentException with a message for the user when the target, field or
     *     value is unknown, malformed or out of range
     */
    public ModSettings set(String target, String field, String value) {
        String f = field.toLowerCase(Locale.ROOT);
        if (target.equalsIgnoreCase(GLOBAL)) {
            return switch (f) {
                case "collectanyitem" -> withCollectAnyItem(parseBoolean(value));
                case "collectmargin" -> {
                    int margin = parseInt(value);
                    if (!validMargin(margin)) {
                        throw new IllegalArgumentException(String.format(Locale.ROOT,
                                "CollectMargin must be %d..%d, got %s", MIN_MARGIN, MAX_MARGIN, value));
                    }
                    yield withCollectMargin(margin);
                }
                case "soilsweepseconds" -> {
                    int seconds = parseInt(value);
                    if (!validSoilSweep(seconds)) {
                        throw new IllegalArgumentException(String.format(Locale.ROOT,
                                "SoilSweepSeconds must be %d..%d, got %s", MIN_SOIL_SWEEP, MAX_SOIL_SWEEP, value));
                    }
                    yield withSoilSweepSeconds(seconds);
                }
                case "exporttoadjacent" -> withExportToAdjacent(parseBoolean(value));
                case "harvestparticles" -> withHarvestParticles(parseBoolean(value));
                default -> throw new IllegalArgumentException("unknown global field " + field
                        + "; expected CollectAnyItem, CollectMargin, SoilSweepSeconds, ExportToAdjacent or HarvestParticles");
            };
        }
        Tier tier = Tier.byName(target);
        if (tier == null) {
            throw new IllegalArgumentException("unknown target " + target + "; expected Copper, Iron, Thorium, Cobalt or " + GLOBAL);
        }
        TierSettings current = tier(tier);
        return switch (f) {
            case "radius" -> {
                int radius = parseInt(value);
                if (!TierSettings.validRadius(radius)) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT,
                            "Radius must be %d..%d, got %s", TierSettings.MIN_RADIUS, TierSettings.MAX_RADIUS, value));
                }
                yield withTier(tier, new TierSettings(radius, current.intervalSeconds(), current.wateringSeconds()));
            }
            case "intervalseconds" -> {
                float seconds = parseFloat(value);
                if (!TierSettings.validInterval(seconds)) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT,
                            "IntervalSeconds must be %s..%s, got %s", TierSettings.MIN_INTERVAL, TierSettings.MAX_INTERVAL, value));
                }
                yield withTier(tier, new TierSettings(current.radius(), seconds, current.wateringSeconds()));
            }
            case "wateringseconds" -> {
                int seconds = parseInt(value);
                if (!TierSettings.validWatering(seconds)) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT,
                            "WateringSeconds must be %d..%d, got %s", TierSettings.MIN_WATERING, TierSettings.MAX_WATERING, value));
                }
                yield withTier(tier, new TierSettings(current.radius(), current.intervalSeconds(), seconds));
            }
            default -> throw new IllegalArgumentException(
                    "unknown tier field " + field + "; expected Radius, IntervalSeconds or WateringSeconds");
        };
    }

    /**
     * One line, e.g. {@code Copper Radius=2 IntervalSeconds=12.0 WateringSeconds=21600 | … |
     * CollectAnyItem=true CollectMargin=2 SoilSweepSeconds=30 ExportToAdjacent=true HarvestParticles=true}.
     */
    public String describe() {
        StringJoiner line = new StringJoiner(" | ");
        for (Tier t : Tier.values()) {
            TierSettings s = tier(t);
            line.add(t.displayName() + " Radius=" + s.radius() + " IntervalSeconds=" + s.intervalSeconds()
                    + " WateringSeconds=" + s.wateringSeconds());
        }
        line.add("CollectAnyItem=" + collectAnyItem + " CollectMargin=" + collectMargin
                + " SoilSweepSeconds=" + soilSweepSeconds + " ExportToAdjacent=" + exportToAdjacent
                + " HarvestParticles=" + harvestParticles);
        return line.toString();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a whole number: " + value);
        }
    }

    private static float parseFloat(String value) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + value);
        }
    }

    private static boolean parseBoolean(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("expected true or false, got " + value);
        };
    }
}
