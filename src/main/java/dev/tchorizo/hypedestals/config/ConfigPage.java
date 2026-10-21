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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings window ({@code Common/UI/Custom/HyPedestals_Config.ui}), opened by
 * {@code /hypedestals config}. The fields are filled from the current settings, which are kept as
 * the window's snapshot; Save sends every field's value in one event (the {@code @} keys are read
 * by the client when the button is pressed), validates them and applies only the fields that
 * differ from the snapshot ({@link ModSettings#withChangesFrom}), so a {@code config set} made
 * while the window was open is not reverted; then it writes the config file. Reset defaults only
 * refills the fields (Save applies them); Close closes the window.
 */
public final class ConfigPage extends InteractiveCustomUIPage<ConfigPage.Event> {

    private static final String LAYOUT = "HyPedestals_Config.ui";
    private static final String ACTION_SAVE = "Save";
    private static final String ACTION_RESET = "Reset";
    private static final String ACTION_CLOSE = "Close";
    private static final String COLLECT_ANY_ITEM_VALUE = "#CollectAnyItem #CheckBox.Value";
    private static final String EXPORT_TO_ADJACENT_VALUE = "#ExportToAdjacent #CheckBox.Value";
    private static final String HARVEST_PARTICLES_VALUE = "#HarvestParticles #CheckBox.Value";

    /**
     * Settings the fields were last filled from by {@link #build} or a Save; the baseline Save
     * compares against. Written from the world thread and from the file-write callback.
     */
    private volatile ModSettings opened;

    public ConfigPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(LAYOUT);
        opened = HyPedestalsPlugin.settings();
        fill(commands, opened);

        EventData save = EventData.of(Event.ACTION, ACTION_SAVE);
        for (Tier tier : Tier.values()) {
            save.append("@" + radiusId(tier), "#" + radiusId(tier) + ".Value");
            save.append("@" + intervalId(tier), "#" + intervalId(tier) + ".Value");
            save.append("@" + wateringId(tier), "#" + wateringId(tier) + ".Value");
        }
        save.append(Event.COLLECT_ANY_ITEM, COLLECT_ANY_ITEM_VALUE);
        save.append(Event.COLLECT_MARGIN, "#CollectMargin.Value");
        save.append(Event.POST_HARVEST_COLLECT, "#PostHarvestCollectSeconds.Value");
        save.append(Event.MAX_HARVESTS, "#MaxHarvestsPerCycle.Value");
        save.append(Event.SOIL_SWEEP, "#SoilSweepSeconds.Value");
        save.append(Event.EXPORT_TO_ADJACENT, EXPORT_TO_ADJACENT_VALUE);
        save.append(Event.HARVEST_PARTICLES, HARVEST_PARTICLES_VALUE);
        save.append(Event.UPGRADE_PARTICLES, "#UpgradeParticlesSeconds.Value");
        save.append(Event.MANURE, "#ManurePerApplication.Value");
        save.append(Event.MANURE_STOCK, "#ManureStock.Value");
        save.append(Event.SEED_STOCK, "#PlanterSeedStock.Value");
        save.append(Event.SAPLING_SPACING, "#SaplingSpacing.Value");
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
        List<String> problems = new ArrayList<>();
        ModSettings entered = event.toSettings(problems);
        ModSettings baseline = opened;
        if (entered != null && problems.isEmpty()) {
            problems.addAll(entered.problems());
        }
        if (entered == null || baseline == null || !problems.isEmpty()) {
            UICommandBuilder commands = new UICommandBuilder();
            commands.set("#Status.Text", Message.translation("hypedestals.ui.config.invalid")
                    .param("problems", problems.isEmpty() ? "missing value" : String.join(", ", problems)));
            sendUpdate(commands, false);
            return;
        }
        String who = playerRef.getUsername();
        ModSettings[] applied = new ModSettings[1];
        HyPedestalsPlugin.updateSettings(current -> applied[0] = current.withChangesFrom(baseline, entered))
                .whenComplete((ignored, error) -> {
                    // Applied in memory either way; the status tells whether the file was written.
                    opened = applied[0];
                    UICommandBuilder commands = new UICommandBuilder();
                    fill(commands, applied[0]);
                    if (error != null) {
                        HyPedestalsPlugin.warn("config: %s saved from the window but writing %s failed: %s",
                                who, HyPedestalsPlugin.configFile(), error);
                        commands.set("#Status.Text", Message.translation("hypedestals.ui.config.writeFailed")
                                .param("error", String.valueOf(error.getMessage())));
                    } else {
                        HyPedestalsPlugin.log("config: %s saved from the window: %s", who, applied[0].describe());
                        commands.set("#Status.Text", Message.translation("hypedestals.ui.config.saved"));
                    }
                    sendUpdate(commands, false);
                });
    }

    /** Number fields get doubles, as the client sends them back. */
    private static void fill(UICommandBuilder commands, ModSettings settings) {
        for (Tier tier : Tier.values()) {
            TierSettings s = settings.tier(tier);
            commands.set("#" + radiusId(tier) + ".Value", (double) s.radius());
            commands.set("#" + intervalId(tier) + ".Value", (double) s.intervalSeconds());
            commands.set("#" + wateringId(tier) + ".Value", (double) s.wateringSeconds());
        }
        commands.set(COLLECT_ANY_ITEM_VALUE, settings.collectAnyItem());
        commands.set("#CollectMargin.Value", (double) settings.collectMargin());
        commands.set("#PostHarvestCollectSeconds.Value", (double) settings.postHarvestCollectSeconds());
        commands.set("#MaxHarvestsPerCycle.Value", (double) settings.maxHarvestsPerCycle());
        commands.set("#SoilSweepSeconds.Value", (double) settings.soilSweepSeconds());
        commands.set(EXPORT_TO_ADJACENT_VALUE, settings.exportToAdjacent());
        commands.set(HARVEST_PARTICLES_VALUE, settings.harvestParticles());
        commands.set("#UpgradeParticlesSeconds.Value", (double) settings.upgradeParticlesSeconds());
        commands.set("#ManurePerApplication.Value", (double) settings.manurePerApplication());
        commands.set("#ManureStock.Value", (double) settings.manureStock());
        commands.set("#PlanterSeedStock.Value", (double) settings.planterSeedStock());
        commands.set("#SaplingSpacing.Value", (double) settings.saplingSpacing());
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
        static final String POST_HARVEST_COLLECT = "@PostHarvestCollectSeconds";
        static final String MAX_HARVESTS = "@MaxHarvestsPerCycle";
        static final String UPGRADE_PARTICLES = "@UpgradeParticlesSeconds";
        static final String MANURE = "@ManurePerApplication";
        static final String MANURE_STOCK = "@ManureStock";
        static final String SEED_STOCK = "@PlanterSeedStock";
        static final String SAPLING_SPACING = "@SaplingSpacing";

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
        Double postHarvestCollectSeconds;
        Double maxHarvestsPerCycle;
        Double upgradeParticlesSeconds;
        Double manurePerApplication;
        Double manureStock;
        Double planterSeedStock;
        Double saplingSpacing;

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
                    .add()
                    .append(new KeyedCodec<>(POST_HARVEST_COLLECT, Codec.DOUBLE),
                            (e, v) -> e.postHarvestCollectSeconds = v, e -> e.postHarvestCollectSeconds)
                    .add()
                    .append(new KeyedCodec<>(MAX_HARVESTS, Codec.DOUBLE),
                            (e, v) -> e.maxHarvestsPerCycle = v, e -> e.maxHarvestsPerCycle)
                    .add()
                    .append(new KeyedCodec<>(UPGRADE_PARTICLES, Codec.DOUBLE),
                            (e, v) -> e.upgradeParticlesSeconds = v, e -> e.upgradeParticlesSeconds)
                    .add()
                    .append(new KeyedCodec<>(MANURE, Codec.DOUBLE),
                            (e, v) -> e.manurePerApplication = v, e -> e.manurePerApplication)
                    .add()
                    .append(new KeyedCodec<>(MANURE_STOCK, Codec.DOUBLE),
                            (e, v) -> e.manureStock = v, e -> e.manureStock)
                    .add()
                    .append(new KeyedCodec<>(SEED_STOCK, Codec.DOUBLE),
                            (e, v) -> e.planterSeedStock = v, e -> e.planterSeedStock)
                    .add()
                    .append(new KeyedCodec<>(SAPLING_SPACING, Codec.DOUBLE),
                            (e, v) -> e.saplingSpacing = v, e -> e.saplingSpacing)
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
         * Entered values as settings (radius, watering, sweep, harvest cap, poop per application, poop stock, seed stock
         * and sapling spacing rounded to whole numbers,
         * interval, post-harvest window and upgrade particle period to one decimal), or {@code null} if any value is
         * missing. Non-finite numbers (NaN, infinity) are added to {@code problems} instead of
         * being rounded to 0; other range checks are left to {@link ModSettings#problems()}.
         */
        ModSettings toSettings(List<String> problems) {
            if (collectAnyItem == null || collectMargin == null || soilSweepSeconds == null
                    || exportToAdjacent == null || harvestParticles == null || postHarvestCollectSeconds == null
                    || maxHarvestsPerCycle == null || upgradeParticlesSeconds == null || manurePerApplication == null
                    || manureStock == null || planterSeedStock == null || saplingSpacing == null) {
                return null;
            }
            Map<Tier, TierSettings> tiers = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                Double r = radius.get(tier), i = interval.get(tier), w = watering.get(tier);
                if (r == null || i == null || w == null) {
                    return null;
                }
                String name = tier.displayName();
                tiers.put(tier, new TierSettings(whole(name + ".Radius", r, problems),
                        tenths(name + ".IntervalSeconds", i, problems),
                        whole(name + ".WateringSeconds", w, problems)));
            }
            return new ModSettings(tiers, collectAnyItem, whole("CollectMargin", collectMargin, problems),
                    whole("SoilSweepSeconds", soilSweepSeconds, problems), exportToAdjacent, harvestParticles,
                    tenths("PostHarvestCollectSeconds", postHarvestCollectSeconds, problems),
                    whole("MaxHarvestsPerCycle", maxHarvestsPerCycle, problems),
                    tenths("UpgradeParticlesSeconds", upgradeParticlesSeconds, problems),
                    whole("ManurePerApplication", manurePerApplication, problems),
                    whole("ManureStock", manureStock, problems),
                    whole("PlanterSeedStock", planterSeedStock, problems),
                    whole("SaplingSpacing", saplingSpacing, problems));
        }

        private static int whole(String field, double value, List<String> problems) {
            if (Double.isFinite(value)) {
                return (int) Math.round(value);
            }
            nonFinite(field, value, problems);
            return 0;
        }

        /** {@code value} rounded to one decimal, the window's precision for seconds. */
        private static float tenths(String field, double value, List<String> problems) {
            return Double.isFinite(value) ? Math.round(value * 10) / 10f : nonFinite(field, value, problems);
        }

        private static float nonFinite(String field, double value, List<String> problems) {
            problems.add(field + "=" + value);
            return 0f;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Event[action=%s radius=%s interval=%s watering=%s collectAnyItem=%s"
                            + " collectMargin=%s soilSweepSeconds=%s exportToAdjacent=%s harvestParticles=%s"
                            + " postHarvestCollectSeconds=%s maxHarvestsPerCycle=%s upgradeParticlesSeconds=%s"
                            + " manurePerApplication=%s manureStock=%s planterSeedStock=%s saplingSpacing=%s]",
                    action, radius, interval, watering, collectAnyItem, collectMargin, soilSweepSeconds,
                    exportToAdjacent, harvestParticles, postHarvestCollectSeconds, maxHarvestsPerCycle,
                    upgradeParticlesSeconds, manurePerApplication, manureStock, planterSeedStock, saplingSpacing);
        }
    }
}
