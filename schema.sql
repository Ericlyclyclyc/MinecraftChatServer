-- MinecraftChatServer Core 模块数据库初始化脚本
-- 适用于 MySQL 8.0+

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS minecraft_chat
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE minecraft_chat;

-- ============================================
-- 玩家群组表
-- ============================================
CREATE TABLE IF NOT EXISTS player_groups (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(64) NOT NULL COMMENT '群组名称（唯一）',
    description VARCHAR(256) DEFAULT NULL COMMENT '群组描述',
    creator VARCHAR(64) NOT NULL COMMENT '创建者（玩家名）',
    created_at BIGINT NOT NULL COMMENT '创建时间（Unix时间戳）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家群组表';

-- ============================================
-- 群组成员关联表
-- ============================================
CREATE TABLE IF NOT EXISTS group_members (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    group_id BIGINT NOT NULL COMMENT '所属群组ID',
    player_name VARCHAR(64) NOT NULL COMMENT '玩家名称',
    joined_at BIGINT NOT NULL COMMENT '加入时间（Unix时间戳）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_player (group_id, player_name),
    KEY idx_player_name (player_name),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES player_groups (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组成员关联表';
