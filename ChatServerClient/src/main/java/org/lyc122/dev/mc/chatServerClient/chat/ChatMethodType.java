package org.lyc122.dev.mc.chatServerClient.chat;

/**
 * 聊天方法类型枚举
 */
public enum ChatMethodType {
    /**
     * 本地聊天 - 只在当前服务器显示
     */
    LOCAL,
    
    /**
     * 广播 - 发送到所有服务器
     */
    BROADCAST,
    
    /**
     * 单播到指定玩家
     */
    UNICAST_PLAYER,
    
    /**
     * 单播到指定服务器
     */
    UNICAST_SERVER,
    
    /**
     * 组播到多个玩家
     */
    MULTICAST_PLAYER,
    
    /**
     * 组播到多个服务器
     */
    MULTICAST_SERVER,
    
    /**
     * 组播到群组
     */
    MULTICAST_GROUP
}
