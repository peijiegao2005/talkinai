// 灵魂匹配功能

let matchState = {
    isSearching: false,
    matchedUser: null,
    roomId: null,
    matchDetails: null,
    pollTimer: null,
    bgWs: null  // 后台WebSocket，等待匹配通知
};

// 渲染匹配页面
function renderMatchPage() {
    const container = document.getElementById('main-container');

    // 检查是否已完成评估
    checkAssessmentStatus().then(hasAssessment => {
        if (!hasAssessment) {
            container.innerHTML = `
                <div class="match-page">
                    <div class="match-content">
                        <h2 style="margin-bottom: 12px; font-size: 24px;">请先完成人格评估</h2>
                        <p style="margin-bottom: 32px; font-size: 14px;">
                            完成评估后，我们才能为你找到最合适的灵魂伴侣
                        </p>
                        <button class="match-btn" onclick="navigateTo('assessment')">
                            去评估
                        </button>
                    </div>
                </div>
            `;
            return;
        }

        if (matchState.matchedUser) {
            renderMatchResult(container);
        } else {
            renderMatchSearch(container);
        }
    });
}

// 渲染匹配搜索界面
function renderMatchSearch(container) {
    container.innerHTML = `
        <div class="match-page">
            <div class="match-content">
                <h1 style="margin-bottom: 16px; font-size: 28px; font-weight: 700;">寻找灵魂伴侣</h1>
                <p style="max-width: 400px; margin: 0 auto 40px; font-size: 14px;">
                    基于你的人格特质和兴趣爱好，我们将为你匹配最契合的TA
                </p>
                <div style="max-width: 400px; margin: 0 auto 32px; text-align: left;">
                    <div style="font-size: 14px; margin-bottom: 12px; font-weight: 600;">匹配算法说明：</div>
                    <div style="display: flex; flex-direction: column; gap: 8px; font-size: 13px;">
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <span style="color: var(--gradient-1);">●</span>
                            <span>人格相似度 (50%) - 基于大五人格理论</span>
                        </div>
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <span style="color: var(--gradient-2);">●</span>
                            <span>共同爱好 (30%) - 兴趣契合度</span>
                        </div>
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <span style="color: var(--gradient-3);">●</span>
                            <span>在线状态 (20%) - 实时互动</span>
                        </div>
                    </div>
                </div>
                <button class="match-btn" id="match-btn" onclick="startMatching()">
                    开始匹配
                </button>
            </div>
        </div>
    `;
}

// 开始匹配
async function startMatching() {
    const btn = document.getElementById('match-btn');
    btn.disabled = true;
    btn.innerHTML = '<span class="loading"></span> 寻找中...';

    matchState.isSearching = true;

    try {
        const data = await post('/match/find-soul', {});
        const result = data.data;

        if (result.matched) {
            // 匹配成功
            stopPolling();
            matchState.matchedUser = {
                userId: result.matchUserId,
                nickname: result.matchNickname,
                avatar: result.matchAvatar
            };
            matchState.roomId = result.roomId;
            matchState.matchDetails = {
                matchScore: result.matchScore,
                matchLevel: result.matchLevel,
                matchLevelDescription: result.matchLevelDescription,
                personalityCompatibility: result.personalityCompatibility,
                interestCompatibility: result.interestCompatibility,
                commonInterests: result.commonInterests,
                matchReason: result.matchReason
            };

            renderMatchResult(document.getElementById('main-container'));
            showToast(`匹配成功！${result.matchLevel}`, 'success');
        } else if (result.waiting) {
            // 进入匹配池等待中，启动轮询
            showSearchingAnimation(result.queuePosition || 1);
            startPolling();
        } else {
            // 其他情况
            stopPolling();
            btn.disabled = false;
            btn.innerHTML = '开始匹配';
            showToast(result.matchReason || '匹配失败，请重试', 'error');
        }
    } catch (error) {
        stopPolling();
        btn.disabled = false;
        btn.innerHTML = '开始匹配';
        showToast(error.message, 'error');
    }
}

