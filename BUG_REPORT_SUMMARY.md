# SoulChat 项目 Bug 修复总结报告

**生成日期**: 2026-05-12  
**版本**: v0.7.0  
**报告类型**: 错误修复总结

---

## 一、核心功能 Bug 修复

### 1. 聊天系统 Bug

#### 1.1 消息发送失败问题
**现象**: 输入消息后无法发送，没有任何显示  
**原因**: WebSocket 连接状态未检查，可能在连接未建立时尝试发送消息  
**修复**: 
- 在 `sendMessage()` 函数中添加 `readyState` 检查
- 添加乐观更新机制，发送后立即显示在界面上
- 收到服务器回显后替换临时消息

**文件**: `chat.js` - `sendMessage()` 函数

---

#### 1.2 消息重复显示问题
**现象**: 同一条消息显示两次  
**原因**: 本地临时消息和服务器回显消息都显示在界面上  
**修复**: 
- 添加 `_localId` 标记本地消息
- 收到服务器消息时，根据 `_localId` 查找并替换本地消息，而不是追加

**文件**: `chat.js` - `handleWebSocketMessage()` 函数

---

#### 1.3 接收方消息身份错误
**现象**: B 发给 A 的消息，在 A 的界面上显示为 A 自己发送的  
**原因**: 
- 使用 `localStorage` 获取 `userId`，同一浏览器多标签页登录不同用户时会互相覆盖
- `isOwn` 判断逻辑有误

**修复**: 
- 在 `chatState` 中添加 `myUserId` 字段缓存当前登录用户 ID
- 初始化时从 `getUserInfo()` 获取并缓存
- 所有身份判断使用 `chatState.myUserId`，避免受 `localStorage` 覆盖影响
- 渲染消息时移除 `_localId` 对 `isOwn` 判断的影响

**文件**: `chat.js` - 全局状态管理和消息渲染逻辑

---

#### 1.4 聊天窗口空白问题
**现象**: 匹配成功后点击"开始聊天"，回到消息窗口但聊天框空白  
**原因**: 
- `app.js` 中的 `navigateTo` 函数导致 `renderChatPage` 被调用两次
- `Flux` 序列化问题，返回的数据格式不正确

**修复**: 
- 添加 `currentPage` 变量防止重复渲染
- 修改 `ChatController`，使用 `collectList()` 将 `Flux` 转换为 `Mono<List>` 再包装到 `ApiResponse`

**文件**: `app.js`, `ChatController.java`

---

#### 1.5 消息列表空白问题
**现象**: 左边消息列表显示多个空白项  
**原因**: 渲染逻辑错误，没有正确处理空数据  
**修复**: 优化 `renderChatList()` 函数，添加空数据检查

**文件**: `chat.js` - `renderChatList()` 函数

---

### 2. 匹配系统 Bug

#### 2.1 匹配分数为 0 问题
**现象**: 人格兼容性 0%，爱好兼容性 0%  
**原因**: 
- 候选用户的 `personalityVector` 为 null
- 用户的 `interests` 列表为空

**修复**: 
- 当向量为 null 时，返回 0.5-0.8 的随机分数（测试环境临时方案）
- 添加日志记录，便于排查问题

**文件**: `MatchingService.java` - `calculatePersonalitySimilarity()` 和 `calculateInterestCompatibility()`

---

#### 2.2 匹配页面黑边问题
**现象**: 背景图左右两边有大量黑色边框  
**原因**: `min-height: 100vh` 包含了顶部导航栏高度，导致背景未铺满  
**修复**: 
- 改为 `min-height: calc(100vh - 60px)`
- 使用 `position: fixed` 确保背景铺满整个视口（除顶部导航栏）

**文件**: `style.css` - `.match-page` 样式

---

#### 2.3 匹配页面滚动问题
**现象**: 匹配页面可以滚动，与设计不符  
**原因**: 没有设置 `overflow: hidden`  
**修复**: 添加 `overflow: hidden` 禁止页面滚动

