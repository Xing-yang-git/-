const api = require('../../utils/api');

Page({
  data: {
    statusBarHeight: 44,
    userId: '',
    userName: '',
    avatarText: '',
    roomInfo: '',
    isAuth: false,
    userTypeText: '业主',
    score: 0,
    starLevel: 0,
    ratingCount: 0,
    recordTab: 'idle',
    idleRecords: [],
    helpRecords: [],

    // Logout alert
    showLogoutAlert: false,

    // Record detail
    showRecordDetail: false,
    recordDetail: {
      item: '',
      role: '',
      peer: '',
      date: '',
      remark: ''
    }
  },

  onLoad() {
    const sysInfo = wx.getSystemInfoSync();
    this.setData({ statusBarHeight: sysInfo.statusBarHeight || 44 });
    const userInfo = wx.getStorageSync('userInfo') || {};
    const userId = userInfo.id || wx.getStorageSync('userId') || '';
    this.setData({
      userId,
      userName: userInfo.name || '用户',
      avatarText: (userInfo.name || '用')[0],
      roomInfo: userInfo.room || userInfo.roomNumber || '未绑定',
      isAuth: userInfo.isAuth || false,
      userTypeText: userInfo.userType === 'owner' ? '业主' : (userInfo.userType === 'tenant' ? '租客' : '业主')
    });
    if (userId) {
      this.loadRatings(userId);
      this.loadRecords();
    }
  },

  onShow() {
    if (this.data.userId) {
      this.loadRatings(this.data.userId);
      this.loadRecords();
    }
  },

  async loadRatings(userId) {
    try {
      const res = await api.get('/api/rating/user/' + userId);
      const data = res.data || {};
      this.setData({
        score: (data.score || 0).toFixed(1),
        starLevel: Math.round((data.score || 0) / 20),
        ratingCount: data.count || 0
      });
    } catch (e) {
      console.error('Load ratings failed:', e);
    }
  },

  async loadRecords() {
    if (this.data.recordTab === 'idle') {
      this.loadIdleRecords();
    } else {
      this.loadHelpRecords();
    }
  },

  async loadIdleRecords() {
    try {
      const res = await api.get('/api/idle/records');
      const list = (res.data && res.data.list) ? res.data.list : (res.data || []);
      const idleRecords = list.map(item => ({
        id: item.id,
        title: item.title || item.itemTitle || '',
        type: item.type || 'LEND',
        typeText: item.type === 'LEND' ? '借出' : '借入',
        roleText: item.type === 'LEND' ? '借入者' : '借出者',
        peerName: item.peerName || item.peer || '',
        dateText: this.formatDate(item.createTime),
        remark: item.remark || item.evaluation || '—'
      }));
      this.setData({ idleRecords });
    } catch (e) {
      console.error('Load idle records failed:', e);
    }
  },

  async loadHelpRecords() {
    try {
      const res = await api.get('/api/help/records');
      const list = (res.data && res.data.list) ? res.data.list : (res.data || []);
      const helpRecords = list.map(item => ({
        id: item.id,
        title: item.title || item.itemTitle || '',
        type: item.type || 'HELP',
        typeText: item.type === 'SEEK' ? '求助' : '帮助',
        roleText: item.type === 'SEEK' ? '帮助者' : '求助者',
        peerName: item.peerName || item.peer || '',
        dateText: this.formatDate(item.createTime),
        remark: item.remark || item.evaluation || '—'
      }));
      this.setData({ helpRecords });
    } catch (e) {
      console.error('Load help records failed:', e);
    }
  },

  // ================================================================
  // Tab Switching
  // ================================================================
  onRecordTabTap(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ recordTab: tab }, () => {
      this.loadRecords();
    });
  },

  // ================================================================
  // Record Detail
  // ================================================================
  onOpenRecord(e) {
    const record = e.currentTarget.dataset.record;
    this.setData({
      showRecordDetail: true,
      recordDetail: {
        item: record.title || '—',
        role: record.typeText || '—',
        peer: record.peerName || '—',
        date: record.dateText || '—',
        remark: record.remark || '—'
      }
    });
  },

  onCloseRecordDetail() {
    this.setData({ showRecordDetail: false });
  },

  // ================================================================
  // Logout
  // ================================================================
  onLogoutTap() {
    this.setData({ showLogoutAlert: true });
  },

  onCloseLogoutAlert() {
    this.setData({ showLogoutAlert: false });
  },

  onCancelLogout() {
    this.setData({ showLogoutAlert: false });
  },

  onDoLogout() {
    this.setData({ showLogoutAlert: false });
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('userId');
    wx.showToast({ title: '已退出登录', icon: 'none' });
    setTimeout(() => {
      wx.reLaunch({ url: '/pages/login/login' });
    }, 600);
  },

  // ================================================================
  // Helpers
  // ================================================================
  formatDate(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const Y = date.getFullYear();
    const M = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return Y + '-' + M + '-' + d;
  },

  formatTime(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const M = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    const h = date.getHours().toString().padStart(2, '0');
    const mi = date.getMinutes().toString().padStart(2, '0');
    return M + '-' + d + ' ' + h + ':' + mi;
  }
});
