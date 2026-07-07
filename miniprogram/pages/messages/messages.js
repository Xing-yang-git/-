const api = require('../../utils/api');

Page({
  data: {
    sessions: []
  },

  onLoad() {
    this.loadSessions();
  },

  onShow() {
    this.loadSessions();
  },

  onPullDownRefresh() {
    this.loadSessions().then(() => {
      wx.stopPullDownRefresh();
    });
  },

  async loadSessions() {
    try {
      const res = await api.get('/api/chat/sessions');
      const list = res.data && res.data.list ? res.data.list : (res.data || []);
      const sessions = list.map(item => ({
        id: item.id,
        otherName: item.otherName || '用户',
        avatarText: (item.otherName || '?')[0],
        avatarColor: this.getAvatarColor(item.id || item.otherName),
        lastMessage: item.lastMessage || '',
        timeText: this.formatRelativeTime(item.lastTime || item.updateTime),
        unreadCount: item.unreadCount || 0,
        isRead: (item.unreadCount || 0) === 0,
        roomInfo: item.roomInfo || '',
        aboutTitle: item.aboutTitle || '',
        aboutId: item.aboutId || ''
      }));
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
    const { id, name, room, about, aboutId } = e.currentTarget.dataset;
    const url = '/pages/chat/chat?sessionId=' + id
      + '&name=' + encodeURIComponent(name)
      + '&room=' + encodeURIComponent(room || '')
      + '&about=' + encodeURIComponent(about || '')
      + '&aboutId=' + (aboutId || '');
    wx.navigateTo({ url });
  }
});
