package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import org.joml.Vector3i;

/**
 * Interaction type {@code HyPedestalsInstallUpgrade}: the upgrade item's {@code Secondary}. Installs
 * the held upgrade into the Harvester or Lumberjack under the cursor and consumes one item from the
 * hand (not in Creative, like the vanilla inventory-modifying interactions). Checked before paid:
 * any other target, an upgrade the machine does not accept (a Lumberjack takes the Planter only),
 * one it already has, or a hand that cannot give up the item fails the interaction without side
 * effects.
 */
public final class InstallUpgradeInteraction extends SimpleBlockInteraction {

    public static final String TYPE_ID = "HyPedestalsInstallUpgrade";

    public static final BuilderCodec<InstallUpgradeInteraction> CODEC = BuilderCodec
            .builder(InstallUpgradeInteraction.class, InstallUpgradeInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Installs the held HyPedestals upgrade into the targeted Harvester or Lumberjack.")
            .build();

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> cb, InteractionType type,
                                     InteractionContext ctx, ItemStack held, Vector3i pos, CooldownHandler cooldown) {
        UpgradeType upgrade = held == null ? null : UpgradeType.fromItemId(held.getItemId());
        UpgradeTarget target = upgrade == null ? null : UpgradeTarget.at(world, pos);
        ItemContainer hand = ctx.getHeldItemContainer();
        if (target == null || hand == null || !target.machine().upgrades().accepts(upgrade)) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        if (target.machine().upgrades().has(upgrade)) {
            UpgradeTarget.tell(cb, ctx, "alreadyInstalled", upgrade);
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        if (!isCreative(cb, ctx) && !hand.removeItemStackFromSlot((short) ctx.getHeldItemSlot(), 1).succeeded()) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        target.machine().upgrades().install(upgrade);
        target.markNeedsSaving();
        MachineVisual.request(world, pos);
        UpgradeTarget.tell(cb, ctx, "installed", upgrade);
        ShowUpgradesInteraction.show(world, cb, ctx, pos, target.machine());
        HyPedestalsPlugin.log("upgrade: %s installed in %s at %d %d %d -> %s",
                upgrade.displayName(), target.machine().machineName(), pos.x, pos.y, pos.z, target.machine().upgrades());
    }

    /** Same test as the vanilla {@code ModifyInventoryInteraction}: the interactor's {@link Player} game mode. */
    private static boolean isCreative(CommandBuffer<EntityStore> cb, InteractionContext ctx) {
        Player player = cb.getComponent(ctx.getEntity(), Player.getComponentType());
        return player != null && player.getGameMode() == GameMode.Creative;
    }

    @Override
    protected void simulateInteractWithBlock(InteractionType type, InteractionContext ctx, ItemStack held,
                                             World world, Vector3i pos) {
        // Server-authoritative; nothing to predict.
    }
}
