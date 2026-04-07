# Minecraft Message Exchange Server API 文档

## 架构说明

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

---

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
| 协议 | 见下方「路由器互联协议」章节 |

---

## 包结构说明

所有 WebSocket 通信使用统一的包结构，通过 `type` 字段区分包类型：

| 包类型 | 说明 | 使用场景 |
|--------|------|----------|
| `MESSAGE` | 聊天消息 | 玩家聊天、广播、私聊等 |
| `OPERATION` | 操作指令 | 玩家加入/离开、群组管理等 |

---

## 包结构

```json
{
  "type": "MESSAGE",
  "msgType": "UNICAST_PLAYER",
  "opType": null,
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "content": "你好！",
  "targetServer": "生存服2",
  "targetServers": ["生存服2", "创造服"],
  "targetPlayer": "Alex",
  "targetPlayers": ["Alex", "Bob", "Charlie"],
  "targetGroup": "挖矿小队",
  "groupDescription": "一起挖矿",
  "timestamp": 1712390400,
  "extra": null
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | 是 | 包类型: `MESSAGE` 或 `OPERATION` |
| `msgType` | enum | 条件 | 消息类型（type=MESSAGE时使用） |
| `opType` | enum | 条件 | 操作类型（type=OPERATION时使用） |
| `senderType` | enum | 是 | `PLAYER`/`SERVER`/`SYSTEM` |
| `sourceServer` | string | 否 | 源服务器（自动填充） |
| `sender` | string | 是 | 发送者标识 |
| `content` | string | 条件 | 消息内容 |
| `targetServer` | string | 条件 | UNICAST_SERVER目标 |
| `targetServers` | array | 条件 | MULTICAST_SERVER目标列表 |
| `targetPlayer` | string | 条件 | UNICAST_PLAYER目标 |
| `targetPlayers` | array | 条件 | MULTICAST_PLAYER目标列表 |
| `targetGroup` | string | 条件 | MULTICAST_GROUP目标群组/群组操作目标 |
| `groupDescription` | string | 条件 | GROUP_CREATE时群组描述 |
| `timestamp` | long | 否 | 时间戳（秒） |
| `extra` | any | 否 | 扩展数据 |

---

## 消息类型 (msgType)

当 `type` = `MESSAGE` 时使用：

| 类型 | 说明 | 路由行为 |
|------|------|----------|
| `UNICAST_SERVER` | 单播到指定服务器 | 发送到 `targetServer`，该服务器转发给所有玩家 |
| `UNICAST_PLAYER` | 单播到指定玩家 | 查找玩家所在服务器，只发送到该服务器 |
| `MULTICAST_SERVER` | 组播到指定服务器列表 | 发送到 `targetServers` 列表中的每个服务器 |
| `MULTICAST_PLAYER` | 组播到指定玩家列表 | 查找每个玩家所在服务器，按服务器分组后发送 |
| `BROADCAST` | 广播到所有服务器 | 发送到所有**其他**服务器（排除源服务器） |
| `MULTICAST_GROUP` | 组播到指定群组 | 查找群组内所有成员所在服务器，按服务器分组后发送 |

---

## 操作类型 (opType)

当 `type` = `OPERATION` 时使用：

| 类型 | 说明 | 处理行为 |
|------|------|----------|
| `PLAYER_JOIN` | 玩家加入服务器 | 更新玩家-服务器映射 |
| `PLAYER_LEAVE` | 玩家离开服务器 | 移除玩家-服务器映射 |
| `SERVER_CONNECT` | 服务器连接成功 | 系统→二级服务器 |
| `SERVER_DISCONNECT` | 服务器断开 | 系统→所有二级服务器 |
| `GROUP_CREATE` | 创建群组 | 创建新群组，创建者自动加入 |
| `GROUP_DELETE` | 删除群组 | 删除群组（仅创建者可删除） |
| `GROUP_JOIN` | 加入群组 | 玩家加入指定群组 |
| `GROUP_LEAVE` | 离开群组 | 玩家离开指定群组 |

---

## 消息示例

### 1. 玩家加入（二级服务器发送）

```json
{
  "type": "OPERATION",
  "opType": "PLAYER_JOIN",
  "senderType": "SERVER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "timestamp": 1712390400
}
```

### 2. 玩家离开（二级服务器发送）

```json
{
  "type": "OPERATION",
  "opType": "PLAYER_LEAVE",
  "senderType": "SERVER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "timestamp": 1712390400
}
```

### 3. 单播到指定服务器

```json
{
  "type": "MESSAGE",
  "msgType": "UNICAST_SERVER",
  "senderType": "SERVER",
  "sourceServer": "生存服1",
  "sender": "生存服1",
  "targetServer": "生存服2",
  "content": "服务器公告：即将维护",
  "timestamp": 1712390400
}
```

### 4. 单播到指定玩家（精准路由）

```json
{
  "type": "MESSAGE",
  "msgType": "UNICAST_PLAYER",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "targetPlayer": "Alex",
  "content": "你好！",
  "timestamp": 1712390400
}
```

### 5. 组播到指定服务器列表

```json
{
  "type": "MESSAGE",
  "msgType": "MULTICAST_SERVER",
  "senderType": "SERVER",
  "sourceServer": "生存服1",
  "sender": "生存服1",
  "targetServers": ["生存服2", "创造服", "小游戏服"],
  "content": "跨服活动即将开始",
  "timestamp": 1712390400
}
```

### 6. 组播到指定玩家列表（精准路由）

```json
{
  "type": "MESSAGE",
  "msgType": "MULTICAST_PLAYER",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "targetPlayers": ["Alex", "Bob", "Charlie", "David"],
  "content": "组队邀请",
  "timestamp": 1712390400
}
```
**路由结果**：查找每个玩家所在服务器，只发送到包含这些玩家的服务器（可能只发送到2-3个服务器）

### 7. 广播（玩家发送）

```json
{
  "type": "MESSAGE",
  "msgType": "BROADCAST",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "content": "有人一起挖矿吗？",
  "timestamp": 1712390400
}
```

### 8. 广播（系统发送）

```json
{
  "type": "MESSAGE",
  "msgType": "BROADCAST",
  "senderType": "SYSTEM",
  "sender": "GlobalNotice",
  "content": "欢迎来到Minecraft服务器网络！",
  "timestamp": 1712390400
}
```

### 9. 群组消息组播

```json
{
  "type": "MESSAGE",
  "msgType": "MULTICAST_GROUP",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "targetGroup": "挖矿小队",
  "content": "我在 (100, 64, 200) 发现了钻石！",
  "timestamp": 1712390400
}
```
**路由结果**：查找"挖矿小队"群组内所有成员所在服务器，只发送到包含这些成员的服务器

### 10. 创建群组

```json
{
  "type": "OPERATION",
  "opType": "GROUP_CREATE",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "targetGroup": "挖矿小队",
  "groupDescription": "一起挖矿的群组",
  "timestamp": 1712390400
}
```

### 11. 删除群组

```json
{
  "type": "OPERATION",
  "opType": "GROUP_DELETE",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Steve",
  "targetGroup": "挖矿小队",
  "timestamp": 1712390400
}
```

### 12. 加入群组

```json
{
  "type": "OPERATION",
  "opType": "GROUP_JOIN",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Alex",
  "targetGroup": "挖矿小队",
  "timestamp": 1712390400
}
```

### 13. 离开群组

```json
{
  "type": "OPERATION",
  "opType": "GROUP_LEAVE",
  "senderType": "PLAYER",
  "sourceServer": "生存服1",
  "sender": "Alex",
  "targetGroup": "挖矿小队",
  "timestamp": 1712390400
}
```

---

## 路由规则

### 消息路由（单级架构）

| 消息类型 | 路由行为 | 同服务器处理 |
|----------|----------|--------------|
| `UNICAST_SERVER` | 发送到 `targetServer` | 丢弃（source == target） |
| `UNICAST_PLAYER` | 查找玩家所在服务器，只发送到该服务器 | 丢弃（source == target） |
| `MULTICAST_SERVER` | 发送到 `targetServers` 列表 | 跳过源服务器 |
| `MULTICAST_PLAYER` | 查找每个玩家所在服务器，按服务器分组后发送 | 跳过源服务器 |
| `MULTICAST_GROUP` | 查找群组内所有成员所在服务器，按服务器分组后发送 | 跳过源服务器 |
| `BROADCAST` | 发送到所有**其他**服务器 | 排除源服务器 |

### 消息路由（多级架构 - 跨路由器）

当目标服务器不在本地时，通过路由器互联转发：

| 场景 | 路由行为 |
|------|----------|
| 目标在本地 | 直接发送给本地服务器 |
| 目标在直连路由器 | 通过最短路径路由器转发 |
| 目标在多跳外 | 逐跳转发，TTL递减 |
| 环路检测 | TTL=0 或已访问过本路由器时丢弃 |

**跨路由器转发示例：**

```
玩家 Steve (生存服1) → 玩家 Alex (生存服3)

