const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    borrowId: '',
    targetName: '',
    ratingType: 'borrow', // 'borrow' or 'help'
    ratingTypeText: '',
    overallScore: 0
  },

  onLoad(options) {
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    const borrowId = options.id || '';
    const targetName = decodeURIComponent(options.name || '用户');
    const ratingType = options.type || 'borrow';

    const ratingTypeText = ratingType === 'help' ? '帮助评价' : '借出评价';

    this.setData({
      borrowId,
      targetName,
      ratingType,
      ratingTypeText
    });
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
  },

  onOverallTap(e) {
    const score = parseInt(e.currentTarget.dataset.score);
    this.setData({ overallScore: score });
  },

  async onSubmit() {
    if (this.data.overallScore === 0) {
      wx.showToast({ title: '请选择总体评分', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '提交中' });
    try {
      await api.post('/api/rating', {
        targetId: this.data.borrowId,
        ratingType: this.data.ratingType,
        overallScore: this.data.overallScore
      });
      wx.hideLoading();
      wx.showToast({ title: '评价成功' });
      setTimeout(() => wx.navigateBack(), 1500);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message || '评价失败', icon: 'none' });
    }
  }
});
