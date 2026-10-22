package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;
import dev.tchorizo.hypedestals.harvest.UpgradeType;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;

/**
 * Interaction type {@code HyPedestalsRemoveUpgrade}: the crouching branch of the Harvester's and
 * Lumberjack's {@code Use}. Takes out the newest upgrade and gives its item back to the player; what
 * does not fit in the inventory is dropped at the player. An interactor that is not a player gets
 * nothing handed: the item drops on top of the block. Fails when the machine has no upgrade. The item is
 * handed over first and the upgrade removed only after that succeeded, so an engine exception on
 * the way never leaves the player with neither the item nor the upgrade.
 */
public final class RemoveUpgradeInteraction extends SimpleBlockInteraction {

    public static final String TYPE_ID = "HyPedestalsRemoveUpgrade";

    public static final BuilderCodec<RemoveUpgradeInteraction> CODEC = BuilderCodec
            .builder(RemoveUpgradeInteraction.class, RemoveUpgradeInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Removes the newest upgrade from the targeted Harvester or Lumberjack and gives it back.")
            .build();

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> cb, InteractionType type,
                                     InteractionContext ctx, ItemStack held, Vector3i pos, CooldownHandler cooldown) {
        UpgradeTarget target = UpgradeTarget.at(world, pos);
        if (target == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        UpgradeType removed = target.machine().upgrades().newest();
        if (removed == null) {
            UpgradeTarget.tell(cb, ctx, "none", null);
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> interactor = ctx.getEntity();
        ItemStack item = new ItemStack(removed.itemId(), 1);
        if (cb.getComponent(interactor, Player.getComponentType()) == null) {
            // Player.giveItem expects a player's inventory; drop on top of the block instead (added
            // through the command buffer: the entity store may be processing during an interaction).
            cb.addEntities(ItemComponent.generateItemDrops(cb, List.of(item),
                    new Vector3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5), Rotation3f.IDENTITY), AddReason.SPAWN);
        } else {
            ItemStack remainder = Player.giveItem(item, interactor, cb).getRemainder();
            if (!ItemStack.isEmpty(remainder)) {
                ItemUtils.dropItem(interactor, remainder, cb);
            }
        }

        target.machine().upgrades().remove(removed);
        target.markNeedsSaving();
        MachineVisual.request(world, pos);
        UpgradeTarget.tell(cb, ctx, "removed", removed);
        HyPedestalsPlugin.log("upgrade: %s removed from %s at %d %d %d -> %s",
                removed.displayName(), target.machine().machineName(), pos.x, pos.y, pos.z, target.machine().upgrades());
    }

    @Override
    protected void simulateInteractWithBlock(InteractionType type, InteractionContext ctx, ItemStack held,
                                             World world, Vector3i pos) {
        // Server-authoritative; nothing to predict.
    }
}
