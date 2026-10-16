package dev.tchorizo.hypedestals.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.tchorizo.hypedestals.HyPedestalsPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings window ({@code Common/UI/Custom/HyPedestals_Config.ui}), opened by
 * {@code /hypedestals config}. The fields are filled from the current settings; Save sends every
 * field's value in one event (the {@code @} keys are read by the client when the button is
 * pressed), validates them, applies them right away and writes the config file; Reset defaults
 * only refills the fields (Save applies them); Close closes the window.
 */
public final class ConfigPage extends InteractiveCustomUIPage<ConfigPage.Event> {

    private static final String LAYOUT = "HyPedestals_Config.ui";
    private static final String ACTION_SAVE = "Save";
    private static final String ACTION_RESET = "Reset";
    private static final String ACTION_CLOSE = "Close";
    private static final String COLLECT_ANY_ITEM_VALUE = "#CollectAnyItem #CheckBox.Value";
    private static final String EXPORT_TO_ADJACENT_VALUE = "#ExportToAdjacent #CheckBox.Value";
    private static final String HARVEST_PARTICLES_VALUE = "#HarvestParticles #CheckBox.Value";

    public ConfigPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(LAYOUT);
        fill(commands, HyPedestalsPlugin.settings());

        EventData save = EventData.of(Event.ACTION, ACTION_SAVE);
        for (Tier tier : Tier.values()) {
            save.append("@" + radiusId(tier), "#" + radiusId(tier) + ".Value");
            save.append("@" + intervalId(tier), "#" + intervalId(tier) + ".Value");
            save.append("@" + wateringId(tier), "#" + wateringId(tier) + ".Value");
        }
        save.append(Event.COLLECT_ANY_ITEM, COLLECT_ANY_ITEM_VALUE);
        save.append(Event.COLLECT_MARGIN, "#CollectMargin.Value");
        save.append(Event.SOIL_SWEEP, "#SoilSweepSeconds.Value");
        save.append(Event.EXPORT_TO_ADJACENT, EXPORT_TO_ADJACENT_VALUE);
        save.append(Event.HARVEST_PARTICLES, HARVEST_PARTICLES_VALUE);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", save, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton", EventData.of(Event.ACTION, ACTION_RESET), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseConfigButton", EventData.of(Event.ACTION, ACTION_CLOSE), false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event event) {
        if (event.action == null) {
            return;
        }
        switch (event.action) {
            case ACTION_SAVE -> save(event);
            case ACTION_RESET -> {
                UICommandBuilder commands = new UICommandBuilder();
                fill(commands, ModSettings.DEFAULTS);
                commands.set("#Status.Text", Message.translation("hypedestals.ui.config.defaultsLoaded"));
                sendUpdate(commands, false);
            }
            case ACTION_CLOSE -> close();
            default -> { }
        }
    }

    private void save(Event event) {
        UICommandBuilder commands = new UICommandBuilder();
        ModSettings entered = event.toSettings();
        List<String> problems = entered == null ? List.of("missing value") : entered.problems();
        if (!problems.isEmpty()) {
            commands.set("#Status.Text", Message.translation("hypedestals.ui.config.invalid")
                    .param("problems", String.join(", ", problems)));
            sendUpdate(commands, false);
            return;
        }
        String who = playerRef.getUsername();
        HyPedestalsPlugin.updateSettings(current -> entered).whenComplete((ignored, error) -> {
            if (error != null) {
                HyPedestalsPlugin.warn("config: %s saved from the window but writing %s failed: %s",
                        who, HyPedestalsPlugin.configFile(), error);
            } else {
                HyPedestalsPlugin.log("config: %s saved from the window: %s", who, entered.describe());
            }
        });
        fill(commands, entered);
        commands.set("#Status.Text", Message.translation("hypedestals.ui.config.saved"));
        sendUpdate(commands, false);
    }

    private static void fill(UICommandBuilder commands, ModSettings settings) {
        for (Tier tier : Tier.values()) {
            TierSettings s = settings.tier(tier);
            commands.set("#" + radiusId(tier) + ".Value", s.radius());
            commands.set("#" + intervalId(tier) + ".Value", s.intervalSeconds());
            commands.set("#" + wateringId(tier) + ".Value", s.wateringSeconds());
        }
        commands.set(COLLECT_ANY_ITEM_VALUE, settings.collectAnyItem());
        commands.set("#CollectMargin.Value", settings.collectMargin());
        commands.set("#SoilSweepSeconds.Value", settings.soilSweepSeconds());
        commands.set(EXPORT_TO_ADJACENT_VALUE, settings.exportToAdjacent());
        commands.set(HARVEST_PARTICLES_VALUE, settings.harvestParticles());
    }

    /** Element id of a tier's radius field, e.g. {@code CopperRadius}; the event key is the same with {@code @}. */
    static String radiusId(Tier tier) {
        return tier.displayName() + "Radius";
    }

    static String intervalId(Tier tier) {
        return tier.displayName() + "Interval";
    }

    static String wateringId(Tier tier) {
        return tier.displayName() + "Watering";
    }

    /** Event sent by the window's buttons; the values are only present on Save. */
    public static final class Event {
        static final String ACTION = "Action";
        static final String COLLECT_ANY_ITEM = "@CollectAnyItem";
        static final String COLLECT_MARGIN = "@CollectMargin";
        static final String SOIL_SWEEP = "@SoilSweepSeconds";
        static final String EXPORT_TO_ADJACENT = "@ExportToAdjacent";
        static final String HARVEST_PARTICLES = "@HarvestParticles";

        public static final BuilderCodec<Event> CODEC = codec();

        String action;
        final Map<Tier, Double> radius = new EnumMap<>(Tier.class);
        final Map<Tier, Double> interval = new EnumMap<>(Tier.class);
        final Map<Tier, Double> watering = new EnumMap<>(Tier.class);
        Boolean collectAnyItem;
        Double collectMargin;
        Double soilSweepSeconds;
        Boolean exportToAdjacent;
        Boolean harvestParticles;

        private static BuilderCodec<Event> codec() {
            BuilderCodec.Builder<Event> builder = BuilderCodec.builder(Event.class, Event::new)
                    .append(new KeyedCodec<>(ACTION, Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                    .add()
                    .append(new KeyedCodec<>(COLLECT_ANY_ITEM, Codec.BOOLEAN), (e, v) -> e.collectAnyItem = v, e -> e.collectAnyItem)
                    .add()
                    .append(new KeyedCodec<>(COLLECT_MARGIN, Codec.DOUBLE), (e, v) -> e.collectMargin = v, e -> e.collectMargin)
                    .add()
                    .append(new KeyedCodec<>(SOIL_SWEEP, Codec.DOUBLE), (e, v) -> e.soilSweepSeconds = v, e -> e.soilSweepSeconds)
                    .add()
                    .append(new KeyedCodec<>(EXPORT_TO_ADJACENT, Codec.BOOLEAN), (e, v) -> e.exportToAdjacent = v, e -> e.exportToAdjacent)
                    .add()
                    .append(new KeyedCodec<>(HARVEST_PARTICLES, Codec.BOOLEAN), (e, v) -> e.harvestParticles = v, e -> e.harvestParticles)
                    .add();
            // Number fields arrive as doubles, whole or not; they are rounded below.
            for (Tier tier : Tier.values()) {
                builder = builder
                        .append(new KeyedCodec<>("@" + radiusId(tier), Codec.DOUBLE),
                                (e, v) -> e.radius.put(tier, v), e -> e.radius.get(tier))
                        .add()
                        .append(new KeyedCodec<>("@" + intervalId(tier), Codec.DOUBLE),
                                (e, v) -> e.interval.put(tier, v), e -> e.interval.get(tier))
                        .add()
                        .append(new KeyedCodec<>("@" + wateringId(tier), Codec.DOUBLE),
                                (e, v) -> e.watering.put(tier, v), e -> e.watering.get(tier))
                        .add();
            }
            return builder.build();
        }

        /**
         * Entered values as settings (radius, watering and sweep rounded to whole numbers,
         * interval to one decimal), or {@code null} if any value is missing. Range checks are
         * left to {@link ModSettings#problems()}.
         */
        ModSettings toSettings() {
            if (collectAnyItem == null || collectMargin == null || soilSweepSeconds == null
                    || exportToAdjacent == null || harvestParticles == null) {
                return null;
            }
            Map<Tier, TierSettings> tiers = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                Double r = radius.get(tier), i = interval.get(tier), w = watering.get(tier);
                if (r == null || i == null || w == null) {
                    return null;
                }
                tiers.put(tier, new TierSettings((int) Math.round(r), Math.round(i * 10) / 10f, (int) Math.round(w)));
            }
            return new ModSettings(tiers, collectAnyItem, (int) Math.round(collectMargin),
                    (int) Math.round(soilSweepSeconds), exportToAdjacent, harvestParticles);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Event[action=%s radius=%s interval=%s watering=%s collectAnyItem=%s"
                            + " collectMargin=%s soilSweepSeconds=%s exportToAdjacent=%s harvestParticles=%s]",
                    action, radius, interval, watering, collectAnyItem, collectMargin, soilSweepSeconds,
                    exportToAdjacent, harvestParticles);
        }
    }
}
