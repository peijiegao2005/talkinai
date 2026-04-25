# SoulChat 项目知识文档

## 项目概述
AI人格匹配聊天系统 - 基于大五人格理论的社交匹配平台

## 技术栈
- **框架**: Spring Boot 3.2 + WebFlux (响应式编程)
- **数据库**: MongoDB (文档存储) + Redis (缓存/会话)
- **安全**: JWT Token + Spring Security
- **实时通信**: WebSocket

## 核心功能模块

### 1. 用户认证模块
**文件位置**: `controller/AuthController.java`, `service/AuthService.java`
**功能**:
- 用户注册 `/api/auth/register`
- 用户登录 `/api/auth/login`
- JWT Token生成和验证

**关键类**:
- `JwtUtil` - Token工具类
- `JwtAuthenticationFilter` - 认证过滤器
- `SecurityConfig` - 安全配置

### 2. AI评估模块
**文件位置**: `controller/AssessmentController.java`, `service/AssessmentService.java`
**功能**:
- 10题大五人格测试
- Redis会话管理 (30分钟TTL)
- 人格向量生成 (5维, 归一化到[-1,1])

**API端点**:
- `POST /api/assessment/start` - 开始评估
- `POST /api/assessment/answer` - 提交答案
- `POST /api/assessment/complete` - 完成评估
- `GET /api/assessment/result` - 获取报告

**数据结构**:
```java
// 人格向量 [外向性,开放性,宜人性,尽责性,情绪稳定性]
List<Double> personalityVector = [(score-3)/2, ...]
```

### 3. 匹配模块
**文件位置**: `controller/MatchingController.java`, `service/MatchingService.java`
**功能**:
- 简化版灵魂匹配 (Redis队列)
- 在线用户管理 (5分钟TTL)

**API端点**:
- `POST /api/match/find-soul` - 寻找灵魂伴侣
- `GET /api/match/status` - 在线状态

**Redis Key设计**:
```
user:online:{userId} - 用户在线状态
matching:queue - 匹配队列
assessment:session:{userId} - 评估会话
```

### 4. 聊天模块
**文件位置**: `controller/ChatController.java`, `service/ChatService.java`, `handler/ChatWebSocketHandler.java`
**功能**:
- WebSocket实时聊天
- 私聊消息存储
- 聊天室管理

**API端点**:
- `GET /api/chat/messages/private/{userId}` - 私聊历史
- `GET /api/chat/rooms` - 聊天室列表
- `GET /api/chat/unread-count` - 未读数

**WebSocket**:
- 连接: `ws://localhost:8080/ws/chat?token={JWT}`
- 消息格式: `{type, senderId, receiverId, content, timestamp}`

## 实体关系

```
User (1) ----< (N) AssessmentReport
    |
    |----< (N) ChatMessage (as sender/receiver)
    |
    |----< (N) ChatRoom (via participants)
```

## 关键配置

### application.yml
```yaml
server.port: 8080
spring.data.mongodb.uri: mongodb://localhost:27017/soulchat
spring.data.redis.host: localhost
spring.data.redis.port: 6379
jwt.secret: your-secret-key
jwt.expiration: 86400000
```

## 开发规范

### 包结构
```
com.talkingai.soulchat/
  ├── config/      # 配置类
  ├── controller/  # REST API
  ├── service/     # 业务逻辑
  ├── repository/  # 数据访问
  ├── entity/      # 实体类
  ├── dto/         # 数据传输对象
  ├── security/    # 安全相关
  ├── handler/     # WebSocket处理器
  ├── util/        # 工具类
  └── initializer/ # 数据初始化
```

### 命名规范
- Controller: `*Controller`
- Service: `*Service`
- Repository: `*Repository`
- Entity: 驼峰命名，集合名复数
- DTO: `*Request`, `*Response`

## 待开发功能

### 高优先级
1. 群聊大厅功能
2. 内容安全过滤
3. 消息已读回执优化

### 中优先级
4. LLM生成详细人格报告
5. 用户画像系统
6. 匹配算法优化

### 低优先级
7. 文件传输
8. 语音消息
9. 消息撤回

## 性能优化点
1. WebSocket连接池管理
2. MongoDB索引优化
3. Redis缓存策略
4. 消息分页加载

## 安全考虑
1. JWT Token过期处理
2. WebSocket连接鉴权
3. 敏感词过滤 (待实现)
4. 消息内容审核 (待实现)
