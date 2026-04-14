package org.lyc122.dev.minecraftchatclient.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 100, message = "昵称长度不能超过100个字符")
    private String nickname;

    @Size(max = 255, message = "头像URL长度不能超过255个字符")
    private String avatar;

    private String newPassword;

    private String currentPassword;
}