生存服1 ──► 路由器 A ──► 路由器 B ──► 生存服3
              │
         查找路由表：生存服3 经由 router-b
         封装 FORWARD_MESSAGE
         TTL=15 → TTL=14
```

### 操作处理

| 操作类型 | 处理行为 |
|----------|----------|
| `PLAYER_JOIN` | 更新玩家-服务器映射表 |
| `PLAYER_LEAVE` | 从映射表移除玩家 |
| `SERVER_CONNECT` | 通知服务器连接成功 |
| `SERVER_DISCONNECT` | 处理服务器断开，广播给其他服务器 |
| `GROUP_CREATE` | 创建群组，持久化存储 |
| `GROUP_DELETE` | 删除群组及相关数据 |
| `GROUP_JOIN` | 将玩家加入群组 |
| `GROUP_LEAVE` | 将玩家从群组移除 |

---

## 精准路由优势

**MULTICAST_PLAYER 示例**：
- 目标玩家：Alex（生存服2）、Bob（创造服）、Charlie（生存服2）、David（小游戏服）
- 传统方式：广播到所有4个服务器
- **精准路由**：只发送到3个服务器（生存服2、创造服、小游戏服）
- **节省流量**：25%

---

## 群组管理 REST API

供 Minecraft 服务器插件调用，管理玩家群组。

### 基础信息

| 项目 | 值 |
|------|-----|
| Base URL | `http://host:8080/api/groups` |
| Content-Type | `application/json` |

