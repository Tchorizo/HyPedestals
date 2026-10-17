package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * How a Harvester shows its installed upgrades. Each tier's asset repeats the
 * {@code State.Definitions} entries named here (an inherited {@code State} would point at the
 * Template's own variant blocks); they differ only in {@code Particles}: {@link #WATERING_PARTICLE}
 * for watering, {@link #FERTILIZER_PARTICLE} for the fertilizer, both for both, none in the
 * default state. {@link #particlesFor} lists the same effects for the periodic spawn
 * ({@code UpgradeParticlesSeconds}), which does not depend on the client drawing state particles.
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
    /** Size multiplier, as on the vanilla brazier: a ring of radius 0.6, outside the 0.875-wide cap. */
    public static final float PARTICLE_SCALE = 1.2f;
    /** Height of the ring above the block's base: around the shaft, under the cap. */
    public static final double PARTICLE_OFFSET_Y = 0.6;
    /** Blue of the vanilla {@code Rock_Gem_Sapphire} sparkles. */
    public static final Particle WATERING_PARTICLE = new Particle(PARTICLE_SYSTEM, "#64c1ff");
    /** Green of the vanilla {@code Rock_Gem_Emerald} sparkles. */
    public static final Particle FERTILIZER_PARTICLE = new Particle(PARTICLE_SYSTEM, "#60d682");

    /**
     * One particle effect: a vanilla particle system and the {@code #rrggbb} color it is tinted with.
     *
     * @throws IllegalArgumentException if {@code color} is not {@code #rrggbb}
     */
    public record Particle(String systemId, String color) {
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

    /** The effects of {@code upgrades}, watering first, independent of installation order; empty for none. */
    public static List<Particle> particlesFor(UpgradeSet upgrades) {
        List<Particle> particles = new ArrayList<>(2);
        if (upgrades.has(UpgradeType.WATERING)) {
            particles.add(WATERING_PARTICLE);
        }
        if (upgrades.has(UpgradeType.FERTILIZER)) {
            particles.add(FERTILIZER_PARTICLE);
        }
        return particles;
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
