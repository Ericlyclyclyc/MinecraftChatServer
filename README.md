# MinecraftChatServer

Minecraft 跨服聊天消息交换服务器，支持多服务器互联、精准路由、玩家群组和多级路由器架构。

## 项目概述

MinecraftChatServer 是一个基于 Spring Boot 的 WebSocket 消息转发服务器，作为**一级消息交换机**连接多个 Minecraft 服务器（二级交换机），实现跨服聊天互通。支持单播、组播、广播等多种消息路由模式，以及多路由器互联的分布式架构。

## 架构设计

### 单级架构（基础模式）

```
┌─────────────────────────────────────────────────────────────┐
│                    一级消息交换机 (本服务器)                    │
│                     Message Exchange Server                   │
│                                                              │
│   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐ │
│   │  路由    │    │  路由    │    │  路由    │    │  路由    │ │
│   │  引擎    │    │  引擎    │    │  引擎    │    │  引擎    │ │
│   └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘ │
│        │              │              │              │       │
└────────┼──────────────┼──────────────┼──────────────┼───────┘
         │              │              │              │
    WebSocket      WebSocket      WebSocket      WebSocket
         │              │              │              │
    ┌────┴────┐    ┌────┴────┐    ┌────┴────┐    ┌────┴────┐
    │ 生存服1 │    │ 生存服2 │    │ 创造服  │    │ 小游戏服│
    │(二级交换)│    │(二级交换)│    │(二级交换)│    │(二级交换)│
    └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘
         │              │              │              │
      玩家A,B        玩家C,D         玩家E,F         玩家G,H
```

### 多级架构（多路由器互联模式）

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         多路由器互联网络                                   │
│                                                                         │
│   ┌─────────────────┐         WebSocket          ┌─────────────────┐   │
│   │   路由器 A      │◄──────────────────────────►│   路由器 B      │   │
│   │  (本服务器)     │    路由表交换/消息转发       │  (远程服务器)    │   │
│   │                 │                            │                 │   │
│   │  ┌───────────┐  │                            │  ┌───────────┐  │   │
│   │  │ 路由表服务 │  │                            │  │ 路由表服务 │  │   │
│   │  │ - 最短路径 │  │                            │  │ - 最短路径 │  │   │
│   │  │ - 环路避免 │  │                            │  │ - 环路避免 │  │   │
│   │  └───────────┘  │                            │  └───────────┘  │   │
│   └───────┬─────────┘                            └────────┬────────┘   │
│           │                                               │            │
│    WebSocket│                                        WebSocket         │
│           │                                               │            │
│   ┌───────┴───────┐                            ┌────────┴────────┐   │
│   │  本地服务器    │                            │   远程服务器     │   │
│   │  - 生存服1    │                            │   - 生存服3     │   │
│   │  - 创造服     │                            │   - 小游戏服    │   │
│   └───────────────┘                            └─────────────────┘   │
│                                                                         │
│   路由协议: Bellman-Ford + TTL环路避免                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

**职责划分：**
- **一级交换机**：维护玩家-服务器映射，精准路由消息，不管理业务逻辑
- **二级服务器**：管理玩家状态、处理业务逻辑、转发消息给玩家
- **路由器互联**：多服务器集群间路由表交换、消息跨集群转发、环路避免

## 功能特性

### 核心功能

- **精准路由** - 基于玩家-服务器映射表，只发送消息到目标玩家所在服务器
- **多种消息类型**
  - `UNICAST_SERVER` - 单播到指定服务器
  - `UNICAST_PLAYER` - 单播到指定玩家
  - `MULTICAST_SERVER` - 组播到指定服务器列表
  - `MULTICAST_PLAYER` - 组播到指定玩家列表
  - `MULTICAST_GROUP` - 组播到指定群组
  - `BROADCAST` - 广播到所有服务器
- **玩家群组** - 支持创建群组、邀请成员、群组聊天
- **多路由器互联** - 支持多服务器集群互联，自动路由表交换
- **环路避免** - TTL递减、访问列表、消息ID去重三重保障

### 消息路由示例

**MULTICAST_PLAYER 精准路由优势**：
- 目标玩家：Alex（生存服2）、Bob（创造服）、Charlie（生存服2）、David（小游戏服）
- 传统方式：广播到所有4个服务器
- **精准路由**：只发送到3个服务器（生存服2、创造服、小游戏服）
- **节省流量**：25%

