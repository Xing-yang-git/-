const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    item: {},
    images: [],
    showSheet: false,
    showHistorySheet: false,
    historyData: {},
    helpNote: '',
    hasApplied: false,      // 当前用户是否已申请帮忙（禁用按钮）
    submittingHelp: false   // 提交中防重复点击
  },

  onLoad(options) {
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    const id = options.id;
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      return;
    }
    this.loadItem(id);
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
  },

  loadItem(id) {
    wx.showLoading({ title: '加载中...' });
    api.get(`/api/help/${id}`)
      .then((data) => {
        wx.hideLoading();
        const item = this.formatItem(data);
        const historyData = this.buildHistoryData(data);
        this.setData({ item, images: item.images || [], historyData });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  formatItem(item) {
    // 解析 images（JSON 字符串 → 数组），相对路径补全 baseUrl，与 idle-detail 一致
    let imageList = [];
    if (item.images) {
      try {
        imageList = typeof item.images === 'string' ? JSON.parse(item.images) : item.images;
        if (!Array.isArray(imageList)) imageList = [];
        imageList = imageList.slice(0, 9);
      } catch (e) {
        imageList = [];
      }
    }
    const app = getApp();
    const baseUrl = (app && app.globalData && app.globalData.baseUrl) ? app.globalData.baseUrl : '';
    imageList = imageList.map(url => {
      if (url && url.startsWith('/uploads/') && baseUrl) {
        return baseUrl + url;
      }
      return url;
    });

    return {
      ...item,
      images: imageList,
      timeAgo: this.formatTime(item.createdAt),
      // 评分格式对齐 idle-detail：始终一位小数（如 5.0）
      ratingText: item.rating != null ? item.rating.toFixed(1) : null
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
    const userId = auth.getUserId() || (wx.getStorageSync('userInfo') || {}).id || '';

    // 防止与自己聊天
    if (userId && item.userId && String(userId) === String(item.userId)) {
      wx.showModal({
        title: '提示',
        content: '这是您自己发布的求助，无法联系自己',
        showCancel: false,
        confirmText: '知道了'
      });
      return;
    }

    // 直接跳转聊天页（聊天记录仅存本地，无需服务端创建会话）
    const otherUserId = item.userId;
    const ids = [userId, otherUserId].sort();
    const localSessionId = 'HELP_' + item.id + '_' + ids[0] + '_' + ids[1];
    const name = encodeURIComponent(item.userRoom || item.userName || '用户');
    const about = encodeURIComponent(item.title || '');
    const room = encodeURIComponent(item.userRoom || '');
    wx.navigateTo({
      url: `/pages/chat/chat?sessionId=${localSessionId}&name=${name}&about=${about}&room=${room}&aboutId=${item.id}&aboutType=HELP&otherUserId=${otherUserId}`
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
    if (this.data.submittingHelp || this.data.hasApplied) return;
    const { item, helpNote } = this.data;

    this.setData({ submittingHelp: true });
    wx.showLoading({ title: '提交中...' });

    api.post(`/api/help/${item.id}/apply`, { note: helpNote })
      .then(() => {
        wx.hideLoading();
        // 申请成功：关闭弹层、禁用「我来帮忙」按钮，不自动跳转聊天（需联系可点“联系他”）
        this.setData({ showSheet: false, helpNote: '', hasApplied: true, submittingHelp: false });
        wx.showToast({ title: '已申请，等待对方确认', icon: 'success' });
      })
      .catch((err) => {
        wx.hideLoading();
        this.setData({ submittingHelp: false });
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  onViewProfile() {
    const { item } = this.data;
    if (item.userId) {
      wx.navigateTo({ url: `/pages/profile/profile?userId=${item.userId}` });
    }
  },

  /* ===== 以往记录弹层（仿 idle-detail） ===== */

  buildHistoryData(data) {
    // 解析 userRoom: "1栋2单元301号(业主)" → address + userType
    const userRoom = data.userRoom || '';
    const typeMatch = userRoom.match(/\((.+)\)$/);
    const userType = typeMatch ? typeMatch[1] : '';
    const address = typeMatch ? userRoom.replace(/\(.+\)$/, '') : userRoom;

    const rating = data.rating != null ? data.rating.toFixed(1) : '5.0';

    return {
      address: address || userRoom || '—',
      userType: userType || '居民',
      rating: rating,
      borrowCount: data.borrowCount || 0,
      borrowReturnRate: data.returnRate ? parseInt(data.returnRate) || 0 : 100,
      lendCount: data.lendCount || 0,
      helpReqCount: data.helpCount || 0,
      helpProCount: data.helpedCount || 0
    };
  },

  onViewHistory() {
    this.setData({ showHistorySheet: true });
  },

  onCloseHistorySheet() {
    this.setData({ showHistorySheet: false });
  },

  preventTouchMove() {
    // 阻止遮罩层上的触摸事件穿透
  }
});
