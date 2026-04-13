package org.lyc122.dev.mc.chatServerClient.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * 调度器工厂
 * 自动检测并创建适合当前服务器的调度器适配器
 */
public class SchedulerFactory {

    private static final String FOLIA_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    /**
     * 创建调度器适配器
     * 自动检测 Folia 并返回相应的实现
     */
    public static SchedulerAdapter create(Plugin plugin) {
        if (isFolia()) {
            plugin.getLogger().info("检测到 Folia 核心，使用 Folia 调度器适配器");
            return new FoliaSchedulerAdapter(plugin);
        } else {
            plugin.getLogger().info("使用 Bukkit 调度器适配器");
            return new BukkitSchedulerAdapter(plugin);
        }
    }

    /**
     * 检查当前服务器是否是 Folia 或其下游核心
     */
    public static boolean isFolia() {
        try {
            Class.forName(FOLIA_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取服务器类型名称
     */
    public static String getServerType() {
        if (isFolia()) {
            return "Folia";
        }
        return "Bukkit/Paper";
    }
}
