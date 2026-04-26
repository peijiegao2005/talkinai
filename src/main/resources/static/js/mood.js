// 心情聊天室功能

let moodState = {
    currentRoom: null,
    wsConnection: null,
    messages: [],
    onlineUsers: [],
    assessmentCompleted: false,
    currentMood: null
};

// 渲染心情聊天室入口页面
function renderMoodPage() {
    const container = document.getElementById('main-container');

    if (!moodState.assessmentCompleted) {
        renderMoodAssessment(container);
    } else if (moodState.currentRoom) {
        renderMoodChatRoom(container);
    } else {
        renderMoodRoomSelection(container);
    }
}

// 渲染心情评测
function renderMoodAssessment(container) {
    container.innerHTML = `
        <div class="page" style="max-width: 600px; margin: 0 auto; padding: 40px 24px;">
            <div style="text-align: center; margin-bottom: 40px;">
                <div style="font-size: 64px; margin-bottom: 16px;">🎭</div>
                <h1 style="font-size: 24px; font-weight: 600; margin-bottom: 8px;">此刻心情如何？</h1>
                <p style="color: var(--text-secondary);">通过5道简单的问题，让我们了解你当前的情绪状态</p>
            </div>

            <div id="mood-assessment-container">
                <div style="background: var(--bg-secondary); border-radius: 16px; padding: 32px; text-align: center;">
                    <div style="font-size: 48px; margin-bottom: 16px;">✨</div>
                    <h2 style="margin-bottom: 12px;">开始心情评测</h2>
                    <p style="color: var(--text-secondary); margin-bottom: 24px;">
                        根据你当前的真实感受回答，我们会为你推荐最适合的聊天室
                    </p>
                    <button class="btn btn-primary btn-large" onclick="startMoodAssessment()">
                        开始评测
                    </button>
                </div>
            </div>
        </div>
    `;
}

// 开始心情评测
async function startMoodAssessment() {
    try {
        const data = await post('/mood/assessment/start', {});
        const result = data.data;

        renderMoodQuestion(result.sessionId, result.question, result.currentQuestion, result.totalQuestions);
    } catch (error) {
        showToast('开始评测失败: ' + error.message, 'error');
    }
}

// 渲染心情问题
function renderMoodQuestion(sessionId, question, current, total) {
    const container = document.getElementById('mood-assessment-container');

    const progress = (current / total) * 100;

    container.innerHTML = `
        <div style="background: var(--bg-secondary); border-radius: 16px; padding: 32px;">
            <div style="margin-bottom: 24px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px;">
                    <span>问题 ${current}/${total}</span>
                    <span>${Math.round(progress)}%</span>
                </div>
                <div style="height: 6px; background: var(--bg-tertiary); border-radius: 3px; overflow: hidden;">
                    <div style="height: 100%; width: ${progress}%; background: linear-gradient(90deg, var(--gradient-1), var(--gradient-2)); border-radius: 3px; transition: width 0.3s;"></div>
                </div>
            </div>

            <h3 style="font-size: 18px; font-weight: 500; margin-bottom: 24px; line-height: 1.6;">
                ${question.content}
            </h3>

            <div style="display: flex; flex-direction: column; gap: 12px;">
                ${question.options.map(opt => `
                    <button class="btn btn-secondary" style="text-align: left; padding: 16px 20px; justify-content: flex-start;"
                            onclick="submitMoodAnswer('${sessionId}', ${current}, '${opt.label}')">
                        <span style="font-weight: 600; margin-right: 12px; color: var(--gradient-1);">${opt.label}.</span>
                        <span>${opt.text}</span>
                    </button>
                `).join('')}
            </div>
        </div>
    `;
}

// 提交心情答案
async function submitMoodAnswer(sessionId, questionNumber, selectedOption) {
    try {
        const data = await post('/mood/assessment/answer', {
            sessionId: sessionId,
            questionNumber: questionNumber,
            selectedOption: selectedOption
        });
        const result = data.data;

        if (result.completed) {
            // 完成评测
            await completeMoodAssessment(sessionId);
        } else {
            renderMoodQuestion(sessionId, result.nextQuestion, result.currentQuestion, result.totalQuestions);
        }
    } catch (error) {
        showToast('提交答案失败: ' + error.message, 'error');
    }
}

// 完成心情评测
async function completeMoodAssessment(sessionId) {
    try {
        const data = await post('/mood/assessment/complete', { sessionId: sessionId });
        const result = data.data;

        moodState.assessmentCompleted = true;
        moodState.currentMood = result.primaryMood;

        // 显示评测结果
        renderMoodResult(result);
    } catch (error) {
        showToast('完成评测失败: ' + error.message, 'error');
    }
}