// 显示搜索中动画
function showSearchingAnimation(queuePosition) {
    const container = document.getElementById('main-container');
    container.innerHTML = `
        <div class="match-page">
            <div class="match-content">
                <h1 style="margin-bottom: 16px; font-size: 24px; font-weight: 700;">正在寻找灵魂伴侣...</h1>
                <p style="max-width: 400px; margin: 0 auto 8px; font-size: 14px;">
                    已进入匹配池，当前队列第 ${queuePosition} 位
                </p>
                <p style="font-size: 13px; margin-bottom: 40px;">
                    系统正在为你匹配最契合的人，请耐心等待
                </p>
                <div style="display: flex; justify-content: center; gap: 12px; margin-bottom: 48px;">
                    <span class="loading-dot" style="width: 10px; height: 10px; background: var(--gradient-1); border-radius: 50%; animation: bounce 0.6s infinite alternate;"></span>
                    <span class="loading-dot" style="width: 10px; height: 10px; background: var(--gradient-2); border-radius: 50%; animation: bounce 0.6s 0.2s infinite alternate;"></span>
                    <span class="loading-dot" style="width: 10px; height: 10px; background: var(--gradient-3); border-radius: 50%; animation: bounce 0.6s 0.4s infinite alternate;"></span>
                </div>
                <button class="match-btn-secondary" onclick="cancelMatching()">
                    取消匹配
                </button>
            </div>
        </div>
    `;
}

// 开始轮询
function startPolling() {
    stopPolling(); // 先清除之前的
    matchState.pollTimer = setInterval(async () => {
        try {
            const data = await post('/match/find-soul', {});
            const result = data.data;

            if (result.matched) {
                // 匹配成功！
                stopPolling();
                closeBgWs();
                matchState.isSearching = false;
                matchState.matchedUser = {
                    userId: result.matchUserId,
                    nickname: result.matchNickname,
                    avatar: result.matchAvatar
                };
                matchState.roomId = result.roomId;
                matchState.matchDetails = {
                    matchScore: result.matchScore,
                    matchLevel: result.matchLevel,
                    matchLevelDescription: result.matchLevelDescription,
                    personalityCompatibility: result.personalityCompatibility,
                    interestCompatibility: result.interestCompatibility,
                    commonInterests: result.commonInterests,
                    matchReason: result.matchReason
                };

                renderMatchResult(document.getElementById('main-container'));
                showToast(`匹配成功！${result.matchLevel}`, 'success');
            } else if (result.waiting) {
                // 更新队列位置
                const posEl = document.querySelector('.page-subtitle');
                if (posEl) {
                    posEl.textContent = `已进入匹配池，当前队列第 ${result.queuePosition || 1} 位`;
                }
            }
        } catch (error) {
            console.log('轮询匹配:', error.message);
        }
    }, 5000);

    // 同时连接后台WebSocket，接收实时匹配通知
    connectBgWs();
}

// 连接后台WebSocket（用于接收对方发来的匹配通知）
function connectBgWs() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const wsUrl = `ws://${window.location.host}/ws/chat?token=${token}`;
    matchState.bgWs = new WebSocket(wsUrl);

    matchState.bgWs.onopen = () => {
        console.log('后台WebSocket已连接，等待匹配通知');
    };

    matchState.bgWs.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            if (data.type === 'MATCHED') {
                // 被动匹配成功！停止轮询
                stopPolling();
                matchState.isSearching = false;
                matchState.matchedUser = {
                    userId: data.matchUserId,
                    nickname: data.matchNickname || '匹配用户',
                    avatar: data.matchAvatar
                };
                matchState.roomId = data.roomId;
                matchState.matchDetails = {
                    matchScore: 0,
                    matchLevel: '匹配成功',
                    matchLevelDescription: '对方找到了你',
                    personalityCompatibility: 0,
                    interestCompatibility: 0,
                    commonInterests: [],
                    matchReason: '对方主动与你匹配'
                };

                // 当前如果正在显示匹配等待页面，渲染结果
                renderMatchResult(document.getElementById('main-container'));
                showToast('有人与你匹配成功！', 'success');
            }
        } catch (e) {
            console.error('后台WS消息解析失败:', e);
        }
    };

    matchState.bgWs.onerror = () => {
        console.log('后台WebSocket连接失败');
    };

    matchState.bgWs.onclose = () => {
        console.log('后台WebSocket已关闭');
    };
}

// 关闭后台WebSocket
function closeBgWs() {
    if (matchState.bgWs) {
        matchState.bgWs.close();
        matchState.bgWs = null;
    }
}

// 停止轮询
function stopPolling() {
    if (matchState.pollTimer) {
        clearInterval(matchState.pollTimer);
        matchState.pollTimer = null;
    }
    closeBgWs();
    matchState.isSearching = false;
}

// 取消匹配
async function cancelMatching() {
    stopPolling();
    try {
        await post('/match/leave-queue', {});
    } catch (e) {
        console.error('离开匹配队列失败:', e);
    }
    matchState.isSearching = false;
    renderMatchPage();
}

