package org.lyc122.dev.minecraftchatclient.web.controller;

import lombok.RequiredArgsConstructor;
import org.lyc122.dev.minecraftchatclient.web.dto.ApiResponse;
import org.lyc122.dev.minecraftchatclient.web.websocket.RouterWebSocketClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RouterWebSocketClient routerClient;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = Map.of(
            "routerConnected", routerClient.isConnected()
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
