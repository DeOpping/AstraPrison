package dev.paracausal.astra;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class Astra {

    private static AstraUtility utility;

    static void onLoad(@NotNull final AstraUtility plugin) {
        Astra.utility = plugin;
    }

    public static AstraUtility get() {
        return utility;
    }

    public static JavaPlugin getPlugin() {
        return (JavaPlugin) utility;
    }

}