// 渲染评测结果
function renderMoodResult(result) {
    const container = document.getElementById('main-container');

    container.innerHTML = `
        <div class="page" style="max-width: 600px; margin: 0 auto; padding: 40px 24px;">
            <div style="text-align: center; margin-bottom: 32px;">
                <div style="font-size: 64px; margin-bottom: 16px;">${result.recommendedRoomIcon}</div>
                <h1 style="font-size: 24px; font-weight: 600; margin-bottom: 8px;">评测完成！</h1>
                <p style="color: var(--text-secondary);">根据你的心情，我们为你推荐以下聊天室</p>
            </div>

            <div style="background: linear-gradient(135deg, var(--gradient-1), var(--gradient-2)); border-radius: 16px; padding: 32px; color: white; margin-bottom: 24px;">
                <div style="font-size: 48px; margin-bottom: 16px; text-align: center;">${result.recommendedRoomIcon}</div>
                <h2 style="font-size: 24px; font-weight: 600; text-align: center; margin-bottom: 8px;">${result.recommendedRoomName}</h2>
                <p style="text-align: center; opacity: 0.9; margin-bottom: 16px;">${result.description}</p>
                <div style="background: rgba(255,255,255,0.2); border-radius: 8px; padding: 12px 16px; font-size: 14px;">
                    <div style="margin-bottom: 4px;"><strong>氛围：</strong>${result.atmosphere}</div>
                    <div><strong>心情强度：</strong>${result.intensity}%</div>
                </div>
            </div>

            <div style="display: flex; gap: 12px;">
                <button class="btn btn-primary btn-large" style="flex: 1;" onclick="joinRecommendedRoom('${result.recommendedRoomId}')">
                    进入推荐房间
                </button>
                <button class="btn btn-secondary" onclick="renderMoodRoomSelection(document.getElementById('main-container'))">
                    查看所有房间
                </button>
            </div>
        </div>
    `;
}

// 渲染房间选择
async function renderMoodRoomSelection(container) {
    try {
        const response = await get('/mood/rooms');
        const rooms = response.data || [];

        container.innerHTML = `
            <div class="page" style="max-width: 900px; margin: 0 auto; padding: 40px 24px;">
                <div style="text-align: center; margin-bottom: 40px;">
                    <h1 style="font-size: 28px; font-weight: 600; margin-bottom: 8px;">心情聊天室</h1>
                    <p style="color: var(--text-secondary);">选择一个适合你当前心情的房间</p>
                </div>

                <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px;">
                    ${rooms.map(room => `
                        <div style="background: var(--bg-secondary); border-radius: 16px; padding: 24px; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;"
                             onmouseover="this.style.transform='translateY(-4px)'; this.style.boxShadow='0 8px 24px rgba(0,0,0,0.15)'"
                             onmouseout="this.style.transform=''; this.style.boxShadow=''"
                             onclick="showRoomDetail('${room.roomId}')">
                            <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 16px;">
                                <div style="font-size: 40px;">${room.icon}</div>
                                <div>
                                    <h3 style="font-size: 18px; font-weight: 600; margin-bottom: 4px;">${room.name}</h3>
                                    <div style="font-size: 13px; color: var(--text-secondary);">
                                        ${room.currentUsers || 0} 人在线
                                    </div>
                                </div>
                            </div>
                            <p style="font-size: 14px; color: var(--text-secondary); margin-bottom: 12px; line-height: 1.5;">
                                ${room.description}
                            </p>
                            <div style="display: flex; flex-wrap: wrap; gap: 6px;">
                                ${(room.emotions || []).map(emotion => `
                                    <span style="background: var(--bg-tertiary); padding: 4px 10px; border-radius: 12px; font-size: 12px;">${emotion}</span>
                                `).join('')}
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    } catch (error) {
        showToast('获取房间列表失败: ' + error.message, 'error');
    }
}

