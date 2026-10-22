package dev.tchorizo.hypedestals.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The Lumberjack's part of the global settings (Lumberjack spec §3): radius and interval per
 * {@link Tier}, and the most blocks a tree may have. Immutable and pure; {@link ModSettings}
 * carries it and {@link HyPedestalsConfig} persists it under {@code Lumberjack}.
 *
 * @param tiers one entry for every tier
 * @param maxTreeBlocks most blocks (wood, leaves, hanging) of a tree a Lumberjack cuts; bigger
 *     trees are left standing
 * @param maxBlocksPerCycle most blocks the tree searches of one Lumberjack cycle read; the next
 *     cycle goes on where it stopped
 * @param saplingSpacing grid spacing of the saplings a Lumberjack with the Planter upgrade plants
 *     in its area ({@code 0} = only the stump's replant)
 */
public record LumberSettings(Map<Tier, Lumber> tiers, int maxTreeBlocks, int maxBlocksPerCycle, int saplingSpacing) {

    /** Radius and cycle interval of one Lumberjack tier. */
    public record Lumber(int radius, float intervalSeconds) {}

    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 16;
    public static final float MIN_INTERVAL = 1f;
    public static final float MAX_INTERVAL = 600f;
    public static final int MIN_TREE_BLOCKS = 64;
    public static final int MAX_TREE_BLOCKS = 8192;
    /** The spike's grown Birch had 414 blocks; Redwood and Amber are the largest vanilla trees. */
    public static final int DEFAULT_TREE_BLOCKS = 2048;
    public static final int MIN_BLOCKS_PER_CYCLE = 256;
    public static final int MAX_BLOCKS_PER_CYCLE = 65536;
    /** About 2 ms of search at the spike's 1–2 µs per block. */
    public static final int DEFAULT_BLOCKS_PER_CYCLE = 4096;
    public static final int MIN_SAPLING_SPACING = 2;
    public static final int MAX_SAPLING_SPACING = 16;
    /** Same default as the Harvester Planter's grid; a Copper (radius 3) has no point at 4, see the README. */
    public static final int DEFAULT_SAPLING_SPACING = 4;
    /** {@code config set} target of {@link #maxTreeBlocks}; a tier is {@code Lumberjack.<Tier>}. */
    public static final String TARGET = "Lumberjack";

    public static final LumberSettings DEFAULTS = defaults();

    public LumberSettings {
        EnumMap<Tier, Lumber> copy = new EnumMap<>(Tier.class);
        copy.putAll(tiers);
        for (Tier t : Tier.values()) {
            if (!copy.containsKey(t)) {
                throw new IllegalArgumentException("missing Lumberjack tier " + t.displayName());
            }
        }
        tiers = Collections.unmodifiableMap(copy);
    }

    /** Per-tier defaults, also the values of the tiers' block JSON (pinned by a test). */
    public static Lumber defaults(Tier tier) {
        return switch (tier) {
            case COPPER -> new Lumber(3, 30f);
            case IRON -> new Lumber(4, 20f);
            case THORIUM -> new Lumber(6, 15f);
            case COBALT -> new Lumber(8, 10f);
        };
    }

    private static LumberSettings defaults() {
        EnumMap<Tier, Lumber> tiers = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            tiers.put(t, defaults(t));
        }
        return new LumberSettings(tiers, DEFAULT_TREE_BLOCKS, DEFAULT_BLOCKS_PER_CYCLE, DEFAULT_SAPLING_SPACING);
    }

    public static boolean validRadius(int radius) {
        return radius >= MIN_RADIUS && radius <= MAX_RADIUS;
    }

    /** {@code false} for NaN too. */
    public static boolean validInterval(float seconds) {
        return seconds >= MIN_INTERVAL && seconds <= MAX_INTERVAL;
    }

    public static boolean validBlocksPerCycle(int blocks) {
        return blocks >= MIN_BLOCKS_PER_CYCLE && blocks <= MAX_BLOCKS_PER_CYCLE;
    }

    public static boolean validTreeBlocks(int blocks) {
        return blocks >= MIN_TREE_BLOCKS && blocks <= MAX_TREE_BLOCKS;
    }

    /** {@code 0} (only the stump) or {@code 2..16} blocks. */
    public static boolean validSaplingSpacing(int value) {
        return value == 0 || (value >= MIN_SAPLING_SPACING && value <= MAX_SAPLING_SPACING);
    }

    /** A block JSON radius in range, else {@code 0} (inactive Lumberjack). */
    public static int sanitizeRadius(int radius) {
        return validRadius(radius) ? radius : 0;
    }

    /** A positive block JSON interval, else {@code 0} (inactive Lumberjack). */
    public static float sanitizeInterval(float seconds) {
        return seconds > 0f ? seconds : 0f;
    }

    public Lumber tier(Tier tier) {
        return tiers.get(tier);
    }

    /**
     * What a Lumberjack with component tier {@code tierNumber} runs with: this config's entry, or
     * its block JSON values for a tier number the config does not know.
     */
    public Lumber effective(int tierNumber, int assetRadius, float assetInterval) {
        Tier tier = Tier.byNumber(tierNumber);
        return tier == null ? new Lumber(assetRadius, assetInterval) : tier(tier);
    }

    public LumberSettings withTier(Tier tier, Lumber lumber) {
        EnumMap<Tier, Lumber> copy = new EnumMap<>(tiers);
        copy.put(tier, lumber);
        return new LumberSettings(copy, maxTreeBlocks, maxBlocksPerCycle, saplingSpacing);
    }

    public LumberSettings withMaxTreeBlocks(int value) {
        return new LumberSettings(tiers, value, maxBlocksPerCycle, saplingSpacing);
    }

    public LumberSettings withMaxBlocksPerCycle(int value) {
        return new LumberSettings(tiers, maxTreeBlocks, value, saplingSpacing);
    }

    public LumberSettings withSaplingSpacing(int value) {
        return new LumberSettings(tiers, maxTreeBlocks, maxBlocksPerCycle, value);
    }

    /** One message per out-of-range value (e.g. {@code Lumberjack.Copper.Radius=0 (allowed 1..16)}). */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (Tier t : Tier.values()) {
            Lumber l = tier(t);
            if (!validRadius(l.radius())) {
                problems.add(String.format(Locale.ROOT, "%s.%s.Radius=%d (allowed %d..%d)",
                        TARGET, t.displayName(), l.radius(), MIN_RADIUS, MAX_RADIUS));
            }
            if (!validInterval(l.intervalSeconds())) {
                problems.add(String.format(Locale.ROOT, "%s.%s.IntervalSeconds=%s (allowed %s..%s)",
                        TARGET, t.displayName(), l.intervalSeconds(), MIN_INTERVAL, MAX_INTERVAL));
            }
        }
        if (!validTreeBlocks(maxTreeBlocks)) {
            problems.add(String.format(Locale.ROOT, "%s.MaxTreeBlocks=%d (allowed %d..%d)",
                    TARGET, maxTreeBlocks, MIN_TREE_BLOCKS, MAX_TREE_BLOCKS));
        }
        if (!validBlocksPerCycle(maxBlocksPerCycle)) {
            problems.add(String.format(Locale.ROOT, "%s.MaxBlocksPerCycle=%d (allowed %d..%d)",
                    TARGET, maxBlocksPerCycle, MIN_BLOCKS_PER_CYCLE, MAX_BLOCKS_PER_CYCLE));
        }
        if (!validSaplingSpacing(saplingSpacing)) {
            problems.add(String.format(Locale.ROOT, "%s.SaplingSpacing=%d (allowed 0, %d..%d)",
                    TARGET, saplingSpacing, MIN_SAPLING_SPACING, MAX_SAPLING_SPACING));
        }
        return problems;
    }

    /** Copy with every out-of-range value replaced by its default. */
    public LumberSettings sanitized() {
        EnumMap<Tier, Lumber> fixed = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            Lumber l = tier(t);
            Lumber d = defaults(t);
            fixed.put(t, new Lumber(validRadius(l.radius()) ? l.radius() : d.radius(),
                    validInterval(l.intervalSeconds()) ? l.intervalSeconds() : d.intervalSeconds()));
        }
        return new LumberSettings(fixed, validTreeBlocks(maxTreeBlocks) ? maxTreeBlocks : DEFAULT_TREE_BLOCKS,
                validBlocksPerCycle(maxBlocksPerCycle) ? maxBlocksPerCycle : DEFAULT_BLOCKS_PER_CYCLE,
                validSaplingSpacing(saplingSpacing) ? saplingSpacing : DEFAULT_SAPLING_SPACING);
    }

    /**
     * Applies only the fields where {@code entered} differs from {@code opened} (the window's
     * snapshot); intervals compared in tenths of a second, the window's precision. See
     * {@link ModSettings#withChangesFrom}.
     */
    public LumberSettings withChangesFrom(LumberSettings opened, LumberSettings entered) {
        EnumMap<Tier, Lumber> merged = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            Lumber c = tier(t), o = opened.tier(t), e = entered.tier(t);
            merged.put(t, new Lumber(e.radius() != o.radius() ? e.radius() : c.radius(),
                    Math.round(e.intervalSeconds() * 10.0) != Math.round(o.intervalSeconds() * 10.0)
                            ? e.intervalSeconds() : c.intervalSeconds()));
        }
        return new LumberSettings(merged,
                entered.maxTreeBlocks != opened.maxTreeBlocks ? entered.maxTreeBlocks : maxTreeBlocks,
                entered.maxBlocksPerCycle != opened.maxBlocksPerCycle ? entered.maxBlocksPerCycle : maxBlocksPerCycle,
                entered.saplingSpacing != opened.saplingSpacing ? entered.saplingSpacing : saplingSpacing);
    }

    /** Whether {@code target} of {@code config set} is the Lumberjack's ({@code Lumberjack} or {@code Lumberjack.<Tier>}). */
    public static boolean isTarget(String target) {
        String t = target.toLowerCase(Locale.ROOT);
        String prefix = TARGET.toLowerCase(Locale.ROOT);
        return t.equals(prefix) || t.startsWith(prefix + ".");
    }

    /**
     * Copy with one field changed: target {@code Lumberjack} (field {@code MaxTreeBlocks}) or
     * {@code Lumberjack.<Tier>} (fields {@code Radius}, {@code IntervalSeconds}); names ignore case.
     *
     * @throws IllegalArgumentException with a message for the user
     */
    public LumberSettings set(String target, String field, String value) {
        String f = field.toLowerCase(Locale.ROOT);
        int dot = target.indexOf('.');
        if (dot < 0 && f.equals("maxblockspercycle")) {
            int blocks = ModSettings.parseIntValue(value);
            if (!validBlocksPerCycle(blocks)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "MaxBlocksPerCycle must be %d..%d, got %s", MIN_BLOCKS_PER_CYCLE, MAX_BLOCKS_PER_CYCLE, value));
            }
            return withMaxBlocksPerCycle(blocks);
        }
        if (dot < 0 && f.equals("saplingspacing")) {
            int spacing = ModSettings.parseIntValue(value);
            if (!validSaplingSpacing(spacing)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "SaplingSpacing must be 0..%d (0 = only the stump), got %s", MAX_SAPLING_SPACING, value));
            }
            return withSaplingSpacing(spacing);
        }
        if (dot < 0) {
            if (!f.equals("maxtreeblocks")) {
                throw new IllegalArgumentException("unknown Lumberjack field " + field
                        + "; expected MaxTreeBlocks, MaxBlocksPerCycle or SaplingSpacing"
                        + " (or Lumberjack.<Tier> Radius|IntervalSeconds)");
            }
            int blocks = ModSettings.parseIntValue(value);
            if (!validTreeBlocks(blocks)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "MaxTreeBlocks must be %d..%d, got %s", MIN_TREE_BLOCKS, MAX_TREE_BLOCKS, value));
            }
            return withMaxTreeBlocks(blocks);
        }
        Tier tier = Tier.byName(target.substring(dot + 1));
        if (tier == null) {
            throw new IllegalArgumentException("unknown target " + target
                    + "; expected Lumberjack.Copper, Lumberjack.Iron, Lumberjack.Thorium or Lumberjack.Cobalt");
        }
        Lumber current = tier(tier);
        return switch (f) {
            case "radius" -> {
                int radius = ModSettings.parseIntValue(value);
                if (!validRadius(radius)) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT,
                            "Radius must be %d..%d, got %s", MIN_RADIUS, MAX_RADIUS, value));
                }
                yield withTier(tier, new Lumber(radius, current.intervalSeconds()));
            }
            case "intervalseconds" -> {
                float seconds = ModSettings.parseFloatValue(value);
                if (!validInterval(seconds)) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT,
                            "IntervalSeconds must be %s..%s, got %s", MIN_INTERVAL, MAX_INTERVAL, value));
                }
                yield withTier(tier, new Lumber(current.radius(), seconds));
            }
            default -> throw new IllegalArgumentException("unknown Lumberjack tier field " + field
                    + "; expected Radius or IntervalSeconds");
        };
    }

    /** One line for the log and {@code config show}. */
    public String describe() {
        StringJoiner out = new StringJoiner(" | ", TARGET + ": ", "");
        for (Tier t : Tier.values()) {
            Lumber l = tier(t);
            out.add(String.format(Locale.ROOT, "%s Radius=%d IntervalSeconds=%s", t.displayName(), l.radius(), l.intervalSeconds()));
        }
        out.add("MaxTreeBlocks=" + maxTreeBlocks + " MaxBlocksPerCycle=" + maxBlocksPerCycle
                + " SaplingSpacing=" + saplingSpacing);
        return out.toString();
    }
}
