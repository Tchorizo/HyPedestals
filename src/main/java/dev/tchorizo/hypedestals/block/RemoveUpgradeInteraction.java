package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import org.joml.Vector3i;

/**
 * Interaction type {@code HyPedestalsRemoveUpgrade}: the crouching branch of the Harvester's
 * {@code Use}. Takes out the newest upgrade and gives its item back to the player; what does not
 * fit in the inventory is dropped at the player. Fails when the Harvester has no upgrade.
 */
public final class RemoveUpgradeInteraction extends SimpleBlockInteraction {

    public static final String TYPE_ID = "HyPedestalsRemoveUpgrade";

    public static final BuilderCodec<RemoveUpgradeInteraction> CODEC = BuilderCodec
            .builder(RemoveUpgradeInteraction.class, RemoveUpgradeInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Removes the newest upgrade from the targeted Harvester and gives it back.")
            .build();

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> cb, InteractionType type,
                                     InteractionContext ctx, ItemStack held, Vector3i pos, CooldownHandler cooldown) {
        UpgradeTarget target = UpgradeTarget.at(world, pos);
        if (target == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        UpgradeType removed = target.harvester().upgrades().removeLast();
        if (removed == null) {
            UpgradeTarget.tell(cb, ctx, "none", null);
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        target.markNeedsSaving();

        Ref<EntityStore> player = ctx.getEntity();
        ItemStack item = new ItemStack(removed.itemId(), 1);
        ItemStack remainder = Player.giveItem(item, player, cb).getRemainder();
        if (!ItemStack.isEmpty(remainder)) {
            ItemUtils.dropItem(player, remainder, cb);
        }
        UpgradeTarget.tell(cb, ctx, "removed", removed);
        HyPedestalsPlugin.log("upgrade: %s removed at %d %d %d -> %s",
                removed.displayName(), pos.x, pos.y, pos.z, target.harvester().upgrades());
    }

    @Override
    protected void simulateInteractWithBlock(InteractionType type, InteractionContext ctx, ItemStack held,
                                             World world, Vector3i pos) {
        // Server-authoritative; nothing to predict.
    }
}