### 1. 获取所有群组

**请求**：`GET /api/groups`

**响应**：
```json
[
  {
    "id": 1,
    "name": "挖矿小队",
    "description": "一起挖矿的群组",
    "creator": "Steve",
    "createdAt": 1712390400,
    "memberCount": 3,
    "members": ["Steve", "Alex", "Bob"]
  }
]
```

### 2. 获取指定群组

**请求**：`GET /api/groups/{groupName}`

**响应**：
```json
{
  "id": 1,
  "name": "挖矿小队",
  "description": "一起挖矿的群组",
  "creator": "Steve",
  "createdAt": 1712390400,
  "memberCount": 3,
  "members": ["Steve", "Alex", "Bob"]
}
```

### 3. 获取玩家的所有群组

**请求**：`GET /api/groups/player/{playerName}`

### 4. 创建群组

**请求**：`POST /api/groups`

**请求体**：
```json
{
  "name": "挖矿小队",
  "description": "一起挖矿的群组",
  "creator": "Steve"
}
```

**响应**：
```json
{
  "id": 1,
  "name": "挖矿小队",
  "description": "一起挖矿的群组",
  "creator": "Steve",
  "createdAt": 1712390400,
  "memberCount": 1,
  "members": ["Steve"]
}
```

### 5. 删除群组

