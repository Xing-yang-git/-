const api = require('../../utils/api');
const auth = require('../../utils/auth');

const app = getApp();

/** 本地存储 key 前缀 */
const MSG_STORE_PREFIX = 'chat_msgs_';

Page({
  data: {
    sessionId: '',
    otherName: '',
    roomInfo: '',
    aboutTitle: '',
    aboutId: '',
    messages: [],
    inputText: '',
    scrollToView: '',
    userId: '',
    socketOpen: false
  },

  onLoad(options) {
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    const sessionId = options.sessionId || '';
    const nameParam = decodeURIComponent(options.name || '用户');
    const roomInfo = decodeURIComponent(options.room || '');
    const aboutTitle = decodeURIComponent(options.about || '');
    const aboutId = options.aboutId || '';
    const aboutType = options.aboutType || '';
    const otherUserId = options.otherUserId || '';
    const userId = auth.getUserId();

    // 智能选择标题：如果传递的名字不像门牌地址（不含"栋/号/单元"），
    // 则优先使用 room 参数
    const hasAddress = /[栋号楼]/.test(nameParam);
    const displayName = hasAddress ? nameParam : (roomInfo || nameParam);

    // 设置原生导航栏标题
    wx.setNavigationBarTitle({ title: displayName || '聊天' });

    this.setData({ sessionId, otherName: displayName, roomInfo, aboutTitle, aboutId, aboutType, otherUserId, userId });

    // 保存会话元数据（供 messages 页面构建列表；按用户命名空间隔离）
    if (sessionId && userId) {
      const metaKey = 'chat_meta_' + userId + '_' + sessionId;
      wx.setStorageSync(metaKey, {
        otherName: displayName,
        room: roomInfo,
        about: aboutTitle,
        aboutId: aboutId,
        aboutType: aboutType,
        otherUserId: otherUserId
      });
    }

    // 从本地加载历史消息
    this.loadLocalMessages();

    // 连接 WebSocket 接收实时消息
    if (sessionId && userId) {
      this.connectWebSocket();
    }
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
    // 页面显示时重新加载本地消息（可能在其他页面有更新）
    this.loadLocalMessages();
  },

  onUnload() {
    if (this.socketTask) {
      this.socketTask.close();
    }
  },

  /**
   * 从本地存储加载聊天记录（纯本地，不请求服务端）
   */
  loadLocalMessages() {
    if (!this.data.sessionId || !this.data.userId) return;
    const key = MSG_STORE_PREFIX + this.data.userId + '_' + this.data.sessionId;
    try {
      const raw = wx.getStorageSync(key);
      if (raw && Array.isArray(raw) && raw.length > 0) {
        this.setData({ messages: raw });
        this.scrollToBottom();
      }
    } catch (e) {
      console.error('Load local messages failed:', e);
    }
  },

  /**
   * 保存消息到本地存储
   */
  saveLocalMessages(messages) {
    if (!this.data.sessionId || !this.data.userId) return;
    const key = MSG_STORE_PREFIX + this.data.userId + '_' + this.data.sessionId;
    try {
      wx.setStorageSync(key, messages);
    } catch (e) {
      console.error('Save local messages failed:', e);
    }
  },

  formatTime(date) {
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();
    const h = date.getHours().toString().padStart(2, '0');
    const m = date.getMinutes().toString().padStart(2, '0');
    if (isToday) return h + ':' + m;
    const M = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return M + '/' + d + ' ' + h + ':' + m;
  },

  scrollToBottom() {
    setTimeout(() => {
      this.setData({ scrollToView: 'msg-bottom' });
    }, 100);
  },

  connectWebSocket() {
    const baseUrl = (app && app.globalData) ? app.globalData.baseUrl : '';
    // 从 HTTP baseUrl 推导 WS 地址：http://xxx:8080 → ws://xxx:8080
    const wsBase = baseUrl.replace(/^http/, 'ws');
    // 通过 token 建立连接，服务端从 token 解析用户身份（不再信任客户端传的 userId）
    const wsUrl = wsBase + '/ws/chat?token=' + encodeURIComponent(auth.getToken());

    this.socketTask = wx.connectSocket({ url: wsUrl });

    this.socketTask.onOpen(() => {
      this.setData({ socketOpen: true });
      console.log('Chat WebSocket connected:', wsUrl);
    });

    this.socketTask.onMessage(res => {
      try {
        const msg = JSON.parse(res.data);
        // 只接收发给当前会话的消息（来自对方的实时消息）
        if (msg.sessionId === this.data.sessionId) {
          const now = Date.now();
          const messages = this.data.messages;
          const newMsg = {
            id: msg.id || ('ws_' + now),
            content: msg.content,
            isMine: String(msg.fromUserId || msg.senderId) === String(this.data.userId),
            type: msg.messageType === 'system' ? 'system' : 'text',
            systemIcon: msg.messageType === 'system' ? 'ⓘ' : '',
            timestamp: now,
            showTime: messages.length === 0 || (now - (messages[messages.length - 1].timestamp || 0) > 300000),
            timeText: this.formatTime(new Date())
          };

          const updated = [...messages, newMsg];
          this.setData({ messages: updated });
          this.saveLocalMessages(updated);
          this.scrollToBottom();
        }
      } catch (e) {
        console.error('WS message parse error:', e);
      }
    });

    this.socketTask.onError(err => {
      console.error('WebSocket error:', err);
      this.setData({ socketOpen: false });
    });

    this.socketTask.onClose(res => {
      // code 4001：被新登录挤下线（服务端主动关闭），强制重新登录
      if (res && res.code === 4001) {
        api.forceRelogin();
      }
      this.setData({ socketOpen: false });
    });
  },

  onInputChange(e) {
    this.setData({ inputText: e.detail.value });
  },

  onSend() {
    const text = this.data.inputText.trim();
    if (!text) return;

    const now = Date.now();
    const messages = this.data.messages;

    // 本地消息对象
    const newMsg = {
      id: 'local_' + now,
      content: text,
      isMine: true,
      type: 'text',
      timestamp: now,
      showTime: messages.length === 0 || (now - (messages[messages.length - 1].timestamp || 0) > 300000),
      timeText: this.formatTime(new Date())
    };

    const updated = [...messages, newMsg];
    this.setData({ messages: updated, inputText: '' });
    this.saveLocalMessages(updated);
    this.scrollToBottom();

    // 通过 relay 发送消息（服务端仅 WebSocket 转发，不存盘）
    api.post('/api/chat/relay', {
      toUserId: this.data.otherUserId,
      sessionId: this.data.sessionId,
      content: text,
      messageType: 'text'
    }).catch(e => {
      console.error('Send message failed:', e);
    });
  },

  onNavBack() {
    wx.navigateBack({
      fail() { wx.switchTab({ url: '/pages/messages/messages' }); }
    });
  },

  onAboutTap() {
    if (this.data.aboutId && this.data.aboutType) {
      const page = this.data.aboutType === 'HELP' ? 'help-detail' : 'idle-detail';
      wx.navigateTo({ url: '/pages/' + page + '/' + page + '?id=' + this.data.aboutId });
    }
  }
});
