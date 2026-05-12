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
    navigateTo(hash, false);
}

// 页面导航
// @param page 目标页面
// @param updateHash 是否更新URL hash（点击导航时true，初始化时false）
function navigateTo(page, updateHash = true) {
    if (!routes[page]) {
        page = 'assessment';
    }

    // 如果已经在当前页面，不重复渲染
    if (page === currentPage) {
        return;
    }

    // 更新导航状态
    updateNavActiveState(page);

    // 标记当前页面
    currentPage = page;

    // 更新URL（仅在用户点击时更新，避免触发hashchange）
    if (updateHash) {
        window.location.hash = page;
    }

    // 渲染页面
    routes[page]();
}

// 更新导航栏激活状态
function updateNavActiveState(page) {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
        if (link.getAttribute('href') === '#' + page) {
            link.classList.add('active');
        }
    });
}

// 监听hash变化（仅响应浏览器前进后退、手动修改URL等外部操作）
window.addEventListener('hashchange', (event) => {
    const hash = window.location.hash.slice(1);

    // 如果hash为空或无效，忽略
    if (!hash || !routes[hash]) {
        return;
    }

    // 如果hash变化是由navigateTo触发的（currentPage已更新），忽略
    if (hash === currentPage) {
        return;
    }

    // 外部触发的hash变化（如浏览器前进后退）
    currentPage = hash;
    updateNavActiveState(hash);
    routes[hash]();
});

// 为导航链接添加点击事件（阻止默认行为，使用navigateTo）
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const href = link.getAttribute('href');
            if (href && href.startsWith('#')) {
                const page = href.slice(1);
                navigateTo(page, true);
            }
        });
    });
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
