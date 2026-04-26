// 灵魂匹配功能

let matchState = {
    isSearching: false,
    matchedUser: null,
    roomId: null,
    matchDetails: null
};

// 渲染匹配页面
function renderMatchPage() {
    const container = document.getElementById('main-container');

    // 检查是否已完成评估
    checkAssessmentStatus().then(hasAssessment => {
        if (!hasAssessment) {
            container.innerHTML = `
                <div class="page" style="text-align: center; padding: 100px 24px;">
                    <div style="font-size: 64px; margin-bottom: 24px;">📝</div>
                    <h2 style="margin-bottom: 12px;">请先完成人格评估</h2>
                    <p style="color: var(--text-secondary); margin-bottom: 32px;">
                        完成评估后，我们才能为你找到最合适的灵魂伴侣
                    </p>
                    <button class="btn btn-primary btn-large" onclick="navigateTo('assessment')">
                        去评估
                    </button>
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
        <div class="page match-container">
            <div class="match-animation">
                <div class="match-circle"></div>
                <div class="match-circle"></div>
                <div class="match-circle">💫</div>
            </div>
            <h1 class="page-title" style="margin-bottom: 16px;">寻找灵魂伴侣</h1>
            <p class="page-subtitle" style="max-width: 400px; margin: 0 auto 40px;">
                基于你的人格特质和兴趣爱好，我们将为你匹配最契合的TA
            </p>
            <div style="background: var(--bg-secondary); border-radius: 12px; padding: 20px; max-width: 400px; margin: 0 auto 32px; text-align: left;">
                <div style="font-size: 14px; color: var(--text-secondary); margin-bottom: 12px;">匹配算法说明：</div>
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
            <button class="btn btn-primary match-btn" id="match-btn" onclick="startMatching()">
                开始匹配
            </button>
        </div>
    `;
}

// 开始匹配
async function startMatching() {
    const btn = document.getElementById('match-btn');
    btn.disabled = true;
    btn.innerHTML = '<span class="loading"></span> 寻找中...';

    try {
        const data = await post('/match/find-soul', {});
        const result = data.data;

        if (result.matched) {
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
        }
    } catch (error) {
        showToast(error.message, 'error');
        btn.disabled = false;
        btn.innerHTML = '开始匹配';
    }
}

// 渲染匹配结果
function renderMatchResult(container) {
    const user = matchState.matchedUser;
    const details = matchState.matchDetails || {};

    // 获取匹配等级样式
    const getLevelStyle = (level) => {
        switch(level) {
            case '灵魂伴侣': return { color: '#ff6b9d', icon: '✨', bg: 'linear-gradient(135deg, rgba(255,107,157,0.2), rgba(255,142,83,0.2))' };
            case '高度匹配': return { color: '#4ecdc4', icon: '🎯', bg: 'linear-gradient(135deg, rgba(78,205,196,0.2), rgba(68,160,205,0.2))' };
            case '中等匹配': return { color: '#a78bfa', icon: '💫', bg: 'linear-gradient(135deg, rgba(167,139,250,0.2), rgba(139,92,246,0.2))' };
            default: return { color: '#94a3b8', icon: '🌟', bg: 'var(--bg-secondary)' };
        }
    };

    const levelStyle = getLevelStyle(details.matchLevel);
    const matchScore = details.matchScore ? Math.round(details.matchScore * 100) : 0;
    const personalityScore = details.personalityCompatibility ? Math.round(details.personalityCompatibility * 100) : 0;
    const interestScore = details.interestCompatibility ? Math.round(details.interestCompatibility * 100) : 0;

    container.innerHTML = `
        <div class="page" style="max-width: 600px; margin: 0 auto; padding: 24px;">
            <!-- 匹配等级卡片 -->
            <div style="background: ${levelStyle.bg}; border: 1px solid ${levelStyle.color}40; border-radius: 16px; padding: 32px 24px; text-align: center; margin-bottom: 24px;">
                <div style="font-size: 48px; margin-bottom: 12px;">${levelStyle.icon}</div>
                <div style="font-size: 14px; color: ${levelStyle.color}; font-weight: 600; margin-bottom: 8px;">
                    ${details.matchLevel || '匹配成功'}
                </div>
                <div style="font-size: 36px; font-weight: 700; color: ${levelStyle.color}; margin-bottom: 8px;">
                    ${matchScore}%
                </div>
                <div style="font-size: 14px; color: var(--text-secondary);">
                    ${details.matchLevelDescription || '你们有一定的契合度'}
                </div>
            </div>

            <!-- 匹配用户信息 -->
            <div style="background: var(--bg-secondary); border-radius: 16px; padding: 24px; margin-bottom: 24px; text-align: center;">
                <div style="width: 80px; height: 80px; border-radius: 50%; background: linear-gradient(135deg, var(--gradient-1), var(--gradient-2)); display: flex; align-items: center; justify-content: center; font-size: 36px; margin: 0 auto 16px;">
                    ${user.avatar || '👤'}
                </div>
                <h2 style="font-size: 20px; font-weight: 600; margin-bottom: 8px;">${user.nickname || '神秘用户'}</h2>
                <p style="font-size: 14px; color: var(--text-secondary);">
                    ${details.matchReason || '你们有缘相遇，不妨聊聊'}
                </p>
            </div>

            <!-- 匹配详情 -->
            <div style="background: var(--bg-secondary); border-radius: 16px; padding: 24px; margin-bottom: 24px;">
                <div style="font-size: 16px; font-weight: 600; margin-bottom: 20px;">匹配分析</div>

                <!-- 人格兼容性 -->
                <div style="margin-bottom: 20px;">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px;">
                        <span>人格兼容性</span>
                        <span style="color: var(--gradient-1); font-weight: 600;">${personalityScore}%</span>
                    </div>
                    <div style="height: 8px; background: var(--bg-tertiary); border-radius: 4px; overflow: hidden;">
                        <div style="height: 100%; width: ${personalityScore}%; background: linear-gradient(90deg, var(--gradient-1), var(--gradient-2)); border-radius: 4px; transition: width 0.5s ease;"></div>
                    </div>
                    <div style="font-size: 12px; color: var(--text-secondary); margin-top: 6px;">
                        基于大五人格理论的相似度分析
                    </div>
                </div>

                <!-- 爱好兼容性 -->
                <div style="margin-bottom: 20px;">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px;">
                        <span>爱好兼容性</span>
                        <span style="color: var(--gradient-2); font-weight: 600;">${interestScore}%</span>
                    </div>
                    <div style="height: 8px; background: var(--bg-tertiary); border-radius: 4px; overflow: hidden;">
                        <div style="height: 100%; width: ${interestScore}%; background: linear-gradient(90deg, var(--gradient-2), var(--gradient-3)); border-radius: 4px; transition: width 0.5s ease;"></div>
                    </div>
                    <div style="font-size: 12px; color: var(--text-secondary); margin-top: 6px;">
                        共同兴趣爱好的契合程度
                    </div>
                </div>

                <!-- 共同爱好 -->
                ${details.commonInterests && details.commonInterests.length > 0 ? `
                    <div style="margin-top: 20px; padding-top: 20px; border-top: 1px solid var(--border-color);">
                        <div style="font-size: 14px; margin-bottom: 12px;">共同爱好</div>
                        <div style="display: flex; flex-wrap: wrap; gap: 8px;">
                            ${details.commonInterests.map(interest => `
                                <span style="background: linear-gradient(135deg, var(--gradient-1), var(--gradient-2)); color: white; padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 500;">
                                    ${interest}
                                </span>
                            `).join('')}
                        </div>
                    </div>
                ` : ''}
            </div>

            <!-- 操作按钮 -->
            <div style="display: flex; gap: 12px;">
                <button class="btn btn-primary" style="flex: 1;" onclick="startChat()">
                    💬 开始聊天
                </button>
                <button class="btn btn-secondary" style="flex: 1;" onclick="continueMatching()">
                    🔍 继续匹配
                </button>
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
    renderMatchPage();
}
