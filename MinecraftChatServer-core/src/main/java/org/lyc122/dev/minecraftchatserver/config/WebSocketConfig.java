package org.lyc122.dev.minecraftchatserver.config;

import org.lyc122.dev.minecraftchatserver.handler.MinecraftChatWebSocketHandler;
import org.lyc122.dev.minecraftchatserver.handler.RouterWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MinecraftChatWebSocketHandler minecraftChatWebSocketHandler;
    private final RouterWebSocketHandler routerWebSocketHandler;

    public WebSocketConfig(MinecraftChatWebSocketHandler minecraftChatWebSocketHandler,
                          RouterWebSocketHandler routerWebSocketHandler) {
        this.minecraftChatWebSocketHandler = minecraftChatWebSocketHandler;
        this.routerWebSocketHandler = routerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Minecraft 服务器连接端点
        registry.addHandler(minecraftChatWebSocketHandler, "/ws/minecraft-chat")
                .setAllowedOrigins("*");
        
        // 路由器互联端点
        registry.addHandler(routerWebSocketHandler, "/ws/router")
                .setAllowedOrigins("*");
    }
}
