// 全局变量
let ws = null;
let currentUser = null;
let reconnectInterval = null;

// 初始化
document.addEventListener('DOMContentLoaded', function() {
    initUser();
    initWebSocket();
    initEventListeners();
});

// 获取当前用户信息
async function initUser() {
    try {
        const response = await fetch('/api/auth/me');
        const result = await response.json();
        
        if (result.success) {
            currentUser = result.data;
            document.getElementById('username').textContent = currentUser.nickname || currentUser.username;
            document.getElementById('user-avatar-text').textContent = 
                (currentUser.nickname || currentUser.username).charAt(0).toUpperCase();
            
            // 填充设置表单
            document.getElementById('setting-nickname').value = currentUser.nickname || '';
            document.getElementById('setting-email').value = currentUser.email || '';
        } else {
            window.location.href = '/login';
        }
    } catch (error) {
        console.error('Failed to get user info:', error);
        window.location.href = '/login';
    }
}

// 初始化 WebSocket
function initWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/chat`;
    
    ws = new WebSocket(wsUrl);
    
    ws.onopen = function() {
        console.log('WebSocket connected');
        updateConnectionStatus(true);
        if (reconnectInterval) {
            clearInterval(reconnectInterval);
            reconnectInterval = null;
        }
    };
    
    ws.onmessage = function(event) {
        const data = JSON.parse(event.data);
        handleMessage(data);
    };
    
    ws.onclose = function() {
        console.log('WebSocket disconnected');
        updateConnectionStatus(false);
        scheduleReconnect();
    };
    
    ws.onerror = function(error) {
        console.error('WebSocket error:', error);
        updateConnectionStatus(false);
    };
}

// 重新连接
function scheduleReconnect() {
    if (!reconnectInterval) {
        reconnectInterval = setInterval(() => {
            console.log('Attempting to reconnect...');
            initWebSocket();
        }, 5000);
    }
}

// 更新连接状态
function updateConnectionStatus(connected) {
    const statusEl = document.getElementById('connection-status');
    if (connected) {
        statusEl.textContent = '在线';
        statusEl.className = 'status online';
    } else {
        statusEl.textContent = '未连接';
        statusEl.className = 'status offline';
    }
}

// 处理收到的消息
function handleMessage(data) {
    if (data.type === 'CONNECTION_STATUS') {
        updateConnectionStatus(data.connected);
    } else if (data.type === 'ERROR') {
        showError(data.message);
    } else if (data.type === 'MESSAGE') {
        displayMessage(data);
    }
}

// 显示消息
function displayMessage(data) {
    const messageList = document.getElementById('message-list');
    const isOwn = data.sender === currentUser.username;
    
    const messageItem = document.createElement('div');
    messageItem.className = `message-item ${isOwn ? 'own' : ''}`;
    
    const time = data.timestamp ? new Date(data.timestamp * 1000).toLocaleTimeString() : new Date().toLocaleTimeString();
    const sender = data.senderType === 'SYSTEM' ? '系统' : data.sender;
    
    messageItem.innerHTML = `
        <div class="message-avatar">${sender.charAt(0).toUpperCase()}</div>
        <div class="message-content">
            <div class="message-header">
                <span class="message-sender">${sender}</span>
                <span class="message-time">${time}</span>
            </div>
            <div class="message-bubble">${escapeHtml(data.content)}</div>
        </div>
    `;
    
    messageList.appendChild(messageItem);
    messageList.scrollTop = messageList.scrollHeight;
}

// 发送消息
function sendMessage() {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
        showError('未连接到服务器');
        return;
    }
    
    const input = document.getElementById('message-input');
    const content = input.value.trim();
    
    if (!content) return;
    
    const msgType = document.getElementById('msg-type').value;
    const targetInput = document.getElementById('target-input').value.trim();
    
    const message = {
        msgType: msgType,
        content: content
    };
    
    // 根据消息类型添加目标
    if (msgType === 'UNICAST_PLAYER') {
        message.targetPlayer = targetInput;
    } else if (msgType === 'MULTICAST_PLAYER') {
        message.targetPlayers = targetInput.split(',').map(s => s.trim()).filter(s => s);
    } else if (msgType === 'MULTICAST_GROUP') {
        message.targetGroup = targetInput;
    }
    
    ws.send(JSON.stringify(message));
    input.value = '';
    updateCharCount();
}

// 初始化事件监听
function initEventListeners() {
    // 发送按钮
    document.getElementById('send-btn').addEventListener('click', sendMessage);
    
    // 回车发送
    document.getElementById('message-input').addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    
    // 字符计数
    document.getElementById('message-input').addEventListener('input', updateCharCount);
    
    // 消息类型切换
    document.getElementById('msg-type').addEventListener('change', function() {
        const targetInput = document.getElementById('target-input');
        if (this.value === 'BROADCAST') {
            targetInput.style.display = 'none';
        } else {
            targetInput.style.display = 'block';
            const placeholders = {
                'UNICAST_PLAYER': '输入目标玩家名',
                'MULTICAST_PLAYER': '输入玩家名，用逗号分隔',
                'MULTICAST_GROUP': '输入群组名'
            };
            targetInput.placeholder = placeholders[this.value] || '';
        }
    });
    
    // 标签切换
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', function() {
            const tab = this.dataset.tab;
            
            // 更新导航
            document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
            this.classList.add('active');
            
            // 更新内容
            document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
            document.getElementById(tab + '-tab').classList.add('active');
        });
    });
    
    // 保存资料
    document.getElementById('save-profile-btn').addEventListener('click', saveProfile);
    
    // 修改密码
    document.getElementById('change-password-btn').addEventListener('click', changePassword);
    
    // 删除账号
    document.getElementById('delete-account-btn').addEventListener('click', deleteAccount);
}

// 更新字符计数
function updateCharCount() {
    const input = document.getElementById('message-input');
    const count = input.value.length;
    document.querySelector('.char-count').textContent = `${count}/500`;
    document.getElementById('send-btn').disabled = count === 0;
}

// 保存资料
async function saveProfile() {
    const data = {
        nickname: document.getElementById('setting-nickname').value || null,
        email: document.getElementById('setting-email').value || null
    };
    
    try {
        const response = await fetch('/api/auth/me', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('保存成功');
            currentUser = result.data;
            document.getElementById('username').textContent = currentUser.nickname || currentUser.username;
        } else {
            alert(result.message || '保存失败');
        }
    } catch (error) {
        alert('网络错误');
    }
}

// 修改密码
async function changePassword() {
    const currentPassword = document.getElementById('current-password').value;
    const newPassword = document.getElementById('new-password').value;
    
    if (!currentPassword || !newPassword) {
        alert('请填写完整');
        return;
    }
    
    const data = {
        currentPassword: currentPassword,
        newPassword: newPassword
    };
    
    try {
        const response = await fetch('/api/auth/me', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('密码修改成功');
            document.getElementById('current-password').value = '';
            document.getElementById('new-password').value = '';
        } else {
            alert(result.message || '修改失败');
        }
    } catch (error) {
        alert('网络错误');
    }
}

// 删除账号
async function deleteAccount() {
    if (!confirm('确定要删除账号吗？此操作不可恢复！')) {
        return;
    }
    
    try {
        const response = await fetch('/api/auth/me', {
            method: 'DELETE'
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('账号已删除');
            window.location.href = '/login';
        } else {
            alert(result.message || '删除失败');
        }
    } catch (error) {
        alert('网络错误');
    }
}

// 显示错误
function showError(message) {
    console.error(message);
}

// HTML 转义
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
