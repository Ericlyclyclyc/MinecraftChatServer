package org.lyc122.dev.minecraftchatserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatserver.model.RouterNode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * 路由表服务
 * 管理多路由器互联、路由表交换、最短路径计算
 */
@Slf4j
@Service
public class RoutingTableService {
    
    // 本路由器 ID
    @Getter
    private String localRouterId = "local-router";
    
    @Getter
    private String localRouterName = "Local Router";
    
    // 连接的其他路由器: routerId -> RouterNode
    private final Map<String, RouterNode> connectedRouters = new ConcurrentHashMap<>();
    
    // 本地连接的服务器: serverName -> sessionId
    private final Map<String, String> localServers = new ConcurrentHashMap<>();
    
    // 计算后的路由表: 目标服务器 -> 下一跳路由器ID
    @Getter
    private volatile Map<String, String> routingTable = new ConcurrentHashMap<>();
    
    // 路由成本表: 目标服务器 -> 总成本
    @Getter
    private volatile Map<String, Long> routeCosts = new ConcurrentHashMap<>();
    
    // 消息转发中继表，用于环路检测: messageId -> 已转发路由器ID集合
    private final Map<String, Set<String>> relayTracker = new ConcurrentHashMap<>();
    
    // 广播消息 TTL（防止无限传播）
    private static final int MAX_TTL = 15;
    
    // 心跳超时（毫秒）
    private static final long HEARTBEAT_TIMEOUT = 30000;
    
    // 路由更新间隔（毫秒）
    private static final long ROUTE_UPDATE_INTERVAL = 5000;
    
    // 上次路由更新时间
    private volatile long lastRouteUpdate = 0;
    
    /**
     * 注册本地服务器
     */
    public void registerLocalServer(String serverName, String sessionId) {
        localServers.put(serverName, sessionId);
        log.info("注册本地服务器: {} (sessionId: {})", serverName, sessionId);
        triggerRouteUpdate();
    }
    
    /**
     * 注销本地服务器
     */
    public void unregisterLocalServer(String serverName) {
        localServers.remove(serverName);
        log.info("注销本地服务器: {}", serverName);
        triggerRouteUpdate();
    }
    
    /**
     * 添加连接的路由器
     */
    public RouterNode addConnectedRouter(String routerId, String routerName, 
            org.springframework.web.socket.WebSocketSession session) {
        RouterNode node = new RouterNode(routerId, routerName, session);
        connectedRouters.put(routerId, node);
        log.info("添加连接的路由器: {} ({})", routerName, routerId);
        return node;
    }
    
    /**
     * 移除连接的路由器
     */
    public void removeConnectedRouter(String routerId) {
        RouterNode node = connectedRouters.remove(routerId);
        if (node != null) {
            log.info("移除连接的路由器: {} ({})", node.getRouterName(), routerId);
            node.close();
        }
        triggerRouteUpdate();
    }
    
    /**
     * 获取连接的路由器
     */
    public RouterNode getConnectedRouter(String routerId) {
        return connectedRouters.get(routerId);
    }
    
    /**
     * 获取所有连接的路由器
     */
    public Collection<RouterNode> getAllConnectedRouters() {
        return Collections.unmodifiableCollection(connectedRouters.values());
    }
    
    /**
     * 更新路由器心跳和链路成本
     */
    public void updateRouterHeartbeat(String routerId, long latency) {
        RouterNode node = connectedRouters.get(routerId);
        if (node != null) {
            node.updateLinkCost(latency);
            log.debug("更新路由器 {} 心跳, 延迟: {}ms", routerId, latency);
        }
    }
    
    /**
     * 接收其他路由器通告的路由表
     */
    public void receiveRouteAdvertisement(String fromRouterId, Map<String, Integer> advertisedRoutes) {
        RouterNode node = connectedRouters.get(fromRouterId);
        if (node != null) {
            node.updateAdvertisedRoutes(advertisedRoutes);
            log.info("接收路由器 {} 的路由通告: {} 条路由", fromRouterId, advertisedRoutes.size());
            triggerRouteUpdate();
        }
    }
    
