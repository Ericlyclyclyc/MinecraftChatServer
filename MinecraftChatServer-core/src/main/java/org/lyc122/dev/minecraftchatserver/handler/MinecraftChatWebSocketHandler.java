package org.lyc122.dev.minecraftchatserver.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatserver.dto.ChatMessage;
import org.lyc122.dev.minecraftchatserver.service.MessageRoutingService;
import org.lyc122.dev.minecraftchatserver.service.ServerSessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MinecraftChatWebSocketHandler extends TextWebSocketHandler {

    private final ServerSessionService serverSessionService;
    private final MessageRoutingService messageRoutingService;
    private final ObjectMapper objectMapper;

    /**
     * 存储等待认证的会话
     */
    private final Map<String, WebSocketSession> pendingSessions = new ConcurrentHashMap<>();

    public MinecraftChatWebSocketHandler(ServerSessionService serverSessionService, 
                                          MessageRoutingService messageRoutingService) {
        this.serverSessionService = serverSessionService;
        this.messageRoutingService = messageRoutingService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("新的WebSocket连接: {}，等待认证", session.getId());
        pendingSessions.put(session.getId(), session);

        // 从URL参数中解析服务器名称
        String serverName = extractServerName(session);
        String address = extractAddress(session);

        if (serverName == null || serverName.isBlank()) {
            log.warn("连接 [{}] 未提供服务器名称，拒绝连接", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("必须提供服务器名称"));
            return;
        }

        // 注册服务器
        boolean registered = serverSessionService.registerServer(serverName, session, address);
        if (!registered) {
            log.warn("服务器 [{}] 已经存在，拒绝重复连接", serverName);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("服务器名称已存在"));
            return;
        }

        pendingSessions.remove(session.getId());

        // 发送连接成功消息
        ChatMessage connectMessage = ChatMessage.createServerConnect(serverName);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(connectMessage)));

        log.info("服务器 [{}] 认证成功，已连接到消息交换机", serverName);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String serverName = serverSessionService.getServerNameBySessionId(session.getId());
        if (serverName == null) {
            log.warn("收到未认证会话的消息，忽略");
            return;
        }

        String payload = message.getPayload();
        log.debug("收到来自 [{}] 的消息: {}", serverName, payload);

        try {
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);
            
            // 处理服务器主动断开请求
            if (chatMessage.isOperation() && chatMessage.getOpType() == ChatMessage.OpType.SERVER_DISCONNECT) {
                log.info("服务器 [{}] 请求断开连接", serverName);
                session.close(CloseStatus.NORMAL);
                return;
            }

            // 路由消息（处理所有消息和操作）
            messageRoutingService.routeMessage(serverName, chatMessage);
            
        } catch (Exception e) {
            log.error("处理消息失败: {}", payload, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String serverName = serverSessionService.getServerNameBySessionId(session.getId());
        
        // 从待认证列表中移除
        pendingSessions.remove(session.getId());
        
        // 注销服务器
        serverSessionService.unregisterServer(session);
        
        if (serverName != null) {
            log.info("服务器 [{}] 断开连接，状态: {}", serverName, status);
            
            // 广播服务器断开消息给其他服务器
            ChatMessage disconnectMessage = ChatMessage.createServerDisconnect(serverName);
            
            try {
                String jsonMessage = objectMapper.writeValueAsString(disconnectMessage);
                serverSessionService.getAllServerNames().forEach(targetServer -> {
                    serverSessionService.sendToServer(targetServer, jsonMessage);
                });
            } catch (Exception e) {
                log.error("广播服务器断开消息失败", e);
            }
        } else {
            log.info("未认证的连接断开，会话ID: {}", session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String serverName = serverSessionService.getServerNameBySessionId(session.getId());
        if (serverName != null) {
            log.error("服务器 [{}] 传输错误", serverName, exception);
        } else {
            log.error("未认证会话传输错误", exception);
        }
    }

    /**
     * 从WebSocket会话中提取服务器名称
     *
     * @param session WebSocket会话
     * @return 服务器名称
     */
    private String extractServerName(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        
        // 解析URL参数
        Map<String, String> params = parseQueryString(query);
        return params.get("serverName");
    }

    /**
     * 从WebSocket会话中提取服务器地址
     *
     * @param session WebSocket会话
     * @return 服务器地址
     */
    private String extractAddress(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return "unknown";
        }
        
        String query = uri.getQuery();
        if (query == null) {
            return "unknown";
        }
        
        Map<String, String> params = parseQueryString(query);
        return params.getOrDefault("address", uri.getHost() + ":" + uri.getPort());
    }

    /**
     * 解析URL查询字符串
     *
     * @param query 查询字符串
     * @return 参数映射
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new ConcurrentHashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }
}
