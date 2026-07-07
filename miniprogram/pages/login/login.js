const api = require('../../utils/api');

Page({
  data: {
    agreed: false
  },

  onLoad() {
    // Check if already logged in
    const token = wx.getStorageSync('token');
    if (token) {
      wx.switchTab({ url: '/pages/home/home' });
      return;
    }
  },

  onAgreeChange(e) {
    this.setData({ agreed: e.detail.value.length > 0 });
  },

  onLoginTap() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先同意用户协议', icon: 'none' });
      return;
    }

    wx.showLoading({ title: '登录中...' });

    wx.login({
      success: (res) => {
        if (res.code) {
          api.post('/api/auth/wx-login', { code: res.code })
            .then((data) => {
              wx.hideLoading();
              if (data.token) {
                wx.setStorageSync('token', data.token);
                // Store user info for other pages (e.g. home page community name)
                if (data.user) {
                  wx.setStorageSync('userInfo', data.user);
                  const app = getApp();
                  app.globalData.userInfo = data.user;
                }
                const userStatus = data.user ? data.user.authStatus : (data.authStatus || data.status);
                if (data.needRegister || userStatus === 'registering') {
                  wx.redirectTo({ url: '/pages/register/register' });
                } else if (userStatus === 'pending') {
                  wx.redirectTo({ url: '/pages/review-status/review-status' });
                } else {
                  wx.switchTab({ url: '/pages/home/home' });
                }
              } else {
                wx.showToast({ title: '登录失败，请重试', icon: 'none' });
              }
            })
            .catch((err) => {
              wx.hideLoading();
              wx.showToast({ title: err.message || '登录失败', icon: 'none' });
            });
        } else {
          wx.hideLoading();
          wx.showToast({ title: '获取微信登录凭证失败', icon: 'none' });
        }
      },
      fail: () => {
        wx.hideLoading();
        wx.showToast({ title: '微信登录失败', icon: 'none' });
      }
    });
  }
});