// 显示房间详情
async function showRoomDetail(roomId) {
    try {
        const response = await get(`/mood/rooms/${roomId}`);
        const room = response.data;

        const container = document.getElementById('main-container');
        container.innerHTML = `
            <div class="page" style="max-width: 600px; margin: 0 auto; padding: 40px 24px;">
                <button class="btn btn-secondary" style="margin-bottom: 24px;" onclick="renderMoodPage()">
                    ← 返回房间列表
                </button>

                <div style="background: var(--bg-secondary); border-radius: 16px; padding: 32px; margin-bottom: 24px;">
                    <div style="font-size: 64px; text-align: center; margin-bottom: 16px;">${room.icon}</div>
                    <h1 style="font-size: 24px; font-weight: 600; text-align: center; margin-bottom: 8px;">${room.name}</h1>
                    <p style="text-align: center; color: var(--text-secondary); margin-bottom: 24px;">${room.description}</p>

                    <div style="background: var(--bg-tertiary); border-radius: 12px; padding: 20px; margin-bottom: 24px;">
                        <div style="margin-bottom: 16px;">
                            <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 4px;">典型场景</div>
                            <div style="font-size: 14px;">${room.typicalScenario}</div>
                        </div>
                        <div>
                            <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 4px;">房间氛围</div>
                            <div style="font-size: 14px;">${room.atmosphere}</div>
                        </div>
                    </div>

                    <div style="margin-bottom: 24px;">
                        <div style="font-size: 14px; font-weight: 500; margin-bottom: 12px;">在线用户 (${room.onlineUsers?.length || 0})</div>
                        <div style="display: flex; flex-wrap: wrap; gap: 8px;">
                            ${(room.onlineUsers || []).map(user => `
                                <div style="display: flex; align-items: center; gap: 6px; background: var(--bg-tertiary); padding: 6px 12px; border-radius: 20px; font-size: 13px;">
                                    <span>${user.avatar || '👤'}</span>
                                    <span>${user.nickname}</span>
                                    ${user.allowDirectMessage ? '<span style="color: var(--gradient-1);">💬</span>' : ''}
                                </div>
                            `).join('')}
                        </div>
                    </div>

                    <button class="btn btn-primary btn-large" style="width: 100%;" onclick="joinRoom('${room.roomId}')">
                        进入聊天室
                    </button>
                </div>
            </div>
        `;
    } catch (error) {
        showToast('获取房间详情失败: ' + error.message, 'error');
    }
}

// 加入推荐房间
async function joinRecommendedRoom(roomId) {
    await joinRoom(roomId);
}

// 加入房间
async function joinRoom(roomId) {
    try {
        await post(`/mood/rooms/${roomId}/join`, {
            currentMood: moodState.currentMood || 'neutral',
            allowDirectMessage: true
        });

        moodState.currentRoom = roomId;
        renderMoodChatRoom(document.getElementById('main-container'));
        connectMoodRoomWebSocket(roomId);

        showToast('成功加入聊天室', 'success');
    } catch (error) {
        showToast('加入房间失败: ' + error.message, 'error');
    }
}

// 连接WebSocket
function connectMoodRoomWebSocket(roomId) {
    const token = localStorage.getItem('token');
    if (!token) return;

    const wsUrl = `ws://localhost:8080/ws/mood-room?token=${token}&roomId=${roomId}`;

    moodState.wsConnection = new WebSocket(wsUrl);

    moodState.wsConnection.onopen = () => {
        console.log('心情聊天室WebSocket已连接');
        // 发送心跳
        setInterval(() => {
            if (moodState.wsConnection && moodState.wsConnection.readyState === WebSocket.OPEN) {
                moodState.wsConnection.send(JSON.stringify({ type: 'PING' }));
            }
        }, 30000);
    };

    moodState.wsConnection.onmessage = (event) => {
        const message = JSON.parse(event.data);
        handleMoodRoomMessage(message);
    };

    moodState.wsConnection.onclose = () => {
        console.log('心情聊天室WebSocket已断开');
    };

    moodState.wsConnection.onerror = (error) => {
        console.error('WebSocket错误:', error);
    };
}

// 处理收到的消息
function handleMoodRoomMessage(message) {
    moodState.messages.push(message);

    // 限制消息数量，保持实时性
    if (moodState.messages.length > 100) {
        moodState.messages = moodState.messages.slice(-50);
    }

    // 更新UI
    updateMoodChatMessages();
}

