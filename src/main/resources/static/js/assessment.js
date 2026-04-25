// AI人格评估 - DeepSeek风格问答模式

let assessmentState = {
    currentQuestion: 0,
    totalQuestions: 10,
    answers: [],
    sessionId: null,
    isComplete: false
};

// 渲染评估页面
function renderAssessmentPage() {
    const container = document.getElementById('main-container');

    // 检查是否已完成评估
    checkAssessmentStatus().then(hasAssessment => {
        if (hasAssessment) {
            renderReportPage();
        } else {
            renderWelcomeScreen(container);
        }
    });
}

// 检查评估状态
async function checkAssessmentStatus() {
    try {
        const data = await get('/assessment/status');
        return data.data;
    } catch (error) {
        return false;
    }
}

// 渲染欢迎界面
function renderWelcomeScreen(container) {
    container.innerHTML = `
        <div class="page assessment-container">
            <div class="ai-welcome">
                <div class="ai-avatar">🤖</div>
                <h1 class="ai-title">AI人格评估</h1>
                <p class="ai-description">
                    我是你的AI评估助手。通过10个精心设计的问题，
                    我将基于大五人格理论为你生成专属的性格分析报告，
                    帮助你更好地了解自己，找到真正契合的灵魂伴侣。
                </p>
                <div class="ai-features">
                    <div class="ai-feature">
                        <span class="ai-feature-icon">✓</span>
                        <span>科学的人格理论</span>
                    </div>
                    <div class="ai-feature">
                        <span class="ai-feature-icon">✓</span>
                        <span>AI深度分析</span>
                    </div>
                    <div class="ai-feature">
                        <span class="ai-feature-icon">✓</span>
                        <span>精准匹配推荐</span>
                    </div>
                </div>
                <button class="btn btn-primary btn-large" onclick="startAssessment()">
                    开始评估
                </button>
            </div>
        </div>
    `;
}

// 开始评估
async function startAssessment() {
    try {
        const data = await post('/assessment/start', {});
        const result = data.data;

        assessmentState.sessionId = result.sessionId;
        assessmentState.currentQuestion = result.currentQuestion;
        assessmentState.totalQuestions = result.totalQuestions;

        renderChatInterface(result.question);
    } catch (error) {
        showToast('开始评估失败: ' + error.message, 'error');
    }
}

// 渲染聊天式问答界面
function renderChatInterface(question) {
    const container = document.getElementById('main-container');

    // 构建历史消息
    let messagesHtml = '';
    assessmentState.answers.forEach((answer, index) => {
        messagesHtml += `
            <div class="message ai">
                <div class="message-avatar ai">🤖</div>
                <div class="message-content">
                    问题${index + 1}: ${answer.question}
                </div>
            </div>
            <div class="message user">
                <div class="message-avatar user">👤</div>
                <div class="message-content">
                    ${answer.selectedText}
                </div>
            </div>
        `;
    });

    // 添加当前问题
    messagesHtml += `
        <div class="message ai" id="current-question">
            <div class="message-avatar ai">🤖</div>
            <div class="message-content">
                <div style="margin-bottom: 12px;">问题${question.questionNumber}: ${question.content}</div>
                <div style="font-size: 13px; opacity: 0.8; margin-bottom: 8px;">维度: ${getDimensionName(question.dimension)}</div>
                <div class="options-container" id="options-container">
                    ${question.options.map((opt, idx) => `
                        <button class="option-btn" onclick="selectOption(${question.questionNumber}, ${idx + 1}, '${opt.text}')">
                            ${opt.label}. ${opt.text}
                        </button>
                    `).join('')}
                </div>
            </div>
        </div>
    `;

    container.innerHTML = `
        <div class="page" style="max-width: 800px; padding: 20px;">
            <div class="chat-interface">
                <div class="chat-messages" id="chat-messages">
                    <div class="message ai">
                        <div class="message-avatar ai">🤖</div>
                        <div class="message-content">
                            你好！我是你的AI评估助手。让我们开始了解真实的你吧。请根据你的第一直觉回答以下问题。
                        </div>
                    </div>
                    ${messagesHtml}
                </div>
                <div class="progress-container">
                    <div class="progress-info">
                        <span>进度</span>
                        <span>${assessmentState.currentQuestion} / ${assessmentState.totalQuestions}</span>
                    </div>
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: ${(assessmentState.currentQuestion / assessmentState.totalQuestions) * 100}%"></div>
                    </div>
                </div>
            </div>
        </div>
    `;

    // 滚动到底部
    setTimeout(() => {
        const chatMessages = document.getElementById('chat-messages');
        if (chatMessages) {
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }
    }, 100);
}

// 获取维度中文名
function getDimensionName(dimension) {
    const names = {
        'extraversion': '外向性',
        'openness': '开放性',
        'agreeableness': '宜人性',
        'conscientiousness': '尽责性',
        'emotional_stability': '情绪稳定性'
    };
    return names[dimension] || dimension;
}

