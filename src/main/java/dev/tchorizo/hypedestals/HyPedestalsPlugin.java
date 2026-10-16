package dev.tchorizo.hypedestals;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.Locale;

public final class HyPedestalsPlugin extends JavaPlugin {
    private static final String PREFIX = "[HyPedestals] ";

    // The server instantiates the plugin exactly once; the static log helpers route through its logger.
    private static volatile HyPedestalsPlugin instance;

    public HyPedestalsPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        log("setup");
    }

    @Override
    protected void start() {
        log("started");
    }

    static void log(String fmt, Object... args) {
        instance.getLogger().atInfo().log("%s", PREFIX + format(fmt, args));
    }

    static void warn(String fmt, Object... args) {
        instance.getLogger().atWarning().log("%s", PREFIX + format(fmt, args));
    }

    private static String format(String fmt, Object... args) {
        return args.length == 0 ? fmt : String.format(Locale.ROOT, fmt, args);
    }
}
