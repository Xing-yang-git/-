/**
 * 自定义导航栏组件 — 替代系统导航栏，适配异形屏安全区。
 *
 * @property {String}  title      — 导航栏标题
 * @property {Boolean} showBack   — 是否显示返回按钮（默认 true）
 * @property {String}  rightText  — 右侧操作文字（如"发布"）
 * @property {Boolean} customBack — 是否使用自定义返回逻辑（触发 back 事件）
 * @event back   — customBack 为 true 时点击返回触发
 * @event action — 点击右侧文字触发
 */
Component({
  properties: {
    /** 导航栏标题 */
    title: {
      type: String,
      value: ''
    },
    /** 是否显示返回按钮 */
    showBack: {
      type: Boolean,
      value: true
    },
    /** 右侧操作文字（如"发布"） */
    rightText: {
      type: String,
      value: ''
    },
    /** 是否使用自定义返回逻辑（触发 back 事件而非 wx.navigateBack） */
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
