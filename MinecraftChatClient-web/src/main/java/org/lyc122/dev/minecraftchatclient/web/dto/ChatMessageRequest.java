package org.lyc122.dev.minecraftchatclient.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatMessageRequest {

    @NotBlank(message = "消息类型不能为空")
    private String msgType;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private String targetPlayer;
    private List<String> targetPlayers;
    private String targetGroup;
    private List<String> targetServers;
}