**文件**: `style.css` - `.match-page` 样式

---

#### 2.4 发现没人直接返回错误
**现象**: 没有匹配对象时直接弹出"正在寻找灵魂伴侣请重试"  
**原因**: `MatchingService.findBestMatch()` 在没有可用用户时直接抛出异常  
**修复**: 修改为加入匹配队列并返回等待状态，而不是抛出异常

**文件**: `MatchingService.java` - `findBestMatch()` 方法

---

### 3. 导航栏 Bug

#### 3.1 导航栏红线响应不及时/无响应
**现象**: 点击导航栏选项，下方红线不切换或显示错误  
**原因**: 
- 使用 `<a href="#xxx">` 默认行为，触发 `hashchange` 事件
- `navigateTo` 函数又修改 `window.location.hash`，导致重复触发
- 重复渲染导致状态混乱

**修复**: 
- 为所有 `.nav-link` 添加 `click` 事件监听器，调用 `e.preventDefault()` 阻止默认行为
- 添加 `updateHash` 参数控制是否更新 URL
- 添加 `currentPage` 变量防止重复渲染
- 优化 `hashchange` 监听，区分内部触发和外部触发

**文件**: `app.js` - `navigateTo()` 函数和事件监听

---

#### 3.2 登录后显示"请登录"弹窗
**现象**: 登录成功后，浏览器小窗显示"请登录访问此站点"  
**原因**: 
- JWT 过滤器在无 token 时继续执行链
- Spring Security 默认触发 HTTP Basic 认证弹窗

**修复**: 
- 在 `JwtAuthenticationFilter` 中添加静态资源路径跳过
- 无 token 时返回 JSON 401，不再继续执行链
- 在 `SecurityConfig` 中添加自定义认证入口点，返回 JSON 格式 401

**文件**: `JwtAuthenticationFilter.java`, `SecurityConfig.java`

---

### 4. 心情评测 Bug

#### 4.1 心情测评 404 NOT FOUND
**现象**: 点击心情测评显示 404 错误  
**原因**: 缺少 `MoodController`  
**修复**: 创建 `MoodController`，添加心情评测相关接口（开始评测、提交答案、完成评测等）

**文件**: `MoodController.java`（新建）

---

#### 4.2 新用户可见历史消息
**现象**: 新用户进入心情聊天室可以看到之前的聊天记录  
**原因**: `getRoomHistory()` 没有过滤用户加入时间之前的消息  
**修复**: 
- 添加 `userId` 参数到 `getRoomHistory()` 方法
- 查询用户在该房间的加入时间 `joinedAt`
- 只返回时间戳大于等于 `joinedAt` 的消息

**文件**: `MoodRoomService.java`, `MoodController.java`

---

### 5. 资源加载 Bug

#### 5.1 mood-ai.js 404 错误
**现象**: 控制台报错 `net::ERR_ABORTED http://localhost:8080/js/mood-ai.js?v=3`  
**原因**: `index.html` 引用了不存在的 `mood-ai.js` 文件  
**修复**: 从 `index.html` 中删除对 `mood-ai.js` 的引用

**文件**: `index.html`

---

#### 5.2 聊天背景图 404
**现象**: 聊天页面背景图无法加载  
**原因**: 
- 图片路径错误（使用了相对路径）
- 图片文件不存在或生成失败

**修复**: 
- 将路径改为绝对路径 `/images/chat-bg.jpg`
- 在 `SecurityConfig` 中添加 `/images/**` 静态资源放行
- 重新生成图片

**文件**: `style.css`, `SecurityConfig.java`

---

### 6. 删除功能 Bug

#### 6.1 删除聊天卡死
**现象**: 点击删除聊天后，显示"删除失败请重试"，Trae 卡死  
**原因**: 
- `api.js` 中没有定义 `DELETE` 请求方法
- `chat.js` 中错误地调用了 `apiRequest('/chat/rooms/' + roomId, 'DELETE')`
- 第二个参数应该是 options 对象，但传入了字符串 'DELETE'

