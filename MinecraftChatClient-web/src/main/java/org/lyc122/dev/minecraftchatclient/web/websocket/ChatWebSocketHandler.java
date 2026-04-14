package org.lyc122.dev.minecraftchatclient.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatclient.web.dto.ChatMessageRequest;
import org.lyc122.dev.minecraftchatserver.dto.ChatMessage;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler implements RouterWebSocketClient.MessageListener {

    private final RouterWebSocketClient routerClient;
    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                log.error("Error closing unauthenticated session", e);
            }
            return;
        }

        String username = auth.getName();
        session.getAttributes().put("username", username);
        sessions.put(session.getId(), session);
        routerClient.addListener(this);

        log.info("WebSocket client connected: {} (session: {})", username, session.getId());

        sendConnectionStatus(session, true);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            return;
        }

        try {
            ChatMessageRequest request = objectMapper.readValue(message.getPayload(), ChatMessageRequest.class);
            routerClient.sendChatMessage(username, request);
            log.debug("Message from {}: {}", username, request.getMsgType());
        } catch (Exception e) {
            log.error("Failed to handle message from {}: {}", username, e.getMessage());
            sendError(session, "消息格式错误");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = (String) session.getAttributes().get("username");
        sessions.remove(session.getId());
        
        if (sessions.isEmpty()) {
            routerClient.removeListener(this);
        }
        
        log.info("WebSocket client disconnected: {} (session: {})", username, session.getId());
    }

    @Override
    public void onMessage(ChatMessage message) {
        broadcastToAllClients(message);
    }

    private void broadcastToAllClients(ChatMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(payload);

            sessions.values().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            log.error("Error broadcasting message: {}", e.getMessage());
        }
    }

    private void sendConnectionStatus(WebSocketSession session, boolean connected) {
        try {
            Map<String, Object> status = Map.of(
                "type", "CONNECTION_STATUS",
                "connected", connected,
                "routerConnected", routerClient.isConnected()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(status)));
        } catch (IOException e) {
            log.error("Error sending connection status", e);
        }
    }

    private void sendError(WebSocketSession session, String error) {
        try {
            Map<String, Object> errorMsg = Map.of(
                "type", "ERROR",
                "message", error
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
        } catch (IOException e) {
            log.error("Error sending error message", e);
        }
    }
}
