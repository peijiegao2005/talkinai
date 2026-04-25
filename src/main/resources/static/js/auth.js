// 认证相关功能

// 渲染登录/注册页面
function renderAuthPage() {
    const container = document.getElementById('main-container');
    container.innerHTML = `
        <div class="auth-page">
            <div class="auth-container">
                <div class="auth-card">
                    <div class="auth-header">
                        <div class="auth-logo">✨</div>
                        <h1 class="auth-title">SoulChat</h1>
                        <p class="auth-subtitle">AI驱动的灵魂匹配平台</p>
                    </div>
                    
                    <div class="auth-tabs">
                        <div class="auth-tab active" onclick="switchTab('login')">登录</div>
                        <div class="auth-tab" onclick="switchTab('register')">注册</div>
                    </div>
                    
                    <form id="auth-form" onsubmit="handleAuthSubmit(event)">
                        <div id="register-fields" style="display: none;">
                            <div class="input-group">
                                <label class="input-label">昵称</label>
                                <input type="text" class="input-field" id="nickname" placeholder="请输入昵称">
                            </div>
                        </div>
                        
                        <div class="input-group">
                            <label class="input-label">用户名</label>
                            <input type="text" class="input-field" id="username" placeholder="请输入用户名" required>
                        </div>
                        
                        <div class="input-group">
                            <label class="input-label">密码</label>
                            <input type="password" class="input-field" id="password" placeholder="请输入密码" required>
                        </div>
                        
                        <div id="email-field" style="display: none;">
                            <div class="input-group">
                                <label class="input-label">邮箱</label>
                                <input type="email" class="input-field" id="email" placeholder="请输入邮箱">
                            </div>
                        </div>
                        
                        <button type="submit" class="btn btn-primary btn-large" style="width: 100%; margin-top: 24px;">
                            <span id="auth-btn-text">登录</span>
                        </button>
                    </form>
                    
                    <div class="auth-footer">
                        <p>登录即表示您同意我们的服务条款</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// 切换登录/注册标签
function switchTab(tab) {
    const tabs = document.querySelectorAll('.auth-tab');
    tabs.forEach(t => t.classList.remove('active'));
    event.target.classList.add('active');

    const registerFields = document.getElementById('register-fields');
    const emailField = document.getElementById('email-field');
    const btnText = document.getElementById('auth-btn-text');

    if (tab === 'register') {
        registerFields.style.display = 'block';
        emailField.style.display = 'block';
        btnText.textContent = '注册';
        document.getElementById('auth-form').dataset.mode = 'register';
    } else {
        registerFields.style.display = 'none';
        emailField.style.display = 'none';
        btnText.textContent = '登录';
        document.getElementById('auth-form').dataset.mode = 'login';
    }
}

// 处理登录/注册提交
async function handleAuthSubmit(event) {
    event.preventDefault();
    const mode = event.target.dataset.mode || 'login';

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        if (mode === 'login') {
            const data = await post('/auth/login', { username, password });
            handleLoginSuccess(data.data);
        } else {
            const nickname = document.getElementById('nickname').value;
            const email = document.getElementById('email').value;
            const data = await post('/auth/register', { username, password, nickname, email });
            handleLoginSuccess(data.data);
        }
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// 登录成功处理
function handleLoginSuccess(data) {
    localStorage.setItem('token', data.token);
    localStorage.setItem('userInfo', JSON.stringify({
        userId: data.userId,
        username: data.username,
        nickname: data.nickname
    }));

    showToast('登录成功！', 'success');
    initApp();
}

// 退出登录
function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    location.reload();
}

// 检查是否已登录
function isLoggedIn() {
    return !!getToken();
}
