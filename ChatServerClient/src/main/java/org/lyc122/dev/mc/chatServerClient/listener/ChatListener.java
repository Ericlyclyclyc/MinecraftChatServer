package org.lyc122.dev.mc.chatServerClient.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.lyc122.dev.mc.chatServerClient.ChatServerClient;
import org.lyc122.dev.mc.chatServerClient.chat.ChatMethodType;
import org.lyc122.dev.mc.chatServerClient.websocket.WebSocketManager;

/**
 * 聊天监听器
 * 监听玩家聊天、加入、离开事件并发送到 ChatServer
 */
public class ChatListener implements Listener {

    private final ChatServerClient plugin;
    private final WebSocketManager webSocketManager;

    public ChatListener(ChatServerClient plugin, WebSocketManager webSocketManager) {
        this.plugin = plugin;
        this.webSocketManager = webSocketManager;
    }

    /**
     * 监听玩家聊天事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        // 检查是否是命令（以 / 开头）
        if (message.startsWith("/")) {
            return; // 命令不转发
        }

        // 获取玩家的聊天方法
        ChatMethodType method = plugin.getPlayerChatMethod(player);
        
        // 根据聊天方法类型发送不同消息
        switch (method) {
            case BROADCAST -> {
                event.setCancelled(true);
                webSocketManager.sendChatMessage(player.getName(), message, "BROADCAST", null);
                player.sendMessage("§7[广播] §f你 §7» §f" + message);
            }
            case LOCAL -> {
                // 本地消息不发送到服务器，也不取消事件
                // 让 Minecraft 正常处理本地聊天
                // 但我们需要取消原始事件并手动发送本地消息
                event.setCancelled(true);
                String localMessage = "§e[本地] §f" + player.getName() + " §7» §f" + message;
                for (Player p : player.getWorld().getPlayers()) {
                    if (p.getLocation().distance(player.getLocation()) <= 100) {
                        p.sendMessage(localMessage);
                    }
                }
            }
            case UNICAST_PLAYER -> {
                String target = plugin.getUnicastTarget(player);
                if (target != null) {
                    event.setCancelled(true);
                    webSocketManager.sendChatMessage(player.getName(), message, "UNICAST_PLAYER", target);
                    player.sendMessage("§d[私聊] §f你 §7-> §f" + target + " §7» §f" + message);
                }
            }
            case MULTICAST_PLAYER -> {
                // 组播玩家列表
                event.setCancelled(true);
                webSocketManager.sendChatMessage(player.getName(), message, "BROADCAST", null);
                player.sendMessage("§a[组播] §f你 §7» §f" + message);
            }
            case MULTICAST_GROUP -> {
                String group = plugin.getMulticastGroup(player);
                if (group != null) {
                    event.setCancelled(true);
                    webSocketManager.sendChatMessage(player.getName(), message, "MULTICAST_GROUP", group);
                    player.sendMessage("§b[" + group + "] §f你 §7» §f" + message);
                }
            }
            default -> {
                // 默认行为：作为广播发送
                event.setCancelled(true);
                webSocketManager.sendChatMessage(player.getName(), message, "BROADCAST", null);
                player.sendMessage("§7[广播] §f你 §7» §f" + message);
            }
        }
    }

    /**
     * 监听玩家加入事件
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 延迟发送，确保 WebSocket 已连接
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (webSocketManager.isConnected()) {
                webSocketManager.sendPlayerJoin(player.getName());
            }
        }, 40L); // 延迟2秒
        
        // 重置玩家的聊天方法
        plugin.resetPlayerChatMethod(player);
    }

    /**
     * 监听玩家离开事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (webSocketManager.isConnected()) {
            webSocketManager.sendPlayerLeave(player.getName());
        }
        
        // 清理玩家的聊天方法
        plugin.removePlayerChatMethod(player);
    }
}
