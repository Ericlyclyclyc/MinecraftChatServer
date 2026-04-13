package org.lyc122.dev.mc.chatServerClient.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Folia 调度器适配器实现
 * 支持 Folia 及其下游核心（如 PurpurFolia、Petal 等）
 */
public class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        AsyncScheduler scheduler = plugin.getServer().getAsyncScheduler();
        tasks.add(scheduler.runNow(plugin, t -> task.run()));
    }

    @Override
    public void runAsyncDelayed(Runnable task, long delay, TimeUnit unit) {
        AsyncScheduler scheduler = plugin.getServer().getAsyncScheduler();
        tasks.add(scheduler.runDelayed(plugin, t -> task.run(), delay, unit));
    }

    @Override
    public void runAsyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
        AsyncScheduler scheduler = plugin.getServer().getAsyncScheduler();
        tasks.add(scheduler.runAtFixedRate(plugin, t -> task.run(), delay, period, unit));
    }

    @Override
    public void runGlobal(Runnable task) {
        GlobalRegionScheduler scheduler = plugin.getServer().getGlobalRegionScheduler();
        tasks.add(scheduler.run(plugin, t -> task.run()));
    }

    @Override
    public void runGlobalDelayed(Runnable task, long delay, TimeUnit unit) {
        GlobalRegionScheduler scheduler = plugin.getServer().getGlobalRegionScheduler();
        long ticks = unit.toMillis(delay) / 50;
        tasks.add(scheduler.runDelayed(plugin, t -> task.run(), ticks));
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        tasks.add(entity.getScheduler().run(plugin, t -> task.run(), null));
    }

    @Override
    public void runAtEntityDelayed(Entity entity, Runnable task, long delay, TimeUnit unit) {
        long ticks = unit.toMillis(delay) / 50;
        tasks.add(entity.getScheduler().runDelayed(plugin, t -> task.run(), null, ticks));
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        RegionScheduler scheduler = plugin.getServer().getRegionScheduler();
        tasks.add(scheduler.run(plugin, location, t -> task.run()));
    }

    @Override
    public void cancelAllTasks() {
        for (ScheduledTask task : tasks) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
        // 取消所有类型的任务
        plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
        plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean isPrimaryThread() {
        // Folia 使用 Bukkit.isPrimaryThread() 或检查当前是否在区域线程
        return plugin.getServer().isPrimaryThread();
    }
}
