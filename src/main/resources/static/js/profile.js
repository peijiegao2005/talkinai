// 用户画像功能

let profileState = {
    profile: null,
    isEditing: false
};

// 渲染个人资料页面
async function renderProfilePage() {
    const container = document.getElementById('main-container');

    try {
        const data = await get('/profile');
        profileState.profile = data.data;
        renderProfileView(container);
    } catch (error) {
        container.innerHTML = `
            <div class="page" style="text-align: center; padding: 100px 24px;">
                <p style="color: var(--text-secondary);">加载失败，请重试</p>
                <button class="btn btn-primary" style="margin-top: 16px;" onclick="renderProfilePage()">
                    重试
                </button>
            </div>
        `;
    }
}

// 渲染资料展示视图
function renderProfileView(container) {
    const profile = profileState.profile;
    const userInfo = getUserInfo();

    container.innerHTML = `
        <div class="profile-page">
            <div class="profile-container">
                <div class="profile-header">
                    <div class="profile-avatar">${userInfo?.nickname?.[0] || '👤'}</div>
                    <div class="profile-info">
                        <h2>${userInfo?.nickname || '未设置昵称'}</h2>
                        <p>@${userInfo?.username || ''}</p>
                    </div>
                </div>

                <div class="profile-section">
                    <div class="profile-section-title">
                        <span style="font-size: 20px;">📝</span>
                        <span>个人简介</span>
                    </div>
                    <p style="color: #666; line-height: 1.8; font-size: 15px;">
                        ${profile?.bio || '这个人很懒，什么都没写~'}
                    </p>
                </div>

                <div class="profile-section">
                    <div class="profile-section-title">
                        <span style="font-size: 20px;">🏷️</span>
                        <span>兴趣爱好</span>
                    </div>
                    <div class="interests-tags">
                        ${(profile?.interests || []).map(interest => `
                            <span class="interest-tag">${interest}</span>
                        `).join('')}
                        <span class="interest-tag add" onclick="showAddInterest()">+ 添加</span>
                    </div>
                </div>

                <div class="profile-section">
                    <div class="profile-section-title">
                        <span style="font-size: 20px;">📋</span>
                        <span>基本信息</span>
                    </div>
                    <div class="profile-info-grid">
                        <div class="profile-info-item">
                            <span class="profile-info-label">性别</span>
                            <span class="profile-info-value">${profile?.gender || '未设置'}</span>
                        </div>
                        <div class="profile-info-item">
                            <span class="profile-info-label">年龄</span>
                            <span class="profile-info-value">${profile?.age || '未设置'}</span>
                        </div>
                        <div class="profile-info-item">
                            <span class="profile-info-label">地区</span>
                            <span class="profile-info-value">${profile?.location || '未设置'}</span>
                        </div>
                        <div class="profile-info-item">
                            <span class="profile-info-label">职业</span>
                            <span class="profile-info-value">${profile?.occupation || '未设置'}</span>
                        </div>
                        <div class="profile-info-item" style="border-bottom: none;">
                            <span class="profile-info-label">教育</span>
                            <span class="profile-info-value">${profile?.education || '未设置'}</span>
                        </div>
                    </div>
                </div>

                <div style="text-align: center; margin-top: 32px; padding-bottom: 32px;">
                    <button class="btn-edit-profile" onclick="showEditProfile()">
                        编辑资料
                    </button>
                </div>
            </div>
        </div>
    `;
}

// 显示编辑资料
function showEditProfile() {
    const container = document.getElementById('main-container');
    const profile = profileState.profile;

    container.innerHTML = `
        <div class="page" style="max-width: 600px;">
            <h1 class="page-title">编辑资料</h1>
            <p class="page-subtitle">完善你的个人信息</p>

            <form onsubmit="saveProfile(event)">
                <div class="card">
                    <div class="input-group">
                        <label class="input-label">个人简介</label>
                        <textarea class="input-field" id="edit-bio" rows="4" placeholder="介绍一下自己...">${profile?.bio || ''}</textarea>
                    </div>

                    <div class="input-group">
                        <label class="input-label">性别</label>
                        <select class="input-field" id="edit-gender">
                            <option value="">请选择</option>
                            <option value="男" ${profile?.gender === '男' ? 'selected' : ''}>男</option>
                            <option value="女" ${profile?.gender === '女' ? 'selected' : ''}>女</option>
                            <option value="保密" ${profile?.gender === '保密' ? 'selected' : ''}>保密</option>
                        </select>
                    </div>

                    <div class="input-group">
                        <label class="input-label">年龄</label>
                        <input type="number" class="input-field" id="edit-age" value="${profile?.age || ''}" placeholder="请输入年龄">
                    </div>

                    <div class="input-group">
                        <label class="input-label">地区</label>
                        <input type="text" class="input-field" id="edit-location" value="${profile?.location || ''}" placeholder="例如：北京">
                    </div>

                    <div class="input-group">
                        <label class="input-label">职业</label>
                        <input type="text" class="input-field" id="edit-occupation" value="${profile?.occupation || ''}" placeholder="例如：软件工程师">
                    </div>

                    <div class="input-group">
                        <label class="input-label">教育背景</label>
                        <input type="text" class="input-field" id="edit-education" value="${profile?.education || ''}" placeholder="例如：本科">
                    </div>
                </div>

                <div style="display: flex; gap: 12px; justify-content: center;">
                    <button type="button" class="btn btn-secondary" onclick="renderProfilePage()">
                        取消
                    </button>
                    <button type="submit" class="btn btn-primary">
                        保存
                    </button>
                </div>
            </form>
        </div>
    `;
}

// 保存资料
async function saveProfile(event) {
    event.preventDefault();

    const profile = {
        bio: document.getElementById('edit-bio').value,
        gender: document.getElementById('edit-gender').value,
        age: document.getElementById('edit-age').value ? parseInt(document.getElementById('edit-age').value) : null,
        location: document.getElementById('edit-location').value,
        occupation: document.getElementById('edit-occupation').value,
        education: document.getElementById('edit-education').value
    };

    try {
        await put('/profile', profile);
        showToast('保存成功', 'success');
        renderProfilePage();
    } catch (error) {
        showToast('保存失败: ' + error.message, 'error');
    }
}

// 显示添加兴趣
function showAddInterest() {
    const interest = prompt('请输入兴趣爱好：');
    if (interest && interest.trim()) {
        addInterest(interest.trim());
    }
}

// 添加兴趣
async function addInterest(interest) {
    try {
        await post(`/profile/interests?interest=${encodeURIComponent(interest)}`, {});
        showToast('添加成功', 'success');
        renderProfilePage();
    } catch (error) {
        showToast('添加失败: ' + error.message, 'error');
    }
}
