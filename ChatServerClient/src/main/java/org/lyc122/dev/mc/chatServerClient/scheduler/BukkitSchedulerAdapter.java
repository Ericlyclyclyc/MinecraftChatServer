package org.lyc122.dev.mc.chatServerClient.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Bukkit/Paper 调度器适配器实现
 */
public class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        tasks.add(plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public void runAsyncDelayed(Runnable task, long delay, TimeUnit unit) {
        long ticks = toTicks(delay, unit);
        tasks.add(plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, ticks));
    }

    @Override
    public void runAsyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
        long delayTicks = toTicks(delay, unit);
        long periodTicks = toTicks(period, unit);
        tasks.add(plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public void runGlobal(Runnable task) {
        tasks.add(plugin.getServer().getScheduler().runTask(plugin, task));
    }

    @Override
    public void runGlobalDelayed(Runnable task, long delay, TimeUnit unit) {
        long ticks = toTicks(delay, unit);
        tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks));
    }

    @Override
    public void runAtEntity(Entity entity, Runnable task) {
        // Bukkit 在主线程执行
        tasks.add(plugin.getServer().getScheduler().runTask(plugin, task));
    }

    @Override
    public void runAtEntityDelayed(Entity entity, Runnable task, long delay, TimeUnit unit) {
        long ticks = toTicks(delay, unit);
        tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks));
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        // Bukkit 在主线程执行
        tasks.add(plugin.getServer().getScheduler().runTask(plugin, task));
    }

    @Override
    public void cancelAllTasks() {
        for (BukkitTask task : tasks) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean isPrimaryThread() {
        return plugin.getServer().isPrimaryThread();
    }

    private long toTicks(long time, TimeUnit unit) {
        return unit.toMillis(time) / 50; // 1 tick = 50ms
    }
}
