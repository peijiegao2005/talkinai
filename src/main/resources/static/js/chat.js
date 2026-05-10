// 聊天功能

let chatState = {
    ws: null,
    currentRoom: null,
    currentUser: null,
    messages: [],
    rooms: [],
    reconnectTimer: null,
    loadedOnce: false,
    myUserId: ''    // 缓存当前登录用户ID，避免 getUseInfo() 被覆盖
};

// 初始化：缓存自己的 userId
(function initChatState() {
    var info = getUserInfo();
    if (info) chatState.myUserId = info.userId || '';
})();

function renderChatPage() {
    const container = document.getElementById('main-container');
    container.innerHTML = `
        <div class="chat-page">
            <div class="chat-sidebar">
                <div class="chat-sidebar-header"><h3 style="font-weight: 600;">消息</h3></div>
                <div class="chat-list" id="chat-list"></div>
            </div>
            <div class="chat-main" id="chat-main">
                <div class="empty-state"><div class="empty-icon">💬</div><p>选择一个聊天开始对话</p></div>
            </div>
        </div>`;

    loadChatRooms();

    const rid = localStorage.getItem('currentChatRoom');
    if (rid) {
        const user = JSON.parse(localStorage.getItem('currentChatUser') || '{}');
        localStorage.removeItem('currentChatRoom');
        localStorage.removeItem('currentChatUser');
        openChatRoom(rid, user);
    }
}

async function loadChatRooms() {
    try {
        const data = await get('/chat/rooms');
        chatState.rooms = Array.isArray(data.data) ? data.data : [];
    } catch (e) {
        console.error('加载聊天列表失败:', e);
        chatState.rooms = [];
    }
    renderChatList();
}

function renderChatList() {
    var list = document.getElementById('chat-list');
    if (!list) return;

    if (!chatState.rooms || !chatState.rooms.length) {
        list.innerHTML = '<div class="empty-state" style="padding:40px 24px;"><p style="font-size:14px;">暂无聊天</p></div>';
        return;
    }

    var currentUserId = chatState.myUserId;
    list.innerHTML = chatState.rooms.map(function(room) {
        if (!room || !room.roomId) return '';
        var partnerId = '';
        if (room.participants && room.participants.length > 1) {
            for (var i = 0; i < room.participants.length; i++) {
                if (room.participants[i] !== currentUserId) { partnerId = room.participants[i]; break; }
            }
        }
        var name = room.name || '私聊';
        return '<div class="chat-item' + (chatState.currentRoom === room.roomId ? ' active' : '') + '"'
            + ' onclick="openChatRoom(\'' + room.roomId + '\',{nickname:\'' + escapeHtml(name) + '\',userId:\'' + partnerId + '\'})">'
            + '<div class="chat-item-avatar">👤</div>'
            + '<div class="chat-item-info"><div class="chat-item-name">' + escapeHtml(name) + '</div>'
            + '<div class="chat-item-preview">点击开始聊天</div></div></div>';
    }).join('');
}

function openChatRoom(roomId, user) {
    if (!roomId || !user) return;
    console.log('[openChatRoom] roomId=' + roomId + ' user.userId=' + user.userId + ' user.nickname=' + user.nickname);
    chatState.currentRoom = roomId;
    chatState.currentUser = user;
    chatState.messages = [];

    renderChatList();
    renderChatWindow();
    connectWebSocket();

    get('/chat/messages/room/' + roomId + '?page=0&size=50').then(function(data) {
        var arr = Array.isArray(data.data) ? data.data : [];
        chatState.messages = arr.reverse();
        renderMessages();
    }).catch(function(e) {
        console.error('加载消息失败:', e);
    });
}

function renderChatWindow() {
    var main = document.getElementById('chat-main');
    var user = chatState.currentUser;
    main.innerHTML =
        '<div class="chat-header"><div class="chat-header-info">'
        + '<div class="chat-header-avatar">👤</div>'
        + '<div><div class="chat-header-name">' + escapeHtml(user && user.nickname ? user.nickname : '未知用户') + '</div>'
        + '<div class="chat-header-status" id="chat-status">● 连接中...</div></div></div></div>'
        + '<div class="chat-messages-area" id="chat-messages-area"></div>'
        + '<div class="chat-input-area"><div class="chat-input-container">'
        + '<textarea class="chat-input" id="message-input" placeholder="输入消息..." rows="1" onkeydown="handleMessageKeydown(event)"></textarea>'
        + '<button class="chat-send-btn" onclick="sendMessage()">➤</button></div></div>';

    var input = document.getElementById('message-input');
    if (input) {
        input.addEventListener('input', function() {
            this.style.height = 'auto';
            this.style.height = Math.min(this.scrollHeight, 120) + 'px';
        });
    }
}

