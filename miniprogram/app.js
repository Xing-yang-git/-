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
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
    }
  }
});
