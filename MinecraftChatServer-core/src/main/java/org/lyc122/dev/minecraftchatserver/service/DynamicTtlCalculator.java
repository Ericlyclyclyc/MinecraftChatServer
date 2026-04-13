package org.lyc122.dev.minecraftchatserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态 TTL 计算器
 * 
 * 根据网络拓扑自动计算合适的 TTL 值，无需手动配置。
 * 核心策略：
 * 1. 基于网络直径（最长最短路径）计算基础 TTL
 * 2. 考虑网络变化的历史趋势进行预测
 * 3. 设置安全边界防止消息丢失
 * 4. 支持自适应调整以应对网络拓扑变化
 */
@Slf4j
@Service
public class DynamicTtlCalculator {

    // 最小 TTL 值（保证至少能经过几跳）
    private static final int MIN_TTL = 5;
    
    // 最大 TTL 值（防止无限传播）
    private static final int MAX_TTL = 32;
    
    // 默认 TTL（初始值）
    private static final int DEFAULT_TTL = 15;
    
    // 安全边界系数：网络直径 * 系数 = 最终 TTL
    private static final double SAFETY_MARGIN = 1.5;
    
    // 历史网络直径记录（用于趋势分析）
    private final List<Integer> diameterHistory = new ArrayList<>();
    
    // 最大历史记录数
    private static final int MAX_HISTORY_SIZE = 10;
    
    // 当前计算出的 TTL 值
    private volatile int currentTtl = DEFAULT_TTL;
    
    // 网络变化计数器（用于检测网络不稳定性）
    private final AtomicInteger changeCounter = new AtomicInteger(0);
    
    // 上次网络拓扑哈希（用于检测变化）
    private volatile int lastTopologyHash = 0;
    
    // 路由器跳数缓存：目标服务器 -> 跳数
    private final Map<String, Integer> hopCountCache = new ConcurrentHashMap<>();
    
    /**
     * 计算动态 TTL
     * 
     * @param routingTable 当前路由表（目标服务器 -> 下一跳路由器ID）
     * @param routeCosts 路由成本表（目标服务器 -> 总成本）
     * @param connectedRouters 连接的路由器数量
     * @param localServerCount 本地服务器数量
     * @return 计算后的 TTL 值
     */
    public int calculateDynamicTtl(
            Map<String, String> routingTable,
            Map<String, Long> routeCosts,
            int connectedRouters,
            int localServerCount) {
        
        // 1. 计算当前网络直径（最长最短路径）
        int networkDiameter = calculateNetworkDiameter(routingTable, routeCosts, connectedRouters);
        
        // 2. 更新历史记录
        updateHistory(networkDiameter);
        
        // 3. 检测网络拓扑变化
        int currentTopologyHash = calculateTopologyHash(routingTable, connectedRouters);
        if (currentTopologyHash != lastTopologyHash) {
            changeCounter.incrementAndGet();
            lastTopologyHash = currentTopologyHash;
            log.debug("检测到网络拓扑变化，变化计数: {}", changeCounter.get());
        }
        
        // 4. 基于网络趋势预测
        int predictedDiameter = predictFutureDiameter();
        
        // 5. 计算最终 TTL（取当前直径和预测直径的最大值）
        int baseTtl = Math.max(networkDiameter, predictedDiameter);
        
        // 6. 应用安全边界
        int ttlWithMargin = (int) Math.ceil(baseTtl * SAFETY_MARGIN);
        
        // 7. 边界限制
        int finalTtl = Math.max(MIN_TTL, Math.min(MAX_TTL, ttlWithMargin));
        
        // 8. 如果网络不稳定，增加额外缓冲
        if (isNetworkUnstable()) {
            finalTtl = Math.min(MAX_TTL, finalTtl + 3);
            log.debug("网络不稳定，增加 TTL 缓冲至 {}", finalTtl);
        }
        
        // 9. 平滑过渡（避免 TTL 剧烈变化）
        currentTtl = smoothTransition(currentTtl, finalTtl);
        
        log.debug("动态 TTL 计算: 网络直径={}, 预测直径={}, 最终 TTL={}", 
                networkDiameter, predictedDiameter, currentTtl);
        
        return currentTtl;
    }
    
