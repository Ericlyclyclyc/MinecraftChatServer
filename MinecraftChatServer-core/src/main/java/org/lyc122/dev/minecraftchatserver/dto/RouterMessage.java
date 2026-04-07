package org.lyc122.dev.minecraftchatserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * 路由器间通信消息
 * 用于路由表交换、心跳、消息转发等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouterMessage {
    
    /**
     * 消息类型
     */
    private String type;
    
    /**
     * 源路由器ID
     */
    private String sourceRouterId;
    
    /**
     * 源路由器名称
     */
    private String sourceRouterName;
    
    /**
     * 目标路由器ID（点对点消息）
     */
    private String targetRouterId;
    
    /**
     * 消息ID（用于追踪和去重）
     */
    private String messageId;
    
    /**
     * TTL（生存时间，防止无限传播）
     */
    private Integer ttl;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    // ========== 路由表交换相关 ==========
    
    /**
     * 通告的路由表: 目标服务器 -> 跳数
     */
    private Map<String, Integer> routes;
    
    /**
     * 链路延迟（毫秒）
     */
    private Long latency;
    
    // ========== 消息转发相关 ==========
    
    /**
     * 原始聊天消息（转发时）
     */
    private ChatMessage chatMessage;
    
    /**
     * 源服务器名称
     */
    private String sourceServer;
    
    /**
     * 目标服务器名称
     */
    private String targetServer;
    
    /**
     * 已访问的路由器ID列表（环路检测）
     */
    private Set<String> visitedRouters;
    
    /**
     * 广播范围（用于区域广播）
     */
    private Double broadcastRadius;
    
    /**
     * 广播中心位置
     */
    private Position centerPosition;
    
    // ========== 心跳相关 ==========
    
    /**
     * 心跳序列号
     */
    private Long heartbeatSeq;
    
    /**
     * 心跳响应时间
     */
    private Long heartbeatResponseTime;
    
    /**
     * 创建心跳消息
     */
    public static RouterMessage heartbeat(String routerId, String routerName, long seq) {
        return RouterMessage.builder()
                .type("HEARTBEAT")
                .sourceRouterId(routerId)
                .sourceRouterName(routerName)
                .heartbeatSeq(seq)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建路由通告消息
     */
    public static RouterMessage routeAdvertisement(String routerId, String routerName, 
            Map<String, Integer> routes) {
        return RouterMessage.builder()
                .type("ROUTE_ADVERTISEMENT")
                .sourceRouterId(routerId)
                .sourceRouterName(routerName)
                .routes(routes)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建消息转发请求
     */
    public static RouterMessage forwardMessage(String routerId, String routerName,
            ChatMessage chatMessage, String sourceServer, String targetServer,
            Set<String> visitedRouters, int ttl) {
        return RouterMessage.builder()
                .type("FORWARD_MESSAGE")
                .sourceRouterId(routerId)
                .sourceRouterName(routerName)
                .messageId(chatMessage.getMessageId())
                .chatMessage(chatMessage)
                .sourceServer(sourceServer)
                .targetServer(targetServer)
                .visitedRouters(visitedRouters)
                .ttl(ttl)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建广播消息
     */
    public static RouterMessage broadcastMessage(String routerId, String routerName,
            ChatMessage chatMessage, String sourceServer,
            Set<String> visitedRouters, int ttl, Double radius, Position center) {
        return RouterMessage.builder()
                .type("BROADCAST_MESSAGE")
                .sourceRouterId(routerId)
                .sourceRouterName(routerName)
                .messageId(chatMessage.getMessageId())
                .chatMessage(chatMessage)
                .sourceServer(sourceServer)
                .visitedRouters(visitedRouters)
                .ttl(ttl)
                .broadcastRadius(radius)
                .centerPosition(center)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建路由器连接请求
     */
    public static RouterMessage connectRequest(String routerId, String routerName) {
        return RouterMessage.builder()
                .type("CONNECT_REQUEST")
                .sourceRouterId(routerId)
                .sourceRouterName(routerName)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建路由器连接响应
     */
    public static RouterMessage connectResponse(String routerId, String routerName, 
            boolean accepted, String reason) {
        return RouterMessage.builder()
                .type("CONNECT_RESPONSE")
                .sourceRouterId(routerId)
                .sourceRouterName(routerName)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 减少 TTL
     * @return 如果 TTL > 0 返回 true，否则返回 false（消息应被丢弃）
     */
    public boolean decrementTtl() {
        if (ttl == null || ttl <= 0) {
            return false;
        }
        ttl--;
        return ttl > 0;
    }
    
    /**
     * 检查是否已访问过指定路由器
     */
    public boolean hasVisited(String routerId) {
        return visitedRouters != null && visitedRouters.contains(routerId);
    }
    
    /**
     * 添加已访问路由器
     */
    public void addVisitedRouter(String routerId) {
        if (visitedRouters == null) {
            visitedRouters = new java.util.HashSet<>();
        }
        visitedRouters.add(routerId);
    }
}
