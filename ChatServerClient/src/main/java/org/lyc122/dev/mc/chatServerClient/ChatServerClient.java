package org.lyc122.dev.mc.chatServerClient;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.lyc122.dev.mc.chatServerClient.chat.ChatMethodType;
import org.lyc122.dev.mc.chatServerClient.listener.ChatListener;
import org.lyc122.dev.mc.chatServerClient.message.MessageProcessor;
import org.lyc122.dev.mc.chatServerClient.scheduler.SchedulerAdapter;
import org.lyc122.dev.mc.chatServerClient.scheduler.SchedulerFactory;
import org.lyc122.dev.mc.chatServerClient.websocket.WebSocketManager;

import java.io.File;
import java.util.*;
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
    private SchedulerAdapter scheduler;

    // 当前配置文件版本，每次更新配置文件结构时加 1
    private static final int CURRENT_CONFIG_VERSION = 2;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 检查并升级配置文件
        upgradeConfigIfNeeded();

        // 初始化或获取服务器 UUID
        String serverUuid = initializeServerUuid();

        // 初始化调度器适配器（自动检测 Folia）
        scheduler = SchedulerFactory.create(this);

        // 初始化 WebSocket 管理器
        webSocketManager = new WebSocketManager(this, scheduler);

        // 读取配置
        String serverUrl = getConfig().getString("websocket.url", "ws://localhost:8080/ws/minecraft-chat");
        String serverName = getConfig().getString("server.name", "Minecraft-Server");
        boolean autoReconnect = getConfig().getBoolean("websocket.auto-reconnect", true);
        int reconnectInterval = getConfig().getInt("websocket.reconnect-interval", 10);

        // 配置 WebSocket
        webSocketManager.configure(serverUrl, serverName, autoReconnect, reconnectInterval);

        // 初始化消息处理器
        messageProcessor = new MessageProcessor(this, webSocketManager, scheduler);
        webSocketManager.setMessageHandler(messageProcessor);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new ChatListener(this, webSocketManager, scheduler), this);

        // 注册 Tab 补全
        getCommand("chat").setTabCompleter(new ChatTabCompleter());
        getCommand("chatserver").setTabCompleter(new ChatServerTabCompleter());

        // 连接到 ChatServer
        webSocketManager.connect();

        getLogger().info("ChatServerClient 已启用!");
        getLogger().info("服务器类型: " + SchedulerFactory.getServerType());
        getLogger().info("服务器名称: " + serverName);
        getLogger().info("服务器 UUID: " + serverUuid);
        getLogger().info("ChatServer 地址: " + serverUrl);
    }

    /**
     * 检查并升级配置文件
     * 保留用户自定义设置，仅添加新配置项
     */
    private void upgradeConfigIfNeeded() {
        int currentVersion = getConfig().getInt("config-version", 0);

        if (currentVersion < CURRENT_CONFIG_VERSION) {
            getLogger().info("检测到配置文件版本 " + currentVersion + "，需要升级到版本 " + CURRENT_CONFIG_VERSION);

            // 备份当前配置
            backupCurrentConfig();

            // 执行版本升级
            for (int version = currentVersion + 1; version <= CURRENT_CONFIG_VERSION; version++) {
                applyConfigUpgrade(version);
            }

            // 更新配置版本号
            getConfig().set("config-version", CURRENT_CONFIG_VERSION);
            saveConfig();

            getLogger().info("配置文件已成功升级到版本 " + CURRENT_CONFIG_VERSION);
        }
    }

    /**
     * 备份当前配置文件
     */
    private void backupCurrentConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        File backupFile = new File(getDataFolder(), "config-backup-" + System.currentTimeMillis() + ".yml");

        if (configFile.exists()) {
            try {
                java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath());
                getLogger().info("已备份配置文件到: " + backupFile.getName());
            } catch (Exception e) {
                getLogger().warning("备份配置文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 应用特定版本的配置升级
     * @param targetVersion 目标版本号
     */
    private void applyConfigUpgrade(int targetVersion) {
        switch (targetVersion) {
            case 1 -> {
                // 版本 1: 初始版本，添加基本配置
                // 确保 server.uuid 存在
                if (!getConfig().contains("server.uuid")) {
                    getConfig().set("server.uuid", "generate");
                    getLogger().info("添加配置项: server.uuid");
                }
                // 确保 chat.local-radius 存在
                if (!getConfig().contains("chat.local-radius")) {
                    getConfig().set("chat.local-radius", 100);
                    getLogger().info("添加配置项: chat.local-radius");
                }
            }
            // 后续版本在这里添加新的 case
            // case 2 -> { ... }
            // case 3 -> { ... }
            default -> getLogger().warning("未知的配置版本: " + targetVersion);
        }
    }

    /**
     * 初始化服务器 UUID
     * 如果配置中为 "generate" 或为空，则生成新的 UUID
     */
    private String initializeServerUuid() {
        String uuid = getConfig().getString("server.uuid", "generate");

        if (uuid == null || uuid.isBlank() || "generate".equalsIgnoreCase(uuid)) {
            // 生成新的 UUID
            uuid = UUID.randomUUID().toString();
            getConfig().set("server.uuid", uuid);
            saveConfig();
            getLogger().info("已生成新的服务器 UUID: " + uuid);
        }

        return uuid;
    }

    @Override
    public void onDisable() {
        // 断开 WebSocket 连接
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }

        // 取消所有调度器任务
        if (scheduler != null) {
            scheduler.cancelAllTasks();
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
                if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                    sendChatHelp(player);
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
                    default -> player.sendMessage("§c未知模式: " + mode + "，使用 /chat help 查看帮助");
                }
            }
            case "chatserver" -> {
                if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                    sendChatServerHelp(player);
                    return true;
                }

                String subCmd = args[0].toLowerCase();
                switch (subCmd) {
                    case "reconnect" -> {
                        // 仅在未连接或连接断开时才允许手动重连
                        if (webSocketManager.isConnected()) {
                            player.sendMessage("§e已经连接到 ChatServer，无需重连");
                            return true;
                        }
                        player.sendMessage("§a正在尝试重新连接 ChatServer...");
                        webSocketManager.connect();
                    }
                    case "status" -> {
                        player.sendMessage("§eChatServer 状态:");
                        player.sendMessage("§7连接状态: " + (webSocketManager.isConnected() ? "§a已连接" : "§c未连接"));
                        player.sendMessage("§7当前模式: §f" + getPlayerChatMethod(player));
                    }
                    default -> player.sendMessage("§c未知子命令: " + subCmd + "，使用 /chatserver help 查看帮助");
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
     * 重置玩家的聊天方法（使用配置文件中的默认模式）
     */
    public void resetPlayerChatMethod(Player player) {
        ChatMethodType defaultMode = getDefaultChatMode();
        playerChatMethods.put(player.getUniqueId(), defaultMode);
        playerUnicastTargets.remove(player.getUniqueId());
        playerMulticastGroups.remove(player.getUniqueId());
    }

    /**
     * 获取配置文件中的默认聊天模式
     */
    private ChatMethodType getDefaultChatMode() {
        String defaultModeStr = getConfig().getString("chat.default-mode", "BROADCAST");
        try {
            return ChatMethodType.valueOf(defaultModeStr);
        } catch (IllegalArgumentException e) {
            getLogger().warning("未知的默认聊天模式: " + defaultModeStr + "，使用 BROADCAST");
            return ChatMethodType.BROADCAST;
        }
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

    /**
     * 获取调度器适配器
     */
    public SchedulerAdapter getScheduler() {
        return scheduler;
    }

    // ==================== Help 方法 ====================

    /**
     * 发送 /chat 命令帮助
     */
    private void sendChatHelp(Player player) {
        player.sendMessage("§6========== ChatServerClient 聊天命令 ==========");
        player.sendMessage("§e当前模式: §f" + getPlayerChatMethod(player));
        player.sendMessage("");
        player.sendMessage("§7/chat local §8(l) §7- §f切换到本地聊天模式");
        player.sendMessage("§7/chat broadcast §8(b, all) §7- §f切换到广播模式（发送到所有服务器）");
        player.sendMessage("§7/chat unicast §8(u, w, msg) <玩家名> §7- §f切换到私聊模式");
        player.sendMessage("§7/chat multicast §8(m) §7- §f切换到组播模式");
        player.sendMessage("§7/chat group §8(g) <群组名> §7- §f切换到群组模式");
        player.sendMessage("§7/chat help §7- §f显示此帮助信息");
        player.sendMessage("");
        player.sendMessage("§6直接输入消息即可使用当前模式发送");
        player.sendMessage("§6===========================================");
    }

    /**
     * 发送 /chatserver 命令帮助
     */
    private void sendChatServerHelp(Player player) {
        player.sendMessage("§6========== ChatServerClient 管理命令 ==========");
        player.sendMessage("§e当前状态:");
        player.sendMessage("§7  连接状态: " + (webSocketManager.isConnected() ? "§a已连接" : "§c未连接"));
        player.sendMessage("§7  聊天模式: §f" + getPlayerChatMethod(player));
        player.sendMessage("");
        player.sendMessage("§7/chatserver reconnect §7- §f重新连接 ChatServer");
        player.sendMessage("§7/chatserver status §7- §f查看连接状态");
        player.sendMessage("§7/chatserver help §7- §f显示此帮助信息");
        player.sendMessage("§6============================================");
    }

    // ==================== Tab 补全类 ====================

    /**
     * /chat 命令 Tab 补全
     */
    private class ChatTabCompleter implements TabCompleter {
        private final List<String> MODES = Arrays.asList(
            "local", "l", "broadcast", "b", "all",
            "unicast", "u", "whisper", "w", "msg",
            "multicast", "m", "group", "g", "help"
        );

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                // 过滤匹配的模式
                String input = args[0].toLowerCase();
                List<String> completions = new ArrayList<>();
                for (String mode : MODES) {
                    if (mode.startsWith(input)) {
                        completions.add(mode);
                    }
                }
                return completions;
            } else if (args.length == 2) {
                // 第二个参数：unicast 时补全在线玩家名
                String mode = args[0].toLowerCase();
                if (mode.equals("unicast") || mode.equals("u") || mode.equals("whisper") || mode.equals("w") || mode.equals("msg")) {
                    String input = args[1].toLowerCase();
                    List<String> completions = new ArrayList<>();
                    for (Player player : getServer().getOnlinePlayers()) {
                        String name = player.getName();
                        if (name.toLowerCase().startsWith(input)) {
                            completions.add(name);
                        }
                    }
                    return completions;
                }
            }
            return Collections.emptyList();
        }
    }

    /**
     * /chatserver 命令 Tab 补全
     */
    private class ChatServerTabCompleter implements TabCompleter {
        private final List<String> SUBCOMMANDS = Arrays.asList("reconnect", "status", "help");

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                String input = args[0].toLowerCase();
                List<String> completions = new ArrayList<>();
                for (String subCmd : SUBCOMMANDS) {
                    if (subCmd.startsWith(input)) {
                        completions.add(subCmd);
                    }
                }
                return completions;
            }
            return Collections.emptyList();
        }
    }
}
