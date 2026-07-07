const api = require('../../utils/api');

Page({
  data: {
    item: {},
    showSheet: false,
    helpNote: ''
  },

  onLoad(options) {
    const id = options.id;
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      return;
    }
    this.loadItem(id);
  },

  loadItem(id) {
    wx.showLoading({ title: '加载中...' });
    api.get(`/api/help/${id}`)
      .then((data) => {
        wx.hideLoading();
        const item = this.formatItem(data);
        this.setData({ item });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  formatItem(item) {
    return {
      ...item,
      timeAgo: this.formatTime(item.createdAt),
      rewardLabel: this.formatReward(item.rewardType)
    };
  },

  formatTime(time) {
    if (!time) return '';
    const now = Date.now();
    const postTime = new Date(time).getTime();
    const diff = now - postTime;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return minutes + '分钟前';
    if (hours < 24) return hours + '小时前';
    if (days < 7) return days + '天前';
    return '7天前';
  },

  formatReward(type) {
    const map = {
      'free': '无偿互助',
      'paid': '有偿答谢',
      'exchange': '技能互换'
    };
    return map[type] || type || '无偿互助';
  },

  onContactTap() {
    const { item } = this.data;
    wx.showLoading({ title: '创建会话...' });
    api.post('/api/chat/create', { targetUserId: item.userId, postId: item.id, postType: 'HELP' })
      .then((data) => {
        wx.hideLoading();
        wx.navigateTo({ url: `/pages/chat/chat?sessionId=${data.sessionId}` });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  onHelpTap() {
    this.setData({ showSheet: true, helpNote: '' });
  },

  onCloseSheet() {
    this.setData({ showSheet: false, helpNote: '' });
  },

  onHelpNoteInput(e) {
    this.setData({ helpNote: e.detail.value });
  },

  onSubmitHelp() {
    const { item, helpNote } = this.data;

    wx.showLoading({ title: '提交中...' });

    api.post(`/api/help/${item.id}/apply`, { note: helpNote })
      .then(() => {
        wx.hideLoading();
        this.setData({ showSheet: false, helpNote: '' });
        wx.showToast({ title: '你已确认帮助，进入聊天', icon: 'success' });
        setTimeout(() => {
          // Navigate to chat after confirmation
          api.post('/api/chat/create', { targetUserId: item.userId, postId: item.id, postType: 'HELP' })
            .then((data) => {
              wx.navigateTo({ url: `/pages/chat/chat?sessionId=${data.sessionId}` });
            })
            .catch(() => {
              // Non-blocking error
            });
        }, 800);
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  onViewProfile() {
    const { item } = this.data;
    if (item.userId) {
      wx.navigateTo({ url: `/pages/profile/profile?userId=${item.userId}` });
    }
  }
});
