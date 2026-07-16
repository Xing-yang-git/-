App({
  globalData: {
    // 使用局域网IP而非localhost：手机预览时localhost指向手机自身，无法访问PC后端
    // PC端开发工具中 192.168.x.x 同样可用（本机回环）
    baseUrl: 'http://192.168.31.64:8080',
    token: '',
    userId: '',
    userInfo: null
  },

  onLaunch() {
    // ============================================================
    // 局域网开发调试说明：
    // 预览模式下微信强制校验请求域名白名单，局域网IP无法加入白名单。
    // 请使用以下任一方式绕过：
    //   1. 真机调试模式（自动代理，推荐）
    //   2. IDE 生成预览二维码时勾选「不校验合法域名」
    // 不要在代码中调用 wx.setEnableDebug —— 会触发微信广告调优系统的
    // operateWXData:fail invalid scope 错误（框架内部行为，无法捕获）。
    // ============================================================

    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
    }
  }
});