// 渲染聊天室界面
function renderMoodChatRoom(container) {
    container.innerHTML = `
        <div class="page" style="height: calc(100vh - 80px); display: flex; flex-direction: column;">
            <!-- 聊天室头部 -->
            <div style="display: flex; align-items: center; justify-content: space-between; padding: 16px 24px; border-bottom: 1px solid var(--border-color);">
                <div style="display: flex; align-items: center; gap: 12px;">
                    <button class="btn btn-secondary" style="padding: 8px 12px;" onclick="leaveMoodRoom()">
                        ←
                    </button>
                    <span style="font-weight: 600;">心情聊天室</span>
                </div>
                <div style="display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-secondary);">
                    <span style="width: 8px; height: 8px; background: #4ade80; border-radius: 50%;"></span>
                    <span>实时聊天中</span>
                </div>
            </div>

            <!-- 消息区域 -->
            <div id="mood-chat-messages" style="flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 12px;">
                <div style="text-align: center; padding: 20px; color: var(--text-secondary); font-size: 13px;">
                    💡 聊天消息不会被永久保存，刷新页面后将清空
                </div>
            </div>

            <!-- 输入区域 -->
            <div style="padding: 16px 24px; border-top: 1px solid var(--border-color);">
                <div style="display: flex; gap: 12px;">
                    <input type="text" id="mood-chat-input" placeholder="分享你的心情..."
                           style="flex: 1; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 24px; padding: 12px 20px; color: var(--text-primary); outline: none;"
                           onkeypress="if(event.key==='Enter') sendMoodMessage()">
                    <button class="btn btn-primary" onclick="sendMoodMessage()">
                        发送
                    </button>
                </div>
            </div>
        </div>
    `;

    // 加载历史消息
    loadMoodRoomHistory();
}

// 加载历史消息
async function loadMoodRoomHistory() {
    if (!moodState.currentRoom) return;

    try {
        const response = await get(`/mood/rooms/${moodState.currentRoom}/history`);
        const messages = response.data || [];

        // 反转消息顺序（最新的在后面）
        moodState.messages = messages.reverse();
        updateMoodChatMessages();
    } catch (error) {
        console.error('加载历史消息失败:', error);
    }
}

// 更新聊天消息显示
function updateMoodChatMessages() {
    const container = document.getElementById('mood-chat-messages');
    if (!container) return;

    const currentUserId = getCurrentUserId();

    container.innerHTML = `
        <div style="text-align: center; padding: 12px; color: var(--text-secondary); font-size: 12px; background: var(--bg-secondary); border-radius: 8px; margin-bottom: 16px;">
            💡 聊天消息不会被永久保存，刷新页面后将清空
        </div>
        ${moodState.messages.map(msg => {
            const isMe = msg.userId === currentUserId;
            const isSystem = msg.userId === 'system';

            if (isSystem) {
                return `
                    <div style="text-align: center; padding: 8px;">
                        <span style="font-size: 12px; color: var(--text-secondary); background: var(--bg-secondary); padding: 4px 12px; border-radius: 12px;">
                            ${msg.content}
                        </span>
                    </div>
                `;
            }

            if (isMe) {
                return `
                    <div style="display: flex; justify-content: flex-end; gap: 8px;">
                        <div style="max-width: 70%; background: linear-gradient(135deg, var(--gradient-1), var(--gradient-2)); color: white; padding: 12px 16px; border-radius: 16px 16px 4px 16px;">
                            <div style="font-size: 13px; margin-bottom: 4px; opacity: 0.9;">${msg.nickname}</div>
                            <div style="font-size: 14px;">${escapeHtml(msg.content)}</div>
                        </div>
                    </div>
                `;
            }

            return `
                <div style="display: flex; gap: 8px;">
                    <div style="width: 36px; height: 36px; border-radius: 50%; background: var(--bg-tertiary); display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0;">
                        ${msg.avatar || '👤'}
                    </div>
                    <div style="max-width: 70%;">
                        <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 2px; margin-left: 4px;">
                            ${msg.nickname}
                            ${msg.messageType === 'INVITE' ? '<span style="color: var(--gradient-1); margin-left: 4px;">💌</span>' : ''}
                        </div>
                        <div style="background: var(--bg-secondary); padding: 12px 16px; border-radius: 4px 16px 16px 16px; font-size: 14px;">
                            ${escapeHtml(msg.content)}
                        </div>
                    </div>
                </div>
            `;
        }).join('')}
    `;

    // 滚动到底部
    container.scrollTop = container.scrollHeight;
}

// 发送消息
function sendMoodMessage() {
    const input = document.getElementById('mood-chat-input');
    const content = input.value.trim();

    if (!content || !moodState.wsConnection) return;

    moodState.wsConnection.send(JSON.stringify({
        type: 'CHAT',
        content: content
    }));

    input.value = '';
}

// 离开房间
async function leaveMoodRoom() {
    if (moodState.wsConnection) {
        moodState.wsConnection.close();
        moodState.wsConnection = null;
    }

    try {
        await post('/mood/rooms/leave', {});
    } catch (error) {
        console.error('离开房间失败:', error);
    }

    moodState.currentRoom = null;
    moodState.messages = [];
    renderMoodPage();
}

// 辅助函数
function getCurrentUserId() {
    // 从token中解析用户ID
    const token = localStorage.getItem('token');
    if (!token) return null;

    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.sub;
    } catch (e) {
        return null;
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
