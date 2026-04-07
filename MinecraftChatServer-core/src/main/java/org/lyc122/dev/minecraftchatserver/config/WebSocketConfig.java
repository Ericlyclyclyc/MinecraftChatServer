package org.lyc122.dev.minecraftchatserver.config;

import org.lyc122.dev.minecraftchatserver.handler.MinecraftChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MinecraftChatWebSocketHandler minecraftChatWebSocketHandler;

    public WebSocketConfig(MinecraftChatWebSocketHandler minecraftChatWebSocketHandler) {
        this.minecraftChatWebSocketHandler = minecraftChatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(minecraftChatWebSocketHandler, "/ws/minecraft-chat")
                .setAllowedOrigins("*");
    }
}
