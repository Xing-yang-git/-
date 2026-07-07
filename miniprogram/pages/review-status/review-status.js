const api = require('../../utils/api');

Page({
  data: {
    status: 'pending',
    rejectReason: '',
    banReason: '',
    loading: true
  },

  onLoad(options) {
    const state = options.state || 'pending';
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
        if (authStatus === 'approved') {
          wx.switchTab({ url: '/pages/home/home' });
          return;
        }
        this.setData({
          status: authStatus || 'pending',
          rejectReason: data.rejectReason || '',
          banReason: data.banReason || '',
          loading: false
        });
      })
      .catch(() => {
        // Keep current status from onLoad on error
        this.setData({ loading: false });
      });
  },

  onRefresh() {
    this.checkStatus();
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
    wx.redirectTo({ url: '/pages/register/register' });
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
