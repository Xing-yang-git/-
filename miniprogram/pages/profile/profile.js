const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { POST_TYPE } = require('../../utils/constants');

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
    ratingCount: 0,
    recordTab: 'idle',
    idleRecords: [],
    helpRecords: [],
    // 来自后端的统计数据
    lendCount: 0,
    borrowCount: 0,
    borrowReturnRate: 0,
    helpReqCount: 0,
    helpProCount: 0,
    // Loading
    loading: true,

    // 退出登录弹窗
    showLogoutAlert: false,

    // 记录详情
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
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    const sysInfo = wx.getSystemInfoSync();
    this.setData({ statusBarHeight: sysInfo.statusBarHeight || 44 });
    this.loadProfile();
    this.loadRecords();
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
    if (this.data.userId) {
      this.loadProfile();
      this.loadRecords();
    }
  },

  // ================================================================
  // 个人资料数据 — 来自 /api/users/profile（真实数据库数据）
  // ================================================================
  async loadProfile() {
    const token = wx.getStorageSync('token');
    if (!token) {
      this.setData({ loading: false, isAuth: false });
      return;
    }
    try {
      const data = await api.get('/api/users/profile');
      this.setData({
        loading: false,
        userId: data.id || '',
        userName: data.name || '用户',
        avatarText: (data.name || '用')[0],
        roomInfo: data.roomInfo || '未绑定',
        isAuth: data.isAuth || false,
        userTypeText: data.userTypeText || '业主',
        score: (data.score || 0).toFixed(1),
        ratingCount: data.ratingCount || 0,
        lendCount: data.lendCount || 0,
        borrowCount: data.borrowCount || 0,
        borrowReturnRate: data.borrowReturnRate != null ? data.borrowReturnRate : 100,
        helpReqCount: data.helpReqCount || 0,
        helpProCount: data.helpProCount || 0
      });
    } catch (e) {
      console.error('Load profile failed:', e);
      this.setData({ loading: false });
    }
  },

  // ================================================================
  // 记录 — 来自 /api/users/completed（真实数据库数据）
  // ================================================================
  async loadRecords() {
    // 互借记录: borrow + lend
    try {
      const [borrowList, lendList] = await Promise.all([
        api.get('/api/users/completed', { role: 'borrow' }),
        api.get('/api/users/completed', { role: 'lend' })
      ]);
      const allIdle = [
        ...(Array.isArray(borrowList) ? borrowList : []),
        ...(Array.isArray(lendList) ? lendList : [])
      ];
      // 按 completedAt 倒序排列
      allIdle.sort((a, b) => {
        const ta = a.completedAt ? new Date(a.completedAt).getTime() : 0;
        const tb = b.completedAt ? new Date(b.completedAt).getTime() : 0;
        return tb - ta;
      });
      const idleRecords = allIdle.map(item => ({
        id: item.id,
        title: item.title || '',
        type: item.subType || POST_TYPE.LEND,
        typeText: item.subType === 'borrow' ? '借入' : '借出',
        roleText: item.subType === 'borrow' ? '借出者' : '借入者',
        peerName: item.personName || '',
        dateText: this.formatDate(item.completedAt),
        remark: item.myFeedback || item.theirFeedback || '—'
      }));
      this.setData({ idleRecords });
    } catch (e) {
      console.error('Load idle records failed:', e);
    }

    // 互助记录: helpReq + helpPro
    try {
      const [helpReqList, helpProList] = await Promise.all([
        api.get('/api/users/completed', { role: 'helpReq' }),
        api.get('/api/users/completed', { role: 'helpPro' })
      ]);
      const allHelp = [
        ...(Array.isArray(helpReqList) ? helpReqList : []),
        ...(Array.isArray(helpProList) ? helpProList : [])
      ];
      // 按 completedAt 倒序排列
      allHelp.sort((a, b) => {
        const ta = a.completedAt ? new Date(a.completedAt).getTime() : 0;
        const tb = b.completedAt ? new Date(b.completedAt).getTime() : 0;
        return tb - ta;
      });
      const helpRecords = allHelp.map(item => ({
        id: item.id,
        title: item.title || '',
        type: item.subType || 'helpReq',
        typeText: item.subType === 'helpReq' ? '求助' : '帮助',
        roleText: item.subType === 'helpReq' ? '帮助者' : '求助者',
        peerName: item.personName || '',
        dateText: this.formatDate(item.completedAt),
        remark: item.myFeedback || item.theirFeedback || '—'
      }));
      this.setData({ helpRecords });
    } catch (e) {
      console.error('Load help records failed:', e);
    }
  },

  // ================================================================
  // Tab 切换
  // ================================================================
  onRecordTabTap(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ recordTab: tab });
  },

  // ================================================================
  // 记录详情
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
  // 退出登录
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
  // 辅助函数
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