function renderMessages() {
    var container = document.getElementById('chat-messages-area');
    if (!container) return;
    var currentUserId = chatState.myUserId;
    console.log('[renderMessages] myId=' + currentUserId + ' messages=' + chatState.messages.length);

    container.innerHTML = chatState.messages.map(function(msg) {
        var isOwn = msg.senderId === currentUserId;
        console.log('[renderMessages] msg.senderId=' + msg.senderId + ' myId=' + currentUserId + ' isOwn=' + isOwn + ' content=' + (msg.content||'').substring(0,10));
        var time = msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'}) : '';
        return '<div class="chat-message' + (isOwn ? ' own' : '') + '">'
            + '<div class="chat-message-avatar">' + (isOwn ? '我' : '👤') + '</div>'
            + '<div class="chat-message-content"><div class="chat-message-bubble">' + escapeHtml(msg.content) + '</div>'
            + '<div class="chat-message-time">' + time + '</div></div></div>';
    }).join('');

    container.scrollTop = container.scrollHeight;
}

function connectWebSocket() {
    var token = getToken();
    if (!token) return;

    stopReconnect();

    if (chatState.ws) {
        chatState.ws.onclose = null;
        chatState.ws.close();
    }

    var wsUrl = 'ws://' + window.location.host + '/ws/chat?token=' + token;
    chatState.ws = new WebSocket(wsUrl);

    chatState.ws.onopen = function() {
        console.log('WebSocket连接成功');
        updateChatStatus('● 在线');
        if (chatState.currentRoom) {
            chatState.ws.send(JSON.stringify({type:'JOIN_ROOM',roomId:chatState.currentRoom}));
        }
    };

    chatState.ws.onmessage = function(event) {
        try {
            var data = JSON.parse(event.data);
            handleWebSocketMessage(data);
        } catch(e) {
            console.error('WS消息解析失败:', e);
        }
    };

    chatState.ws.onerror = function(err) {
        console.error('WebSocket错误:', err);
        updateChatStatus('● 连接异常');
    };

    chatState.ws.onclose = function(event) {
        console.log('WebSocket关闭, code:', event.code);
        updateChatStatus('● 连接断开, 重连中...');
        scheduleReconnect();
    };
}

function scheduleReconnect() {
    stopReconnect();
    chatState.reconnectTimer = setInterval(function() {
        console.log('尝试重连WebSocket...');
        connectWebSocket();
    }, 3000);
}

function stopReconnect() {
    if (chatState.reconnectTimer) {
        clearInterval(chatState.reconnectTimer);
        chatState.reconnectTimer = null;
    }
}

function updateChatStatus(text) {
    var el = document.getElementById('chat-status');
    if (el) el.textContent = text;
}

function handleWebSocketMessage(data) {
    if (data.type === 'CHAT') {
        console.log('[WS] CHAT received: senderId=' + data.senderId + ' _localId=' + data._localId + ' content=' + (data.content||'').substring(0,10));
        if (data._localId) {
            for (var i = chatState.messages.length - 1; i >= 0; i--) {
                if (chatState.messages[i]._localId === data._localId) {
                    console.log('[WS] replacing local msg[' + i + '] with server echo');
                    chatState.messages[i] = data;
                    renderMessages();
                    return;
                }
            }
            console.log('[WS] _localId not found locally, deleting it (receiver side)');
            delete data._localId;
        }
        chatState.messages.push(data);
        renderMessages();
    } else if (data.type === 'MATCHED') {
        var matchedUser = {userId:data.matchUserId, nickname:data.matchNickname||'匹配用户', avatar:data.matchAvatar};
        localStorage.setItem('currentChatRoom', data.roomId);
        localStorage.setItem('currentChatUser', JSON.stringify(matchedUser));
        loadChatRooms().then(function() { openChatRoom(data.roomId, matchedUser); });
    } else if (data.type === 'SYSTEM') {
        console.log('系统消息:', data.content);
    }
}

function sendMessage() {
    var input = document.getElementById('message-input');
    if (!input) return;
    var content = input.value.trim();
    if (!content) return;

    if (!chatState.ws || !chatState.currentUser) {
        showToast('未连接到聊天服务', 'error');
        return;
    }
    if (chatState.ws.readyState !== WebSocket.OPEN) {
        showToast('正在连接中，请稍候再发', 'error');
        return;
    }

    var localId = 'local_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6);
    var currentUserId = chatState.myUserId;
    var rcvrId = chatState.currentUser ? chatState.currentUser.userId : '';

    console.log('[sendMessage] myId=' + currentUserId + ' receiverId=' + rcvrId + ' content=' + content + ' localId=' + localId);

    // 乐观显示本地消息（带 _localId 标记）
    var localMsg = {_localId:localId, senderId:currentUserId, content:content, timestamp:Date.now()};
    chatState.messages.push(localMsg);
    renderMessages();

    // 发送给服务器（包含 _localId 用于回显去重）
    chatState.ws.send(JSON.stringify({
        type:'CHAT',
        receiverId:chatState.currentUser.userId,
        content:content,
        timestamp:Date.now(),
        _localId:localId
    }));

    input.value = '';
    input.style.height = 'auto';
}

function handleMessageKeydown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
