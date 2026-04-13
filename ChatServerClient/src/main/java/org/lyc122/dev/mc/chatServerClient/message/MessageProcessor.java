package org.lyc122.dev.mc.chatServerClient.message;

import com.fasterxml.jackson.databind.JsonNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lyc122.dev.mc.chatServerClient.ChatServerClient;
import org.lyc122.dev.mc.chatServerClient.scheduler.SchedulerAdapter;
import org.lyc122.dev.mc.chatServerClient.websocket.WebSocketManager;

import java.util.concurrent.TimeUnit;

/**
 * 消息处理器
 * 处理从 ChatServer 接收到的消息
 */
public class MessageProcessor implements WebSocketManager.MessageHandler {

    private final ChatServerClient plugin;
    private final WebSocketManager webSocketManager;
    private final SchedulerAdapter scheduler;

    // 颜色代码映射
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer
            .builder()
            .character('&')
            .hexColors()
            .build();

    public MessageProcessor(ChatServerClient plugin, WebSocketManager webSocketManager, SchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.webSocketManager = webSocketManager;
        this.scheduler = scheduler;
    }

    @Override
    public void onConnected() {
        plugin.getLogger().info("消息处理器已就绪");

        // 发送当前在线玩家列表
        scheduler.runGlobalDelayed(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                webSocketManager.sendPlayerJoin(player.getName());
            }
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onDisconnected(String reason) {
        plugin.getLogger().info("消息处理器已断开: " + reason);
    }

    @Override
    public void onMessage(String type, JsonNode message) {
        try {
            switch (type) {
                case "MESSAGE" -> handleChatMessage(message);
                case "OPERATION" -> handleOperation(message);
                default -> plugin.getLogger().warning("未知消息类型: " + type);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理消息时出错: " + e.getMessage());
        }
    }

    /**
     * 处理聊天消息
     */
    private void handleChatMessage(JsonNode message) {
        String msgType = message.has("msgType") ? message.get("msgType").asText() : "UNKNOWN";
        String sender = message.has("sender") ? message.get("sender").asText() : "Unknown";
        String content = message.has("content") ? message.get("content").asText() : "";
        String sourceServer = message.has("sourceServer") ? message.get("sourceServer").asText() : "Unknown";
        String senderType = message.has("senderType") ? message.get("senderType").asText() : "PLAYER";

        switch (msgType) {
            case "UNICAST_PLAYER" -> handleUnicastPlayer(message, sender, content, sourceServer);
            case "MULTICAST_PLAYER" -> handleMulticastPlayer(message, sender, content, sourceServer);
            case "MULTICAST_GROUP" -> handleMulticastGroup(message, sender, content, sourceServer);
            case "BROADCAST" -> handleBroadcast(sender, content, sourceServer, senderType);
            case "UNICAST_SERVER", "MULTICAST_SERVER" -> handleServerMessage(sender, content, sourceServer);
            default -> plugin.getLogger().warning("未知消息类型: " + msgType);
        }
    }

    /**
     * 处理单播到玩家
     */
    private void handleUnicastPlayer(JsonNode message, String sender, String content, String sourceServer) {
        String targetPlayer = message.has("targetPlayer") ? message.get("targetPlayer").asText() : null;
        if (targetPlayer == null) return;

        Player player = Bukkit.getPlayerExact(targetPlayer);
        if (player != null && player.isOnline()) {
            Component msg = formatPrivateMessage(sender, content, sourceServer);
            player.sendMessage(msg);
        }
    }

    /**
     * 处理组播到玩家列表
     */
    private void handleMulticastPlayer(JsonNode message, String sender, String content, String sourceServer) {
        JsonNode targetPlayersNode = message.get("targetPlayers");
        if (targetPlayersNode == null || !targetPlayersNode.isArray()) return;

        Component msg = formatMulticastMessage(sender, content, sourceServer);
        
        for (JsonNode playerNode : targetPlayersNode) {
            String playerName = playerNode.asText();
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                player.sendMessage(msg);
            }
        }
    }

    /**
     * 处理群组消息
     */
    private void handleMulticastGroup(JsonNode message, String sender, String content, String sourceServer) {
        String targetGroup = message.has("targetGroup") ? message.get("targetGroup").asText() : null;
        if (targetGroup == null) return;

        Component msg = formatGroupMessage(sender, content, sourceServer, targetGroup);
        
        // 发送给所有在线玩家（由 ChatServer 决定哪些玩家应该收到）
        // 这里简单处理：如果目标玩家在本服务器，则发送
        JsonNode targetPlayersNode = message.get("targetPlayers");
        if (targetPlayersNode != null && targetPlayersNode.isArray()) {
            for (JsonNode playerNode : targetPlayersNode) {
                String playerName = playerNode.asText();
                Player player = Bukkit.getPlayerExact(playerName);
                if (player != null && player.isOnline()) {
                    player.sendMessage(msg);
                }
            }
        }
    }

    /**
     * 处理广播消息
     */
    private void handleBroadcast(String sender, String content, String sourceServer, String senderType) {
        Component msg = formatBroadcastMessage(sender, content, sourceServer, senderType);
        Bukkit.broadcast(msg);
    }

    /**
     * 处理服务器消息
     */
    private void handleServerMessage(String sender, String content, String sourceServer) {
        Component msg = formatServerMessage(sender, content, sourceServer);
        Bukkit.broadcast(msg);
    }

    /**
     * 处理操作消息
     */
    private void handleOperation(JsonNode message) {
        String opType = message.has("opType") ? message.get("opType").asText() : "UNKNOWN";

        switch (opType) {
            case "SERVER_CONNECT" -> {
                // 使用消息中的 sourceServer 字段（服务端使用此字段存储服务器名）
                String serverName = message.has("sourceServer") ? message.get("sourceServer").asText()
                    : plugin.getConfig().getString("server.name", "Unknown");
                plugin.getLogger().info("服务器 [" + serverName + "] 连接成功");
            }
            case "SERVER_DISCONNECT" -> {
                // 使用消息中的 sourceServer 字段
                String disconnectedServer = message.has("sourceServer") ? message.get("sourceServer").asText()
                    : plugin.getConfig().getString("server.name", "Unknown");
                String reason = message.has("reason") ? message.get("reason").asText() : "未知原因";
                plugin.getLogger().warning("服务器 [" + disconnectedServer + "] 断开连接，原因: " + reason);

                // 广播给所有在线玩家
                String disconnectMsg = "§c[系统] §f服务器 [" + disconnectedServer + "] 已断开连接";
                scheduler.runGlobal(() -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(disconnectMsg);
                    }
                });
            }
            case "PLAYER_JOIN" -> {
                String joinedPlayer = message.has("sender") ? message.get("sender").asText() : "Unknown";
                // 使用消息中的 sourceServer 字段
                String fromServer = message.has("sourceServer") ? message.get("sourceServer").asText()
                    : plugin.getConfig().getString("server.name", "Unknown");
                plugin.getLogger().info("玩家 " + joinedPlayer + " 加入了服务器 [" + fromServer + "]");
            }
            case "PLAYER_LEAVE" -> {
                String leftPlayer = message.has("sender") ? message.get("sender").asText() : "Unknown";
                // 使用消息中的 sourceServer 字段
                String fromServer = message.has("sourceServer") ? message.get("sourceServer").asText()
                    : plugin.getConfig().getString("server.name", "Unknown");
                plugin.getLogger().info("玩家 " + leftPlayer + " 离开了服务器 [" + fromServer + "]");
            }
            default -> plugin.getLogger().fine("操作类型: " + opType);
        }
    }

    // ==================== 消息格式化 ====================

    private Component formatPrivateMessage(String sender, String content, String sourceServer) {
        return Component.text("[私聊] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("[" + sourceServer + "] ", NamedTextColor.GRAY))
                .append(Component.text(sender, NamedTextColor.YELLOW))
                .append(Component.text(" » ", NamedTextColor.WHITE))
                .append(LEGACY_SERIALIZER.deserialize(content));
    }

    private Component formatMulticastMessage(String sender, String content, String sourceServer) {
        return Component.text("[组播] ", NamedTextColor.GREEN)
                .append(Component.text("[" + sourceServer + "] ", NamedTextColor.GRAY))
                .append(Component.text(sender, NamedTextColor.YELLOW))
                .append(Component.text(" » ", NamedTextColor.WHITE))
                .append(LEGACY_SERIALIZER.deserialize(content));
    }

    private Component formatGroupMessage(String sender, String content, String sourceServer, String groupName) {
        return Component.text("[" + groupName + "] ", NamedTextColor.AQUA)
                .append(Component.text("[" + sourceServer + "] ", NamedTextColor.GRAY))
                .append(Component.text(sender, NamedTextColor.YELLOW))
                .append(Component.text(" » ", NamedTextColor.WHITE))
                .append(LEGACY_SERIALIZER.deserialize(content));
    }

    private Component formatBroadcastMessage(String sender, String content, String sourceServer, String senderType) {
        // 从配置读取是否显示服务器前缀
        boolean showServerPrefix = plugin.getConfig().getBoolean("chat.show-server-prefix", true);
        String prefixFormat = plugin.getConfig().getString("chat.server-prefix-format", "&7[%server%] ");

        Component prefix = switch (senderType) {
            case "SYSTEM" -> Component.text("[系统] ", NamedTextColor.RED);
            case "SERVER" -> Component.text("[服务器] ", NamedTextColor.GOLD);
            default -> {
                // 普通玩家广播：显示来源服务器名称（绿色）
                if (showServerPrefix) {
                    String formattedPrefix = prefixFormat.replace("%server%", sourceServer);
                    yield LEGACY_SERIALIZER.deserialize("&a[" + sourceServer + "] ");
                } else {
                    yield Component.empty();
                }
            }
        };

        return prefix
                .append(Component.text(sender, NamedTextColor.YELLOW))
                .append(Component.text(" » ", NamedTextColor.WHITE))
                .append(LEGACY_SERIALIZER.deserialize(content));
    }

    private Component formatServerMessage(String sender, String content, String sourceServer) {
        return Component.text("[" + sourceServer + "] ", NamedTextColor.GRAY)
                .append(Component.text(sender, NamedTextColor.YELLOW))
                .append(Component.text(" » ", NamedTextColor.WHITE))
                .append(LEGACY_SERIALIZER.deserialize(content));
    }
}
