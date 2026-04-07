package org.lyc122.dev.minecraftchatserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatserver.dto.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MessageRoutingService {

    private final ServerSessionService serverSessionService;
    private final PlayerGroupService playerGroupService;
    private final ObjectMapper objectMapper;

    public MessageRoutingService(ServerSessionService serverSessionService, PlayerGroupService playerGroupService) {
        this.serverSessionService = serverSessionService;
        this.playerGroupService = playerGroupService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 路由消息
     *
     * @param sourceServer 源服务器名称（WebSocket连接时声明的serverName）
     * @param message      消息对象
     */
    public void routeMessage(String sourceServer, ChatMessage message) {
        if (message.getType() == null) {
            log.warn("收到未知类型的消息，忽略处理");
            return;
        }

        // 设置源服务器（如果消息中未指定）
        if (message.getSourceServer() == null) {
            message.setSourceServer(sourceServer);
        }

        // 根据包类型分发处理
        if (message.isMessage()) {
            routeChatMessage(sourceServer, message);
        } else if (message.isOperation()) {
            handleOperation(sourceServer, message);
        } else {
            log.warn("未知的包类型: {}", message.getType());
        }
    }

    /**
     * 路由聊天消息
     */
    private void routeChatMessage(String sourceServer, ChatMessage message) {
        if (message.getMsgType() == null) {
            log.warn("收到未知消息类型的聊天消息，忽略处理");
            return;
        }

        switch (message.getMsgType()) {
            case UNICAST_SERVER -> handleUnicastServer(sourceServer, message);
            case UNICAST_PLAYER -> handleUnicastPlayer(sourceServer, message);
            case MULTICAST_SERVER -> handleMulticastServer(sourceServer, message);
            case MULTICAST_PLAYER -> handleMulticastPlayer(sourceServer, message);
            case MULTICAST_GROUP -> handleMulticastGroup(sourceServer, message);
            case BROADCAST -> handleBroadcast(sourceServer, message);
            default -> log.debug("消息类型 [{}] 无需路由处理", message.getMsgType());
        }
    }

    /**
     * 处理操作消息
     */
    private void handleOperation(String sourceServer, ChatMessage message) {
        if (message.getOpType() == null) {
            log.warn("收到未知操作类型的操作消息，忽略处理");
            return;
        }

        switch (message.getOpType()) {
            case PLAYER_JOIN -> handlePlayerJoin(sourceServer, message);
            case PLAYER_LEAVE -> handlePlayerLeave(sourceServer, message);
            case GROUP_CREATE -> handleGroupCreate(sourceServer, message);
            case GROUP_DELETE -> handleGroupDelete(sourceServer, message);
            case GROUP_JOIN -> handleGroupJoin(sourceServer, message);
            case GROUP_LEAVE -> handleGroupLeave(sourceServer, message);
            default -> log.debug("操作类型 [{}] 无需处理", message.getOpType());
        }
    }

    // ==================== 聊天消息处理 ====================

    /**
     * 处理单播到指定服务器
     */
    private void handleUnicastServer(String sourceServer, ChatMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            String targetServer = message.getTargetServer();

            if (targetServer == null || targetServer.isBlank()) {
                log.warn("UNICAST_SERVER 未指定目标服务器，丢弃消息");
                return;
            }

            // 丢弃同服务器消息
            if (targetServer.equals(sourceServer)) {
                log.debug("UNICAST_SERVER 目标与源相同 [{}]，丢弃", sourceServer);
                return;
            }

            boolean sent = serverSessionService.sendToServer(targetServer, jsonMessage);
            if (sent) {
                log.debug("UNICAST_SERVER 从 [{}] 转发到 [{}]", sourceServer, targetServer);
            } else {
                log.warn("UNICAST_SERVER 发送到 [{}] 失败", targetServer);
            }
        } catch (Exception e) {
            log.error("处理 UNICAST_SERVER 失败", e);
        }
    }

    /**
     * 处理单播到指定玩家
     */
    private void handleUnicastPlayer(String sourceServer, ChatMessage message) {
        try {
            String targetPlayer = message.getTargetPlayer();
            if (targetPlayer == null || targetPlayer.isBlank()) {
                log.warn("UNICAST_PLAYER 未指定目标玩家，丢弃消息");
                return;
            }

            String targetServer = serverSessionService.findPlayerServer(targetPlayer);
            if (targetServer == null) {
                log.warn("UNICAST_PLAYER 未找到玩家 [{}] 所在服务器", targetPlayer);
                return;
            }

            // 丢弃同服务器消息
            if (targetServer.equals(sourceServer)) {
                log.debug("UNICAST_PLAYER 目标与源相同 [{}]，丢弃", sourceServer);
                return;
            }

            String jsonMessage = objectMapper.writeValueAsString(message);
            boolean sent = serverSessionService.sendToServer(targetServer, jsonMessage);
            if (sent) {
                log.debug("UNICAST_PLAYER 从 [{}] 转发到 [{}] 的玩家 [{}]", sourceServer, targetServer, targetPlayer);
            } else {
                log.warn("UNICAST_PLAYER 发送到 [{}] 失败", targetServer);
            }
        } catch (Exception e) {
            log.error("处理 UNICAST_PLAYER 失败", e);
        }
    }

    /**
     * 处理组播到指定服务器列表
     */
    private void handleMulticastServer(String sourceServer, ChatMessage message) {
        try {
            List<String> targetServers = message.getTargetServers();
            if (targetServers == null || targetServers.isEmpty()) {
                log.warn("MULTICAST_SERVER 未指定目标服务器列表，丢弃消息");
                return;
            }

            String jsonMessage = objectMapper.writeValueAsString(message);
            int sentCount = 0;

            for (String targetServer : targetServers) {
                if (targetServer.equals(sourceServer)) {
                    continue;
                }
                if (serverSessionService.sendToServer(targetServer, jsonMessage)) {
                    sentCount++;
                }
            }

            log.debug("MULTICAST_SERVER 从 [{}] 转发到 {} 个服务器", sourceServer, sentCount);
        } catch (Exception e) {
            log.error("处理 MULTICAST_SERVER 失败", e);
        }
    }

    /**
     * 处理组播到指定玩家列表
     */
    private void handleMulticastPlayer(String sourceServer, ChatMessage message) {
        try {
            List<String> targetPlayers = message.getTargetPlayers();
            if (targetPlayers == null || targetPlayers.isEmpty()) {
                log.warn("MULTICAST_PLAYER 未指定目标玩家列表，丢弃消息");
                return;
            }

            Set<String> targetServers = new HashSet<>();
            for (String player : targetPlayers) {
                String server = serverSessionService.findPlayerServer(player);
                if (server != null && !server.equals(sourceServer)) {
                    targetServers.add(server);
                }
            }

            if (targetServers.isEmpty()) {
                log.debug("MULTICAST_PLAYER 没有有效的目标服务器");
                return;
            }

            String jsonMessage = objectMapper.writeValueAsString(message);
            int sentCount = 0;
            for (String targetServer : targetServers) {
                if (serverSessionService.sendToServer(targetServer, jsonMessage)) {
                    sentCount++;
                }
            }

            log.debug("MULTICAST_PLAYER 从 [{}] 转发到 {} 个服务器（覆盖 {} 个玩家）",
                    sourceServer, sentCount, targetPlayers.size());
        } catch (Exception e) {
            log.error("处理 MULTICAST_PLAYER 失败", e);
        }
    }

    /**
     * 处理群组消息组播
     */
    private void handleMulticastGroup(String sourceServer, ChatMessage message) {
        try {
            String targetGroup = message.getTargetGroup();
            if (targetGroup == null || targetGroup.isBlank()) {
                log.warn("MULTICAST_GROUP 未指定目标群组，丢弃消息");
                return;
            }

            Set<String> members = playerGroupService.getGroupMembers(targetGroup);
            if (members.isEmpty()) {
                log.debug("群组 [{}] 没有成员", targetGroup);
                return;
            }

            Set<String> targetServers = new HashSet<>();
            for (String player : members) {
                String server = serverSessionService.findPlayerServer(player);
                if (server != null && !server.equals(sourceServer)) {
                    targetServers.add(server);
                }
            }

            if (targetServers.isEmpty()) {
                log.debug("MULTICAST_GROUP 群组 [{}] 成员没有有效的目标服务器", targetGroup);
                return;
            }

            String jsonMessage = objectMapper.writeValueAsString(message);
            int sentCount = 0;
            for (String targetServer : targetServers) {
                if (serverSessionService.sendToServer(targetServer, jsonMessage)) {
                    sentCount++;
                }
            }

            log.debug("MULTICAST_GROUP 从 [{}] 转发到 {} 个服务器（群组 [{}] 共 {} 个成员）",
                    sourceServer, sentCount, targetGroup, members.size());
        } catch (Exception e) {
            log.error("处理 MULTICAST_GROUP 失败", e);
        }
    }

    /**
     * 处理广播消息
     */
    private void handleBroadcast(String sourceServer, ChatMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);

            int sentCount = 0;
            for (String targetServer : serverSessionService.getAllServerNames()) {
                if (!targetServer.equals(sourceServer)) {
                    if (serverSessionService.sendToServer(targetServer, jsonMessage)) {
                        sentCount++;
                    }
                }
            }

            log.debug("BROADCAST 从 [{}] 转发到 {} 个服务器", sourceServer, sentCount);
        } catch (Exception e) {
            log.error("处理 BROADCAST 失败", e);
        }
    }

    // ==================== 操作处理 ====================

    /**
     * 处理玩家加入
     */
    private void handlePlayerJoin(String sourceServer, ChatMessage message) {
        String playerName = message.getSender();
        if (playerName != null && !playerName.isBlank()) {
            serverSessionService.playerJoin(sourceServer, playerName);
            log.info("玩家 [{}] 加入服务器 [{}]", playerName, sourceServer);
        }
    }

    /**
     * 处理玩家离开
     */
    private void handlePlayerLeave(String sourceServer, ChatMessage message) {
        String playerName = message.getSender();
        if (playerName != null && !playerName.isBlank()) {
            serverSessionService.playerLeave(sourceServer, playerName);
            log.info("玩家 [{}] 离开服务器 [{}]", playerName, sourceServer);
        }
    }

    /**
     * 处理群组创建
     */
    private void handleGroupCreate(String sourceServer, ChatMessage message) {
        try {
            String creator = message.getSender();
            String groupName = message.getTargetGroup();
            String description = message.getGroupDescription();

            if (groupName == null || creator == null) {
                log.warn("GROUP_CREATE 缺少必要参数");
                return;
            }

            playerGroupService.createGroup(groupName, description != null ? description : "", creator);
            log.info("玩家 [{}] 创建群组 [{}]", creator, groupName);
        } catch (Exception e) {
            log.error("处理 GROUP_CREATE 失败: {}", e.getMessage());
        }
    }

    /**
     * 处理群组删除
     */
    private void handleGroupDelete(String sourceServer, ChatMessage message) {
        try {
            String requester = message.getSender();
            String groupName = message.getTargetGroup();

            if (groupName == null || requester == null) {
                log.warn("GROUP_DELETE 缺少必要参数");
                return;
            }

            playerGroupService.deleteGroup(groupName, requester);
            log.info("玩家 [{}] 删除群组 [{}]", requester, groupName);
        } catch (Exception e) {
            log.error("处理 GROUP_DELETE 失败: {}", e.getMessage());
        }
    }

    /**
     * 处理加入群组
     */
    private void handleGroupJoin(String sourceServer, ChatMessage message) {
        try {
            String playerName = message.getSender();
            String groupName = message.getTargetGroup();

            if (groupName == null || playerName == null) {
                log.warn("GROUP_JOIN 缺少必要参数");
                return;
            }

            playerGroupService.joinGroup(groupName, playerName);
            log.info("玩家 [{}] 加入群组 [{}]", playerName, groupName);
        } catch (Exception e) {
            log.error("处理 GROUP_JOIN 失败: {}", e.getMessage());
        }
    }

    /**
     * 处理离开群组
     */
    private void handleGroupLeave(String sourceServer, ChatMessage message) {
        try {
            String playerName = message.getSender();
            String groupName = message.getTargetGroup();

            if (groupName == null || playerName == null) {
                log.warn("GROUP_LEAVE 缺少必要参数");
                return;
            }

            playerGroupService.leaveGroup(groupName, playerName);
            log.info("玩家 [{}] 离开群组 [{}]", playerName, groupName);
        } catch (Exception e) {
            log.error("处理 GROUP_LEAVE 失败: {}", e.getMessage());
        }
    }

    /**
     * 发送系统广播消息到所有服务器
     */
    public void sendSystemBroadcast(String sender, String content) {
        try {
            ChatMessage message = ChatMessage.builder()
                    .type(ChatMessage.TYPE_MESSAGE)
                    .msgType(ChatMessage.MsgType.BROADCAST)
                    .senderType(ChatMessage.SenderType.SYSTEM)
                    .sender(sender)
                    .content(content)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .build();
            String jsonMessage = objectMapper.writeValueAsString(message);

            int sentCount = 0;
            for (String targetServer : serverSessionService.getAllServerNames()) {
                if (serverSessionService.sendToServer(targetServer, jsonMessage)) {
                    sentCount++;
                }
            }

            log.debug("系统广播 [{}] 发送到 {} 个服务器", sender, sentCount);
        } catch (Exception e) {
            log.error("发送系统广播失败", e);
        }
    }
}
