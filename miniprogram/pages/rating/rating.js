const api = require('../../utils/api');

Page({
  data: {
    borrowId: '',
    targetName: '',
    ratingType: 'borrow', // 'borrow' or 'help'
    ratingTypeText: '',
    overallScore: 0,
    dimScores: [0, 0],
    dimLabels: ['物品完好度', '沟通态度']
  },

  onLoad(options) {
    const borrowId = options.id || '';
    const targetName = decodeURIComponent(options.name || '用户');
    const ratingType = options.type || 'borrow';

    let dimLabels = ['物品完好度', '沟通态度'];
    let ratingTypeText = '借出评价';
    if (ratingType === 'help') {
      dimLabels = ['帮助质量', '响应速度'];
      ratingTypeText = '帮助评价';
    }

    this.setData({
      borrowId,
      targetName,
      ratingType,
      ratingTypeText,
      dimLabels
    });
  },

  onOverallTap(e) {
    const score = parseInt(e.currentTarget.dataset.score);
    this.setData({ overallScore: score });
  },

  onDimTap(e) {
    const dim = parseInt(e.currentTarget.dataset.dim);
    const score = parseInt(e.currentTarget.dataset.score);
    const dimScores = [...this.data.dimScores];
    dimScores[dim] = score;
    this.setData({ dimScores });
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
        overallScore: this.data.overallScore,
        dimScores: JSON.stringify(this.data.dimScores),
        dimLabels: JSON.stringify(this.data.dimLabels)
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
