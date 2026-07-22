/**
 * 服务通知详情页 — 只读卡片列表，无输入框
 * 通知按时间倒序排列：最新通知在最上面
 */
const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    notifications: [],
    loading: true,
    windowHeight: 0
  },

  onLoad() {
    if (!auth.ensureAccess()) return;
    wx.setNavigationBarTitle({ title: '服务通知' });
    api.put('/api/notifications/read-all').catch(() => {});
    // 进入服务通知页后清除全局通知未读计数，刷新 tabBar 红点
    const app = getApp();
    app.globalData.noticeUnreadCount = 0;
    app._updateTabBarBadge();
    this.setData({ windowHeight: wx.getSystemInfoSync().windowHeight });
    this.loadNotifications();

    // 注册全局 WebSocket 回调：接收实时推送的通知
    this._wsHandler = (msg) => {
      if (msg.type === 'notification' && msg.data) {
        const n = msg.data;
        const notification = this._formatNotification(n);
        const notifications = [notification, ...this.data.notifications];
        this.setData({ notifications });
      }
    };
    app.onGlobalMessage(this._wsHandler);
  },

  onShow() {
    // 从 my-posts 评价完返回时，同步已评价状态
    this._syncRatedStatus();
  },

  onUnload() {
    if (this._wsHandler) {
      getApp().offGlobalMessage(this._wsHandler);
      this._wsHandler = null;
    }
  },

  async loadNotifications() {
    this.setData({ loading: true });
    try {
      const data = await api.get('/api/notifications');
      // API 返回 ORDER BY created_at DESC，最新通知已在最前面，无需 reverse
      const list = Array.isArray(data) ? data : [];
      const notifications = list.map(n => this._formatNotification(n));
      this.setData({ notifications, loading: false });
    } catch (e) {
      console.error('加载通知失败:', e);
      this.setData({ loading: false });
    }
  },

  /** 将原始通知数据格式化为展示模型，同步已评价状态 */
  _formatNotification(n) {
    const app = getApp();
    const ratedIds = app.globalData.ratedNotificationIds || [];
    // 清洗标题和内容中的方括号
    const cleanTitle = (n.title || '').replace(/\[/g, '').replace(/\]/g, '');
    const cleanContent = (n.content || '').replace(/\[/g, '').replace(/\]/g, '');
    // rateable 由后端根据实际状态（已完成 + 未评分）计算
    const backendRateable = n.rateable === true;
    // 审批类通知：后端 actionable 决定是否仍可操作（待审批）；help_approved 始终有效
    const isApprovalType = ['help_application', 'borrow_request'].includes(n.type);
    const isApprovalActive = isApprovalType && n.actionable === true;
    return {
      ...n,
      title: cleanTitle,
      content: cleanContent,
      dateTimeText: this.formatDateTime(n.createdAt),
      isTappable: isApprovalActive || (!isApprovalType && ['help_approved'].includes(n.type)),
      isExpired: isApprovalType && !isApprovalActive,
      isRateable: backendRateable && !ratedIds.includes(n.relatedId),
      isRated: backendRateable && ratedIds.includes(n.relatedId)
    };
  },

  /** 同步 globalData 中的已评价 ID 列表到当前通知卡片的 isRated 标记 */
  _syncRatedStatus() {
    const ratedIds = getApp().globalData.ratedNotificationIds || [];
    if (ratedIds.length === 0) return;
    const notifications = this.data.notifications.map(n => {
      const isApprovalType = ['help_application', 'borrow_request'].includes(n.type);
      const isApprovalActive = isApprovalType && n.actionable === true;
      return {
        ...n,
        isTappable: isApprovalActive || (!isApprovalType && ['help_approved'].includes(n.type)),
        isExpired: isApprovalType && !isApprovalActive,
        isRateable: n.rateable && !ratedIds.includes(n.relatedId),
        isRated: n.rateable && ratedIds.includes(n.relatedId)
      };
    });
    this.setData({ notifications });
  },

  onCardTap(e) {
    const item = e.currentTarget.dataset.item;
    if (item.isRated || item.isExpired) {
      // 已评价 / 已失效的通知不可再点击跳转
      return;
    }
    if (item.isRateable) {
      // 存入 globalData，由 my-posts 页 onShow 消费后自动跳转到已完成 tab 并弹出详情弹框
      const app = getApp();
      app.globalData.pendingCompletedTarget = {
        relatedId: item.relatedId,
        type: item.type
      };
      wx.switchTab({ url: '/pages/my-posts/my-posts' });
      return;
    }
    if (!item.isTappable) return;
    const app = getApp();
    if (item.type === 'help_application' || item.type === 'borrow_request') {
      // 存入 globalData，由 my-posts 页 onShow 消费后自动跳转到审批 tab 并弹出审批弹框
      app.globalData.pendingApprovalTarget = {
        relatedId: item.relatedId,
        type: item.type
      };
      wx.switchTab({ url: '/pages/my-posts/my-posts' });
    } else if (item.type === 'help_approved') {
      // 帮助申请已通过 → 跳转到管理页，用户在「进行中」tab 查看
      wx.switchTab({ url: '/pages/my-posts/my-posts' });
    }
  },

  formatDateTime(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    if (isNaN(d.getTime())) return '';
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 '
      + d.getHours().toString().padStart(2, '0') + ':'
      + d.getMinutes().toString().padStart(2, '0');
  }
});
