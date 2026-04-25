// 聊天功能

let chatState = {
    ws: null,
    currentRoom: null,
    currentUser: null,
    messages: [],
    rooms: []
};

// 渲染聊天页面
function renderChatPage() {
    const container = document.getElementById('main-container');

    container.innerHTML = `
        <div class="chat-page">
            <div class="chat-sidebar">
                <div class="chat-sidebar-header">
                    <h3 style="font-weight: 600;">消息</h3>
                </div>
                <div class="chat-list" id="chat-list">
                    <!-- 聊天列表 -->
                </div>
            </div>
            <div class="chat-main" id="chat-main">
                <div class="empty-state">
                    <div class="empty-icon">💬</div>
                    <p>选择一个聊天开始对话</p>
                </div>
            </div>
        </div>
    `;

    loadChatRooms();

    // 如果有当前房间，自动打开
    const currentRoom = localStorage.getItem('currentChatRoom');
    if (currentRoom) {
        const currentUser = JSON.parse(localStorage.getItem('currentChatUser') || '{}');
        openChatRoom(currentRoom, currentUser);
        localStorage.removeItem('currentChatRoom');
        localStorage.removeItem('currentChatUser');
    }
}

// 加载聊天列表
async function loadChatRooms() {
    try {
        const data = await get('/chat/rooms');
        chatState.rooms = data.data || [];
        renderChatList();
    } catch (error) {
        console.error('加载聊天列表失败:', error);
    }
}

// 渲染聊天列表
function renderChatList() {
    const list = document.getElementById('chat-list');

    if (chatState.rooms.length === 0) {
        list.innerHTML = `
            <div class="empty-state" style="padding: 40px 24px;">
                <p style="font-size: 14px;">暂无聊天</p>
            </div>
        `;
        return;
    }

    list.innerHTML = chatState.rooms.map(room => `
        <div class="chat-item ${chatState.currentRoom === room.roomId ? 'active' : ''}"
             onclick="openChatRoom('${room.roomId}', {nickname: '${room.name}', userId: '${room.participants.find(p => p !== getUserInfo()?.userId) || ''}'})">
            <div class="chat-item-avatar">👤</div>
            <div class="chat-item-info">
                <div class="chat-item-name">${room.name || '未知用户'}</div>
                <div class="chat-item-preview">点击开始聊天</div>
            </div>
        </div>
    `).join('');
}

// 打开聊天房间
async function openChatRoom(roomId, user) {
    chatState.currentRoom = roomId;
    chatState.currentUser = user;

    renderChatList();
    renderChatWindow();
    connectWebSocket();

    // 加载历史消息
    try {
        const data = await get(`/chat/messages/room/${roomId}?page=0&size=50`);
        chatState.messages = data.data || [];
        renderMessages();
    } catch (error) {
        console.error('加载消息失败:', error);
    }
}

// 渲染聊天窗口
function renderChatWindow() {
    const main = document.getElementById('chat-main');
    const user = chatState.currentUser;

    main.innerHTML = `
        <div class="chat-header">
            <div class="chat-header-info">
                <div class="chat-header-avatar">👤</div>
                <div>
                    <div class="chat-header-name">${user?.nickname || '未知用户'}</div>
                    <div class="chat-header-status">● 在线</div>
                </div>
            </div>
        </div>
        <div class="chat-messages-area" id="chat-messages-area">
            <!-- 消息列表 -->
        </div>
        <div class="chat-input-area">
            <div class="chat-input-container">
                <textarea class="chat-input" id="message-input"
                    placeholder="输入消息..."
                    rows="1"
                    onkeydown="handleMessageKeydown(event)"></textarea>
                <button class="chat-send-btn" onclick="sendMessage()">
                    ➤
                </button>
            </div>
        </div>
    `;

    // 自动调整输入框高度
    const input = document.getElementById('message-input');
    input.addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = Math.min(this.scrollHeight, 120) + 'px';
    });
}

// 渲染消息
function renderMessages() {
    const container = document.getElementById('chat-messages-area');
    const currentUserId = getUserInfo()?.userId;

    container.innerHTML = chatState.messages.map(msg => {
        const isOwn = msg.senderId === currentUserId;
        const time = new Date(msg.timestamp).toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit'
        });

        return `
            <div class="chat-message ${isOwn ? 'own' : ''}">
                <div class="chat-message-avatar">${isOwn ? '我' : '👤'}</div>
                <div class="chat-message-content">
                    <div class="chat-message-bubble">${escapeHtml(msg.content)}</div>
                    <div class="chat-message-time">${time}</div>
                </div>
            </div>
        `;
    }).join('');

    // 滚动到底部
    container.scrollTop = container.scrollHeight;
}

// 连接WebSocket
function connectWebSocket() {
    const token = getToken();
    if (!token) return;

    // 关闭旧连接
    if (chatState.ws) {
        chatState.ws.close();
    }

    const wsUrl = `ws://${window.location.host}/ws/chat?token=${token}`;
    chatState.ws = new WebSocket(wsUrl);

    chatState.ws.onopen = () => {
        console.log('WebSocket连接成功');
        // 加入房间
        chatState.ws.send(JSON.stringify({
            type: 'JOIN_ROOM',
            roomId: chatState.currentRoom
        }));
    };

    chatState.ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        handleWebSocketMessage(data);
    };

    chatState.ws.onerror = (error) => {
        console.error('WebSocket错误:', error);
    };

    chatState.ws.onclose = () => {
        console.log('WebSocket连接关闭');
    };
}

// 处理WebSocket消息
function handleWebSocketMessage(data) {
    if (data.type === 'CHAT') {
        chatState.messages.push(data);
        renderMessages();
    }
}

// 发送消息
function sendMessage() {
    const input = document.getElementById('message-input');
    const content = input.value.trim();

    if (!content || !chatState.ws || !chatState.currentUser) return;

    const message = {
        type: 'CHAT',
        receiverId: chatState.currentUser.userId,
        content: content,
        timestamp: Date.now()
    };

    chatState.ws.send(JSON.stringify(message));
    input.value = '';
    input.style.height = 'auto';
}

// 处理键盘事件
function handleMessageKeydown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

// HTML转义
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
