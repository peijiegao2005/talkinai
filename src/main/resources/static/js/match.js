// 灵魂匹配功能

let matchState = {
    isSearching: false,
    matchedUser: null,
    roomId: null
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
                基于你的人格特质，我们将为你匹配最契合的TA
            </p>
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

            renderMatchResult(document.getElementById('main-container'));
            showToast('匹配成功！', 'success');
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

    container.innerHTML = `
        <div class="page" style="display: flex; align-items: center; justify-content: center; min-height: calc(100vh - 140px);">
            <div class="match-result">
                <div class="match-avatar">${user.avatar || '👤'}</div>
                <h2 class="match-name">${user.nickname || '神秘用户'}</h2>
                <p class="match-compatibility">✨ 灵魂契合度: 85%</p>
                <p style="color: var(--text-secondary); margin-bottom: 24px;">
                    你们的性格互补，可能会成为很好的朋友
                </p>
                <div style="display: flex; gap: 12px; justify-content: center;">
                    <button class="btn btn-primary" onclick="startChat()">
                        开始聊天
                    </button>
                    <button class="btn btn-secondary" onclick="continueMatching()">
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
        navigateTo('chat');
        // 设置当前聊天房间
        localStorage.setItem('currentChatRoom', matchState.roomId);
        localStorage.setItem('currentChatUser', JSON.stringify(matchState.matchedUser));
    }
}

// 继续匹配
function continueMatching() {
    matchState.matchedUser = null;
    matchState.roomId = null;
    renderMatchPage();
}
