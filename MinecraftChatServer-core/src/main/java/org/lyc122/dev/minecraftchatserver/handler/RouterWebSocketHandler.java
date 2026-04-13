package org.lyc122.dev.minecraftchatserver.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatserver.dto.ChatMessage;
import org.lyc122.dev.minecraftchatserver.dto.RouterMessage;
import org.lyc122.dev.minecraftchatserver.model.RouterNode;
import org.lyc122.dev.minecraftchatserver.service.RoutingTableService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 路由器间 WebSocket 处理器
 * 处理多个路由器之间的连接、路由表交换和消息转发
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouterWebSocketHandler extends TextWebSocketHandler {
    
    private final ObjectMapper objectMapper;
    private final RoutingTableService routingTableService;
    
    // 会话到路由器ID的映射
    private final Map<String, String> sessionToRouterId = new ConcurrentHashMap<>();
    
    // 心跳管理
    private final Map<String, Long> heartbeatSeqMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
    
    // 本路由器配置
    private String localRouterId;
    private String localRouterName;
    
    // 最大 TTL（备用值，实际使用动态 TTL）
    private static final int MAX_TTL = 32;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("路由器连接建立: {} (sessionId: {})", 
                session.getRemoteAddress(), session.getId());
        
        // 等待对方发送 CONNECT_REQUEST 进行身份验证
        // 不立即注册，防止未授权连接
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        try {
            RouterMessage routerMsg = objectMapper.readValue(payload, RouterMessage.class);
            String msgType = routerMsg.getType();
            
            log.debug("收到路由器消息: type={}, from={}", msgType, routerMsg.getSourceRouterId());
            
            switch (msgType) {
                case "CONNECT_REQUEST" -> handleConnectRequest(session, routerMsg);
                case "CONNECT_RESPONSE" -> handleConnectResponse(session, routerMsg);
                case "HEARTBEAT" -> handleHeartbeat(session, routerMsg);
                case "HEARTBEAT_RESPONSE" -> handleHeartbeatResponse(session, routerMsg);
                case "ROUTE_ADVERTISEMENT" -> handleRouteAdvertisement(session, routerMsg);
                case "FORWARD_MESSAGE" -> handleForwardMessage(session, routerMsg);
                case "BROADCAST_MESSAGE" -> handleBroadcastMessage(session, routerMsg);
                default -> log.warn("未知的路由器消息类型: {}", msgType);
            }
            
        } catch (Exception e) {
            log.error("处理路由器消息失败: {}", payload, e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String routerId = sessionToRouterId.remove(sessionId);
        
        if (routerId != null) {
            log.info("路由器连接关闭: {} (sessionId: {}, status: {})", 
                    routerId, sessionId, status);
            routingTableService.removeConnectedRouter(routerId);
        }
    }
    
    /**
     * 处理连接请求
     */
    private void handleConnectRequest(WebSocketSession session, RouterMessage msg) {
        String routerId = msg.getSourceRouterId();
        String routerName = msg.getSourceRouterName();
        
        log.info("收到路由器连接请求: {} ({})", routerName, routerId);
        
        // 注册路由器
        RouterNode node = routingTableService.addConnectedRouter(routerId, routerName, session);
        sessionToRouterId.put(session.getId(), routerId);
        
        // 发送连接响应
        sendMessage(session, RouterMessage.connectResponse(
                localRouterId, localRouterName, true, "Connected"));
        
        // 启动心跳检测
        startHeartbeat(routerId, session);
        
        // 触发路由表更新
        routingTableService.triggerRouteUpdate();
    }
    
    /**
     * 处理连接响应
     */
    private void handleConnectResponse(WebSocketSession session, RouterMessage msg) {
        log.info("路由器连接响应: {} ({})", msg.getSourceRouterName(), msg.getSourceRouterId());
        // 连接已建立，可以开始交换路由表
    }
    
    /**
     * 处理心跳
     */
    private void handleHeartbeat(WebSocketSession session, RouterMessage msg) {
        String routerId = msg.getSourceRouterId();
        
        // 发送心跳响应
        RouterMessage response = RouterMessage.builder()
                .type("HEARTBEAT_RESPONSE")
                .sourceRouterId(localRouterId)
                .sourceRouterName(localRouterName)
                .heartbeatSeq(msg.getHeartbeatSeq())
                .heartbeatResponseTime(System.currentTimeMillis())
                .timestamp(System.currentTimeMillis())
                .build();
        
        sendMessage(session, response);
    }
    
    /**
     * 处理心跳响应
     */
    private void handleHeartbeatResponse(WebSocketSession session, RouterMessage msg) {
        String routerId = msg.getSourceRouterId();
        long sendTime = heartbeatSeqMap.getOrDefault(routerId + "_" + msg.getHeartbeatSeq(), 0L);
        
        if (sendTime > 0) {
            long latency = System.currentTimeMillis() - sendTime;
            routingTableService.updateRouterHeartbeat(routerId, latency);
            log.debug("路由器 {} 心跳延迟: {}ms", routerId, latency);
        }
    }
    
    /**
     * 处理路由表通告
     */
    private void handleRouteAdvertisement(WebSocketSession session, RouterMessage msg) {
        String routerId = msg.getSourceRouterId();
        Map<String, Integer> routes = msg.getRoutes();
        
        if (routes != null) {
            routingTableService.receiveRouteAdvertisement(routerId, routes);
        }
    }
    
    /**
     * 处理转发消息
     */
    private void handleForwardMessage(WebSocketSession session, RouterMessage msg) {
        // 如果 TTL 未设置，使用动态 TTL
        if (msg.getTtl() == null) {
            int dynamicTtl = routingTableService.getDynamicTtl();
            msg.setTtl(dynamicTtl);
            log.debug("消息未设置 TTL，使用动态 TTL: {}", dynamicTtl);
        }
        
        // TTL 检查
        if (!msg.decrementTtl()) {
            log.warn("消息 TTL 耗尽，丢弃: {}", msg.getMessageId());
            return;
        }
        
        // 环路检测
        if (msg.hasVisited(localRouterId)) {
            log.warn("检测到消息环路，丢弃: {}", msg.getMessageId());
            return;
        }
        msg.addVisitedRouter(localRouterId);
        
        String targetServer = msg.getTargetServer();
        ChatMessage chatMsg = msg.getChatMessage();
        
        if (chatMsg == null) {
            log.warn("转发消息中没有聊天内容");
            return;
        }
        
        // 检查目标是否为本地的服务器
        if (routingTableService.isLocalServer(targetServer)) {
            // 转发到本地服务器
            log.info("转发消息到本地服务器: {}", targetServer);
            forwardToLocalServer(targetServer, chatMsg);
        } else {
            // 继续转发到下一跳
            String nextHop = routingTableService.findNextHop(targetServer);
            if (nextHop != null && !"local".equals(nextHop)) {
                forwardToNextRouter(nextHop, msg);
            } else {
                log.warn("无法找到到目标服务器 {} 的路由", targetServer);
            }
        }
    }
    
    /**
     * 处理广播消息
     */
    private void handleBroadcastMessage(WebSocketSession session, RouterMessage msg) {
        // 如果 TTL 未设置，使用动态 TTL
        if (msg.getTtl() == null) {
            int dynamicTtl = routingTableService.getDynamicTtl();
            msg.setTtl(dynamicTtl);
            log.debug("广播消息未设置 TTL，使用动态 TTL: {}", dynamicTtl);
        }
        
        // TTL 检查
        if (!msg.decrementTtl()) {
            log.warn("广播消息 TTL 耗尽，丢弃: {}", msg.getMessageId());
            return;
        }
        
        // 环路检测
        if (msg.hasVisited(localRouterId)) {
            log.warn("检测到广播消息环路，丢弃: {}", msg.getMessageId());
            return;
        }
        msg.addVisitedRouter(localRouterId);
        
        ChatMessage chatMsg = msg.getChatMessage();
        if (chatMsg == null) {
            return;
        }
        
        // 广播到所有本地服务器
        Set<String> localServers = routingTableService.getRoutingTable().keySet().stream()
                .filter(s -> "local".equals(routingTableService.findNextHop(s)))
                .collect(java.util.stream.Collectors.toSet());
        
        for (String server : localServers) {
            forwardToLocalServer(server, chatMsg);
        }
        
        // 继续广播到其他路由器（泛洪，但避免环路）
        Set<String> visited = msg.getVisitedRouters();
        for (RouterNode router : routingTableService.getAllConnectedRouters()) {
            if (visited == null || !visited.contains(router.getRouterId())) {
                forwardToRouter(router, msg);
            }
        }
    }
    
    /**
     * 转发消息到本地服务器
     */
    private void forwardToLocalServer(String serverName, ChatMessage msg) {
        // 通过 ServerSessionService 转发
        // 这里需要与现有的消息路由服务集成
        log.info("转发消息到本地服务器 {}: {}", serverName, msg.getContent());
    }
    
    /**
     * 转发消息到下一跳路由器
     */
    private void forwardToNextRouter(String nextRouterId, RouterMessage msg) {
        RouterNode router = routingTableService.getConnectedRouter(nextRouterId);
        if (router != null) {
            // 确保 TTL 已设置
            ensureTtlSet(msg);
            forwardToRouter(router, msg);
        }
    }
    
    /**
     * 确保消息设置了 TTL
     */
    private void ensureTtlSet(RouterMessage msg) {
        if (msg.getTtl() == null) {
            int dynamicTtl = routingTableService.getDynamicTtl();
            msg.setTtl(dynamicTtl);
            log.debug("转发消息时设置动态 TTL: {}", dynamicTtl);
        }
    }
    
    /**
     * 转发消息到指定路由器
     */
    private void forwardToRouter(RouterNode router, RouterMessage msg) {
        try {
            // 确保 TTL 已设置
            ensureTtlSet(msg);
            String json = objectMapper.writeValueAsString(msg);
            router.sendMessage(json);
            log.debug("转发消息到路由器: {} (TTL: {})", router.getRouterId(), msg.getTtl());
        } catch (Exception e) {
            log.error("转发消息到路由器 {} 失败", router.getRouterId(), e);
        }
    }
    
    /**
     * 发送消息
     */
    private void sendMessage(WebSocketSession session, RouterMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }
    
    /**
     * 启动心跳检测
     */
    private void startHeartbeat(String routerId, WebSocketSession session) {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!session.isOpen()) {
                return;
            }
            
            long seq = System.currentTimeMillis();
            heartbeatSeqMap.put(routerId + "_" + seq, seq);
            
            RouterMessage heartbeat = RouterMessage.heartbeat(
                    localRouterId, localRouterName, seq);
            sendMessage(session, heartbeat);
            
        }, 5, 10, TimeUnit.SECONDS); // 每 10 秒发送一次心跳
    }
    
    /**
     * 设置本地路由器信息
     */
    public void setLocalRouterInfo(String routerId, String routerName) {
        this.localRouterId = routerId;
        this.localRouterName = routerName;
        this.routingTableService.setLocalRouterInfo(routerId, routerName);
    }
    
    /**
     * 连接到远程路由器
     */
    public void connectToRouter(String url) {
        // 通过 WebSocketClient 连接到其他路由器
        // 这里需要实现客户端连接逻辑
        log.info("连接到远程路由器: {}", url);
    }
}
