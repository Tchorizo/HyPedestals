package dev.tchorizo.hypedestals.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Immutable snapshot of the mod's global settings: radius and interval per {@link Tier}, plus
 * {@code CollectAnyItem} and {@code CollectMargin}. Pure (no server classes), so the rules are
 * unit-tested; {@link HyPedestalsConfig} persists it.
 *
 * @param tiers one entry for every tier
 * @param collectAnyItem {@code true}: the Harvester collects every dropped item in its collect
 *     box; {@code false}: it leaves tools, weapons and armor on the ground (see
 *     {@link dev.tchorizo.hypedestals.harvest.CollectFilter})
 * @param collectMargin blocks beyond the harvest area (horizontally) where drops are still collected
 */
public record ModSettings(Map<Tier, TierSettings> tiers, boolean collectAnyItem, int collectMargin) {

    public static final int MIN_MARGIN = 0;
    public static final int MAX_MARGIN = 4;
    public static final boolean DEFAULT_COLLECT_ANY_ITEM = true;
    public static final int DEFAULT_COLLECT_MARGIN = 2;
    /** {@code config set} target of {@code CollectAnyItem} and {@code CollectMargin}. */
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
        return new ModSettings(tiers, DEFAULT_COLLECT_ANY_ITEM, DEFAULT_COLLECT_MARGIN);
    }

    public static boolean validMargin(int margin) {
        return margin >= MIN_MARGIN && margin <= MAX_MARGIN;
    }

    public TierSettings tier(Tier tier) {
        return tiers.get(tier);
    }

    /**
     * Settings a Harvester runs with: this config's entry for its tier number, or the values of
     * its block JSON ({@code assetRadius}, {@code assetInterval}) when the number is not one of
     * the four tiers (e.g. a custom tier added by an asset pack).
     */
    public TierSettings effective(int tierNumber, int assetRadius, float assetInterval) {
        Tier tier = Tier.byNumber(tierNumber);
        return tier == null ? new TierSettings(assetRadius, assetInterval) : tier(tier);
    }

    public ModSettings withTier(Tier tier, TierSettings settings) {
        EnumMap<Tier, TierSettings> copy = new EnumMap<>(tiers);
        copy.put(tier, settings);
        return new ModSettings(copy, collectAnyItem, collectMargin);
    }

    public ModSettings withCollectAnyItem(boolean value) {
        return new ModSettings(tiers, value, collectMargin);
    }

    public ModSettings withCollectMargin(int value) {
        return new ModSettings(tiers, collectAnyItem, value);
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
        }
        if (!validMargin(collectMargin)) {
            problems.add(String.format(Locale.ROOT, "CollectMargin=%d (allowed %d..%d)", collectMargin, MIN_MARGIN, MAX_MARGIN));
        }
        return problems;
    }

    /** Copy with every out-of-range value (see {@link #problems()}) replaced by its default. */
    public ModSettings sanitized() {
        EnumMap<Tier, TierSettings> fixed = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            TierSettings s = tier(t);
            fixed.put(t, new TierSettings(
                    TierSettings.validRadius(s.radius()) ? s.radius() : t.defaults().radius(),
                    TierSettings.validInterval(s.intervalSeconds()) ? s.intervalSeconds() : t.defaults().intervalSeconds()));
        }
        return new ModSettings(fixed, collectAnyItem, validMargin(collectMargin) ? collectMargin : DEFAULT_COLLECT_MARGIN);
    }

    /**
     * Copy with one field changed, for {@code /hypedestals config set <target> <field> <value>}.
     * {@code target} is a tier name (fields {@code Radius}, {@code IntervalSeconds}) or
     * {@link #GLOBAL} (fields {@code CollectAnyItem}, {@code CollectMargin}); names ignore case.
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
                default -> throw new IllegalArgumentException(
                        "unknown global field " + field + "; expected CollectAnyItem or CollectMargin");
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
                yield withTier(tier, new TierSettings(radius, current.intervalSeconds()));
            }
            case "intervalseconds" -> {
                float seconds = parseFloat(value);
                if (!TierSettings.validInterval(seconds)) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT,
                            "IntervalSeconds must be %s..%s, got %s", TierSettings.MIN_INTERVAL, TierSettings.MAX_INTERVAL, value));
                }
                yield withTier(tier, new TierSettings(current.radius(), seconds));
            }
            default -> throw new IllegalArgumentException(
                    "unknown tier field " + field + "; expected Radius or IntervalSeconds");
        };
    }

    /** One line, e.g. {@code Copper Radius=2 IntervalSeconds=12.0 | … | CollectAnyItem=true CollectMargin=2}. */
    public String describe() {
        StringJoiner line = new StringJoiner(" | ");
        for (Tier t : Tier.values()) {
            TierSettings s = tier(t);
            line.add(t.displayName() + " Radius=" + s.radius() + " IntervalSeconds=" + s.intervalSeconds());
        }
        line.add("CollectAnyItem=" + collectAnyItem + " CollectMargin=" + collectMargin);
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