**请求**：`DELETE /api/groups/{groupName}?requester=Steve`

**响应**：
```json
{
  "success": true,
  "message": "群组删除成功"
}
```

### 6. 加入群组

**请求**：`POST /api/groups/{groupName}/join?playerName=Alex`

**响应**：
```json
{
  "success": true,
  "message": "加入群组成功"
}
```

### 7. 离开群组

**请求**：`POST /api/groups/{groupName}/leave?playerName=Alex`

**响应**：
```json
{
  "success": true,
  "message": "离开群组成功"
}
```

### 8. 获取群组成员

**请求**：`GET /api/groups/{groupName}/members`

**响应**：
```json
["Steve", "Alex", "Bob"]
```

### 9. 检查玩家是否在群组中

**请求**：`GET /api/groups/{groupName}/isMember?playerName=Alex`

**响应**：
```json
{
  "groupName": "挖矿小队",
  "playerName": "Alex",
  "isMember": true
}
```

---

## 路由器互联协议

### 连接流程

```
┌─────────────┐                    ┌─────────────┐
│  路由器 A   │                    │  路由器 B   │
└──────┬──────┘                    └──────┬──────┘
       │                                   │
       │─── 1. WebSocket 连接建立 ─────────►│
       │                                   │
       │─── 2. CONNECT_REQUEST ───────────►│
       │    {                              │
       │      "type": "CONNECT_REQUEST",    │
       │      "sourceRouterId": "router-a", │
       │      "sourceRouterName": "Router A"│
       │    }                              │
       │                                   │
       │◄── 3. CONNECT_RESPONSE ───────────│
       │    {                              │
       │      "type": "CONNECT_RESPONSE",   │
       │      "sourceRouterId": "router-b", │
       │      "sourceRouterName": "Router B"│
       │    }                              │
       │                                   │
       │◄──►4. 定期 HEARTBEAT 交换 ◄───────►│
       │                                   │
       │◄──►5. 定期 ROUTE_ADVERTISEMENT ───►│
       │                                   │
```

### 消息类型

| 类型 | 说明 | 方向 |
|------|------|------|
| `CONNECT_REQUEST` | 连接请求 | A → B |
| `CONNECT_RESPONSE` | 连接响应 | B → A |
| `HEARTBEAT` | 心跳检测 | 双向 |
| `HEARTBEAT_RESPONSE` | 心跳响应 | 双向 |
| `ROUTE_ADVERTISEMENT` | 路由表通告 | 双向 |
| `FORWARD_MESSAGE` | 消息转发 | 双向 |
| `BROADCAST_MESSAGE` | 广播消息 | 双向 |

### 消息格式

#### 1. 连接请求

```json
{
  "type": "CONNECT_REQUEST",
  "sourceRouterId": "router-a-uuid",
  "sourceRouterName": "Asia-Shanghai-Router",
  "timestamp": 1712390400000
}
```

#### 2. 心跳

```json
{
  "type": "HEARTBEAT",
  "sourceRouterId": "router-a-uuid",
  "sourceRouterName": "Asia-Shanghai-Router",
  "heartbeatSeq": 1712390400000,
  "timestamp": 1712390400000
}
```

#### 3. 路由表通告

```json
{
  "type": "ROUTE_ADVERTISEMENT",
  "sourceRouterId": "router-a-uuid",
  "sourceRouterName": "Asia-Shanghai-Router",
  "routes": {
    "生存服1": 1,
    "创造服": 1,
    "生存服3": 2,
    "小游戏服": 3
  },
  "timestamp": 1712390400000
}
```

