Component({
  properties: {
    title: {
      type: String,
      value: ''
    },
    showBack: {
      type: Boolean,
      value: true
    },
    rightText: {
      type: String,
      value: ''
    }
  },

  data: {
    statusBarHeight: 0
  },

  lifetimes: {
    attached() {
      const systemInfo = wx.getSystemInfoSync();
      this.setData({
        statusBarHeight: systemInfo.statusBarHeight || 20
      });
    }
  },

  methods: {
    onBack() {
      wx.navigateBack({
        delta: 1,
        fail() {
          wx.switchTab({ url: '/pages/home/home' });
        }
      });
    },

    onRightAction() {
      this.triggerEvent('action');
    }
  }
});
