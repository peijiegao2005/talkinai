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
        <div class="mood-page">
            <div class="mood-container" style="max-width: 600px; margin: 0 auto; padding: 40px 24px;">
                <div id="mood-assessment-container">
                    <div class="mood-card" style="text-align: center; padding: 40px 32px;">
                        <h1 style="font-size: 28px; font-weight: 700; margin-bottom: 8px; color: #333;">此刻心情如何？</h1>
                        <p style="color: #666; font-size: 15px; margin-bottom: 24px;">和AI聊聊，让我们了解你当前的情绪状态</p>
                        <div style="font-size: 56px; margin-bottom: 20px;">🤖</div>
                        <h2 style="font-size: 22px; font-weight: 600; margin-bottom: 12px; color: #333;">AI心情评测</h2>
                        <p style="color: #666; margin-bottom: 32px; font-size: 15px; line-height: 1.6;">
                            通过和AI的自然对话，我们会为你推荐最适合的聊天室
                        </p>
                        <button class="mood-btn mood-btn-primary" style="padding: 16px 48px; font-size: 16px;" onclick="startMoodAssessment()">
                            开始评测
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// 开始心情评测 - 优先使用AI对话模式
async function startMoodAssessment() {
    try {
        // 优先调用AI评测接口
        const data = await post('/mood/ai-assessment/start', {});
        const result = data.data;

        if (result.assessmentType === 'CHOICE_QUESTIONS' || result.fallbackMode) {
            // AI不可用，降级到选择题模式
            if (result.fallbackMessage) {
                showToast(result.fallbackMessage, 'info');
            }
            renderMoodQuestion(result.sessionId, result.question, result.currentQuestion, result.totalQuestions);
        } else {
            // AI对话模式
            renderAiDialog(result.sessionId, result.aiMessage, result.currentRound, result.totalRounds);
        }
    } catch (error) {
        // AI接口调用失败，降级到选择题模式
        console.log('AI评测不可用，降级到选择题模式:', error);
        try {
            const data = await post('/mood/assessment/start', {});
            const result = data.data;
            renderMoodQuestion(result.sessionId, result.question, result.currentQuestion, result.totalQuestions);
        } catch (fallbackError) {
            showToast('开始评测失败: ' + fallbackError.message, 'error');
        }
    }
}

