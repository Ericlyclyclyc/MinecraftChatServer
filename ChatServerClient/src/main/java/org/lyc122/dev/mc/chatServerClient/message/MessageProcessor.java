package org.lyc122.dev.mc.chatServerClient.message;

import com.fasterxml.jackson.databind.JsonNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lyc122.dev.mc.chatServerClient.ChatServerClient;
import org.lyc122.dev.mc.chatServerClient.websocket.WebSocketManager;

import java.util.List;
import java.util.Set;

/**
 * 消息处理器
 * 处理从 ChatServer 接收到的消息
 */
public class MessageProcessor implements WebSocketManager.MessageHandler {

    private final ChatServerClient plugin;
    private final WebSocketManager webSocketManager;

    // 颜色代码映射
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer
            .builder()
            .character('&')
            .hexColors()
            .build();

    public MessageProcessor(ChatServerClient plugin, WebSocketManager webSocketManager) {
        this.plugin = plugin;
        this.webSocketManager = webSocketManager;
    }

    @Override
    public void onConnected() {
        plugin.getLogger().info("消息处理器已就绪");
        
        // 发送当前在线玩家列表
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                webSocketManager.sendPlayerJoin(player.getName());
            }
        }, 20L); // 延迟1秒发送
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
            case "SERVER_CONNECT" -> plugin.getLogger().info("服务器连接成功");
            case "SERVER_DISCONNECT" -> plugin.getLogger().warning("服务器断开连接通知");
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
        Component prefix = switch (senderType) {
            case "SYSTEM" -> Component.text("[系统] ", NamedTextColor.RED);
            case "SERVER" -> Component.text("[服务器] ", NamedTextColor.GOLD);
            default -> Component.text("[广播] ", NamedTextColor.BLUE);
        };
        
        return prefix
                .append(Component.text("[" + sourceServer + "] ", NamedTextColor.GRAY))
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
