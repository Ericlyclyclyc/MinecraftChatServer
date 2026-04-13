# ChatServerClient

一个支持 **Bukkit/Paper** 和 **Folia** 及其下游核心（PurpurFolia、Petal 等）的跨服聊天客户端插件。

## 特性

- 🚀 **多核心支持** - 同时兼容 Bukkit/Paper 和 Folia 核心
- 🔄 **自动重连** - WebSocket 断开时自动重连
- 💬 **多种聊天模式** - 本地、广播、私聊、组播、群组聊天
- 🏗️ **调度器适配** - 自动检测服务器类型并使用合适的调度器
- 📦 **开箱即用** - 简单的配置即可连接聊天服务器

## 支持的服务器核心

| 核心类型 | 支持状态 |
|---------|---------|
| Bukkit | ✅ 完全支持 |
| Spigot | ✅ 完全支持 |
| Paper | ✅ 完全支持 |
| Purpur | ✅ 完全支持 |
| Folia | ✅ 完全支持 |
| PurpurFolia | ✅ 完全支持 |
| Petal | ✅ 完全支持 |
| 其他 Folia 下游 | ✅ 理论上支持 |

## 安装

1. 从 [Releases](../../releases) 下载最新版本的插件
2. 将 `ChatServerClient-<version>.jar` 放入服务器的 `plugins` 文件夹
3. 启动服务器生成配置文件
4. 编辑 `plugins/ChatServerClient/config.yml` 配置聊天服务器地址
5. 重启服务器或使用 `/chatserver reconnect` 命令连接

## 配置

```yaml
# 服务器配置
server:
  name: "Server-1"  # 服务器名称，用于标识

# WebSocket 配置
websocket:
  url: "ws://localhost:8080/ws/minecraft-chat"  # 聊天服务器地址
  auto-reconnect: true                           # 断开后自动重连
  reconnect-interval: 10                         # 重连间隔（秒）
```

## 命令

### /chat - 切换聊天模式

```
/chat local              # 本地聊天（仅当前世界100格内可见）
/chat broadcast          # 广播模式（发送到所有服务器）
/chat unicast <玩家>      # 私聊模式（发送给指定玩家）
/chat multicast          # 组播模式（发送给指定玩家列表）
/chat group <群组名>      # 群组模式（发送给群组成员）
```

**别名**: `/chat l` (本地), `/chat b` (广播), `/chat u` (私聊), `/chat m` (组播), `/chat g` (群组)

### /chatserver - 管理命令

```
/chatserver reconnect    # 重新连接聊天服务器
/chatserver status       # 查看连接状态
```

## 权限

| 权限 | 描述 | 默认 |
|-----|------|-----|
| `chatserverclient.chat` | 允许使用聊天模式切换命令 | 所有玩家 |
| `chatserverclient.admin` | 允许使用管理命令 | OP |

## 消息格式

### 聊天消息类型

| 类型 | 格式示例 |
|-----|---------|
| 本地 | `[本地] 玩家名 » 消息内容` |
| 广播 | `[广播] [服务器名] 玩家名 » 消息内容` |
| 私聊 | `[私聊] [服务器名] 玩家名 » 消息内容` |
| 组播 | `[组播] [服务器名] 玩家名 » 消息内容` |
| 群组 | `[群组名] [服务器名] 玩家名 » 消息内容` |

## 技术实现

### 调度器适配器模式

插件使用适配器模式自动检测服务器类型：

```
SchedulerAdapter (接口)
├── BukkitSchedulerAdapter (Bukkit/Paper 实现)
└── FoliaSchedulerAdapter (Folia 实现)
```

自动检测逻辑：
```java
if (Class.forName("io.papermc.paper.threadedregions.RegionizedServer") != null) {
    // 使用 Folia 调度器
} else {
    // 使用 Bukkit 调度器
}
```

### WebSocket 消息格式

```json
{
  "type": "MESSAGE",
  "msgType": "BROADCAST",
  "senderType": "PLAYER",
  "sourceServer": "Server-1",
  "sender": "PlayerName",
  "content": "Hello World!",
  "timestamp": 1700000000
}
```

## 依赖

- **Java 21+**
- **Paper API 1.21+** (或兼容的 Bukkit 实现)
- **MinecraftChatServer-core** (聊天服务器)

## 构建

```bash
# 克隆项目
git clone <repository-url>
cd MinecraftChatServer

# 构建 ChatServerClient 模块
mvn clean package -pl ChatServerClient

# 输出文件
# ChatServerClient/target/ChatServerClient-<version>.jar
```

## 故障排除

### 无法连接到聊天服务器

1. 检查 `config.yml` 中的 `websocket.url` 配置是否正确
2. 确认聊天服务器已启动并在监听指定端口
3. 检查防火墙设置是否允许连接

### Folia 核心报错

确保使用的是支持 Folia 的插件版本（v0.0.1-SNAPSHOT 及以上）。

### 消息发送失败

1. 使用 `/chatserver status` 检查连接状态
2. 使用 `/chatserver reconnect` 尝试重新连接
3. 检查服务器日志中的错误信息

## 许可证

[MIT License](../../LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request！

## 更新日志

### 0.0.1-SNAPSHOT
- ✨ 初始版本
- 🚀 支持 Bukkit/Paper 和 Folia 核心
- 💬 实现多种聊天模式
- 🔄 自动重连功能
