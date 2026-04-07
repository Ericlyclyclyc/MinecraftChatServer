package org.lyc122.dev.minecraftchatserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
public class ServerSessionService {

    /**
     * 存储服务器名称到WebSocket会话的映射
     */
    private final Map<String, WebSocketSession> serverSessions = new ConcurrentHashMap<>();

    /**
     * 存储WebSocket会话ID到服务器名称的映射
     */
    private final Map<String, String> sessionToServerMap = new ConcurrentHashMap<>();

    /**
     * 存储玩家到服务器的映射（用于精准路由）
     */
    private final Map<String, String> playerServerMap = new ConcurrentHashMap<>();

    /**
     * 存储服务器到玩家列表的映射
     */
    private final Map<String, Set<String>> serverPlayersMap = new ConcurrentHashMap<>();

    /**
     * 注册服务器连接
     *
     * @param serverName 服务器名称
     * @param session    WebSocket会话
     * @param address    服务器地址
     * @return 是否注册成功
     */
    public synchronized boolean registerServer(String serverName, WebSocketSession session, String address) {
        if (serverSessions.containsKey(serverName)) {
            log.warn("服务器 [{}] 已经连接，拒绝重复连接", serverName);
            return false;
        }

        serverSessions.put(serverName, session);
        sessionToServerMap.put(session.getId(), serverName);
        serverPlayersMap.put(serverName, new CopyOnWriteArraySet<>());

        log.info("服务器 [{}] 已连接，地址: {}", serverName, address);
        return true;
    }

    /**
     * 注销服务器连接
     *
     * @param session WebSocket会话
     */
    public synchronized void unregisterServer(WebSocketSession session) {
        String serverName = sessionToServerMap.get(session.getId());
        if (serverName != null) {
            // 清理该服务器的所有玩家映射
            Set<String> players = serverPlayersMap.remove(serverName);
            if (players != null) {
                players.forEach(playerServerMap::remove);
            }
            serverSessions.remove(serverName);
            sessionToServerMap.remove(session.getId());
            log.info("服务器 [{}] 已断开连接", serverName);
        }
    }

    /**
     * 根据服务器名称获取会话
     *
     * @param serverName 服务器名称
     * @return WebSocket会话
     */
    public WebSocketSession getSession(String serverName) {
        return serverSessions.get(serverName);
    }

    /**
     * 根据会话ID获取服务器名称
     *
     * @param sessionId 会话ID
     * @return 服务器名称
     */
    public String getServerNameBySessionId(String sessionId) {
        return sessionToServerMap.get(sessionId);
    }

    /**
     * 获取所有已连接的服务器名称
     *
     * @return 服务器名称集合
     */
    public Set<String> getAllServerNames() {
        return serverSessions.keySet();
    }

    /**
     * 检查服务器是否已连接
     *
     * @param serverName 服务器名称
     * @return 是否已连接
     */
    public boolean isServerConnected(String serverName) {
        return serverSessions.containsKey(serverName);
    }

    /**
     * 获取连接数
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return serverSessions.size();
    }

    /**
     * 玩家加入服务器
     *
     * @param serverName 服务器名称
     * @param playerName 玩家名称
     */
    public void playerJoin(String serverName, String playerName) {
        // 如果玩家已在其他服务器，先移除
        String oldServer = playerServerMap.get(playerName);
        if (oldServer != null && !oldServer.equals(serverName)) {
            Set<String> oldPlayers = serverPlayersMap.get(oldServer);
            if (oldPlayers != null) {
                oldPlayers.remove(playerName);
            }
        }
        
        // 添加到新服务器
        playerServerMap.put(playerName, serverName);
        Set<String> players = serverPlayersMap.computeIfAbsent(serverName, k -> new CopyOnWriteArraySet<>());
        players.add(playerName);
        
        log.debug("玩家 [{}] 加入服务器 [{}]", playerName, serverName);
    }

    /**
     * 玩家离开服务器
     *
     * @param serverName 服务器名称
     * @param playerName 玩家名称
     */
    public void playerLeave(String serverName, String playerName) {
        String currentServer = playerServerMap.get(playerName);
        if (currentServer != null && currentServer.equals(serverName)) {
            playerServerMap.remove(playerName);
        }
        
        Set<String> players = serverPlayersMap.get(serverName);
        if (players != null) {
            players.remove(playerName);
        }
        
        log.debug("玩家 [{}] 离开服务器 [{}]", playerName, serverName);
    }

    /**
     * 查找玩家所在的服务器
     *
     * @param playerName 玩家名称
     * @return 服务器名称，未找到返回null
     */
    public String findPlayerServer(String playerName) {
        return playerServerMap.get(playerName);
    }

    /**
     * 获取服务器的所有在线玩家
     *
     * @param serverName 服务器名称
     * @return 玩家集合
     */
    public Set<String> getServerPlayers(String serverName) {
        return serverPlayersMap.getOrDefault(serverName, new CopyOnWriteArraySet<>());
    }

    /**
     * 获取所有在线玩家
     *
     * @return 玩家名称集合
     */
    public Set<String> getAllOnlinePlayers() {
        return playerServerMap.keySet();
    }

    /**
     * 发送消息到指定服务器
     *
     * @param serverName 目标服务器名称
     * @param message    消息内容
     * @return 是否发送成功
     */
    public boolean sendToServer(String serverName, String message) {
        WebSocketSession session = serverSessions.get(serverName);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                return true;
            } catch (IOException e) {
                log.error("发送消息到服务器 [{}] 失败", serverName, e);
            }
        }
        return false;
    }

    /**
     * 广播消息到所有服务器
     *
     * @param message 消息内容
     */
    public void broadcastToAllServers(String message) {
        serverSessions.forEach((serverName, session) -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                } catch (IOException e) {
                    log.error("广播消息到服务器 [{}] 失败", serverName, e);
                }
            }
        });
    }
}
