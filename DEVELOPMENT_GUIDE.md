# SoulChat 开发注意事项指南

**版本**: v0.7.0  
**日期**: 2026-05-12  
**用途**: 新上下文开发前必读

---

## 一、项目架构速览

### 技术栈
- **后端**: Spring Boot 3.2 + WebFlux (响应式编程)
- **数据库**: MongoDB (Reactive)
- **缓存**: Redis
- **前端**: 原生 HTML + CSS + JavaScript
- **实时通信**: WebSocket
- **安全**: JWT Token

### 目录结构
```
src/main/
├── java/com/talkingai/soulchat/
│   ├── config/          # 配置类
│   ├── controller/      # REST API 控制器
│   ├── service/         # 业务逻辑层
│   ├── repository/      # 数据访问层
│   ├── entity/          # 实体类
│   ├── dto/             # 数据传输对象
│   ├── security/        # 安全配置
│   └── handler/         # WebSocket 处理器
└── resources/static/
    ├── index.html       # 主页面
    ├── css/style.css    # 全局样式
    └── js/              # JavaScript 文件
        ├── api.js       # API 请求封装
        ├── app.js       # 主应用逻辑
        ├── chat.js      # 聊天功能
        ├── match.js     # 匹配功能
        ├── mood.js      # 心情聊天室
        ├── assessment.js # 人格评估
        └── profile.js   # 个人资料
```

---

## 二、关键设计模式

### 1. 响应式编程
- 所有数据库操作返回 `Mono<T>` 或 `Flux<T>`
- 控制器层使用 `.collectList()` 将 `Flux` 转为 `Mono<List>`
- **注意**: 不要直接返回 `Flux` 给前端，需要包装成 `ApiResponse`

### 2. API 统一响应格式
```java
ApiResponse.success(data)  // 成功
ApiResponse.error(code, message)  // 失败
```

### 3. 前端状态管理
- 每个功能模块有自己的 state 对象（如 `chatState`, `moodState`）
- 用户 ID 缓存: `chatState.myUserId`（避免 localStorage 被覆盖）
- 当前页面: `currentPage`（防止重复渲染）

### 4. WebSocket 消息格式
```javascript
{
    type: 'CHAT',      // 消息类型
    roomId: 'xxx',     // 房间ID
    senderId: 'xxx',   // 发送者ID
    content: 'xxx',    // 内容
    timestamp: 123456  // 时间戳
}
```

---

## 三、常见陷阱与避免方法

### ❌ 陷阱 1: API 请求方式错误
**错误**:
```javascript
await apiRequest('/url', 'DELETE');  // 错误！第二个参数是 options 对象
```

**正确**:
```javascript
await del('/url');  // 使用封装好的方法
// 或
await apiRequest('/url', { method: 'DELETE' });
```

---

### ❌ 陷阱 2: 导航栏重复渲染
**错误**: 直接修改 `window.location.hash`，触发 `hashchange`，导致重复渲染

**正确**: 
- 使用 `navigateTo(page, updateHash)` 函数
- 点击导航时 `updateHash = true`
- 初始化时 `updateHash = false`
- 添加 `currentPage` 检查，相同页面不重复渲染

---

### ❌ 陷阱 3: Flux 直接返回前端
**错误**:
```java
@GetMapping("/list")
public Flux<Data> getList() {
    return service.getList();  // 错误！Flux 无法正确序列化
}
```

**正确**:
```java
@GetMapping("/list")
public Mono<ApiResponse<List<Data>>> getList() {
    return service.getList()
        .collectList()
        .map(ApiResponse::success);
}
```

---

### ❌ 陷阱 4: 使用 localStorage 获取当前用户
**错误**:
```javascript
const userId = getUserInfo().userId;  // 可能被其他标签页覆盖
```

**正确**:
```javascript
// 初始化时缓存
chatState.myUserId = getUserInfo().userId;

// 使用时
const userId = chatState.myUserId;
```

---

### ❌ 陷阱 5: 消息身份判断错误
**错误**:
```javascript
const isOwn = msg.senderId === currentUserId || msg._localId;
```

**正确**:
```javascript
const isOwn = msg.senderId === currentUserId;  // 只使用 senderId 判断
```

---