// 渲染匹配结果
function renderMatchResult(container) {
    const user = matchState.matchedUser;
    const details = matchState.matchDetails || {};

    // 获取匹配等级样式
    const getLevelStyle = (level) => {
        switch(level) {
            case '灵魂伴侣': return { color: '#000000', label: '灵魂伴侣' };
            case '高度匹配': return { color: '#000000', label: '高度匹配' };
            case '中等匹配': return { color: '#000000', label: '中等匹配' };
            default: return { color: '#000000', label: '匹配成功' };
        }
    };

    const levelStyle = getLevelStyle(details.matchLevel);
    const matchScore = details.matchScore ? Math.round(details.matchScore * 100) : 0;
    const personalityScore = details.personalityCompatibility ? Math.round(details.personalityCompatibility * 100) : 0;
    const interestScore = details.interestCompatibility ? Math.round(details.interestCompatibility * 100) : 0;

    container.innerHTML = `
        <div class="match-page">
            <div class="match-content">
                <!-- 匹配等级卡片 -->
                <div style="border: 2px solid ${levelStyle.color}; border-radius: 16px; padding: 24px; text-align: center; margin-bottom: 20px;">
                    <div style="font-size: 14px; color: ${levelStyle.color}; font-weight: 600; margin-bottom: 8px;">
                        ${levelStyle.label}
                    </div>
                    <div style="font-size: 36px; font-weight: 700; color: ${levelStyle.color}; margin-bottom: 8px;">
                        ${matchScore}%
                    </div>
                    <div style="font-size: 13px;">
                        ${details.matchLevelDescription || '你们有一定的契合度'}
                    </div>
                </div>

                <!-- 匹配用户信息 -->
                <div style="padding: 16px; margin-bottom: 20px; text-align: center;">
                    <h2 style="font-size: 20px; font-weight: 600; margin-bottom: 8px;">${user.nickname || '神秘用户'}</h2>
                    <p style="font-size: 13px;">
                        ${details.matchReason || '你们有缘相遇，不妨聊聊'}
                    </p>
                </div>

                <!-- 匹配详情 -->
                <div style="padding: 16px; margin-bottom: 20px;">
                    <div style="font-size: 15px; font-weight: 600; margin-bottom: 16px;">匹配分析</div>

                    <!-- 人格兼容性 -->
                    <div style="margin-bottom: 16px;">
                        <div style="display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 13px;">
                            <span>人格兼容性</span>
                            <span style="color: var(--gradient-1); font-weight: 600;">${personalityScore}%</span>
                        </div>
                        <div style="height: 6px; background: #e0e0e0; border-radius: 3px; overflow: hidden;">
                            <div style="height: 100%; width: ${personalityScore}%; background: linear-gradient(90deg, var(--gradient-1), var(--gradient-2)); border-radius: 3px;"></div>
                        </div>
                    </div>

                    <!-- 爱好兼容性 -->
                    <div style="margin-bottom: 16px;">
                        <div style="display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 13px;">
                            <span>爱好兼容性</span>
                            <span style="color: var(--gradient-2); font-weight: 600;">${interestScore}%</span>
                        </div>
                        <div style="height: 6px; background: #e0e0e0; border-radius: 3px; overflow: hidden;">
                            <div style="height: 100%; width: ${interestScore}%; background: linear-gradient(90deg, var(--gradient-2), var(--gradient-3)); border-radius: 3px;"></div>
                        </div>
                    </div>

                    <!-- 共同爱好 -->
                    ${details.commonInterests && details.commonInterests.length > 0 ? `
                        <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid #ddd;">
                            <div style="font-size: 13px; margin-bottom: 10px;">共同爱好</div>
                            <div style="display: flex; flex-wrap: wrap; gap: 6px; justify-content: center;">
                                ${details.commonInterests.map(interest => `
                                    <span style="background: linear-gradient(135deg, var(--gradient-1), var(--gradient-2)); color: white; padding: 4px 12px; border-radius: 16px; font-size: 12px;">
                                        ${interest}
                                    </span>
                                `).join('')}
                            </div>
                        </div>
                    ` : ''}
                </div>

                <!-- 操作按钮 -->
                <div style="display: flex; gap: 12px; justify-content: center;">
                    <button class="match-btn" onclick="startChat()">
                        开始聊天
                    </button>
                    <button class="match-btn-secondary" onclick="continueMatching()">
                        继续匹配
                    </button>
                </div>
            </div>
        </div>
    `;
}

// 开始聊天
function startChat() {
    if (matchState.roomId) {
        localStorage.setItem('currentChatRoom', matchState.roomId);
        localStorage.setItem('currentChatUser', JSON.stringify(matchState.matchedUser));
        navigateTo('chat');
    }
}

// 继续匹配
function continueMatching() {
    matchState.matchedUser = null;
    matchState.roomId = null;
    matchState.matchDetails = null;
    stopPolling();
    renderMatchPage();
}