// 渲染AI对话界面
function renderAiDialog(sessionId, aiMessage, currentRound, totalRounds, isComplete = false) {
    const container = document.getElementById('mood-assessment-container');

    // 初始化消息历史（只在第一次调用时）
    if (!moodState.aiMessages) {
        moodState.aiMessages = [];
    }

    // 如果是新消息，添加到历史（避免重复添加）
    if (aiMessage && !moodState.aiMessages.find(m => m.role === 'ai' && m.content === aiMessage)) {
        moodState.aiMessages.push({
            role: 'ai',
            content: aiMessage
        });
    }

    // 计算进度
    const aiMsgCount = moodState.aiMessages.filter(m => m.role === 'ai').length;
    const progress = Math.min((aiMsgCount / 5) * 100, 100);

    const messagesHtml = moodState.aiMessages.map(msg => {
        if (msg.role === 'ai') {
            return `
                <div class="mood-msg" style="display: flex; gap: 12px; margin-bottom: 16px;">
                    <div style="width: 40px; height: 40px; background: linear-gradient(135deg, #FF6B9D, #DDA0DD); border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border: 3px solid white; box-shadow: 2px 2px 0px rgba(255, 107, 157, 0.3);">🤖</div>
                    <div style="background: rgba(255, 255, 255, 0.95); padding: 12px 16px; border-radius: 16px; max-width: 75%; line-height: 1.6; color: #333; border: 2px solid rgba(255, 182, 193, 0.4); box-shadow: 2px 2px 0px rgba(255, 182, 193, 0.2);">${msg.content}</div>
                </div>
            `;
        } else {
            return `
                <div class="mood-msg" style="display: flex; gap: 12px; margin-bottom: 16px; flex-direction: row-reverse;">
                    <div style="width: 40px; height: 40px; background: linear-gradient(135deg, #98FB98, #87CEEB); border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border: 3px solid white; box-shadow: 2px 2px 0px rgba(135, 206, 235, 0.3);">👤</div>
                    <div style="background: linear-gradient(135deg, rgba(152, 251, 152, 0.9), rgba(135, 206, 235, 0.9)); padding: 12px 16px; border-radius: 16px; max-width: 75%; line-height: 1.6; color: #333; border: 2px solid rgba(255, 255, 255, 0.5); box-shadow: 2px 2px 0px rgba(135, 206, 235, 0.3);">${msg.content}</div>
                </div>
            `;
        }
    }).join('');

    // 根据对话轮数显示不同的提示
    let progressText = `对话进行中`;
    if (aiMsgCount >= 5) {
        progressText = `对话已完成，可以继续聊或查看结果`;
    }

    // 构建输入区域
    let inputAreaHtml = '';
    if (isComplete) {
        // 评测已完成，显示完成按钮
        inputAreaHtml = `
            <div style="display: flex; gap: 12px; flex-direction: column;">
                <div style="text-align: center; color: #666; font-size: 14px; margin-bottom: 8px;">
                    💡 对话已完成！你可以继续和AI聊，或者查看评测结果
                </div>
                <div style="display: flex; gap: 12px;">
                    <input type="text" id="ai-message-input" placeholder="还想继续聊聊吗..." style="flex: 1; padding: 14px 18px; border: 2px dashed #FFB6C1; border-radius: 9999px; font-size: 15px; background: rgba(255, 255, 255, 0.9); outline: none; transition: all 0.3s;" onkeypress="if(event.key==='Enter') submitAiMessage('${sessionId}')">
                    <button class="mood-btn mood-btn-primary" style="padding: 14px 28px;" onclick="submitAiMessage('${sessionId}')">
                        发送
                    </button>
                </div>
                <button class="mood-btn mood-btn-secondary" style="width: 100%;" onclick="completeAiAssessment('${sessionId}')">
                    查看评测结果 ✨
                </button>
            </div>
        `;
    } else {
        // 评测进行中
        inputAreaHtml = `
            <div style="display: flex; gap: 12px;">
                <input type="text" id="ai-message-input" placeholder="输入你的回复..." style="flex: 1; padding: 14px 18px; border: 2px dashed #FFB6C1; border-radius: 9999px; font-size: 15px; background: rgba(255, 255, 255, 0.9); outline: none; transition: all 0.3s;" onkeypress="if(event.key==='Enter') submitAiMessage('${sessionId}')">
                <button class="mood-btn mood-btn-primary" style="padding: 14px 28px;" onclick="submitAiMessage('${sessionId}')">
                    发送
                </button>
            </div>
        `;
    }

    container.innerHTML = `
        <div class="mood-card" style="padding: 24px;">
            <div style="margin-bottom: 20px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; color: #666;">
                    <span style="font-weight: 600;">${progressText}</span>
                    <span style="font-weight: 600; color: #FF6B9D;">${Math.round(progress)}%</span>
                </div>
                <div style="height: 8px; background: rgba(255, 182, 193, 0.2); border-radius: 4px; overflow: hidden; border: 1px dashed #FFB6C1;">
                    <div style="height: 100%; width: ${progress}%; background: linear-gradient(90deg, #FF6B9D, #DDA0DD); border-radius: 4px; transition: width 0.3s;"></div>
                </div>
            </div>

            <div id="ai-chat-messages" style="height: 300px; overflow-y: auto; margin-bottom: 16px; padding: 12px; background: rgba(255, 255, 255, 0.5); border-radius: 12px; border: 1px dashed #FFB6C1;">
                ${messagesHtml}
            </div>

            ${inputAreaHtml}
        </div>
    `;

    // 滚动到底部
    setTimeout(() => {
        const chatMessages = document.getElementById('ai-chat-messages');
        if (chatMessages) {
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }
        // 聚焦输入框
        const input = document.getElementById('ai-message-input');
        if (input) input.focus();
    }, 100);
}

// 完成AI评测并查看结果
async function completeAiAssessment(sessionId) {
    try {
        // 调用完成评测的API
        const data = await post('/mood/ai-assessment/complete', { sessionId: sessionId });
        const result = data.data;

        moodState.assessmentCompleted = true;
        moodState.currentMood = result.assessmentResult?.primaryMood;
        moodState.aiMessages = []; // 清空消息历史
        renderMoodResult(result.assessmentResult);
    } catch (error) {
        showToast('获取评测结果失败: ' + error.message, 'error');
    }
}

