package org.lyc122.dev.minecraftchatserver.model;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路由器节点
 * 表示一个连接到本路由器的其他路由器
 */
@Slf4j
@Getter
public class RouterNode {
    
    private final String routerId;
    private final String routerName;
    private final WebSocketSession session;
    private final long connectedAt;
    
    @Setter
    private volatile long lastHeartbeat;
    
    // 该路由器通告的路由表: 目标服务器 -> 跳数
    private final Map<String, Integer> advertisedRoutes = new ConcurrentHashMap<>();
    
    // 该路由器连接的服务器列表
    private final Set<String> connectedServers = new CopyOnWriteArraySet<>();
    
    // 链路成本（延迟，毫秒）
    private final AtomicLong linkCost = new AtomicLong(1);
    
    public RouterNode(String routerId, String routerName, WebSocketSession session) {
        this.routerId = routerId;
        this.routerName = routerName;
        this.session = session;
        this.connectedAt = Instant.now().getEpochSecond();
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    /**
     * 更新链路成本
     */
    public void updateLinkCost(long latencyMs) {
        this.linkCost.set(Math.max(1, latencyMs));
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    /**
     * 获取链路成本
     */
    public long getLinkCost() {
        return linkCost.get();
    }
    
    /**
     * 更新通告的路由表
     */
    public void updateAdvertisedRoutes(Map<String, Integer> routes) {
        advertisedRoutes.clear();
        advertisedRoutes.putAll(routes);
    }
    
    /**
     * 添加连接的服务器
     */
    public void addConnectedServer(String serverName) {
        connectedServers.add(serverName);
    }
    
    /**
     * 移除连接的服务器
     */
    public void removeConnectedServer(String serverName) {
        connectedServers.remove(serverName);
    }
    
    /**
     * 检查是否存活
     */
    public boolean isAlive(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat < timeoutMs;
    }
    
    /**
     * 发送消息到该路由器
     */
    public void sendMessage(String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
            } catch (IOException e) {
                log.error("发送消息到路由器 {} 失败", routerId, e);
            }
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.error("关闭路由器 {} 连接失败", routerId, e);
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format("RouterNode{id='%s', name='%s', cost=%d, routes=%d}",
                routerId, routerName, linkCost.get(), advertisedRoutes.size());
    }
}
