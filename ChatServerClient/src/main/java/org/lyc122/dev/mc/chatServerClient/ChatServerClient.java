package org.lyc122.dev.mc.chatServerClient;

import ChatMethods.BaseChatMethod;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;

public final class ChatServerClient extends JavaPlugin {

    private static ConcurrentHashMap<Player, BaseChatMethod> playerChatMethod = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {

        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