// 提交AI对话消息
async function submitAiMessage(sessionId) {
    const input = document.getElementById('ai-message-input');
    const message = input.value.trim();

    if (!message) {
        showToast('请输入你的回复', 'warning');
        return;
    }

    // 禁用输入和按钮
    input.disabled = true;
    const btn = input.nextElementSibling;
    if (btn) btn.disabled = true;

    // 添加到本地消息历史
    moodState.aiMessages.push({
        role: 'user',
        content: message
    });

    // 重新渲染以显示用户消息（不添加新AI消息）
    const aiMsgCount = moodState.aiMessages.filter(m => m.role === 'ai').length;
    const lastAiMessage = moodState.aiMessages.filter(m => m.role === 'ai').pop()?.content || '';
    const isComplete = aiMsgCount >= 5;
    renderAiDialog(sessionId, null, aiMsgCount, 5, isComplete);

    try {
        const data = await post('/mood/ai-assessment/dialog', {
            sessionId: sessionId,
            userMessage: message
        });
        const result = data.data;

        if (result.completed) {
            // 评测数据已收集完成，但允许用户继续对话
            const newAiMsgCount = moodState.aiMessages.filter(m => m.role === 'ai').length + 1;
            moodState.aiMessages.push({
                role: 'ai',
                content: result.aiMessage
            });
            // 显示完成状态，但允许继续聊
            renderAiDialog(sessionId, null, newAiMsgCount, 5, true);
        } else if (result.fallbackMode) {
            // 降级到选择题模式
            moodState.aiMessages = [];
            if (result.fallbackMessage) {
                showToast(result.fallbackMessage, 'info');
            }
            renderMoodQuestion(result.sessionId, result.question, result.currentRound, result.totalRounds);
        } else {
            // 继续AI对话
            renderAiDialog(sessionId, result.aiMessage, result.currentRound, result.totalRounds, false);
        }
    } catch (error) {
        showToast('发送消息失败: ' + error.message, 'error');
        input.disabled = false;
        if (btn) btn.disabled = false;
    }
}

// 渲染心情问题（选择题降级模式）
function renderMoodQuestion(sessionId, question, current, total) {
    const container = document.getElementById('mood-assessment-container');

    const progress = (current / total) * 100;

    container.innerHTML = `
        <div class="mood-card" style="padding: 32px;">
            <div style="margin-bottom: 28px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; color: #666;">
                    <span style="font-weight: 600;">问题 ${current}/${total}</span>
                    <span style="font-weight: 600; color: #FF6B9D;">${Math.round(progress)}%</span>
                </div>
                <div style="height: 8px; background: rgba(255, 182, 193, 0.2); border-radius: 4px; overflow: hidden; border: 1px dashed #FFB6C1;">
                    <div style="height: 100%; width: ${progress}%; background: linear-gradient(90deg, #FF6B9D, #DDA0DD); border-radius: 4px; transition: width 0.3s;"></div>
                </div>
            </div>

            <h3 style="font-size: 18px; font-weight: 600; margin-bottom: 28px; line-height: 1.6; color: #333;">
                ${question.content}
            </h3>

            <div style="display: flex; flex-direction: column; gap: 12px;">
                ${question.options.map(opt => `
                    <button class="mood-option-btn" onclick="submitMoodAnswer('${sessionId}', ${current}, '${opt.label}')">
                        <span class="mood-option-label">${opt.label}</span>
                        <span class="mood-option-text">${opt.text}</span>
                    </button>
                `).join('')}
            </div>
        </div>
    `;
}

// 提交心情答案（选择题降级模式）
async function submitMoodAnswer(sessionId, questionNumber, selectedOption) {
    try {
        const data = await post('/mood/assessment/answer', {
            sessionId: sessionId,
            questionNumber: questionNumber,
            selectedOption: selectedOption
        });
        const result = data.data;

        if (result.completed) {
            await completeMoodAssessment(sessionId);
        } else {
            renderMoodQuestion(sessionId, result.nextQuestion, result.currentQuestion, result.totalQuestions);
        }
    } catch (error) {
        showToast('提交答案失败: ' + error.message, 'error');
    }
}

// 完成心情评测（选择题模式）
async function completeMoodAssessment(sessionId) {
    try {
        const data = await post('/mood/assessment/complete', { sessionId: sessionId });
        const result = data.data;

        moodState.assessmentCompleted = true;
        moodState.currentMood = result.primaryMood;

        renderMoodResult(result);
    } catch (error) {
        showToast('完成评测失败: ' + error.message, 'error');
    }
}

