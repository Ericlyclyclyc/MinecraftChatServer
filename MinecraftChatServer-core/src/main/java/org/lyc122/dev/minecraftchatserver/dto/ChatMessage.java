package org.lyc122.dev.minecraftchatserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    /**
     * 包类型：MESSAGE(消息) 或 OPERATION(操作)
     */
    private String type;

    /**
     * 消息类型（当 type=MESSAGE 时使用）
     * UNICAST_SERVER, UNICAST_PLAYER, MULTICAST_SERVER, MULTICAST_PLAYER, BROADCAST, MULTICAST_GROUP
     */
    private MsgType msgType;

    /**
     * 操作类型（当 type=OPERATION 时使用）
     * PLAYER_JOIN, PLAYER_LEAVE, SERVER_CONNECT, SERVER_DISCONNECT,
     * GROUP_CREATE, GROUP_DELETE, GROUP_JOIN, GROUP_LEAVE
     */
    private OpType opType;

    /**
     * 发送者类型
     */
    private SenderType senderType;

    /**
     * 发送者服务器名称
     */
    private String sourceServer;

    /**
     * 发送者标识（玩家名或系统标识）
     */
    private String sender;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 目标服务器（UNICAST_SERVER时使用）
     */
    private String targetServer;

    /**
     * 目标服务器列表（MULTICAST_SERVER时使用）
     */
    private List<String> targetServers;

    /**
     * 目标玩家（UNICAST_PLAYER时使用）
     */
    private String targetPlayer;

    /**
     * 目标玩家列表（MULTICAST_PLAYER时使用）
     */
    private List<String> targetPlayers;

    /**
     * 目标群组名称（MULTICAST_GROUP或群组操作时使用）
     */
    private String targetGroup;

    /**
     * 群组描述（GROUP_CREATE时使用）
     */
    private String groupDescription;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 附加数据（扩展用）
     */
    private Object extra;

    /**
     * 包类型常量
     */
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_OPERATION = "OPERATION";

    /**
     * 消息类型（用于聊天消息路由）
     */
    public enum MsgType {
        /**
         * 单播到指定服务器（发给该服务器的所有玩家）
         */
        UNICAST_SERVER,
        /**
         * 单播到指定玩家（需要查找玩家所在服务器）
         */
        UNICAST_PLAYER,
        /**
         * 组播到指定服务器列表
         */
        MULTICAST_SERVER,
        /**
         * 组播到指定玩家列表（需要查找每个玩家所在服务器）
         */
        MULTICAST_PLAYER,
        /**
         * 广播到所有服务器的所有玩家
         */
        BROADCAST,
        /**
         * 组播到指定群组（发送给群组内所有玩家）
         */
        MULTICAST_GROUP
    }

    /**
     * 操作类型（用于系统和群组操作）
     */
    public enum OpType {
        /**
         * 玩家加入服务器
         */
        PLAYER_JOIN,
        /**
         * 玩家离开服务器
         */
        PLAYER_LEAVE,
        /**
         * 服务器连接成功
         */
        SERVER_CONNECT,
        /**
         * 服务器断开
         */
        SERVER_DISCONNECT,
        /**
         * 群组创建
         */
        GROUP_CREATE,
        /**
         * 群组删除
         */
        GROUP_DELETE,
        /**
         * 加入群组
         */
        GROUP_JOIN,
        /**
         * 离开群组
         */
        GROUP_LEAVE
    }

    public enum SenderType {
        /**
         * 玩家发送
         */
        PLAYER,
        /**
         * 服务器发送
         */
        SERVER,
        /**
         * 系统发送
         */
        SYSTEM
    }

    // ==================== 消息类型工厂方法 ====================

    public static ChatMessage createUnicastPlayer(String sourceServer, String sender, SenderType senderType,
                                                   String targetPlayer, String content) {
        return ChatMessage.builder()
                .type(TYPE_MESSAGE)
                .msgType(MsgType.UNICAST_PLAYER)
                .senderType(senderType)
                .sourceServer(sourceServer)
                .sender(sender)
                .targetPlayer(targetPlayer)
                .content(content)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createUnicastServer(String sourceServer, String sender, SenderType senderType,
                                                   String targetServer, String content) {
        return ChatMessage.builder()
                .type(TYPE_MESSAGE)
                .msgType(MsgType.UNICAST_SERVER)
                .senderType(senderType)
                .sourceServer(sourceServer)
                .sender(sender)
                .targetServer(targetServer)
                .content(content)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createMulticastServer(String sourceServer, String sender, SenderType senderType,
                                                     List<String> targetServers, String content) {
        return ChatMessage.builder()
                .type(TYPE_MESSAGE)
                .msgType(MsgType.MULTICAST_SERVER)
                .senderType(senderType)
                .sourceServer(sourceServer)
                .sender(sender)
                .targetServers(targetServers)
                .content(content)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createMulticastPlayer(String sourceServer, String sender, SenderType senderType,
                                                     List<String> targetPlayers, String content) {
        return ChatMessage.builder()
                .type(TYPE_MESSAGE)
                .msgType(MsgType.MULTICAST_PLAYER)
                .senderType(senderType)
                .sourceServer(sourceServer)
                .sender(sender)
                .targetPlayers(targetPlayers)
                .content(content)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createBroadcast(String sourceServer, String sender, SenderType senderType,
                                               String content) {
        return ChatMessage.builder()
                .type(TYPE_MESSAGE)
                .msgType(MsgType.BROADCAST)
                .senderType(senderType)
                .sourceServer(sourceServer)
                .sender(sender)
                .content(content)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createSystemBroadcast(String sender, String content) {
        return ChatMessage.builder()
                .type(TYPE_MESSAGE)
                .msgType(MsgType.BROADCAST)
                .senderType(SenderType.SYSTEM)
                .sender(sender)
                .content(content)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createMulticastGroup(String sourceServer, String sender, SenderType senderType,
                                                    String targetGroup, String content) {
        return ChatMessage.builder()
                .type(TYPE_MESSAGE)
                .msgType(MsgType.MULTICAST_GROUP)
                .senderType(senderType)
                .sourceServer(sourceServer)
                .sender(sender)
                .targetGroup(targetGroup)
                .content(content)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    // ==================== 操作类型工厂方法 ====================

    public static ChatMessage createPlayerJoin(String sourceServer, String playerName) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.PLAYER_JOIN)
                .senderType(SenderType.SYSTEM)
                .sourceServer(sourceServer)
                .sender(playerName)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createPlayerLeave(String sourceServer, String playerName) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.PLAYER_LEAVE)
                .senderType(SenderType.SYSTEM)
                .sourceServer(sourceServer)
                .sender(playerName)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createServerConnect(String serverName) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.SERVER_CONNECT)
                .senderType(SenderType.SYSTEM)
                .sourceServer(serverName)
                .sender("SYSTEM")
                .content("Connected to message exchange")
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createServerDisconnect(String serverName) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.SERVER_DISCONNECT)
                .senderType(SenderType.SYSTEM)
                .sourceServer(serverName)
                .sender("SYSTEM")
                .content("Server disconnected")
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createGroupCreate(String sourceServer, String creator, String groupName, String description) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.GROUP_CREATE)
                .senderType(SenderType.PLAYER)
                .sourceServer(sourceServer)
                .sender(creator)
                .targetGroup(groupName)
                .groupDescription(description)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createGroupDelete(String sourceServer, String requester, String groupName) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.GROUP_DELETE)
                .senderType(SenderType.PLAYER)
                .sourceServer(sourceServer)
                .sender(requester)
                .targetGroup(groupName)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createGroupJoin(String sourceServer, String playerName, String groupName) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.GROUP_JOIN)
                .senderType(SenderType.PLAYER)
                .sourceServer(sourceServer)
                .sender(playerName)
                .targetGroup(groupName)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    public static ChatMessage createGroupLeave(String sourceServer, String playerName, String groupName) {
        return ChatMessage.builder()
                .type(TYPE_OPERATION)
                .opType(OpType.GROUP_LEAVE)
                .senderType(SenderType.PLAYER)
                .sourceServer(sourceServer)
                .sender(playerName)
                .targetGroup(groupName)
                .timestamp(Instant.now().getEpochSecond())
                .build();
    }

    // ==================== 便捷判断方法 ====================

    public boolean isMessage() {
        return TYPE_MESSAGE.equals(type);
    }

    public boolean isOperation() {
        return TYPE_OPERATION.equals(type);
    }
}
