package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * How a Harvester shows its installed upgrades. Each combination of upgrades has a named block
 * state ({@link #STATES}); each tier's asset repeats those {@code State.Definitions} entries (an
 * inherited {@code State} would point at the Template's own variant blocks), and they differ only
 * in {@code Particles}: one tinted ring per installed upgrade, watering (blue), fertilizer
 * (green) and planter (amber) from the bottom up, none in the default state.
 * {@link #particlesFor} lists the same effects for the periodic spawn
 * ({@code UpgradeParticlesSeconds}), which does not depend on the client drawing state particles.
 * The names of the watering/fertilizer states predate the planter and are kept: they are part of
 * block ids saved in the world's chunks.
 */
public final class UpgradeVisual {

    /** The block's base form: Hytale's state name for "no named state" ({@code StateData.NULL_STATE_ID}). */
    public static final String DEFAULT = "default";
    public static final String WATERING = "Watering";
    public static final String FERTILIZER = "Fertilizer";
    public static final String BOTH = "Both";
    public static final String PLANTER = "Planter";
    public static final String WATERING_PLANTER = "Watering_Planter";
    public static final String FERTILIZER_PLANTER = "Fertilizer_Planter";
    public static final String ALL = "All";
    /**
     * Every non-default state, indexed by the upgrade bit mask minus one (watering 1, fertilizer
     * 2, planter 4); also the order the tier assets declare them in.
     */
    public static final List<String> STATES =
            List.of(WATERING, FERTILIZER, BOTH, PLANTER, WATERING_PLANTER, FERTILIZER_PLANTER, ALL);

    /**
     * Vanilla {@code Server/Particles/Item/Lantern/Temple_Light_Lantern.particlesystem}: one
     * looping spawner (no {@code LifeSpan}, {@code TotalParticles -1}) of up to 5 glowing balls on a
     * ring of radius 0.5 around its center; its particles have no color of their own, so the
     * {@code Color} of the block particle (or of the spawn packet) is what shows. Vanilla uses it,
     * tinted blue ({@code #286cb3}, {@code Scale 1.2}, {@code PositionOffset Y 0.5}), on
     * {@code Furniture_Temple_Scarak_Brazier}.
     */
    public static final String PARTICLE_SYSTEM = "Temple_Light_Lantern";
    /**
     * Size multiplier: a ring of radius 0.75, outside the 0.875-wide cap, with balls of 0.15–0.5
     * (the user found {@code 1.2} too faint).
     */
    public static final float PARTICLE_SCALE = 1.5f;
    /**
     * Height of the ring above the block's base with one upgrade: over the pedestal, just above
     * the cap (which ends at about 0.97), so the effect is not hidden under it.
     */
    public static final double PARTICLE_OFFSET_Y = 1.1;
    /** Ring heights by number of upgrades, bottom up; at least 0.15 apart so they do not overlap. */
    private static final double[][] OFFSETS = {{PARTICLE_OFFSET_Y}, {1.05, 1.2}, {1.0, 1.15, 1.3}};
    /**
     * Saturated colors: the balls are additive ({@code BlendAdd}) and at most 55% opaque, so a
     * pale tint (the former {@code #64c1ff}/{@code #60d682}) washes out to white-ish.
     */
    public static final String WATERING_COLOR = "#2a7cff";
    public static final String FERTILIZER_COLOR = "#3cff5a";
    public static final String PLANTER_COLOR = "#ffa31a";

    private static final List<List<Particle>> PARTICLES = buildParticles();

    /**
     * One particle effect: a vanilla particle system, the {@code #rrggbb} color it is tinted with
     * and its height above the block's base (at the block's center).
     *
     * @throws IllegalArgumentException if {@code color} is not {@code #rrggbb}
     */
    public record Particle(String systemId, String color, double offsetY) {
        public Particle {
            UpgradeVisual.rgb(color);
        }

        /** Red, green and blue of {@link #color()}, {@code 0..255} each. */
        public int[] rgb() {
            return UpgradeVisual.rgb(color);
        }
    }

    private UpgradeVisual() {}

    /** State name for {@code upgrades}, independent of installation order. */
    public static String stateFor(UpgradeSet upgrades) {
        int mask = mask(upgrades);
        return mask == 0 ? DEFAULT : STATES.get(mask - 1);
    }

    /** The upgrades a state shows (the inverse of {@link #stateFor}); empty for the default or an unknown name. */
    public static UpgradeSet upgradesOf(String state) {
        UpgradeSet set = new UpgradeSet();
        int mask = STATES.indexOf(state) + 1;
        for (UpgradeType type : UpgradeType.values()) {
            if ((mask & bit(type)) != 0) {
                set.install(type);
            }
        }
        return set;
    }

    /**
     * The effects of {@code upgrades}, watering, fertilizer, planter from the bottom up,
     * independent of installation order; empty for none. Shared immutable lists.
     */
    public static List<Particle> particlesFor(UpgradeSet upgrades) {
        return PARTICLES.get(mask(upgrades));
    }

    /** The {@code #rrggbb} color of {@code type}'s ring. */
    public static String color(UpgradeType type) {
        return switch (type) {
            case WATERING -> WATERING_COLOR;
            case FERTILIZER -> FERTILIZER_COLOR;
            case PLANTER -> PLANTER_COLOR;
        };
    }

    private static int bit(UpgradeType type) {
        return 1 << type.ordinal();
    }

    private static int mask(UpgradeSet upgrades) {
        int mask = 0;
        for (UpgradeType type : UpgradeType.values()) {
            if (upgrades.has(type)) {
                mask |= bit(type);
            }
        }
        return mask;
    }

    private static List<List<Particle>> buildParticles() {
        List<List<Particle>> all = new ArrayList<>(STATES.size() + 1);
        for (int mask = 0; mask <= STATES.size(); mask++) {
            List<UpgradeType> shown = new ArrayList<>(3);
            for (UpgradeType type : UpgradeType.values()) {
                if ((mask & bit(type)) != 0) {
                    shown.add(type);
                }
            }
            List<Particle> particles = new ArrayList<>(shown.size());
            for (int i = 0; i < shown.size(); i++) {
                particles.add(new Particle(PARTICLE_SYSTEM, color(shown.get(i)), OFFSETS[shown.size() - 1][i]));
            }
            all.add(List.copyOf(particles));
        }
        return List.copyOf(all);
    }

    /**
     * Parses {@code #rrggbb} (the {@code #} is optional, case ignored) into red, green and blue.
     *
     * @throws IllegalArgumentException for anything else
     */
    public static int[] rgb(String color) {
        String hex = color == null ? "" : color.startsWith("#") ? color.substring(1) : color;
        if (hex.length() != 6 || !hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            throw new IllegalArgumentException("expected a color like #64c1ff, got " + color);
        }
        int value = Integer.parseInt(hex, 16);
        return new int[] {(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff};
    }
}
