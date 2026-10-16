package dev.tchorizo.hypedestals.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import dev.tchorizo.hypedestals.block.HarvesterComponent;
import dev.tchorizo.hypedestals.harvest.CycleResult;
import org.joml.Vector3i;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /hypedestals inspect <x y z>}: one line with the harvester's settings, timer, container
 * usage and last cycle result, {@code no Harvester at <x y z>}, or {@code section not loaded at
 * <x y z>} when the section was never generated (inspecting never generates terrain).
 */
final class InspectCommand extends HyPedestalsCommand.PositionCommand {

    InspectCommand() {
        super("inspect", "Show the state of the Harvester at a position");
    }

    /** Inspecting must never generate terrain; a never-generated section is reported instead. */
    @Override
    int loadFlags() {
        return super.loadFlags() | GetChunkFlags.NO_GENERATE;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        Vector3i pos = position(context);
        return runAt(context, world -> HyPedestalsCommand.reply(context, "%s", describe(world, pos)), pos);
    }

    private static String describe(World world, Vector3i pos) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        Store<ChunkStore> store = world.getChunkStore().getStore();
        HarvesterComponent harvester = ref == null ? null : store.getComponent(ref, HarvesterComponent.TYPE);
        if (harvester == null) {
            return "no Harvester at " + HyPedestalsCommand.format(pos);
        }
        ItemContainerBlock containerBlock = store.getComponent(ref, ItemContainerBlock.getComponentType());
        CycleResult last = harvester.lastResult();
        return String.format(Locale.ROOT, "tier=%d radius=%d interval=%.1fs next=%.1fs slots=%s last: %s",
                harvester.tier(), harvester.radius(), harvester.intervalSeconds(), harvester.secondsToNextCycle(),
                containerBlock == null ? "missing" : slots(containerBlock.getItemContainer()),
                last == null ? "none" : String.format(Locale.ROOT, "harvested=%d collected=%d paused=%b",
                        last.harvested(), last.collected(), last.pausedFull()));
    }

    /** {@code <used>/<capacity>}, counting slots that hold a non-empty stack. */
    private static String slots(ItemContainer container) {
        short capacity = container.getCapacity();
        int used = 0;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty()) {
                used++;
            }
        }
        return used + "/" + capacity;
    }
}