## 技术栈

- **后端框架**: Spring Boot 4.0.5
- **编程语言**: Java 21
- **通信协议**: WebSocket
- **构建工具**: Maven
- **数据格式**: JSON (Jackson)
- **数据库**: MySQL (生产环境) / H2 (测试环境)
- **简化代码**: Lombok

## 项目结构

```
MinecraftChatServer
├── MinecraftChatServer-core    # 核心服务器模块
│   ├── src/main/java
│   │   └── org/lyc122/dev/minecraftchatserver
│   │       ├── config/         # 配置类
│   │       ├── controller/     # REST API 控制器
│   │       ├── dto/            # 数据传输对象
│   │       ├── handler/        # WebSocket 处理器
│   │       ├── model/          # 实体模型
│   │       ├── repository/     # 数据访问层
│   │       └── service/        # 业务逻辑层
│   └── pom.xml
├── ChatServerClient            # Minecraft Paper 插件客户端
│   ├── src/main/java
│   │   ├── ChatMethods/        # 聊天方法实现
│   │   └── org/lyc122/dev/mc/chatServerClient
│   │       ├── chat/           # 聊天类型定义
│   │       ├── listener/       # 事件监听器
│   │       ├── message/        # 消息处理器
│   │       ├── scheduler/      # 调度器适配器
│   │       └── websocket/      # WebSocket 管理
│   └── pom.xml
└── pom.xml                     # 父 POM
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+ (可选，用于生产环境)

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd MinecraftChatServer

# 构建所有模块
mvn clean package

# 构建核心服务器
mvn clean package -pl MinecraftChatServer-core

# 构建 Paper 插件
mvn clean package -pl ChatServerClient
```

### 运行核心服务器

```bash
cd MinecraftChatServer-core
java -jar target/MinecraftChatServer-core-1.0-beta.jar
```

首次启动会自动在 `config/` 目录下生成配置文件。

### 配置服务器

编辑 `config/application.properties`：

```properties
# 服务器端口
server.port=8080

# MySQL 配置（可选）
spring.datasource.url=jdbc:mysql://localhost:3306/minecraft_chat
spring.datasource.username=root
spring.datasource.password=your_password
```

### 安装 Paper 插件

1. 将 `ChatServerClient/target/ChatServerClient-1.0-beta.jar` 放入服务器的 `plugins` 文件夹
2. 启动服务器生成配置文件
3. 编辑 `plugins/ChatServerClient/config.yml` 配置聊天服务器地址
4. 重启服务器或使用 `/chatserver reconnect` 命令连接

## 连接信息

### Minecraft 服务器连接

| 项目 | 值 |
|------|-----|
| WebSocket URL | `ws://host:8080/ws/minecraft-chat` |
| 连接参数 | `serverName` (必填) - 服务器唯一名称 |
| 示例 | `ws://localhost:8080/ws/minecraft-chat?serverName=生存服1` |

### 路由器互联连接

| 项目 | 值 |
|------|-----|
| WebSocket URL | `ws://host:8080/ws/router` |
| 用途 | 路由器之间的互联，交换路由表和转发消息 |

## API 文档

详细的 API 文档请参考 [API.md](API.md)，包含：

- 完整的包结构说明
- 消息类型和操作类型定义
- 路由规则详解
- 群组管理 REST API
- 路由器互联协议
- 错误处理说明

## 模块说明

### MinecraftChatServer-core

核心消息交换服务器，负责：
- WebSocket 连接管理
- 消息路由和转发
- 玩家-服务器映射维护
- 群组管理和持久化
- 路由器互联和路由表交换

### ChatServerClient

Minecraft Paper 插件客户端，支持 Bukkit/Paper 和 Folia 核心：
- 自动重连机制
- 多种聊天模式切换
- 调度器适配器（自动检测服务器类型）
- 玩家事件同步（加入/离开）

## 版本信息

- **当前版本**: v1.0-beta
- **Java 版本**: 21
- **Spring Boot**: 4.0.5
- **Paper API**: 1.21.4-R0.1-SNAPSHOT

## 许可证

[MIT License](LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request！
