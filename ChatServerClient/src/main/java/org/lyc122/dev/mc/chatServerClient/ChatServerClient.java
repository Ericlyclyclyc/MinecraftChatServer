package org.lyc122.dev.mc.chatServerClient;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.lyc122.dev.mc.chatServerClient.chat.ChatMethodType;
import org.lyc122.dev.mc.chatServerClient.listener.ChatListener;
import org.lyc122.dev.mc.chatServerClient.message.MessageProcessor;
import org.lyc122.dev.mc.chatServerClient.websocket.WebSocketManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatServerClient extends JavaPlugin {

    // 玩家聊天方法映射
    private final ConcurrentHashMap<UUID, ChatMethodType> playerChatMethods = new ConcurrentHashMap<>();
    // 玩家单播目标映射
    private final ConcurrentHashMap<UUID, String> playerUnicastTargets = new ConcurrentHashMap<>();
    // 玩家组播群组映射
    private final ConcurrentHashMap<UUID, String> playerMulticastGroups = new ConcurrentHashMap<>();

    private WebSocketManager webSocketManager;
    private MessageProcessor messageProcessor;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        
        // 初始化 WebSocket 管理器
        webSocketManager = new WebSocketManager(this);
        
        // 读取配置
        String serverUrl = getConfig().getString("websocket.url", "ws://localhost:8080/ws/minecraft-chat");
        String serverName = getConfig().getString("server.name", "Minecraft-Server");
        boolean autoReconnect = getConfig().getBoolean("websocket.auto-reconnect", true);
        int reconnectInterval = getConfig().getInt("websocket.reconnect-interval", 10);
        
        // 配置 WebSocket
        webSocketManager.configure(serverUrl, serverName, autoReconnect, reconnectInterval);
        
        // 初始化消息处理器
        messageProcessor = new MessageProcessor(this, webSocketManager);
        webSocketManager.setMessageHandler(messageProcessor);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new ChatListener(this, webSocketManager), this);
        
        // 连接到 ChatServer
        webSocketManager.connect();
        
        getLogger().info("ChatServerClient 已启用!");
        getLogger().info("服务器名称: " + serverName);
        getLogger().info("ChatServer 地址: " + serverUrl);
    }

    @Override
    public void onDisable() {
        // 断开 WebSocket 连接
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
        
        // 清理映射
        playerChatMethods.clear();
        playerUnicastTargets.clear();
        playerMulticastGroups.clear();
        
        getLogger().info("ChatServerClient 已禁用!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行!");
            return true;
        }

        String cmd = command.getName().toLowerCase();
        
        switch (cmd) {
            case "chat" -> {
                if (args.length == 0) {
                    player.sendMessage("§e用法: /chat <local|broadcast|unicast|multicast|group> [目标]");
                    return true;
                }
                
                String mode = args[0].toLowerCase();
                switch (mode) {
                    case "local", "l" -> {
                        setPlayerChatMethod(player, ChatMethodType.LOCAL);
                        player.sendMessage("§a已切换到本地聊天模式");
                    }
                    case "broadcast", "b", "all" -> {
                        setPlayerChatMethod(player, ChatMethodType.BROADCAST);
                        player.sendMessage("§a已切换到广播模式 - 消息将发送到所有服务器");
                    }
                    case "unicast", "u", "whisper", "w", "msg" -> {
                        if (args.length < 2) {
                            player.sendMessage("§e用法: /chat unicast <玩家名>");
                            return true;
                        }
                        setPlayerChatMethod(player, ChatMethodType.UNICAST_PLAYER);
                        setUnicastTarget(player, args[1]);
                        player.sendMessage("§a已切换到私聊模式 - 消息将发送给 " + args[1]);
                    }
                    case "multicast", "m" -> {
                        setPlayerChatMethod(player, ChatMethodType.MULTICAST_PLAYER);
                        player.sendMessage("§a已切换到组播模式");
                    }
                    case "group", "g" -> {
                        if (args.length < 2) {
                            player.sendMessage("§e用法: /chat group <群组名>");
                            return true;
                        }
                        setPlayerChatMethod(player, ChatMethodType.MULTICAST_GROUP);
                        setMulticastGroup(player, args[1]);
                        player.sendMessage("§a已切换到群组模式 - 消息将发送到群组 " + args[1]);
                    }
                    default -> player.sendMessage("§c未知模式: " + mode);
                }
            }
            case "chatserver" -> {
                if (args.length == 0) {
                    player.sendMessage("§eChatServer 状态:");
                    player.sendMessage("§7连接状态: " + (webSocketManager.isConnected() ? "§a已连接" : "§c未连接"));
                    player.sendMessage("§7当前模式: §f" + getPlayerChatMethod(player));
                    return true;
                }
                
                String subCmd = args[0].toLowerCase();
                switch (subCmd) {
                    case "reconnect" -> {
                        player.sendMessage("§a正在重新连接...");
                        webSocketManager.connect();
                    }
                    case "status" -> {
                        player.sendMessage("§eChatServer 状态:");
                        player.sendMessage("§7连接状态: " + (webSocketManager.isConnected() ? "§a已连接" : "§c未连接"));
                    }
                    default -> player.sendMessage("§c未知子命令: " + subCmd);
                }
            }
        }
        
        return true;
    }

    // ==================== 玩家聊天方法管理 ====================

    /**
     * 获取玩家的聊天方法
     */
    public ChatMethodType getPlayerChatMethod(Player player) {
        return playerChatMethods.getOrDefault(player.getUniqueId(), ChatMethodType.BROADCAST);
    }

    /**
     * 设置玩家的聊天方法
     */
    public void setPlayerChatMethod(Player player, ChatMethodType method) {
        playerChatMethods.put(player.getUniqueId(), method);
    }

    /**
     * 重置玩家的聊天方法（默认为广播）
     */
    public void resetPlayerChatMethod(Player player) {
        playerChatMethods.put(player.getUniqueId(), ChatMethodType.BROADCAST);
        playerUnicastTargets.remove(player.getUniqueId());
        playerMulticastGroups.remove(player.getUniqueId());
    }

    /**
     * 移除玩家的聊天方法
     */
    public void removePlayerChatMethod(Player player) {
        playerChatMethods.remove(player.getUniqueId());
        playerUnicastTargets.remove(player.getUniqueId());
        playerMulticastGroups.remove(player.getUniqueId());
    }

    /**
     * 获取玩家的单播目标
     */
    public String getUnicastTarget(Player player) {
        return playerUnicastTargets.get(player.getUniqueId());
    }

    /**
     * 设置玩家的单播目标
     */
    public void setUnicastTarget(Player player, String target) {
        playerUnicastTargets.put(player.getUniqueId(), target);
    }

    /**
     * 获取玩家的组播群组
     */
    public String getMulticastGroup(Player player) {
        return playerMulticastGroups.get(player.getUniqueId());
    }

    /**
     * 设置玩家的组播群组
     */
    public void setMulticastGroup(Player player, String group) {
        playerMulticastGroups.put(player.getUniqueId(), group);
    }

    /**
     * 获取 WebSocket 管理器
     */
    public WebSocketManager getWebSocketManager() {
        return webSocketManager;
    }
}