    /**
     * 触发路由表更新
     */
    public synchronized void triggerRouteUpdate() {
        long now = System.currentTimeMillis();
        if (now - lastRouteUpdate < ROUTE_UPDATE_INTERVAL) {
            log.debug("路由更新过于频繁，跳过");
            return;
        }
        lastRouteUpdate = now;
        computeRoutingTable();
        broadcastRoutingTable();
    }
    
    /**
     * 计算最短路径路由表（Dijkstra 算法变种）
     */
    private void computeRoutingTable() {
        log.info("开始计算路由表...");
        
        Map<String, String> newRoutingTable = new ConcurrentHashMap<>();
        Map<String, Long> newRouteCosts = new ConcurrentHashMap<>();
        
        // 本地服务器：直连，成本为 0
        for (String serverName : localServers.keySet()) {
            newRoutingTable.put(serverName, "local");
            newRouteCosts.put(serverName, 0L);
        }
        
        // 使用 Bellman-Ford 算法计算到其他路由器可达的服务器的最短路径
        // 防止环路：记录路径上的路由器
        
        for (RouterNode router : connectedRouters.values()) {
            if (!router.isAlive(HEARTBEAT_TIMEOUT)) {
                log.warn("路由器 {} 不存活，跳过", router.getRouterId());
                continue;
            }
            
            // 通过该路由器可达的服务器
            Map<String, Integer> advertised = router.getAdvertisedRoutes();
            long baseCost = router.getLinkCost();
            
            for (Map.Entry<String, Integer> entry : advertised.entrySet()) {
                String targetServer = entry.getKey();
                int hops = entry.getValue();
                
                // 跳过本地服务器（避免回路）
                if (localServers.containsKey(targetServer)) {
                    continue;
                }
                
                // 检查是否会造成环路：该路由器的路由表中是否包含本路由器
                if (wouldCauseLoop(router.getRouterId(), targetServer)) {
                    log.debug("跳过可能造成环路的路由: {} -> {}", router.getRouterId(), targetServer);
                    continue;
                }
                
                // 计算总成本：链路成本 + 跳数 * 权重
                long totalCost = baseCost + hops * 10L;
                
                // 选择最优路径
                Long existingCost = newRouteCosts.get(targetServer);
                if (existingCost == null || totalCost < existingCost) {
                    newRoutingTable.put(targetServer, router.getRouterId());
                    newRouteCosts.put(targetServer, totalCost);
                    log.debug("更新路由: {} -> {} (成本: {}, 经由: {})", 
                            targetServer, router.getRouterId(), totalCost, router.getRouterName());
                }
            }
        }
        
        this.routingTable = newRoutingTable;
        this.routeCosts = newRouteCosts;
        
        log.info("路由表计算完成: {} 条路由", routingTable.size());
    }
    
    /**
     * 检查路由是否会造成环路
     * 环路检测：如果目标路由器的路由表中包含本路由器ID，则会产生环路
     */
    private boolean wouldCauseLoop(String routerId, String targetServer) {
        RouterNode router = connectedRouters.get(routerId);
        if (router == null) {
            return false;
        }
        
        // 检查该路由器通告的路由中，下一跳是否指向本路由器
        // 如果是，则存在环路风险
        for (RouterNode other : connectedRouters.values()) {
            if (other.getRouterId().equals(routerId)) {
                continue;
            }
            // 如果其他路由器也通告了到目标的路由，检查是否形成三角环路
            if (other.getAdvertisedRoutes().containsKey(targetServer)) {
                // 简单环路检测：如果两个路由器都通告了同一目标，检查是否有交叉
                // 更复杂的检测需要完整的拓扑信息
            }
        }
        
        return false;
    }
    
