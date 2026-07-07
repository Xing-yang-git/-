const api = require('../../utils/api');

Page({
  data: {
    borrowId: '',
    itemImage: '',
    itemTitle: '',
    borrowerName: '',
    isOnTime: null,
    returnStatus: 'ontime',
    damageType: 'none',
    damageNote: '',
    returnNote: '',
    images: []
  },

  onLoad(options) {
    const borrowId = options.id || '';
    this.setData({ borrowId });
    if (borrowId) {
      this.loadBorrowInfo(borrowId);
    }
  },

  async loadBorrowInfo(id) {
    try {
      const data = await api.get('/api/borrow/' + id);
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
    if (val === 'none') {
      this.setData({ damageNote: '' });
    }
  },

  onChooseImage() {
    const remaining = 9 - this.data.images.length;
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
      // Upload return photos first
      let returnPhotos = '';
      if (this.data.images.length > 0) {
        const uploadTasks = this.data.images.map(filePath => api.upload('/api/common/upload', filePath));
        const imageUrls = await Promise.all(uploadTasks);
        returnPhotos = JSON.stringify(imageUrls);
      }
      await api.put('/api/borrow/' + this.data.borrowId + '/return', {
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
