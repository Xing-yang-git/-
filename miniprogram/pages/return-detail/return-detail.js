const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { DAMAGE_TYPE, RETURN_STATUS } = require('../../utils/constants');

/**
 * 归还详情页 — 借用归还确认与损坏评估。
 *
 * 功能：归还照片上传、归还状态选择（按时/逾期/未归还）、损坏类型评估、
 *        归还备注填写、归还确认提交。
 */
Page({
  data: {
    borrowId: '',
    itemImage: '',
    itemTitle: '',
    borrowerName: '',
    isOnTime: null,
    returnStatus: RETURN_STATUS.ON_TIME,
    damageType: DAMAGE_TYPE.NORMAL,
    damageNote: '',
    returnNote: '',
    images: []
  },

  onLoad(options) {
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    const borrowId = options.id || '';
    this.setData({ borrowId });
    if (borrowId) {
      this.loadBorrowInfo(borrowId);
    }
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
  },

  async loadBorrowInfo(id) {
    try {
      const data = await api.get('/api/borrow-requests/' + id);
      this.setData({
        itemImage: data.itemImage || '',
        itemTitle: data.idleTitle || '',
        borrowerName: data.borrowerName || ''
      });
    } catch (e) {
      console.error('Load borrow info failed:', e);
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  onOnTimeTap(e) {
    const val = e.currentTarget.dataset.value;
    this.setData({ isOnTime: val });
  },

  onReturnStatusTap(e) {
    this.setData({ returnStatus: e.currentTarget.dataset.value });
  },

  onDamageTap(e) {
    const val = e.currentTarget.dataset.value;
    this.setData({ damageType: val });
    if (val === DAMAGE_TYPE.NORMAL) {
      this.setData({ damageNote: '' });
    }
  },

  onChooseImage() {
    const remaining = 4 - this.data.images.length;
    if (remaining <= 0) return;
    wx.chooseImage({
      count: remaining,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: res => {
        this.setData({ images: [...this.data.images, ...res.tempFilePaths] });
      }
    });
  },

  onRemoveImage(e) {
    const idx = e.currentTarget.dataset.index;
    const imgs = [...this.data.images];
    imgs.splice(idx, 1);
    this.setData({ images: imgs });
  },

  async onSubmit() {
    if (this.data.isOnTime === null) {
      wx.showToast({ title: '请选择是否准时归还', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '提交中' });
    try {
      // 先上传归还照片
      let returnPhotos = '';
      if (this.data.images.length > 0) {
        const uploadTasks = this.data.images.map(filePath => api.upload('/api/common/upload', filePath));
        const imageUrls = await Promise.all(uploadTasks);
        returnPhotos = JSON.stringify(imageUrls);
      }
      await api.put('/api/borrow-requests/' + this.data.borrowId + '/return', {
        isOnTime: this.data.isOnTime,
        returnStatus: this.data.returnStatus,
        damageType: this.data.damageType,
        damageNote: this.data.damageNote,
        returnNote: this.data.returnNote,
        returnPhotos: returnPhotos
      });
      wx.hideLoading();
      wx.showToast({ title: '归还确认成功' });
      setTimeout(() => {
        wx.redirectTo({ url: '/pages/rating/rating?id=' + this.data.borrowId + '&name=' + encodeURIComponent(this.data.borrowerName) });
      }, 1500);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message || '提交失败', icon: 'none' });
    }
  }
});
