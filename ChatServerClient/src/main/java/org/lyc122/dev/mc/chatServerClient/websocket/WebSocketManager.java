package org.lyc122.dev.mc.chatServerClient.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.lyc122.dev.mc.chatServerClient.ChatServerClient;
import org.lyc122.dev.mc.chatServerClient.scheduler.SchedulerAdapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * WebSocket 连接管理器
 * 负责与 ChatServer 建立和维护 WebSocket 连接
 */
public class WebSocketManager {

    private final ChatServerClient plugin;
    private final SchedulerAdapter scheduler;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean isConnecting;

    private WebSocketClient webSocketClient;
    private String serverUrl;
    private String serverName;
    private boolean autoReconnect;
    private int reconnectInterval;

    private volatile boolean connected = false;
    private MessageHandler messageHandler;

    public WebSocketManager(ChatServerClient plugin, SchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.objectMapper = new ObjectMapper();
        this.isConnecting = new AtomicBoolean(false);
    }

    /**
     * 初始化配置
     */
    public void configure(String serverUrl, String serverName, boolean autoReconnect, int reconnectInterval) {
        this.serverUrl = serverUrl;
        this.serverName = serverName;
        this.autoReconnect = autoReconnect;
        this.reconnectInterval = reconnectInterval;
    }

    /**
     * 连接到 ChatServer
     */
    public void connect() {
        if (isConnecting.compareAndSet(false, true)) {
            scheduler.runAsync(this::doConnect);
        }
    }

    private void doConnect() {
        try {
            if (serverUrl == null || serverName == null) {
                plugin.getLogger().warning("WebSocket 配置不完整，无法连接");
                return;
            }

            String wsUrl = serverUrl + "?serverName=" + encodeURIComponent(serverName);
            plugin.getLogger().info("正在连接到 ChatServer: " + wsUrl);

            webSocketClient = new WebSocketClient(new URI(wsUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    isConnecting.set(false);
                    plugin.getLogger().info("成功连接到 ChatServer!");

                    // 在全局区域触发连接成功事件
                    scheduler.runGlobal(() -> {
                        if (messageHandler != null) {
                            messageHandler.onConnected();
                        }
                    });
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    isConnecting.set(false);
                    plugin.getLogger().info("与 ChatServer 断开连接: " + reason + " (code: " + code + ")");

                    // 检查插件是否仍然启用，避免在关闭时尝试使用调度器
                    if (plugin.isEnabled()) {
                        scheduler.runGlobal(() -> {
                            if (messageHandler != null) {
                                messageHandler.onDisconnected(reason);
                            }
                        });

                        // 自动重连
                        if (autoReconnect && remote) {
                            scheduleReconnect();
                        }
                    }
                }

                @Override
                public void onError(Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "WebSocket 错误", ex);
                    isConnecting.set(false);
                }
            };

            webSocketClient.connect();
            
        } catch (URISyntaxException e) {
            plugin.getLogger().log(Level.SEVERE, "WebSocket URL 格式错误", e);
            isConnecting.set(false);
        }
    }

    /**
     * 处理接收到的消息
     */
    private void handleIncomingMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "UNKNOWN";

            // 检查插件是否仍然启用
            if (plugin.isEnabled()) {
                scheduler.runGlobal(() -> {
                    if (messageHandler != null) {
                        messageHandler.onMessage(type, jsonNode);
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析消息失败: " + message, e);
        }
    }

    /**
     * 发送消息到 ChatServer
     */
    public boolean sendMessage(ObjectNode message) {
        if (!connected || webSocketClient == null) {
            plugin.getLogger().warning("WebSocket 未连接，无法发送消息");
            return false;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            webSocketClient.send(json);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "发送消息失败", e);
            return false;
        }
    }

    /**
     * 发送玩家加入事件
     */
    public void sendPlayerJoin(String playerName) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "OPERATION");
        message.put("opType", "PLAYER_JOIN");
        message.put("senderType", "SERVER");
        message.put("sourceServer", serverName);
        message.put("sender", playerName);
        message.put("timestamp", System.currentTimeMillis() / 1000);
        
        sendMessage(message);
    }

    /**
     * 发送玩家离开事件
     */
    public void sendPlayerLeave(String playerName) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "OPERATION");
        message.put("opType", "PLAYER_LEAVE");
        message.put("senderType", "SERVER");
        message.put("sourceServer", serverName);
        message.put("sender", playerName);
        message.put("timestamp", System.currentTimeMillis() / 1000);
        
        sendMessage(message);
    }

    /**
     * 发送聊天消息
     */
    public void sendChatMessage(String playerName, String content, String msgType, String target) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "MESSAGE");
        message.put("msgType", msgType);
        message.put("senderType", "PLAYER");
        message.put("sourceServer", serverName);
        message.put("sender", playerName);
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis() / 1000);
        
        if (target != null) {
            switch (msgType) {
                case "UNICAST_PLAYER" -> message.put("targetPlayer", target);
                case "UNICAST_SERVER" -> message.put("targetServer", target);
                case "MULTICAST_PLAYER" -> message.putArray("targetPlayers").add(target);
                case "MULTICAST_SERVER" -> message.putArray("targetServers").add(target);
                case "MULTICAST_GROUP" -> message.put("targetGroup", target);
            }
        }
        
        sendMessage(message);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        autoReconnect = false;
        connected = false;

        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "关闭 WebSocket 时出错", e);
            }
            webSocketClient = null;
        }
    }

    /**
     * 计划重连
     */
    private void scheduleReconnect() {
        plugin.getLogger().info("将在 " + reconnectInterval + " 秒后尝试重连...");
        scheduler.runAsyncDelayed(() -> {
            if (!connected && autoReconnect) {
                connect();
            }
        }, reconnectInterval, TimeUnit.SECONDS);
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 设置消息处理器
     */
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    /**
     * URL 编码
     */
    private String encodeURIComponent(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 消息处理器接口
     */
    public interface MessageHandler {
        void onConnected();
        void onDisconnected(String reason);
        void onMessage(String type, JsonNode message);
    }
}
