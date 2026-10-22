package dev.tchorizo.hypedestals.block;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.tchorizo.hypedestals.harvest.UpgradeIndicator;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Interaction type {@code HyPedestalsShowUpgrades}: runs just before the {@code OpenContainer} of
 * the Harvester's and Lumberjack's {@code Use} and shows a toast on the interacting player's screen
 * with the upgrades the machine accepts, installed ones green with {@code [x]}, missing ones gray
 * with {@code [ ]} ({@link UpgradeIndicator}); the toast's icon is the machine's item. The container
 * window itself takes no custom title or text: its window data is just {@code blockItemId} (see
 * AGENTS.md), so a toast is the surface. Never fails or blocks: a target that is not a machine, or
 * a non-player interactor, just ends the interaction and the chain goes on to {@code OpenContainer}.
 */
public final class ShowUpgradesInteraction extends SimpleBlockInteraction {

    public static final String TYPE_ID = "HyPedestalsShowUpgrades";

    private static final String INSTALLED_COLOR = "#4ade80";
    private static final String MISSING_COLOR = "#9ca3af";

    public static final BuilderCodec<ShowUpgradesInteraction> CODEC = BuilderCodec
            .builder(ShowUpgradesInteraction.class, ShowUpgradesInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Shows the player which upgrades the targeted Harvester or Lumberjack has and lacks.")
            .build();

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> cb, InteractionType type,
                                     InteractionContext ctx, ItemStack held, Vector3i pos, CooldownHandler cooldown) {
        UpgradeTarget target = UpgradeTarget.at(world, pos);
        if (target != null) {
            show(world, cb, ctx, pos, target.machine());
        }
    }

    /** The toast of one machine: title line, colored upgrade list, and the machine's item as icon ({@code null} when unknown). */
    public record Toast(Message title, Message list, ItemWithAllMetadata icon, String machineId) {}

    /** Builds the indicator toast of {@code machine} at {@code pos}. */
    public static Toast toast(World world, Vector3i pos, UpgradeHost machine) {
        String machineId = MachineVisual.machineId(world, pos);
        Item item = machineId == null ? null : Item.getAssetMap().getAsset(machineId);
        ItemWithAllMetadata icon = item == null ? null : new ItemStack(machineId, 1).toPacket();
        return new Toast(title(machineId, machine), list(machine), icon, machineId);
    }

    /**
     * Sends the indicator toast of {@code machine} (at {@code pos}) to the interacting player; nothing
     * for an interactor that is not a player.
     */
    static void show(World world, CommandBuffer<EntityStore> cb, InteractionContext ctx, Vector3i pos, UpgradeHost machine) {
        PlayerRef player = cb.getComponent(ctx.getEntity(), PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        Toast toast = toast(world, pos, machine);
        NotificationUtil.sendNotification(player.getPacketHandler(), toast.title(), toast.list(), toast.icon(),
                NotificationStyle.Default);
    }

    /** {@code <Iron Harvester>: upgrades}; the machine kind alone when the tier id is unknown. */
    private static Message title(String machineId, UpgradeHost machine) {
        Message name = machineId == null
                ? Message.raw(machine.machineName())
                : Message.translation(HytaleIds.itemTranslationKey(machineId, "name"));
        return Message.translation("hypedestals.upgrade.indicator.title").param("machine", name);
    }

    /** One colored entry per accepted upgrade, joined by {@code " · "}. */
    private static Message list(UpgradeHost machine) {
        List<Message> parts = new ArrayList<>();
        for (UpgradeIndicator.Entry entry : UpgradeIndicator.entries(machine.upgrades())) {
            if (!parts.isEmpty()) {
                parts.add(Message.raw(" · "));
            }
            Message name = Message.translation(HytaleIds.itemTranslationKey(entry.type().itemId(), "name"));
            parts.add(Message.translation(entry.installed()
                            ? "hypedestals.upgrade.indicator.installed"
                            : "hypedestals.upgrade.indicator.missing")
                    .param("upgrade", name)
                    .color(entry.installed() ? INSTALLED_COLOR : MISSING_COLOR));
        }
        return Message.empty().insertAll(parts);
    }

    @Override
    protected void simulateInteractWithBlock(InteractionType type, InteractionContext ctx, ItemStack held,
                                             World world, Vector3i pos) {
        // Server-authoritative; nothing to predict.
    }
}
