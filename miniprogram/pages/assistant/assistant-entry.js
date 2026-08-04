/**
 * 小邻 tab 中转页。
 *
 * tabBar 页面会被微信强制显示底部导航栏，而小邻对话页需要对齐聊天页的
 * "无底栏单页"体验，故 tabBar 第 3 项指向本空白中转页。
 *
 * 导航策略（保证手势返回回首页而不是退出小程序）：
 * - 点小邻 tab → navigateTo 打开对话页（保留返回栈，对话页手势/‹ 返回可回到本页）；
 * - 从对话页返回（对话页 onHide/onUnload 已置 assistantReturning 标记）→ 切回首页 tab，
 *   避免停在空白中转页或直接退出小程序。
 */
Page({
  onShow() {
    const app = getApp();
    const returning = !!(app && app.globalData && app.globalData.assistantReturning);
    if (returning) {
      // 从对话页返回（手势返回 / ‹ 按钮）：回首页 tab
      app.globalData.assistantReturning = false;
      wx.switchTab({ url: '/pages/home/home' });
      return;
    }
    // 点小邻 tab：打开对话页（普通页面，天然无底栏）
    wx.navigateTo({
      url: '/pages/assistant/assistant',
      // 打开失败兜底：退回首页，避免停在空白中转页
      fail: () => wx.switchTab({ url: '/pages/home/home' })
    });
  }
});
