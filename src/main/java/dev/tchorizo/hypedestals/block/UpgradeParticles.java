package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.harvest.UpgradeSet;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import dev.tchorizo.hypedestals.harvest.UpgradeVisual;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;

/**
 * Spawns the {@link UpgradeVisual} particles of a Harvester's upgrades as world particle systems,
 * on top of the block state's own {@code Particles}, so the effect shows even if the client does
 * not draw state particles on this block. Each spawn carries a duration: the particle system is
 * looping, and a looping system spawned without one never ends on the client (vanilla
 * {@code Butterflies_Harvest}); with it, a spawn every {@code seconds} keeps one effect alive.
 */
public final class UpgradeParticles {

    /** Shared by every packet and never mutated. */
    private static final Color WATERING_COLOR = color(UpgradeVisual.WATERING_PARTICLE.rgb());
    private static final Color FERTILIZER_COLOR = color(UpgradeVisual.FERTILIZER_PARTICLE.rgb());

    private UpgradeParticles() {}

    /**
     * One spawn of each effect of {@code upgrades} ({@link UpgradeVisual#particlesFor}) at the
     * Harvester at {@code pos}, lasting {@code seconds}; must run on the world thread.
     */
    static void spawn(World world, Vector3i pos, UpgradeSet upgrades, float seconds) {
        Vector3d at = new Vector3d(pos.x + 0.5, pos.y + UpgradeVisual.PARTICLE_OFFSET_Y, pos.z + 0.5);
        if (upgrades.has(UpgradeType.WATERING)) {
            spawn(world, UpgradeVisual.WATERING_PARTICLE.systemId(), at, UpgradeVisual.PARTICLE_SCALE, WATERING_COLOR, seconds);
        }
        if (upgrades.has(UpgradeType.FERTILIZER)) {
            spawn(world, UpgradeVisual.FERTILIZER_PARTICLE.systemId(), at, UpgradeVisual.PARTICLE_SCALE, FERTILIZER_COLOR, seconds);
        }
    }

    /**
     * Sends one {@code systemId} particle system to the players within
     * {@link ParticleUtil#DEFAULT_PARTICLE_DISTANCE} of {@code at} (the radius of
     * {@code ParticleUtil}'s own overloads), tinted with {@code color} ({@code null}: the system's
     * own colors) and limited to {@code maxDuration} seconds ({@code 0}: the system's own
     * lifetime, endless for a looping one). Must run on the world thread. Returns the number of
     * players it was sent to.
     */
    public static int spawn(World world, String systemId, Vector3d at, float scale, Color color, float maxDuration) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                store.getResource(EntityModule.get().getPlayerSpatialResourceType());
        if (spatial == null || spatial.getSpatialStructure() == null) {
            return 0;
        }
        List<Ref<EntityStore>> players = SpatialResource.getThreadLocalReferenceList();
        players.clear();
        spatial.getSpatialStructure().collect(at, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, players);
        if (players.isEmpty()) {
            return 0;
        }
        ParticleUtil.spawnParticleEffect(systemId, at.x, at.y, at.z, 0f, 0f, 0f, scale, color, null, players, store,
                maxDuration);
        return players.size();
    }

    /** Protocol color of {@code rgb} (red, green, blue, {@code 0..255}). */
    public static Color color(int[] rgb) {
        return new Color((byte) rgb[0], (byte) rgb[1], (byte) rgb[2]);
    }
}
