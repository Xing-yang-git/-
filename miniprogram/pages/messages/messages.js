const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    sessions: []
  },

  onLoad() {
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    this.loadSessions();
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
    this.loadSessions();
  },

  onPullDownRefresh() {
    this.loadSessions().then(() => {
      wx.stopPullDownRefresh();
    });
  },

  async loadSessions() {
    const token = wx.getStorageSync('token');
    if (!token) {
      this.setData({ sessions: [] });
      return;
    }
    const uid = auth.getUserId();
    if (!uid) { this.setData({ sessions: [] }); return; }
    try {
      // 从本地存储构建会话列表（不再从服务端获取；键按用户命名空间隔离）
      const MSG_PREFIX = 'chat_msgs_' + uid + '_';
      const META_PREFIX = 'chat_meta_' + uid + '_';
      const sessions = [];
      try {
        const info = wx.getStorageInfoSync();
        info.keys.forEach(key => {
          // 一次性清理旧格式键（无用户前缀，无法归属账号，直接删除）
          if ((key.startsWith('chat_msgs_') || key.startsWith('chat_meta_')) && !/^\d+_/.test(key.substring(10))) {
            wx.removeStorageSync(key);
            return;
          }
          if (key.startsWith(MSG_PREFIX)) {
            const sessionId = key.substring(MSG_PREFIX.length);
            // 读取最后一条消息
            const messages = wx.getStorageSync(key) || [];
            const lastMsg = messages.length > 0 ? messages[messages.length - 1] : null;
            // 读取会话元数据
            const meta = wx.getStorageSync(META_PREFIX + sessionId) || {};
            if (lastMsg || meta.otherName) {
              sessions.push({
                id: sessionId,
                otherName: meta.otherName || '用户',
                avatarText: (meta.otherName || '?')[0],
                avatarColor: this.getAvatarColor(sessionId),
                lastMessage: lastMsg ? lastMsg.content : '',
                timeText: lastMsg ? this.formatRelativeTime(lastMsg.timestamp) : '',
                roomInfo: meta.room || '',
                aboutTitle: meta.about || '',
                aboutId: meta.aboutId || '',
                aboutType: meta.aboutType || '',
                otherUserId: meta.otherUserId || '',
                _sortTime: lastMsg ? lastMsg.timestamp : 0
              });
            }
          }
        });
      } catch (e) {
        console.error('Scan local storage failed:', e);
      }
      sessions.sort((a, b) => {
        return (b._sortTime || 0) - (a._sortTime || 0);
      });
      this.setData({ sessions });
    } catch (e) {
      console.error('Load sessions failed:', e);
    }
  },

  getAvatarColor(str) {
    const colors = ['#007AFF', '#34c759', '#ff9500', '#ff3b30', '#5856d6', '#af52de', '#ff2d55', '#00c7be'];
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
  },

  formatRelativeTime(timestamp) {
    if (!timestamp) return '';
    const now = Date.now();
    const diff = now - new Date(timestamp).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return minutes + '分钟前';
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return hours + '小时前';
    const days = Math.floor(hours / 24);
    if (days === 1) return '昨天';
    if (days < 7) return days + '天前';
    const date = new Date(timestamp);
    return (date.getMonth() + 1) + '/' + date.getDate();
  },

  onSessionTap(e) {
    const { id, name, room, about, aboutId, aboutType, otheruserid } = e.currentTarget.dataset;
    const url = '/pages/chat/chat?sessionId=' + id
      + '&name=' + encodeURIComponent(name)
      + '&room=' + encodeURIComponent(room || '')
      + '&about=' + encodeURIComponent(about || '')
      + '&aboutId=' + (aboutId || '')
      + '&aboutType=' + (aboutType || '')
      + '&otherUserId=' + (otheruserid || '');
    wx.navigateTo({ url });
  }
});