// 渲染评测结果
function renderMoodResult(result) {
    const container = document.getElementById('main-container');

    container.innerHTML = `
        <div class="mood-page">
            <div class="mood-container" style="max-width: 600px; margin: 0 auto; padding: 40px 24px;">
                <div style="text-align: center; margin-bottom: 32px;">
                    <div style="font-size: 64px; margin-bottom: 16px;">${result.recommendedRoomIcon}</div>
                    <h1 style="font-size: 28px; font-weight: 700; margin-bottom: 8px; color: #333;">评测完成！</h1>
                    <p style="color: #666; font-size: 15px;">根据你的心情，我们为你推荐以下聊天室</p>
                </div>

                <div class="mood-result-card" style="margin-bottom: 24px;">
                    <div style="font-size: 56px; margin-bottom: 16px; text-align: center;">${result.recommendedRoomIcon}</div>
                    <h2 style="font-size: 24px; font-weight: 700; text-align: center; margin-bottom: 8px; color: white;">${result.recommendedRoomName}</h2>
                    <p style="text-align: center; opacity: 0.95; margin-bottom: 20px; color: white; font-size: 15px;">${result.description}</p>
                    <div class="mood-result-info">
                        <div style="margin-bottom: 8px;"><strong>氛围：</strong>${result.atmosphere}</div>
                        <div><strong>心情强度：</strong>${result.intensity}%</div>
                    </div>
                </div>

                <div style="display: flex; gap: 12px;">
                    <button class="mood-btn mood-btn-primary" style="flex: 1;" onclick="joinRecommendedRoom('${result.recommendedRoomId}')">
                        进入推荐房间
                    </button>
                    <button class="mood-btn mood-btn-secondary" onclick="renderMoodRoomSelection(document.getElementById('main-container'))">
                        查看所有房间
                    </button>
                </div>
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
            <div class="mood-page">
                <div class="mood-container" style="max-width: 900px; margin: 0 auto; padding: 40px 24px;">
                    <div style="text-align: center; margin-bottom: 40px;">
                        <h1 style="font-size: 32px; font-weight: 700; margin-bottom: 8px; color: #333;">心情聊天室</h1>
                        <p style="color: #666; font-size: 15px;">选择一个适合你当前心情的房间</p>
                    </div>

                    <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px;">
                        ${rooms.map(room => `
                            <div class="mood-room-card" onclick="showRoomDetail('${room.roomId}')">
                                <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 16px;">
                                    <div style="font-size: 44px;">${room.icon}</div>
                                    <div>
                                        <h3 style="font-size: 18px; font-weight: 700; margin-bottom: 4px; color: #333;">${room.name}</h3>
                                        <div style="font-size: 13px; color: #888;">
                                            ${room.currentUsers || 0} 人在线
                                        </div>
                                    </div>
                                </div>
                                <p style="font-size: 14px; color: #666; margin-bottom: 12px; line-height: 1.5;">
                                    ${room.description}
                                </p>
                                <div style="display: flex; flex-wrap: wrap; gap: 6px;">
                                    ${(room.emotions || []).map(emotion => `
                                        <span class="mood-tag">${emotion}</span>
                                    `).join('')}
                                </div>
                            </div>
                        `).join('')}
                    </div>
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
            <div class="mood-page">
                <div class="mood-container" style="max-width: 600px; margin: 0 auto; padding: 40px 24px;">
                    <button class="mood-btn mood-btn-secondary" style="margin-bottom: 24px; padding: 10px 20px; font-size: 14px;" onclick="renderMoodPage()">
                        ← 返回房间列表
                    </button>

                    <div class="mood-card" style="padding: 32px; margin-bottom: 24px;">
                        <div style="font-size: 64px; text-align: center; margin-bottom: 16px;">${room.icon}</div>
                        <h1 style="font-size: 24px; font-weight: 700; text-align: center; margin-bottom: 8px; color: #333;">${room.name}</h1>
                        <p style="text-align: center; color: #666; margin-bottom: 24px; font-size: 15px;">${room.description}</p>

                        <div class="mood-info-box" style="margin-bottom: 24px;">
                            <div style="margin-bottom: 16px;">
                                <div style="font-size: 12px; color: #888; margin-bottom: 4px;">典型场景</div>
                                <div style="font-size: 14px; color: #333;">${room.typicalScenario}</div>
                            </div>
                            <div>
                                <div style="font-size: 12px; color: #888; margin-bottom: 4px;">房间氛围</div>
                                <div style="font-size: 14px; color: #333;">${room.atmosphere}</div>
                            </div>
                        </div>

                        <div style="margin-bottom: 24px;">
                            <div style="font-size: 14px; font-weight: 600; margin-bottom: 12px; color: #333;">在线用户 (${room.onlineUsers?.length || 0})</div>
                            <div style="display: flex; flex-wrap: wrap; gap: 8px;">
                                ${(room.onlineUsers || []).map(user => `
                                    <div class="mood-user-tag">
                                        <span>${user.avatar || '👤'}</span>
                                        <span>${user.nickname}</span>
                                        ${user.allowDirectMessage ? '<span style="color: #FF6B9D;">💬</span>' : ''}
                                    </div>
                                `).join('')}
                            </div>
                        </div>

                        <button class="mood-btn mood-btn-primary" style="width: 100%;" onclick="joinRoom('${room.roomId}')">
                            进入聊天室
                        </button>
                    </div>
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

    if (moodState.messages.length > 100) {
        moodState.messages = moodState.messages.slice(-50);
    }

    updateMoodChatMessages();
}

// 渲染聊天室界面
function renderMoodChatRoom(container) {
    container.innerHTML = `
        <div class="mood-chat-page">
            <div class="mood-chat-header">
                <div style="display: flex; align-items: center; gap: 12px;">
                    <button class="mood-btn mood-btn-secondary" style="padding: 8px 14px; font-size: 14px;" onclick="leaveMoodRoom()">
                        ←
                    </button>
                    <span style="font-weight: 700; color: #333; font-size: 16px;">心情聊天室</span>
                </div>
                <div style="display: flex; align-items: center; gap: 8px; font-size: 13px; color: #4CAF50; font-weight: 500;">
                    <span style="width: 8px; height: 8px; background: #4CAF50; border-radius: 50%;"></span>
                    <span>实时聊天中</span>
                </div>
            </div>

            <div id="mood-chat-messages" class="mood-chat-messages">
                <div class="mood-chat-tip">
                    💡 聊天消息不会被永久保存，刷新页面后将清空
                </div>
            </div>

            <div class="mood-chat-input-area">
                <div class="mood-chat-input-container">
                    <input type="text" id="mood-chat-input" class="mood-chat-input" placeholder="分享你的心情..."
                           onkeypress="if(event.key==='Enter') sendMoodMessage()">
                    <button class="mood-chat-send-btn" onclick="sendMoodMessage()">
                        ➤
                    </button>
                </div>
            </div>
        </div>
    `;

    loadMoodRoomHistory();
}

// 加载历史消息
async function loadMoodRoomHistory() {
    if (!moodState.currentRoom) return;

    try {
        const response = await get(`/mood/rooms/${moodState.currentRoom}/history`);
        const messages = response.data || [];

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
        <div class="mood-chat-tip">
            💡 聊天消息不会被永久保存，刷新页面后将清空
        </div>
        ${moodState.messages.map(msg => {
            const isMe = msg.userId === currentUserId;
            const isSystem = msg.userId === 'system';

            if (isSystem) {
                return `
                    <div style="text-align: center; padding: 8px;">
                        <span class="mood-system-msg">${msg.content}</span>
                    </div>
                `;
            }

            if (isMe) {
                return `
                    <div class="mood-msg mood-msg-own">
                        <div class="mood-msg-bubble mood-msg-bubble-own">
                            <div class="mood-msg-name">${msg.nickname}</div>
                            <div class="mood-msg-text">${escapeHtml(msg.content)}</div>
                        </div>
                    </div>
                `;
            }

            return `
                <div class="mood-msg">
                    <div class="mood-msg-avatar">${msg.avatar || '👤'}</div>
                    <div class="mood-msg-content">
                        <div class="mood-msg-name">
                            ${msg.nickname}
                            ${msg.messageType === 'INVITE' ? '<span style="color: #FF6B9D; margin-left: 4px;">💌</span>' : ''}
                        </div>
                        <div class="mood-msg-bubble">
                            <div class="mood-msg-text">${escapeHtml(msg.content)}</div>
                        </div>
                    </div>
                </div>
            `;
        }).join('')}
    `;

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
