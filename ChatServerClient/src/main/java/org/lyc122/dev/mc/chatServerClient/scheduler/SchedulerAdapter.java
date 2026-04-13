package org.lyc122.dev.mc.chatServerClient.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;

/**
 * 调度器适配器接口
 * 支持 Bukkit/Paper 和 Folia 及其下游核心
 */
public interface SchedulerAdapter {

    /**
     * 在全局区域执行异步任务
     */
    void runAsync(Runnable task);

    /**
     * 延迟执行异步任务
     */
    void runAsyncDelayed(Runnable task, long delay, TimeUnit unit);

    /**
     * 定时执行异步任务
     */
    void runAsyncTimer(Runnable task, long delay, long period, TimeUnit unit);

    /**
     * 在主线程执行同步任务（Bukkit）或在全局区域执行（Folia）
     */
    void runGlobal(Runnable task);

    /**
     * 延迟执行全局任务
     */
    void runGlobalDelayed(Runnable task, long delay, TimeUnit unit);

    /**
     * 在指定实体的调度器上执行任务（Folia）或在主线程执行（Bukkit）
     */
    void runAtEntity(Entity entity, Runnable task);

    /**
     * 延迟在指定实体上执行任务
     */
    void runAtEntityDelayed(Entity entity, Runnable task, long delay, TimeUnit unit);

    /**
     * 在指定位置的区域执行同步任务（Folia）或在主线程执行（Bukkit）
     */
    void runAtLocation(Location location, Runnable task);

    /**
     * 取消所有任务
     */
    void cancelAllTasks();

    /**
     * 检查当前线程是否是主线程（Bukkit）或区域线程（Folia）
     */
    boolean isPrimaryThread();
}
