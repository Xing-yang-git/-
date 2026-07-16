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
    },
    customBack: {
      type: Boolean,
      value: false
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
      if (this.data.customBack) {
        // 由页面自行处理返回（如审核页返回登录页）
        this.triggerEvent('back');
        return;
      }
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
