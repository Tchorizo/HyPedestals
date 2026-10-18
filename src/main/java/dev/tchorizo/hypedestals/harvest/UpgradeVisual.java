package dev.tchorizo.hypedestals.harvest;

import java.util.List;

/**
 * How a Harvester shows its installed upgrades. Each tier's asset repeats the
 * {@code State.Definitions} entries named here (an inherited {@code State} would point at the
 * Template's own variant blocks); they differ only in {@code Particles}: {@link #WATERING_PARTICLE}
 * for watering, {@link #FERTILIZER_PARTICLE} for the fertilizer, {@link #WATERING_BOTH_PARTICLE}
 * and {@link #FERTILIZER_BOTH_PARTICLE} for both, none in the default state. {@link #particlesFor}
 * lists the same effects for the periodic spawn ({@code UpgradeParticlesSeconds}), which does not
 * depend on the client drawing state particles.
 */
public final class UpgradeVisual {

    /** The block's base form: Hytale's state name for "no named state" ({@code StateData.NULL_STATE_ID}). */
    public static final String DEFAULT = "default";
    public static final String WATERING = "Watering";
    public static final String FERTILIZER = "Fertilizer";
    public static final String BOTH = "Both";

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
    /** Heights of the two rings with both upgrades, apart so they do not overlap. */
    public static final double WATERING_BOTH_OFFSET_Y = 1.05;
    public static final double FERTILIZER_BOTH_OFFSET_Y = 1.2;
    /**
     * Saturated blue and green: the balls are additive ({@code BlendAdd}) and at most 55% opaque,
     * so a pale tint (the former {@code #64c1ff}/{@code #60d682}) washes out to white-ish.
     */
    public static final String WATERING_COLOR = "#2a7cff";
    public static final String FERTILIZER_COLOR = "#3cff5a";
    public static final Particle WATERING_PARTICLE = new Particle(PARTICLE_SYSTEM, WATERING_COLOR, PARTICLE_OFFSET_Y);
    public static final Particle FERTILIZER_PARTICLE = new Particle(PARTICLE_SYSTEM, FERTILIZER_COLOR, PARTICLE_OFFSET_Y);
    public static final Particle WATERING_BOTH_PARTICLE =
            new Particle(PARTICLE_SYSTEM, WATERING_COLOR, WATERING_BOTH_OFFSET_Y);
    public static final Particle FERTILIZER_BOTH_PARTICLE =
            new Particle(PARTICLE_SYSTEM, FERTILIZER_COLOR, FERTILIZER_BOTH_OFFSET_Y);

    private static final List<Particle> WATERING_ONLY = List.of(WATERING_PARTICLE);
    private static final List<Particle> FERTILIZER_ONLY = List.of(FERTILIZER_PARTICLE);
    private static final List<Particle> BOTH_PARTICLES = List.of(WATERING_BOTH_PARTICLE, FERTILIZER_BOTH_PARTICLE);

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
        boolean watering = upgrades.has(UpgradeType.WATERING);
        boolean fertilizer = upgrades.has(UpgradeType.FERTILIZER);
        if (watering && fertilizer) {
            return BOTH;
        }
        if (watering) {
            return WATERING;
        }
        return fertilizer ? FERTILIZER : DEFAULT;
    }

    /**
     * The effects of {@code upgrades}, watering first, independent of installation order; empty
     * for none. Shared immutable lists.
     */
    public static List<Particle> particlesFor(UpgradeSet upgrades) {
        boolean watering = upgrades.has(UpgradeType.WATERING);
        boolean fertilizer = upgrades.has(UpgradeType.FERTILIZER);
        if (watering && fertilizer) {
            return BOTH_PARTICLES;
        }
        if (watering) {
            return WATERING_ONLY;
        }
        return fertilizer ? FERTILIZER_ONLY : List.of();
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
