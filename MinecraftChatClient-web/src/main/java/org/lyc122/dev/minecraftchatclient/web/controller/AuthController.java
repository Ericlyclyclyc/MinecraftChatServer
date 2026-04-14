package org.lyc122.dev.minecraftchatclient.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.lyc122.dev.minecraftchatclient.web.dto.*;
import org.lyc122.dev.minecraftchatclient.web.entity.User;
import org.lyc122.dev.minecraftchatclient.web.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = userService.register(request);
            return ResponseEntity.ok(ApiResponse.success("注册成功", user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("未登录"));
        }

        return userService.findByUsername(auth.getName())
                .map(user -> ResponseEntity.ok(ApiResponse.success(user)))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error("用户不存在")));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<User>> updateCurrentUser(@RequestBody UpdateUserRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("未登录"));
        }

        User user = userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        try {
            User updated = userService.updateUser(user.getId(), request);
            return ResponseEntity.ok(ApiResponse.success("更新成功", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("未登录"));
        }

        User user = userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        userService.deleteUser(user.getId());
        return ResponseEntity.ok(ApiResponse.success("账号已删除", null));
    }
}
