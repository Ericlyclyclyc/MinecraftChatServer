package org.lyc122.dev.minecraftchatclient.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度必须在3-32个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在6-64个字符之间")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 100, message = "昵称长度不能超过100个字符")
    private String nickname;
}