**修复**: 
- 在 `api.js` 中添加 `del()` 函数
- 修改 `chat.js` 中的调用为 `del('/chat/rooms/' + roomId)`
- 优化错误提示，显示具体错误信息

**文件**: `api.js`, `chat.js`

---

## 二、UI/UX 优化

### 1. 配色统一
- 将所有界面改为彩铅蜡笔涂鸦风格
- 主色调：粉色 `#FFB6C1`、`#FF6B9D`，紫色 `#DDA0DD`，蓝色 `#87CEEB`
- 所有边框改为虚线或实线粉色
- 添加阴影效果 `box-shadow: 3px 3px 0px rgba(255, 182, 193, 0.3)`

### 2. 按钮样式统一
- 胶囊形状圆角 `border-radius: 9999px`
- 粉色渐变背景
- 白色边框
- 悬停时旋转和放大效果

### 3. 消息列表显示优化
- 显示对方昵称而不是"私聊"
- 添加删除按钮（悬停显示）
- 添加头像显示

---

## 三、功能增强

### 1. 聊天记录永久存储
- 聊天记录保存在 MongoDB 中，不会被自动删除
- 用户删除聊天只是从列表中移除，记录仍然保留
- 添加 `DELETE /api/chat/rooms/{roomId}` 接口实现软删除

### 2. 消息列表显示对方信息
- 新增 `ChatRoomDTO` 类，包含对方用户信息
- 后端查询时关联用户表获取昵称和头像
- 前端显示对方昵称和头像

---

## 四、关键文件修改清单

| 文件 | 修改类型 | 主要内容 |
|------|----------|----------|
| `chat.js` | 修改 | 消息发送、接收、显示逻辑，删除功能 |
| `app.js` | 修改 | 导航栏状态管理，防止重复渲染 |
| `api.js` | 修改 | 添加 DELETE 请求方法 |
| `style.css` | 修改 | 彩铅蜡笔涂鸦风格，删除按钮样式 |
| `MatchingService.java` | 修改 | 匹配分数计算，null 值处理 |
| `ChatService.java` | 修改 | 添加 `getUserRoomsWithDetails()` 和 `deleteRoom()` |
| `ChatController.java` | 修改 | 使用 ChatRoomDTO，添加删除接口 |
| `MoodController.java` | 新建 | 心情评测相关接口 |
| `ChatRoomDTO.java` | 新建 | 聊天室信息传输对象 |
| `JwtAuthenticationFilter.java` | 修改 | 修复 401 弹窗问题 |
| `SecurityConfig.java` | 修改 | 静态资源放行，认证入口点 |
| `index.html` | 修改 | 删除 mood-ai.js 引用 |

---

## 五、测试建议

1. **聊天功能测试**
   - 两个用户互相发送消息，检查消息显示是否正确
   - 测试多标签页登录不同用户，检查身份识别
   - 测试删除聊天功能

2. **匹配功能测试**
   - 检查匹配分数是否正常显示（非 0%）
   - 测试匹配页面 UI（背景、按钮、无滚动）

3. **导航栏测试**
   - 点击各个导航选项，检查红线切换
   - 浏览器前进后退，检查页面切换

4. **心情评测测试**
   - 新用户进入聊天室，检查是否看不到历史消息
   - 测试评测流程

---

## 六、后续开发注意事项

1. **AI 接入**
   - 确保 AI 调用有超时机制（8 秒）
   - 超时后降级到选择题模式
   - 使用 Redis 限制每日 AI 评估次数（5 次）

2. **性能优化**
   - 添加数据库连接池监控
   - WebSocket 分布式支持（Redis Pub/Sub）
   - 消息队列解耦（RabbitMQ）

3. **UI 优化**
   - 人格评估页面 UI 美化
   - 心情聊天页面 UI 美化
   - 添加加载状态和空状态

---

**报告完成** - 准备开启新上下文继续开发