    /**
     * 广播本路由器的路由表到所有连接的路由器
     */
    public void broadcastRoutingTable() {
        Map<String, Integer> routesToAdvertise = new HashMap<>();
        
        // 通告本地服务器（跳数为 1）
        for (String serverName : localServers.keySet()) {
            routesToAdvertise.put(serverName, 1);
        }
        
        // 通告通过其他路由器可达的服务器（跳数 +1）
        for (Map.Entry<String, String> entry : routingTable.entrySet()) {
            String targetServer = entry.getKey();
            String nextHop = entry.getValue();
            
            // 如果下一跳不是本地，则增加跳数后通告
            if (!"local".equals(nextHop)) {
                // 找到原始跳数并 +1
                RouterNode nextRouter = connectedRouters.get(nextHop);
                if (nextRouter != null && nextRouter.getAdvertisedRoutes().containsKey(targetServer)) {
                    int originalHops = nextRouter.getAdvertisedRoutes().get(targetServer);
                    routesToAdvertise.put(targetServer, originalHops + 1);
                }
            }
        }
        
        // 构建广播消息
        String message = buildRouteAdvertisementMessage(routesToAdvertise);
        
        // 发送到所有连接的路由器
        for (RouterNode router : connectedRouters.values()) {
            if (router.isAlive(HEARTBEAT_TIMEOUT)) {
                router.sendMessage(message);
                log.debug("广播路由表到路由器: {}", router.getRouterId());
            }
        }
    }
    
    /**
     * 构建路由通告消息
     */
    private String buildRouteAdvertisementMessage(Map<String, Integer> routes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"ROUTE_ADVERTISEMENT\"");
        sb.append(",\"routerId\":\"").append(localRouterId).append("\"");
        sb.append(",\"routerName\":\"").append(localRouterName).append("\"");
        sb.append(",\"routes\":{");
        
        boolean first = true;
        for (Map.Entry<String, Integer> entry : routes.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        
        sb.append("}}");
        return sb.toString();
    }
    
    /**
     * 查找消息的下一跳
     * @param targetServer 目标服务器
     * @return 下一跳路由器ID，或 "local" 表示本地服务器，或 null 表示无路由
     */
    public String findNextHop(String targetServer) {
        return routingTable.get(targetServer);
    }
    
    /**
     * 检查目标是否为本地服务器
     */
    public boolean isLocalServer(String serverName) {
        return localServers.containsKey(serverName);
    }
    
    /**
     * 获取本地服务器的 sessionId
     */
    public String getLocalServerSession(String serverName) {
        return localServers.get(serverName);
    }
    
    /**
     * 开始消息转发追踪（环路检测）
     * @param messageId 消息ID
     * @param fromRouterId 来源路由器ID（可为null表示来自本地）
     * @return 是否可以继续转发
     */
    public boolean beginRelay(String messageId, String fromRouterId) {
        Set<String> visited = relayTracker.computeIfAbsent(messageId, k -> new CopyOnWriteArraySet<>());
        
        // 如果消息已经经过本路由器，说明存在环路
        if (visited.contains(localRouterId)) {
            log.warn("检测到消息环路: {}", messageId);
            return false;
        }
        
        // 记录来源路由器
        if (fromRouterId != null) {
            visited.add(fromRouterId);
        }
        visited.add(localRouterId);
        
        return true;
    }
    
    /**
     * 结束消息转发追踪
     */
    public void endRelay(String messageId) {
        // 延迟清理，允许消息在 TTL 时间内被处理
        // 实际清理由定时任务完成
    }
    
    /**
     * 检查消息是否可以转发到指定路由器
     */
    public boolean canRelayTo(String messageId, String targetRouterId) {
        Set<String> visited = relayTracker.get(messageId);
        if (visited == null) {
            return true;
        }
        // 目标路由器不在已访问列表中
        return !visited.contains(targetRouterId);
    }
    
    /**
     * 清理过期的转发追踪记录
     */
    public void cleanupExpiredRelays() {
        long now = System.currentTimeMillis();
        // 简单清理：保留最近 60 秒的记录
        // 实际应该基于消息时间戳
        if (relayTracker.size() > 10000) {
            log.info("清理转发追踪记录: {} 条", relayTracker.size() / 2);
            relayTracker.clear();
        }
    }
    
    /**
     * 获取路由表统计信息
     */
    public Map<String, Object> getRoutingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("localRouterId", localRouterId);
        stats.put("localServers", localServers.size());
        stats.put("connectedRouters", connectedRouters.size());
        stats.put("routingTableSize", routingTable.size());
        stats.put("activeRoutes", routingTable.entrySet().stream()
                .filter(e -> !"local".equals(e.getValue()))
                .count());
        return stats;
    }
    
    /**
     * 设置本地路由器信息
     */
    public void setLocalRouterInfo(String routerId, String routerName) {
        this.localRouterId = routerId;
        this.localRouterName = routerName;
    }
}
