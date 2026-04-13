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
import org.lyc122.dev.mc.chatServerClient.scheduler.SchedulerAdapter;
import org.lyc122.dev.mc.chatServerClient.websocket.WebSocketManager;

import java.util.concurrent.TimeUnit;

/**
 * 聊天监听器
 * 监听玩家聊天、加入、离开事件并发送到 ChatServer
 */
public class ChatListener implements Listener {

    private final ChatServerClient plugin;
    private final WebSocketManager webSocketManager;
    private final SchedulerAdapter scheduler;

    public ChatListener(ChatServerClient plugin, WebSocketManager webSocketManager, SchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.webSocketManager = webSocketManager;
        this.scheduler = scheduler;
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

                // 构建广播消息（显示服务器名称）
                String serverName = plugin.getConfig().getString("server.name", "Server");
                String broadcastMessage = "§a[" + serverName + "] §f" + player.getName() + " §7» §f" + message;

                // 发送给本地服务器的所有玩家（包括自己）
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    p.sendMessage(broadcastMessage);
                }

                // 输出到服务器控制台
                plugin.getLogger().info("[广播] " + player.getName() + " » " + message);
            }
            case LOCAL -> {
                // 本地消息不发送到 WebSocket，只在本地服务器内广播
                event.setCancelled(true);

                // 获取服务器名称
                String serverName = plugin.getConfig().getString("server.name", "Server");

                // 构建本地消息（显示服务器名称，使用黄色区分）
                String localMessage = "§e[" + serverName + "] §f" + player.getName() + " §7» §f" + message;

                // 获取本地聊天范围配置
                int localRadius = plugin.getConfig().getInt("chat.local-radius", 100);

                // 如果半径为 -1，则发送到整个服务器（本地服务器广播）
                if (localRadius == -1) {
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage(localMessage);
                    }
                } else {
                    // 否则按距离发送
                    for (Player p : player.getWorld().getPlayers()) {
                        if (p.getLocation().distance(player.getLocation()) <= localRadius) {
                            p.sendMessage(localMessage);
                        }
                    }
                }
                // 输出到服务器控制台
                plugin.getLogger().info("[本地] " + player.getName() + " » " + message);
            }
            case UNICAST_PLAYER -> {
                String target = plugin.getUnicastTarget(player);
                if (target != null) {
                    event.setCancelled(true);
                    webSocketManager.sendChatMessage(player.getName(), message, "UNICAST_PLAYER", target);
                    player.sendMessage("§d[私聊] §f你 §7-> §f" + target + " §7» §f" + message);
                    // 输出到服务器控制台
                    plugin.getLogger().info("[私聊] " + player.getName() + " -> " + target + " » " + message);
                }
            }
            case MULTICAST_PLAYER -> {
                // 组播玩家列表
                event.setCancelled(true);
                webSocketManager.sendChatMessage(player.getName(), message, "BROADCAST", null);

                // 构建组播消息
                String serverName = plugin.getConfig().getString("server.name", "Server");
                String multicastMessage = "§a[" + serverName + "] §f" + player.getName() + " §7» §f" + message;

                // 发送给本地服务器的所有玩家
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    p.sendMessage(multicastMessage);
                }

                // 输出到服务器控制台
                plugin.getLogger().info("[组播] " + player.getName() + " » " + message);
            }
            case MULTICAST_GROUP -> {
                String group = plugin.getMulticastGroup(player);
                if (group != null) {
                    event.setCancelled(true);
                    webSocketManager.sendChatMessage(player.getName(), message, "MULTICAST_GROUP", group);
                    player.sendMessage("§b[" + group + "] §f你 §7» §f" + message);
                    // 输出到服务器控制台
                    plugin.getLogger().info("[" + group + "] " + player.getName() + " » " + message);
                }
            }
            default -> {
                // 默认行为：作为广播发送
                event.setCancelled(true);
                webSocketManager.sendChatMessage(player.getName(), message, "BROADCAST", null);

                // 构建广播消息
                String serverName = plugin.getConfig().getString("server.name", "Server");
                String broadcastMessage = "§a[" + serverName + "] §f" + player.getName() + " §7» §f" + message;

                // 发送给本地服务器的所有玩家
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    p.sendMessage(broadcastMessage);
                }

                // 输出到服务器控制台
                plugin.getLogger().info("[广播] " + player.getName() + " » " + message);
            }
        }
    }

    /**
     * 监听玩家加入事件
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 获取服务器名称
        String serverName = plugin.getConfig().getString("server.name", "Server");

        // 广播玩家加入消息给所有在线玩家: [+,绿色]<playerName,绿色> ->（白色)[服务器名，金色]
        String joinMessage = "§a[+] §a" + player.getName() + " §7-> §6[" + serverName + "]";
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p != player) { // 不发送给自己
                p.sendMessage(joinMessage);
            }
        }

        // 延迟发送 WebSocket 消息，确保连接已建立
        scheduler.runGlobalDelayed(() -> {
            if (webSocketManager.isConnected()) {
                webSocketManager.sendPlayerJoin(player.getName());
            }
        }, 2, TimeUnit.SECONDS);

        // 重置玩家的聊天方法
        plugin.resetPlayerChatMethod(player);
    }

    /**
     * 监听玩家离开事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 获取服务器名称
        String serverName = plugin.getConfig().getString("server.name", "Server");

        // 广播玩家离开消息给所有在线玩家: [-,红色]<playerName,绿色> ->（白色)[服务器名，金色]
        String quitMessage = "§c[-] §a" + player.getName() + " §7-> §6[" + serverName + "]";
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(quitMessage);
        }

        if (webSocketManager.isConnected()) {
            webSocketManager.sendPlayerLeave(player.getName());
        }

        // 清理玩家的聊天方法
        plugin.removePlayerChatMethod(player);
    }
}