    /**
     * 计算网络直径
     * 网络直径 = 网络中最长的最短路径
     */
    private int calculateNetworkDiameter(
            Map<String, String> routingTable,
            Map<String, Long> routeCosts,
            int connectedRouters) {
        
        if (routingTable.isEmpty() && connectedRouters == 0) {
            // 单机模式，只有本地服务器
            return 1;
        }
        
        int maxHops = 0;
        
        // 从路由成本估算跳数（成本 / 每跳基准成本）
        for (Map.Entry<String, Long> entry : routeCosts.entrySet()) {
            Long cost = entry.getValue();
            if (cost != null && cost > 0) {
                // 假设每跳基准成本为 10（与 RoutingTableService 中的计算一致）
                int estimatedHops = (int) Math.ceil(cost / 10.0);
                maxHops = Math.max(maxHops, estimatedHops);
            }
        }
        
        // 如果没有远程路由，基于连接的路由器数量估算
        if (maxHops == 0 && connectedRouters > 0) {
            // 保守估计：每个路由器至少增加一跳
            maxHops = connectedRouters;
        }
        
        // 至少保证能覆盖直连路由器
        maxHops = Math.max(maxHops, connectedRouters > 0 ? 2 : 1);
        
        return maxHops;
    }
    
    /**
     * 更新历史记录
     */
    private synchronized void updateHistory(int diameter) {
        diameterHistory.add(diameter);
        if (diameterHistory.size() > MAX_HISTORY_SIZE) {
            diameterHistory.remove(0);
        }
    }
    
    /**
     * 预测未来的网络直径
     * 基于历史趋势进行简单线性预测
     */
    private int predictFutureDiameter() {
        if (diameterHistory.size() < 2) {
            return diameterHistory.isEmpty() ? DEFAULT_TTL : diameterHistory.get(diameterHistory.size() - 1);
        }
        
        // 取历史最大值作为预测（保守策略）
        int maxHistorical = diameterHistory.stream().mapToInt(Integer::intValue).max().orElse(0);
        
        // 如果最近有增长趋势，增加预测
        int last = diameterHistory.get(diameterHistory.size() - 1);
        int secondLast = diameterHistory.size() > 1 ? diameterHistory.get(diameterHistory.size() - 2) : last;
        
        if (last > secondLast) {
            // 增长趋势，增加 1 跳缓冲
            return Math.min(maxHistorical + 1, MAX_TTL);
        }
        
        return maxHistorical;
    }
    
    /**
     * 计算网络拓扑哈希（用于检测变化）
     */
    private int calculateTopologyHash(Map<String, String> routingTable, int connectedRouters) {
        return Objects.hash(routingTable.hashCode(), connectedRouters);
    }
    
    /**
     * 检查网络是否不稳定
     * 基于变化频率判断
     */
    private boolean isNetworkUnstable() {
        // 如果变化过于频繁，认为网络不稳定
        return changeCounter.get() > 3;
    }
    
    /**
     * 平滑过渡 TTL 值
     * 避免 TTL 剧烈波动
     */
    private int smoothTransition(int current, int target) {
        int diff = target - current;
        
        // 如果变化小于 2，直接应用
        if (Math.abs(diff) <= 2) {
            return target;
        }
        
        // 否则，逐步调整（每次最多变化 2）
        if (diff > 0) {
            return current + Math.min(diff, 2);
        } else {
            return current + Math.max(diff, -2);
        }
    }
    
    /**
     * 获取当前 TTL 值
     */
    public int getCurrentTtl() {
        return currentTtl;
    }
    
    /**
     * 为特定目标计算 TTL
     * 根据目标距离动态调整
     */
    public int calculateTtlForTarget(String targetServer, 
            Map<String, String> routingTable,
            Map<String, Long> routeCosts) {
        
        // 本地服务器，TTL=1 足够
        if ("local".equals(routingTable.get(targetServer))) {
            return 2; // 留一点余量
        }
        
        // 获取到目标的成本
        Long cost = routeCosts.get(targetServer);
        if (cost == null) {
            // 未知目标，使用全局 TTL
            return currentTtl;
        }
        
        // 根据成本估算跳数
        int estimatedHops = (int) Math.ceil(cost / 10.0);
        
        // 添加安全边界
        int ttl = (int) Math.ceil(estimatedHops * 1.3);
        
        return Math.max(MIN_TTL, Math.min(MAX_TTL, ttl));
    }
    
    /**
     * 重置状态（用于测试或网络重大变化时）
     */
    public synchronized void reset() {
        diameterHistory.clear();
        changeCounter.set(0);
        lastTopologyHash = 0;
        currentTtl = DEFAULT_TTL;
        hopCountCache.clear();
        log.info("动态 TTL 计算器已重置");
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentTtl", currentTtl);
        stats.put("historySize", diameterHistory.size());
        stats.put("changeCounter", changeCounter.get());
        stats.put("isUnstable", isNetworkUnstable());
        if (!diameterHistory.isEmpty()) {
            stats.put("diameterHistory", new ArrayList<>(diameterHistory));
            stats.put("maxHistoricalDiameter", diameterHistory.stream().mapToInt(Integer::intValue).max().orElse(0));
        }
        return stats;
    }
}
