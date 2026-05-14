// AI人格评估 - 彩铅蜡笔涂鸦风格

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
        <div class="assessment-page">
            <div class="assessment-container">
                <div class="assessment-welcome">
                    <div class="assessment-welcome-card">
                        <div class="assessment-avatar">🤖</div>
                        <h1 class="assessment-title">AI人格评估</h1>
                        <p class="assessment-desc">
                            我是你的AI评估助手。通过10个精心设计的问题，
                            我将基于大五人格理论为你生成专属的性格分析报告，
                            帮助你更好地了解自己，找到真正契合的灵魂伴侣。
                        </p>
                        <div class="assessment-features">
                            <div class="assessment-feature">
                                <span class="assessment-feature-icon">✓</span>
                                <span>科学的人格理论</span>
                            </div>
                            <div class="assessment-feature">
                                <span class="assessment-feature-icon">✓</span>
                                <span>AI深度分析</span>
                            </div>
                            <div class="assessment-feature">
                                <span class="assessment-feature-icon">✓</span>
                                <span>精准匹配推荐</span>
                            </div>
                        </div>
                        <button class="assessment-btn assessment-btn-primary" onclick="startAssessment()">
                            开始评估
                        </button>
                    </div>
                </div>
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
            <div class="assessment-msg ai">
                <div class="assessment-msg-avatar ai">🤖</div>
                <div class="assessment-msg-content">
                    问题${index + 1}: ${answer.question}
                </div>
            </div>
            <div class="assessment-msg user">
                <div class="assessment-msg-avatar user">👤</div>
                <div class="assessment-msg-content">
                    ${answer.selectedText}
                </div>
            </div>
        `;
    });

    // 添加当前问题
    messagesHtml += `
        <div class="assessment-msg ai" id="current-question">
            <div class="assessment-msg-avatar ai">🤖</div>
            <div class="assessment-msg-content">
                <div style="margin-bottom: 12px;">问题${question.questionNumber}: ${question.content}</div>
                <div style="font-size: 13px; color: #888; margin-bottom: 8px;">维度: ${getDimensionName(question.dimension)}</div>
                <div class="assessment-options" id="options-container">
                    ${question.options.map((opt, idx) => `
                        <button class="assessment-option-btn" onclick="selectOption(${question.questionNumber}, ${idx + 1}, '${opt.text}')">
                            <span style="font-weight: 700; color: #87CEEB; min-width: 24px;">${opt.label}.</span>
                            <span>${opt.text}</span>
                        </button>
                    `).join('')}
                </div>
            </div>
        </div>
    `;

    container.innerHTML = `
        <div class="assessment-chat-page">
            <div class="assessment-chat-card">
                <div class="assessment-messages" id="chat-messages">
                    <div class="assessment-msg ai">
                        <div class="assessment-msg-avatar ai">🤖</div>
                        <div class="assessment-msg-content">
                            你好！我是你的AI评估助手。让我们开始了解真实的你吧。请根据你的第一直觉回答以下问题。
                        </div>
                    </div>
                    ${messagesHtml}
                </div>
                <div class="assessment-progress">
                    <div class="assessment-progress-info">
                        <span>进度</span>
                        <span>${assessmentState.currentQuestion} / ${assessmentState.totalQuestions}</span>
                    </div>
                    <div class="assessment-progress-bar">
                        <div class="assessment-progress-fill" style="width: ${(assessmentState.currentQuestion / assessmentState.totalQuestions) * 100}%"></div>
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
    const buttons = document.querySelectorAll('.assessment-option-btn');
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
        <div class="assessment-chat-page">
            <div class="assessment-chat-card">
                <div class="assessment-messages" style="text-align: center; padding: 60px 24px; display: flex; flex-direction: column; align-items: center; justify-content: center;">
                    <div class="loading" style="width: 48px; height: 48px; margin-bottom: 24px; border-color: #87CEEB; border-top-color: #DDA0DD;"></div>
                    <h2 style="margin-bottom: 12px; color: #333;">AI正在分析你的人格特质...</h2>
                    <p style="color: #666;">基于大五人格理论生成专属报告</p>
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
        <div class="assessment-report-page">
            <div class="assessment-container">
                <div style="text-align: center; margin-bottom: 32px;">
                    <div class="assessment-badge">
                        <span>✓</span>
                        <span>评估完成</span>
                    </div>
                    <h1 style="font-size: 32px; font-weight: 700; margin-bottom: 8px; color: #333;">你的人格报告</h1>
                    <p style="color: #666; font-size: 15px;">基于大五人格理论的专业分析</p>
                </div>

                <div class="assessment-personality-card">
                    <div class="assessment-personality-type">${report.summary || '独特的人格类型'}</div>
                    <div class="assessment-personality-desc">${report.description || '暂无详细描述'}</div>
                </div>

                <div class="assessment-report-card">
                    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 20px;">
                        <div style="font-size: 24px;">📊</div>
                        <div style="font-size: 18px; font-weight: 700; color: #333;">五维人格分析</div>
                    </div>
                    <div class="dimensions-grid">
                        ${dimensions.map(dim => {
                            const score = report.dimensionScores?.[dim.key] || 3.0;
                            const percentage = (score / 5) * 100;
                            return `
                                <div class="assessment-dimension-item">
                                    <div class="assessment-dimension-header">
                                        <span class="assessment-dimension-name">${dim.name}</span>
                                        <span class="assessment-dimension-score">${score.toFixed(1)}</span>
                                    </div>
                                    <div class="assessment-dimension-desc">${dim.desc}</div>
                                    <div class="assessment-dimension-bar">
                                        <div class="assessment-dimension-fill" style="width: ${percentage}%"></div>
                                    </div>
                                </div>
                            `;
                        }).join('')}
                    </div>
                </div>

                <div class="assessment-report-card">
                    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 20px;">
                        <div style="font-size: 24px;">🎯</div>
                        <div style="font-size: 18px; font-weight: 700; color: #333;">人格向量</div>
                    </div>
                    <div style="display: flex; gap: 16px; flex-wrap: wrap; justify-content: center; padding: 20px;">
                        ${(report.personalityVector || [0, 0, 0, 0, 0]).map((v, i) => `
                            <div style="text-align: center;">
                                <div style="font-size: 24px; font-weight: 700; color: #87CEEB;">${v.toFixed(2)}</div>
                                <div style="font-size: 12px; color: #888;">${dimensions[i].name}</div>
                            </div>
                        `).join('')}
                    </div>
                </div>

                <div style="text-align: center; margin-top: 32px; display: flex; gap: 16px; justify-content: center; flex-wrap: wrap;">
                    <button class="assessment-btn assessment-btn-primary" onclick="navigateTo('match')">
                        寻找灵魂匹配
                    </button>
                    <button class="assessment-btn assessment-btn-secondary" onclick="restartAssessment()">
                        🔄 重新评估
                    </button>
                </div>
            </div>
        </div>
    `;
}

// 重新评估
async function restartAssessment() {
    if (!confirm('确定要重新进行人格评估吗？之前的评估记录将被保留。')) {
        return;
    }

    try {
        console.log('正在调用重新评估API...');
        const response = await post('/assessment/restart', {});
        console.log('重新评估API返回:', response);

        // 检查响应状态
        if (response.code !== 200) {
            throw new Error(response.message || '服务器返回错误');
        }

        if (!response.data) {
            throw new Error('服务器返回数据为空');
        }

        const result = response.data;

        // 重置状态
        assessmentState = {
            currentQuestion: 1,
            totalQuestions: result.totalQuestions,
            answers: [],
            sessionId: result.sessionId,
            isComplete: false
        };

        renderChatInterface(result.question);
        showToast('开始重新评估', 'success');
    } catch (error) {
        console.error('重新评估失败:', error);
        showToast('重新开始评估失败: ' + (error.message || '未知错误'), 'error');
    }
}
