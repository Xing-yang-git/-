const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { STATUS } = require('../../utils/constants');

Page({
  data: {
    status: STATUS.PENDING,
    rejectReason: '',
    banReason: '',
    loading: true
  },

  onLoad(options) {
    // 此页不该被无 token 者到达
    if (!auth.getToken()) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    const state = options.state || STATUS.PENDING;
    this.setData({ status: state });
    this.checkStatus();
  },

  onPullDownRefresh() {
    this.checkStatus().finally(() => {
      wx.stopPullDownRefresh();
    });
  },

  checkStatus() {
    this.setData({ loading: true });
    return api.get('/api/auth/status')
      .then((data) => {
        const authStatus = data.authStatus || data.status;
        // 关键：先把最新审核状态写回本地，防止页面门禁用旧状态造成循环跳转
        const userInfo = wx.getStorageSync('userInfo') || {};
        if (authStatus && userInfo.authStatus !== authStatus) {
          userInfo.authStatus = authStatus;
          wx.setStorageSync('userInfo', userInfo);
          const app = getApp();
          if (app) app.globalData.userInfo = userInfo;
        }
        if (authStatus === STATUS.APPROVED) {
          wx.switchTab({ url: '/pages/home/home' });
          return;
        }
        this.setData({
          status: authStatus || STATUS.PENDING,
          rejectReason: data.rejectReason || '',
          banReason: data.bannedReason || '', // 修复：服务端返回键名是 bannedReason
          loading: false
        });
      })
      .catch(() => {
        // 出错时保留 onLoad 传入的当前状态
        this.setData({ loading: false });
      });
  },

  onRefresh() {
    this.checkStatus();
  },

  // 自定义导航返回 = 换账号登录，必须清 token，否则登录页会按 pending 状态把用户弹回本页
  onBackToLogin() {
    auth.clearToken();
    wx.removeStorageSync('userInfo');
    wx.reLaunch({ url: '/pages/login/login' });
  },

  onContactProperty() {
    wx.showModal({
      title: '联系物业',
      content: '请拨打物业电话或前往物业服务中心咨询',
      showCancel: true,
      confirmText: '知道了'
    });
  },

  onResubmit() {
    wx.navigateTo({ url: '/pages/register/register' });
  },

  onAppeal() {
    wx.showModal({
      title: '申诉',
      content: '确认提交申诉请求？申诉通常需要 3-5 个工作日处理。',
      success: (res) => {
        if (res.confirm) {
          wx.showLoading({ title: '提交中...' });
          api.post('/api/auth/appeal')
            .then(() => {
              wx.hideLoading();
              wx.showToast({ title: '申诉已提交', icon: 'success' });
            })
            .catch((err) => {
              wx.hideLoading();
              wx.showToast({ title: err.message || '提交失败', icon: 'none' });
            });
        }
      }
    });
  }
});