### ❌ 陷阱 6: 忘记添加 API 方法
**新增接口时**:
1. 在 `api.js` 添加对应的请求方法（get/post/put/patch/del）
2. 确保方法名不与原生 API 冲突（如 `delete` 是保留字，使用 `del`）

---

## 四、UI 开发规范

### 1. 彩铅蜡笔涂鸦风格
- **主色调**: 
  - 粉色: `#FFB6C1` (浅粉), `#FF6B9D` (深粉)
  - 紫色: `#DDA0DD`
  - 蓝色: `#87CEEB`
  - 黄色: `#FFD700`
  - 绿色: `#98FB98`

- **边框**: 
  - 虚线: `2px dashed #FFB6C1`
  - 实线: `2px solid #FFB6C1`

- **阴影**: 
  - `box-shadow: 3px 3px 0px rgba(255, 182, 193, 0.3)`

- **圆角**: 
  - 小元素: `12px-16px`
  - 大元素: `20px-24px`
  - 按钮: `9999px` (胶囊形)

### 2. 按钮样式模板
```css
.btn-custom {
    background: linear-gradient(135deg, #FF6B9D, #DDA0DD);
    color: white;
    border: 3px solid white;
    padding: 14px 48px;
    border-radius: 28px;
    font-size: 16px;
    font-weight: 600;
    cursor: pointer;
    box-shadow: 4px 4px 0px rgba(255, 107, 157, 0.4);
    transition: all 0.3s ease;
}

.btn-custom:hover {
    transform: scale(1.05) rotate(-2deg);
    box-shadow: 5px 5px 0px rgba(255, 107, 157, 0.6);
}
```

### 3. 卡片样式模板
```css
.card-custom {
    background: rgba(255, 255, 255, 0.85);
    border-radius: 16px;
    border: 2px dashed #DDA0DD;
    box-shadow: 3px 3px 0px rgba(221, 160, 221, 0.3);
    padding: 20px;
}
```

---

## 五、AI 接入注意事项

### 1. 超时机制
- AI 调用必须设置 8 秒超时
- 超时后抛出异常，降级到选择题模式

### 2. 限流机制
- 使用 Redis 限制每日 AI 评估次数
- Key 格式: `mood_ai_limit:{userId}:{date}`
- 限制: 每日 5 次

### 3. 提示词管理
- 系统提示词写在 `AiPromptTemplates` 常量类中
- 方便后续调优

### 4. 降级策略
- API 未接入或调用失败时，自动降级到选择题模式
- 用户无感知切换

---

## 六、测试清单

### 功能测试
- [ ] 用户注册/登录
- [ ] 人格评估（选择题 + AI 对话）
- [ ] 灵魂匹配
- [ ] 私聊功能（发送/接收/历史记录）
- [ ] 心情聊天室
- [ ] 删除聊天记录

### UI 测试
- [ ] 导航栏切换
- [ ] 响应式布局
- [ ] 动画效果
- [ ] 空状态显示

### 性能测试
- [ ] 多用户并发
- [ ] WebSocket 连接稳定性
- [ ] 消息延迟

---

## 七、快速启动命令

```bash
# 编译
mvn clean compile -DskipTests

# 运行
mvn spring-boot:run

# 打包
mvn clean package -DskipTests
```

---

## 八、重要文件位置

| 功能 | 文件路径 |
|------|----------|
| API 封装 | `src/main/resources/static/js/api.js` |
| 全局样式 | `src/main/resources/static/css/style.css` |
| 聊天功能 | `src/main/resources/static/js/chat.js` |
| 匹配功能 | `src/main/resources/static/js/match.js` |
| 心情聊天 | `src/main/resources/static/js/mood.js` |
| 人格评估 | `src/main/resources/static/js/assessment.js` |
| 主应用 | `src/main/resources/static/js/app.js` |
| 聊天服务 | `src/main/java/.../service/ChatService.java` |
| 匹配服务 | `src/main/java/.../service/MatchingService.java` |
| 安全配置 | `src/main/java/.../config/SecurityConfig.java` |

---

## 九、后续开发优先级

### P0 (必须)
1. 人格评估页面 UI 美化
2. 心情聊天页面 UI 美化
3. AI 接入测试

### P1 (重要)
1. 加载状态优化
2. 错误提示优化
3. 性能监控

### P2 (可选)
1. 动画效果增强
2. 主题切换
3. 多语言支持

---

**祝开发顺利！**
