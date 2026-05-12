// API 基础配置
const API_BASE_URL = window.location.origin + '/api';

// 获取Token
function getToken() {
    return localStorage.getItem('token');
}

// 获取用户信息
function getUserInfo() {
    const userInfo = localStorage.getItem('userInfo');
    return userInfo ? JSON.parse(userInfo) : null;
}

// API请求封装
async function apiRequest(url, options = {}) {
    const token = getToken();
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        }
    };

    const fullUrl = `${API_BASE_URL}${url}`;
    console.log(`API请求: ${options.method || 'GET'} ${fullUrl}`);

    try {
        const response = await fetch(fullUrl, {
            ...defaultOptions,
            ...options,
            headers: {
                ...defaultOptions.headers,
                ...options.headers
            }
        });

        console.log(`API响应: ${response.status} ${response.statusText}`);

        let data;
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            data = await response.json();
            console.log('API响应数据:', data);
        } else {
            const text = await response.text();
            console.log('API响应文本:', text.substring(0, 200));
            // 如果是 HTML 错误页面，提取错误信息
            if (text.includes('<!DOCTYPE') || text.includes('<html')) {
                data = { message: `服务器错误: ${response.status} ${response.statusText}` };
            } else {
                data = { message: text || '请求失败' };
            }
        }

        // HTTP 状态码错误 (404, 500等)
        if (!response.ok) {
            throw new Error(data.message || data.error || `请求失败: ${response.status}`);
        }

        // 业务逻辑错误（code 不为 200）
        if (data.code !== undefined && data.code !== 200) {
            throw new Error(data.message || '请求失败');
        }

        return data;
    } catch (error) {
        console.error('API请求错误:', error);
        throw error;
    }
}

// GET请求
function get(url) {
    return apiRequest(url, { method: 'GET' });
}

// POST请求
function post(url, body) {
    return apiRequest(url, {
        method: 'POST',
        body: JSON.stringify(body)
    });
}

// PUT请求
function put(url, body) {
    return apiRequest(url, {
        method: 'PUT',
        body: JSON.stringify(body)
    });
}

// PATCH请求
function patch(url, body) {
    return apiRequest(url, {
        method: 'PATCH',
        body: JSON.stringify(body)
    });
}

// DELETE请求
function del(url) {
    return apiRequest(url, {
        method: 'DELETE'
    });
}
