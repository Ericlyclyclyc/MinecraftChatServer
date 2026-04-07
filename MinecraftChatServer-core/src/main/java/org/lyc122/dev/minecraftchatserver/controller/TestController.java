package org.lyc122.dev.minecraftchatserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatserver.model.PlayerGroup;
import org.lyc122.dev.minecraftchatserver.service.PlayerGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试接口 - 用于验证 MySQL 连接和功能
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final DataSource dataSource;
    private final PlayerGroupService groupService;

    /**
     * 测试数据库连接
     */
    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> testDatabaseConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            result.put("success", true);
            result.put("databaseUrl", metaData.getURL());
            result.put("databaseProductName", metaData.getDatabaseProductName());
            result.put("databaseProductVersion", metaData.getDatabaseProductVersion());
            result.put("driverName", metaData.getDriverName());
            result.put("driverVersion", metaData.getDriverVersion());
            
            log.info("数据库连接测试成功: {}", metaData.getURL());
            
        } catch (SQLException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            log.error("数据库连接测试失败", e);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 创建测试群组
     */
    @PostMapping("/group")
    public ResponseEntity<Map<String, Object>> createTestGroup(
            @RequestParam String groupName,
            @RequestParam String creator) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            PlayerGroup group = groupService.createGroup(groupName, "测试群组", creator);
            result.put("success", true);
            result.put("group", convertToMap(group));
            log.info("测试群组创建成功: {}", groupName);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            log.error("测试群组创建失败", e);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有群组
     */
    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> getAllGroups() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<PlayerGroup> groups = groupService.getAllGroups();
            List<Map<String, Object>> groupList = groups.stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());
            
            result.put("success", true);
            result.put("count", groupList.size());
            result.put("groups", groupList);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 加入群组
     */
    @PostMapping("/group/{groupName}/join")
    public ResponseEntity<Map<String, Object>> joinGroup(
            @PathVariable String groupName,
            @RequestParam String playerName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            groupService.joinGroup(groupName, playerName);
            result.put("success", true);
            result.put("message", playerName + " 成功加入群组 " + groupName);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取群组成员
     */
    @GetMapping("/group/{groupName}/members")
    public ResponseEntity<Map<String, Object>> getGroupMembers(@PathVariable String groupName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            var members = groupService.getGroupMembers(groupName);
            result.put("success", true);
            result.put("groupName", groupName);
            result.put("members", members);
            result.put("memberCount", members.size());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> convertToMap(PlayerGroup group) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getName());
        map.put("description", group.getDescription());
        map.put("creator", group.getCreator());
        map.put("createdAt", group.getCreatedAt());
        map.put("memberCount", group.getMemberCount());
        map.put("members", group.getMemberNames());
        return map;
    }
}