// 选择选项
async function selectOption(questionNumber, score, text) {
    // 禁用所有选项按钮
    const buttons = document.querySelectorAll('.option-btn');
    buttons.forEach(btn => btn.disabled = true);

    // 记录答案
    assessmentState.answers.push({
        questionNumber: questionNumber,
        selectedScore: score,
        selectedText: text
    });

    try {
        const data = await post('/assessment/answer', {
            questionNumber: questionNumber,
            selectedScore: score
        });

        const result = data.data;

        if (result.isCompleted) {
            assessmentState.isComplete = true;
            await completeAssessment();
        } else {
            assessmentState.currentQuestion = result.answeredCount + 1;
            renderChatInterface(result.nextQuestion);
        }
    } catch (error) {
        showToast('提交答案失败: ' + error.message, 'error');
        buttons.forEach(btn => btn.disabled = false);
    }
}

// 完成评估
async function completeAssessment() {
    const container = document.getElementById('main-container');

    container.innerHTML = `
        <div class="page" style="max-width: 800px;">
            <div class="chat-interface">
                <div class="chat-messages" style="text-align: center; padding: 60px 24px;">
                    <div class="loading" style="width: 48px; height: 48px; margin: 0 auto 24px;"></div>
                    <h2 style="margin-bottom: 12px;">AI正在分析你的人格特质...</h2>
                    <p style="color: var(--text-secondary);">基于大五人格理论生成专属报告</p>
                </div>
            </div>
        </div>
    `;

    try {
        const data = await post('/assessment/complete', {});
        const result = data.data;

        // 保存报告数据
        localStorage.setItem('assessmentReport', JSON.stringify(result));

        renderReportPage(result);
    } catch (error) {
        showToast('生成报告失败: ' + error.message, 'error');
    }
}

// 渲染报告页面
function renderReportPage(reportData) {
    const report = reportData || JSON.parse(localStorage.getItem('assessmentReport') || '{}');
    const container = document.getElementById('main-container');

    const dimensions = [
        { key: 'extraversion', name: '外向性', desc: '社交活跃度与能量来源' },
        { key: 'openness', name: '开放性', desc: '对新经验的接受程度' },
        { key: 'agreeableness', name: '宜人性', desc: '合作与同理心' },
        { key: 'conscientiousness', name: '尽责性', desc: '组织性与自律性' },
        { key: 'emotional_stability', name: '情绪稳定性', desc: '情绪控制能力' }
    ];

    container.innerHTML = `
        <div class="page report-container">
            <div class="report-header">
                <div class="report-badge">
                    <span>✓</span>
                    <span>评估完成</span>
                </div>
                <h1 class="page-title">你的人格报告</h1>
                <p class="page-subtitle">基于大五人格理论的专业分析</p>
            </div>

            <div class="personality-card">
                <div class="personality-type">${report.summary || '独特的人格类型'}</div>
                <div class="personality-desc">${report.description || '暂无详细描述'}</div>
            </div>

            <div class="card">
                <div class="card-header">
                    <div class="card-icon">📊</div>
                    <div class="card-title">五维人格分析</div>
                </div>
                <div class="dimensions-grid">
                    ${dimensions.map(dim => {
                        const score = report.dimensionScores?.[dim.key] || 3.0;
                        const percentage = (score / 5) * 100;
                        return `
                            <div class="dimension-item">
                                <div class="dimension-header">
                                    <span class="dimension-name">${dim.name}</span>
                                    <span class="dimension-score">${score.toFixed(1)}</span>
                                </div>
                                <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 8px;">${dim.desc}</div>
                                <div class="dimension-bar">
                                    <div class="dimension-fill" style="width: ${percentage}%"></div>
                                </div>
                            </div>
                        `;
                    }).join('')}
                </div>
            </div>

            <div class="card">
                <div class="card-header">
                    <div class="card-icon">🎯</div>
                    <div class="card-title">人格向量</div>
                </div>
                <div style="display: flex; gap: 16px; flex-wrap: wrap; justify-content: center; padding: 20px;">
                    ${(report.personalityVector || [0, 0, 0, 0, 0]).map((v, i) => `
                        <div style="text-align: center;">
                            <div style="font-size: 24px; font-weight: 700; color: var(--gradient-1);">${v.toFixed(2)}</div>
                            <div style="font-size: 12px; color: var(--text-secondary);">${dimensions[i].name}</div>
                        </div>
                    `).join('')}
                </div>
            </div>

            <div style="text-align: center; margin-top: 32px;">
                <button class="btn btn-primary btn-large" onclick="navigateTo('match')">
                    寻找灵魂匹配
                </button>
            </div>
        </div>
    `;
}
