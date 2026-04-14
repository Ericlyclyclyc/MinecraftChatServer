package org.lyc122.dev.minecraftchatclient.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatclient.web.dto.ChatMessageRequest;
import org.lyc122.dev.minecraftchatserver.dto.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouterWebSocketClient {

    private final ObjectMapper objectMapper;

    @Value("${router.websocket.url:ws://localhost:8080/ws/minecraft-chat}")
    private String routerUrl;

    @Value("${chat.server.name:WebClient}")
    private String serverName;

    @Getter
    private volatile boolean connected = false;

    private WebSocketSession session;
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        connect();
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }

    public void connect() {
        try {
            WebSocketClient client = new StandardWebSocketClient();
            String url = routerUrl + "?serverName=" + serverName;

            client.execute(new RouterWebSocketHandler(), url)
                    .get(5, TimeUnit.SECONDS);

            log.info("Connected to router: {}", url);
        } catch (Exception e) {
            log.error("Failed to connect to router: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.error("Error closing WebSocket session: {}", e.getMessage());
            }
        }
        connected = false;
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void sendMessage(ChatMessage message) {
        if (!connected || session == null || !session.isOpen()) {
            log.warn("Cannot send message: not connected to router");
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(payload));
            log.debug("Message sent to router: {}", message.getMsgType());
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }

    public void sendChatMessage(String sender, ChatMessageRequest request) {
        ChatMessage.SenderType senderType = ChatMessage.SenderType.PLAYER;

        ChatMessage.MsgType msgType;
        try {
            msgType = ChatMessage.MsgType.valueOf(request.getMsgType());
        } catch (IllegalArgumentException e) {
            log.error("Invalid message type: {}", request.getMsgType());
            return;
        }

        ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                .type(ChatMessage.TYPE_MESSAGE)
                .msgType(msgType)
                .senderType(senderType)
                .sourceServer(serverName)
                .sender(sender)
                .content(request.getContent())
                .timestamp(System.currentTimeMillis() / 1000);

        switch (msgType) {
            case UNICAST_PLAYER -> builder.targetPlayer(request.getTargetPlayer());
            case UNICAST_SERVER -> builder.targetServer(request.getTargetServers() != null ? 
                    request.getTargetServers().get(0) : null);
            case MULTICAST_PLAYER -> builder.targetPlayers(request.getTargetPlayers());
            case MULTICAST_SERVER -> builder.targetServers(request.getTargetServers());
            case MULTICAST_GROUP -> builder.targetGroup(request.getTargetGroup());
        }

        sendMessage(builder.build());
    }

    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(ChatMessage message) {
        for (MessageListener listener : listeners) {
            try {
                listener.onMessage(message);
            } catch (Exception e) {
                log.error("Error notifying listener: {}", e.getMessage());
            }
        }
    }

    public interface MessageListener {
        void onMessage(ChatMessage message);
    }

    private class RouterWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            RouterWebSocketClient.this.session = session;
            connected = true;
            log.info("WebSocket connection established with router");

            ChatMessage connectMsg = ChatMessage.createServerConnect(serverName);
            sendMessage(connectMsg);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            try {
                ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);
                log.debug("Received message from router: {}", chatMessage.getMsgType());
                notifyListeners(chatMessage);
            } catch (Exception e) {
                log.error("Failed to parse message: {}", e.getMessage());
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            connected = false;
            log.warn("WebSocket connection closed: {}", status);
            scheduleReconnect();
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.error("WebSocket transport error: {}", exception.getMessage());
        }
    }
}
