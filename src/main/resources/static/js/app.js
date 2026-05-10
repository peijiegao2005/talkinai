// SoulChat 主应用

// 当前页面，防止hashchange重复渲染
let currentPage = '';

// 路由配置
const routes = {
    'assessment': renderAssessmentPage,
    'mood': renderMoodPage,
    'match': renderMatchPage,
    'chat': renderChatPage,
    'profile': renderProfilePage
};

// 初始化应用
function initApp() {
    if (isLoggedIn()) {
        showMainApp();
    } else {
        renderAuthPage();
    }
}

// 显示主应用
function showMainApp() {
    const navbar = document.getElementById('navbar');
    const userInfo = getUserInfo();

    navbar.style.display = 'flex';
    document.getElementById('userNickname').textContent = userInfo?.nickname || userInfo?.username;

    // 默认跳转到评估页面
    const hash = window.location.hash.slice(1) || 'assessment';
    navigateTo(hash);
}

// 页面导航
function navigateTo(page) {
    if (!routes[page]) {
        page = 'assessment';
    }

    // 更新导航状态
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
        if (link.getAttribute('href') === '#' + page) {
            link.classList.add('active');
        }
    });

    // 标记当前页面（必须在改hash之前，防止hashchange重复渲染）
    currentPage = page;

    // 更新URL
    window.location.hash = page;

    // 渲染页面
    routes[page]();
}

// 监听hash变化（仅响应用户手动操作，如浏览器前进后退）
window.addEventListener('hashchange', () => {
    const hash = window.location.hash.slice(1);
    if (hash && routes[hash] && hash !== currentPage) {
        currentPage = hash;
        routes[hash]();
    }
});

// Toast提示
function showToast(message, type = 'info') {
    // 移除现有toast
    const existingToast = document.querySelector('.toast');
    if (existingToast) {
        existingToast.remove();
    }

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);

    // 显示动画
    setTimeout(() => toast.classList.add('show'), 10);

    // 自动隐藏
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', initApp);