**路由表说明：**
- Key: 目标服务器名称
- Value: 到达该服务器的跳数（hop count）
- 本地服务器跳数为 1
- 通过其他路由器可达的服务器跳数递增

#### 4. 消息转发

```json
{
  "type": "FORWARD_MESSAGE",
  "sourceRouterId": "router-a-uuid",
  "sourceRouterName": "Asia-Shanghai-Router",
  "messageId": "msg-uuid-123",
  "chatMessage": {
    "type": "MESSAGE",
    "msgType": "UNICAST_PLAYER",
    "senderType": "PLAYER",
    "sourceServer": "生存服1",
    "sender": "Steve",
    "targetPlayer": "Alex",
    "content": "你好！"
  },
  "sourceServer": "生存服1",
  "targetServer": "生存服3",
  "visitedRouters": ["router-a-uuid"],
  "ttl": 14,
  "timestamp": 1712390400000
}
```

**字段说明：**
- `messageId`: 消息唯一ID，用于去重和追踪
- `visitedRouters`: 已访问的路由器ID列表（环路检测）
- `ttl`: 生存时间，每经过一跳减1，为0时丢弃

#### 5. 广播消息

```json
{
  "type": "BROADCAST_MESSAGE",
  "sourceRouterId": "router-a-uuid",
  "sourceRouterName": "Asia-Shanghai-Router",
  "messageId": "msg-uuid-456",
  "chatMessage": {
    "type": "MESSAGE",
    "msgType": "BROADCAST",
    "senderType": "PLAYER",
    "sourceServer": "生存服1",
    "sender": "Steve",
    "content": "有人一起挖矿吗？"
  },
  "sourceServer": "生存服1",
  "visitedRouters": ["router-a-uuid"],
  "ttl": 14,
  "timestamp": 1712390400000
}
```

### 路由算法

#### 最短路径计算

使用 **Bellman-Ford** 算法变种：

```
成本 = 链路延迟 + 跳数 × 10

例如：
- 直接连接：链路延迟 5ms + 1跳 × 10 = 15
- 经由1个路由器：链路延迟 5ms + 2跳 × 10 = 25
- 经由2个路由器：链路延迟 5ms + 3跳 × 10 = 35
```

#### 环路避免机制

1. **TTL 递减**：最大15跳，为0时丢弃消息
2. **访问列表**：记录已访问的路由器ID，重复访问时丢弃
3. **消息ID去重**：同一消息ID不重复处理

### 路由表示例

假设网络拓扑：

```
路由器 A ──(5ms)── 路由器 B ──(10ms)── 路由器 C
   │                    │
 生存服1              生存服3
 创造服               小游戏服
```

**路由器 A 的路由表：**

| 目标服务器 | 下一跳 | 总成本 | 路径 |
|-----------|--------|--------|------|
| 生存服1 | local | 0 | 直连 |
| 创造服 | local | 0 | 直连 |
| 生存服3 | router-b | 25 | A → B |
| 小游戏服 | router-b | 35 | A → B → C |

### 心跳机制

- **间隔**：10秒
- **超时**：30秒未收到心跳视为断开
- **链路成本**：根据心跳响应时间动态计算

---

## 错误处理

### WebSocket 连接错误

| 场景 | 状态码 | 原因 |
|------|--------|------|
| 未提供 serverName | 1008 | `必须提供服务器名称` |
| 名称已存在 | 1008 | `服务器名称已存在` |

### REST API 错误

| 场景 | HTTP 状态码 | 错误信息 |
|------|-------------|----------|
| 群组名称已存在 | 400 | `群组名称已存在: xxx` |
| 群组不存在 | 404 | Not Found |
| 无权限删除群组 | 400 | `只有创建者可以删除群组` |
| 玩家已在群组中 | 400 | `玩家已在群组中` |
| 玩家不在群组中 | 400 | `玩家不在群组中` |
| 创建者不能离开群组 | 400 | `创建者不能离开群组，请删除群组` |
