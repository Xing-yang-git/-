const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    // 全局加载/错误状态
    loading: true,
    loadError: false,
    loadErrorMsg: '',

    // 主 tab：publish | approval | inProgress | completed
    mainTab: 'publish',

    // 「我发布的」子 tab
    publishSubTab: 'online',
    onlineCount: 0,
    offlineCount: 0,
    onlinePosts: [],
    offlinePosts: [],

    // 「待审批」子 tab
    approvalSubTab: 'borrow',
    borrowApprovals: [],
    lendApprovals: [],
    helpApprovals: [],

    // 「进行中」子 tab：borrow | lend | helpReq | helpPro
    inProgressSubTab: 'borrow',
    inProgressBorrows: [],
    inProgressLends: [],
    inProgressHelpReqs: [],
    inProgressHelpPros: [],

    // 「已完成」子 tab：borrow | lend | helpReq | helpPro
    completedSubTab: 'borrow',
    completedBorrows: [],
    completedLends: [],
    completedHelpReqs: [],
    completedHelpPros: [],

    // Drawer
    showDrawer: false,

    // Alerts
    showOfflineAlert: false,
    offlineTargetId: null,
    offlineTargetPostType: null,

    // Approval Sheet
    showApprovalSheet: false,
    approvalSheetItem: null,

    // Progress Sheet (进行中 detail)
    showProgressSheet: false,
    progressSheetItem: null,
    progressSheetType: '',
    progressSheetRating: 0,
    progressSheetFeedback: '',

    // Completed Sheet (已完成 detail)
    showCompletedSheet: false,
    completedSheetItem: null,
    completedRating: 0,        // 补充评价：本方尚未评分时在已完成页评价对方
    completedFeedback: '',
    // 待评价提示：各角色已完成列表中是否存在本方尚未评分的记录
    pendingRating: { borrow: false, lend: false, helpReq: false, helpPro: false },
    hasPendingRating: false,
    // 下拉刷新状态（四个 tab 共用，同一时刻只显示一个列表）
    refreshing: false,

    // Confirmation Alerts
    showConfirmAlert: false,
    confirmAction: '', // 'approve' | 'reject'
    confirmAlertTitle: '',
    confirmAlertBody: '',
    showReturnConfirmAlert: false,
    returnConfirmData: null,
    showOverdueTipAlert: false,
    showSaveConfirmAlert: false,

    // Edit Sheet — mirrors publish-idle form fields
    showEditSheet: false,
    editSource: 'online',
    editTargetId: null,
    editPostType: 'LEND',
    editTitle: '',
    editCategory: '',
    editCustomType: '',
    editPrice: '',
    editDesc: '',
    editDurationUnit: 'day',
    editDurationOptions: ['1 天', '2 天', '3 天', '4 天', '5 天', '6 天', '7 天'],
    editDurationIndex: 6,
    editPickupMethod: 'self_pickup',
    editCondition: 'normal',
    editUrgency: 'normal',
    hourOptions: []
  },

  onLoad() {
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    const hours = [];
    for (let i = 0; i < 24; i++) {
      hours.push((i < 10 ? '0' : '') + i + ':00');
    }
    this.setData({ hourOptions: hours });

    // 首次加载：onLoad → loadAllData，onShow 不再重复触发
    this._initialLoad = true;
    this.loadAllData();
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
    // 跳过首次 onShow（onLoad 已触发 loadAllData）
    if (this._initialLoad) {
      this._initialLoad = false;
      return;
    }
    // 后续切回 tab 时刷新数据（静默刷新，不显示全屏 loading）
    this._isRefresh = true;
    this.loadAllData();
  },

  // 重试加载（用户点击错误提示中的重试按钮）
  onRetryLoad() {
    this._initialLoad = true;  // 模拟首次加载，显示 loading
    this._isRefresh = false;
    this.loadAllData();
  },

  // 下拉刷新：静默重载全部列表（发布/审批/进行中/已完成），完成后收起刷新动画
  onPullRefresh() {
    this.setData({ refreshing: true });
    this._isRefresh = true;   // 复用静默刷新分支，不弹全屏 loading
    this.loadAllData().finally(() => {
      this.setData({ refreshing: false });
    });
  },

  // ================================================================
  // 数据加载（API 驱动）
  // ================================================================
  async loadAllData() {
    // 重置上一次的错误
    this._lastError = null;

    // 检查登录状态 —— 管理页所有接口都需要 JWT 认证
    const token = wx.getStorageSync('token');
    if (!token) {
      if (!this._loginModalShown) {
        this._loginModalShown = true;
        wx.showModal({
          title: '请先登录',
          content: '管理页需要登录后才能查看您的发布、审批和进行中的记录。',
          cancelText: '稍后',
          confirmText: '去登录',
          success: (res) => {
            this._loginModalShown = false;
            if (res.confirm) {
              wx.navigateTo({ url: '/pages/login/login' });
            }
          },
          fail: () => { this._loginModalShown = false; }
        });
      }
      this.setData({ loading: false, loadError: true, loadErrorMsg: '未登录' });
      return;
    }

    // 仅首次加载显示全屏 loading，后续切回 tab 时静默刷新
    const isRefresh = this._isRefresh;
    this._isRefresh = false;

    if (!isRefresh) {
      this.setData({ loading: true, loadError: false, loadErrorMsg: '' });
    }

    // 并行加载所有数据（每个子函数内部 catch 错误，返回 number 计数）
    const counts = await Promise.all([
      this.loadMyPosts(),
      this.loadApprovals('borrow'),
      this.loadApprovals('lend'),
      this.loadApprovals('help'),
      this.loadInProgress('borrow'),
      this.loadInProgress('lend'),
      this.loadInProgress('helpReq'),
      this.loadInProgress('helpPro'),
      this.loadCompleted('borrow'),
      this.loadCompleted('lend'),
      this.loadCompleted('helpReq'),
      this.loadCompleted('helpPro')
    ]);

    this.setData({ loading: false });

    // 统计总数据量
    const totalItems = counts.reduce((sum, c) => sum + (typeof c === 'number' ? c : 0), 0);
    const allFailed = this._lastError && totalItems === 0;

    // 首次加载时，所有请求都失败 → 展示错误提示
    if (!isRefresh && allFailed) {
      const errMsg = this._lastError;
      this.setData({ loadError: true, loadErrorMsg: errMsg });
      // 401 错误由 api.js 统一处理 reLaunch，此处不重复提示
      if (errMsg !== '请先登录') {
        wx.showToast({ title: errMsg, icon: 'none', duration: 2500 });
      }
    }
  },

  async loadMyPosts() {
    try {
      const data = await api.get('/api/user/posts');
      const allPosts = Array.isArray(data) ? data : [];
      const onlinePosts = allPosts.filter(p => p.displayStatus === '在线').map(p => this.formatPostFromDTO(p, 'online'));
      const offlinePosts = allPosts.filter(p => p.displayStatus === '已下架').map(p => this.formatPostFromDTO(p, 'offline'));
      this.setData({ onlinePosts, onlineCount: onlinePosts.length, offlinePosts, offlineCount: offlinePosts.length });
      return onlinePosts.length + offlinePosts.length;
    } catch (e) {
      console.error('Load my posts failed:', e);
      this._lastError = (e && e.message) || '加载发布列表失败';
      return 0;
    }
  },

  formatPostFromDTO(dto, status) {
    const isIdle = dto.type === 'idle';
    const durationNum = dto.maxDuration || 7;
    const durationUnit = dto.durationUnit || 'day';
    const durationLabel = '可借 ≤' + durationNum + (durationUnit === 'hour' ? '小时' : '天');
    const timeText = status === 'offline'
      ? (dto.updatedAt ? this.formatRelativeTime(new Date(dto.updatedAt).getTime()) + '下架' : '')
      : (dto.createdAt ? this.formatRelativeTime(new Date(dto.createdAt).getTime()) + '发布' : '');

    const base = {
      id: dto.id,
      postType: isIdle ? (dto.postType || 'LEND') : 'HELP',
      title: dto.title,
      category: dto.category || '',
      customType: '',
      description: dto.description || '',
      createTime: dto.createdAt ? new Date(dto.createdAt).getTime() : Date.now(),
      status: status,
      statusText: status === 'online' ? '在线' : '已下架',
      statusTagClass: status === 'online' ? 'post-status-tag-blue' : 'post-status-tag-fill',
      durationLabel: durationLabel,
      conditionLabel: isIdle ? this.conditionText(dto.condition) : '',
      timeText: timeText,
      isProxy: dto.isProxy
    };

    if (isIdle) {
      return { ...base, iconBg: '#E8F0FE', iconSrc: '../../images/icon-wrench.svg', price: dto.price || '', maxDuration: durationNum, durationUnit: durationUnit, pickupMethod: dto.pickupMethod || 'self_pickup', condition: dto.condition || 'normal' };
    } else {
      return { ...base, iconBg: '#FFF3E0', iconSrc: '../../images/icon-heart.svg', urgency: dto.isUrgent ? 'urgent' : 'normal', timeStart: '', timeEnd: '', timeStartHour: 9, timeEndHour: 18 };
    }
  },

  conditionText(condition) {
    const map = { 'like-new': '几乎全新', 'normal': '正常使用痕迹', 'worn': '有明显磨损' };
    return map[condition] || '';
  },

  // ================================================================
  // 审批数据
  // ================================================================
  async loadApprovals(type) {
    try {
      const data = await api.get('/api/user/approvals', { type });
      const items = (Array.isArray(data) ? data : []).map(dto => ({
        id: dto.id,
        itemTitle: dto.title || '',
        postType: dto.postType || '',
        applicantName: dto.personName || '',
        applicantAddress: dto.personRoom || '',
        applicantType: dto.personType || '',
        applicantRating: (dto.personRating || 5).toFixed(1),
        borrowCount: dto.borrowCount || 0,
        borrowReturnRate: dto.borrowReturnRate != null ? dto.borrowReturnRate : 100,
        lendCount: dto.lendCount || 0,
        helpReqCount: dto.helpReqCount || 0,
        helpProCount: dto.helpProCount || 0,
        status: dto.status || 'pending',
        note: dto.note || ''
      }));
      const keyMap = { borrow: 'borrowApprovals', lend: 'lendApprovals', help: 'helpApprovals' };
      this.setData({ [keyMap[type] || 'borrowApprovals']: items });
      return items.length;
    } catch (e) {
      console.error('Load ' + type + ' approvals failed:', e);
      this._lastError = (e && e.message) || '加载审批列表失败';
      return 0;
    }
  },

  // ================================================================
  // 进行中数据
  // ================================================================
  async loadInProgress(role) {
    try {
      const data = await api.get('/api/user/in-progress', { role });
      const items = (Array.isArray(data) ? data : []).map(dto => ({
        id: dto.id,
        personName: dto.personName || '',
        personAddress: dto.personRoom || '',
        personType: dto.personType || '',
        personRating: (dto.personRating || 5).toFixed(1),
        itemTitle: dto.title || '',
        postType: dto.postType || '',
        metaText: dto.metaText || '',
        remainingDays: dto.remainingDays || 0,
        expectedReturnDays: dto.expectedReturnDays || 0,
        isOverdue: dto.isOverdue || false,
        roleLabel: dto.roleLabel || '',
        note: dto.note || ''
      }));
      const keyMap = { borrow: 'inProgressBorrows', lend: 'inProgressLends', helpReq: 'inProgressHelpReqs', helpPro: 'inProgressHelpPros' };
      this.setData({ [keyMap[role]]: items });
      return items.length;
    } catch (e) {
      console.error('Load in-progress ' + role + ' failed:', e);
      this._lastError = (e && e.message) || '加载进行中列表失败';
      return 0;
    }
  },

  // ================================================================
  // 已完成数据
  // ================================================================
  async loadCompleted(role) {
    try {
      const data = await api.get('/api/user/completed', { role });
      const keyMap = { borrow: 'completedBorrows', lend: 'completedLends', helpReq: 'completedHelpReqs', helpPro: 'completedHelpPros' };
      const roleLabelMap = { borrow: '借出住户', lend: '借走住户', helpReq: '帮忙用户', helpPro: '求助住户' };
      const typeMap = { borrow: 'borrow', lend: 'lend', helpReq: 'helpReq', helpPro: 'helpPro' };
      const items = (Array.isArray(data) ? data : []).map(dto => this.formatCompletedItem({
        id: dto.id,
        itemTitle: dto.title,
        helpTitle: dto.title,
        postType: dto.postType || '',
        completedTime: dto.completedAt ? new Date(dto.completedAt).getTime() : Date.now(),
        isOverdue: dto.isOverdue || false,
        overdueDays: dto.isOverdue ? (dto.expectedReturnDays || 0) - (dto.remainingDays || 0) : 0,
        myRating: dto.myRating,
        myFeedback: dto.myFeedback || '',
        theirRating: dto.theirRating,
        theirFeedback: dto.theirFeedback || '',
        note: dto.note || ''
      }, {
        type: typeMap[role],
        personName: dto.personName || '',
        personAddress: dto.personRoom || '',
        personType: dto.personType || '',
        roleLabel: roleLabelMap[role]
      }));
      this.setData({ [keyMap[role]]: items });
      // 更新待评价提示（本方 myRating 为空即待评价）
      const pending = items.some(i => i.myRating === null);
      const pendingRating = Object.assign({}, this.data.pendingRating, { [role]: pending });
      const hasPendingRating = Object.keys(pendingRating).some(k => pendingRating[k]);
      this.setData({ pendingRating, hasPendingRating });
      return items.length;
    } catch (e) {
      console.error('Load completed ' + role + ' failed:', e);
      this._lastError = (e && e.message) || '加载已完成列表失败';
      return 0;
    }
  },

  // 已完成项通用格式化（列表 + 详情弹层）
  formatCompletedItem(item, opts) {
    const isBorrow = opts.type === 'borrow' || opts.type === 'lend';
    const metaText = isBorrow
      ? (item.isOverdue ? ('超时 ' + item.overdueDays + ' 天归还') : ('已于 ' + this.formatDate(item.completedTime) + ' 归还'))
      : ('已于 ' + this.formatDate(item.completedTime) + ' 结束');
    return {
      id: item.id,
      type: opts.type,
      postType: item.postType || '',
      personName: opts.personName,
      personAddress: opts.personAddress,
      personType: opts.personType,
      roleLabel: opts.roleLabel,
      itemTitle: item.itemTitle || item.helpTitle,
      metaText: metaText,
      statusText: item.isOverdue ? '超时归还' : '已完成',
      statusTagClass: item.isOverdue ? 'post-status-tag-red' : 'post-status-tag-green',
      // 已完成详情弹层的字段
      myRating: item.myRating != null ? Number(item.myRating).toFixed(1) : null,
      myFeedback: this.sanitizeFeedback(item.myFeedback),
      theirRating: item.theirRating != null ? Number(item.theirRating).toFixed(1) : null,
      theirFeedback: this.sanitizeFeedback(item.theirFeedback),
      note: item.note || '',
    };
  },

  // ================================================================
  // Tab 切换
  // ================================================================
  onMainTabTap(e) {
    this.setData({ mainTab: e.currentTarget.dataset.tab });
  },

  onPublishSubTap(e) {
    this.setData({ publishSubTab: e.currentTarget.dataset.sub });
  },

  onApprovalSubTap(e) {
    this.setData({ approvalSubTab: e.currentTarget.dataset.sub });
  },

  onInProgressSubTap(e) {
    this.setData({ inProgressSubTab: e.currentTarget.dataset.sub });
  },

  onCompletedSubTap(e) {
    this.setData({ completedSubTab: e.currentTarget.dataset.sub });
  },

  // ================================================================
  // 抽屉面板
  // ================================================================
  onOpenDrawer() {
    this.setData({ showDrawer: true });
  },

  onCloseDrawer() {
    this.setData({ showDrawer: false });
  },

  onFeedBack() {
    this.setData({ showDrawer: false });
    wx.showToast({ title: '功能反馈已收到', icon: 'none' });
  },

  // ================================================================
  // 下架流程
  // ================================================================
  onConfirmOffline(e) {
    this.setData({
      showOfflineAlert: true,
      offlineTargetId: e.currentTarget.dataset.id,
      offlineTargetPostType: e.currentTarget.dataset.postType
    });
  },

  onCloseOfflineAlert() {
    this.setData({ showOfflineAlert: false, offlineTargetId: null, offlineTargetPostType: null });
  },

  onCancelOffline() {
    this.setData({ showOfflineAlert: false, offlineTargetId: null, offlineTargetPostType: null });
  },

  async onDoOffline() {
    const id = this.data.offlineTargetId;
    const postType = this.data.offlineTargetPostType;
    this.setData({ showOfflineAlert: false, offlineTargetId: null, offlineTargetPostType: null });
    if (!id) return;

    // 调后端持久化下架：HELP → /api/help，LEND/WANTED → /api/idle
    const endpoint = postType === 'HELP' ? `/api/help/${id}/delist` : `/api/idle/${id}/delist`;
    wx.showLoading({ title: '下架中...' });
    try {
      await api.put(endpoint);
      wx.hideLoading();

      // 后端成功后再更新本地列表（在线 → 已下架）
      const onlinePosts = this.data.onlinePosts;
      const idx = onlinePosts.findIndex(p => p.id === id);
      if (idx >= 0) {
        const item = { ...onlinePosts[idx] };
        item.status = 'offline';
        item.statusText = '已下架';
        item.statusTagClass = 'post-status-tag-fill';
        item.timeText = this.formatRelativeTime(Date.now()) + '下架';
        const offlinePosts = [item, ...this.data.offlinePosts];
        onlinePosts.splice(idx, 1);
        this.setData({
          onlinePosts,
          onlineCount: onlinePosts.length,
          offlinePosts,
          offlineCount: offlinePosts.length
        });
      }
      wx.showToast({ title: '已下架', icon: 'success' });
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: (err && err.message) || '下架失败', icon: 'none' });
    }
  },

  // ================================================================
  // 编辑弹层
  // ================================================================
  onOpenEdit(e) {
    const post = e.currentTarget.dataset.post;
    const type = e.currentTarget.dataset.type;
    const source = e.currentTarget.dataset.source || 'online';
    if (!post) return;

    const unit = post.durationUnit || 'day';
    let durationOptions, durationIndex;
    if (unit === 'hour') {
      durationOptions = Array.from({ length: 23 }, (_, i) => (i + 1) + ' 小时');
      durationIndex = Math.max(0, Math.min((post.maxDuration || 3) - 1, 22));
    } else {
      durationOptions = Array.from({ length: 7 }, (_, i) => (i + 1) + ' 天');
      durationIndex = Math.max(0, Math.min((post.maxDuration || 7) - 1, 6));
    }

    this.setData({
      showEditSheet: true,
      editSource: source,
      editTargetId: post.id,
      editPostType: post.postType || (type === 'help' ? 'HELP' : 'LEND'),
      editTitle: post.title || '',
      editCategory: post.category || '',
      editCustomType: post.customType || '',
      editPrice: post.price || '',
      editDesc: post.description || '',
      editDurationUnit: unit,
      editDurationOptions: durationOptions,
      editDurationIndex: durationIndex,
      editPickupMethod: post.pickupMethod || 'self_pickup',
      editCondition: post.condition || 'normal',
      editUrgency: post.urgency || 'normal'
    });
  },

  onCloseEdit() {
    this.setData({ showEditSheet: false, editTargetId: null });
  },

  onEditInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  onEditCategoryTap(e) {
    const value = e.currentTarget.dataset.value;
    if (value === '其他') {
      if (this.data.editCategory === '其他') {
        this.setData({ editCategory: '', editCustomType: '' });
        return;
      }
      this.setData({ editCategory: '其他' });
    } else {
      this.setData({ editCategory: value, editCustomType: '' });
    }
  },

  onEditUnitTap(e) {
    const unit = e.currentTarget.dataset.value;
    if (unit === 'hour') {
      const hours = Array.from({ length: 23 }, (_, i) => (i + 1) + ' 小时');
      this.setData({
        editDurationUnit: 'hour',
        editDurationOptions: hours,
        editDurationIndex: 2
      });
    } else {
      const days = Array.from({ length: 7 }, (_, i) => (i + 1) + ' 天');
      this.setData({
        editDurationUnit: 'day',
        editDurationOptions: days,
        editDurationIndex: 6
      });
    }
  },

  onEditDurationChange(e) {
    this.setData({ editDurationIndex: parseInt(e.detail.value) });
  },

  onEditPickupTap(e) {
    this.setData({ editPickupMethod: e.currentTarget.dataset.value });
  },

  onEditConditionTap(e) {
    this.setData({ editCondition: e.currentTarget.dataset.value });
  },

  onEditUrgencyTap(e) {
    this.setData({ editUrgency: e.currentTarget.dataset.value });
  },

  onSaveEdit() {
    if (!this.data.editTitle.trim()) {
      wx.showToast({ title: '请输入标题', icon: 'none' });
      return;
    }
    this.setData({ showSaveConfirmAlert: true });
  },

  onCancelSaveConfirm() {
    this.setData({ showSaveConfirmAlert: false });
  },

  async onDoSave() {
    this.setData({ showSaveConfirmAlert: false });

    const id = this.data.editTargetId;
    const isRepublish = this.data.editSource === 'offline';
    const postType = this.data.editPostType;
    const isLend = postType === 'LEND';
    const isHelp = postType === 'HELP';

    const durationStr = this.data.editDurationOptions[this.data.editDurationIndex] || '7 天';
    const maxDuration = parseInt(durationStr) || 7;

    const updates = {
      title: this.data.editTitle.trim(),
      category: this.data.editCategory,
      customType: this.data.editCustomType,
      description: this.data.editDesc.trim(),
      durationUnit: this.data.editDurationUnit,
      maxDuration: maxDuration
    };

    if (isLend) {
      updates.price = parseFloat(this.data.editPrice) || 0;
      updates.pickupMethod = this.data.editPickupMethod;
      updates.condition = this.data.editCondition;
    }

    if (isHelp) {
      updates.urgency = this.data.editUrgency;
    }

    const applyUpdates = (item) => {
      if (item.id !== id) return item;
      Object.assign(item, updates);
      const isIdleItem = item.postType === 'LEND';
      if (isIdleItem) {
        item.durationLabel = '可借 ≤' + (updates.maxDuration || 7) + (updates.durationUnit === 'hour' ? '小时' : '天');
        item.conditionLabel = this.conditionText(updates.condition || item.condition);
      }
      item.timeText = item.status === 'offline' ? this.formatRelativeTime(item.offlineTime || Date.now()) + '下架' : this.formatRelativeTime(item.createTime) + '发布';
      return item;
    };

    if (isRepublish) {
      const offlinePosts = [...this.data.offlinePosts];
      const idx = offlinePosts.findIndex(p => p.id === id);
      if (idx >= 0) {
        let item = { ...offlinePosts[idx] };
        Object.assign(item, updates);
        item.status = 'online';
        item.statusText = '在线';
        item.statusTagClass = 'post-status-tag-blue';
        if (isLend) {
          item.durationLabel = '可借 ≤' + maxDuration + (updates.durationUnit === 'hour' ? '小时' : '天');
          item.conditionLabel = this.conditionText(updates.condition || item.condition);
        }
        item.timeText = '刚刚重新发布';

        offlinePosts.splice(idx, 1);
        this.setData({
          onlinePosts: [item, ...this.data.onlinePosts],
          onlineCount: this.data.onlinePosts.length + 1,
          offlinePosts,
          offlineCount: offlinePosts.length
        });
      }
    } else {
      this.setData({
        onlinePosts: this.data.onlinePosts.map(applyUpdates),
        offlinePosts: this.data.offlinePosts.map(applyUpdates)
      });
    }

    wx.showToast({ title: isRepublish ? '已重新发布' : '已保存', icon: 'none' });
    this.onCloseEdit();
  },

  // ================================================================
  // 审批弹层
  // ================================================================
  onApprovalDetail(e) {
    const id = e.currentTarget.dataset.id;
    const type = e.currentTarget.dataset.type;
    const keyMap = { borrow: 'borrowApprovals', lend: 'lendApprovals', help: 'helpApprovals' };
    const key = keyMap[type] || 'borrowApprovals';
    const list = this.data[key];
    const item = list.find(i => i.id === id);
    if (item) {
      this.setData({
        showApprovalSheet: true,
        approvalSheetItem: { ...item, approvalType: type }
      });
    }
  },

  onCloseApprovalSheet() {
    this.setData({ showApprovalSheet: false, approvalSheetItem: null });
  },

  // "同意"按钮 → 显示确认弹窗
  onConfirmApproval() {
    const item = this.data.approvalSheetItem;
    const type = item ? item.approvalType : 'borrow';
    let body;
    if (type === 'lend') {
      body = '是否确认借入？';        // 需求借入：确认接受对方的借出
    } else if (type === 'borrow') {
      body = '是否确认借出？';
    } else {
      body = '是否确认接受帮助？';
    }
    this.setData({
      showConfirmAlert: true,
      confirmAction: 'approve',
      confirmAlertTitle: '确认同意',
      confirmAlertBody: body
    });
  },

  // "拒绝"按钮 → 显示确认弹窗
  onRejectClick() {
    const item = this.data.approvalSheetItem;
    const type = item ? item.approvalType : 'borrow';
    let body;
    if (type === 'lend') {
      body = '是否确认拒绝借入？';
    } else if (type === 'borrow') {
      body = '是否确认拒绝借出？';
    } else {
      body = '是否确认拒绝接受帮助？';
    }
    this.setData({
      showConfirmAlert: true,
      confirmAction: 'reject',
      confirmAlertTitle: '确认拒绝',
      confirmAlertBody: body
    });
  },

  onCancelConfirm() {
    this.setData({ showConfirmAlert: false });
  },

  // 确认 → 路由到同意或拒绝
  onDoConfirm() {
    if (this.data.confirmAction === 'approve') {
      this.onDoApprove();
    } else {
      this.onDoReject();
    }
  },
  onDoApprove() {
    this.submitApproval(true);
  },

  // 确认拒绝
  onDoReject() {
    this.submitApproval(false);
  },

  /**
   * 提交审批到后端，成功后从服务端刷新「审批」和「进行中」列表。
   * 借入(borrow) / 借出(lend) 都是同一条 BorrowRequest：PUT /api/borrow/{id}/approve
   *   - borrow：我是出借方，审批别人借入我的物品
   *   - lend：  我是借入方，确认别人对我需求的借出意向
   * 求助(help)：PUT /api/help/applications/{helpApplicationId}/approve
   * 审批项的 id 即对应记录的主键（见后端 getApprovals 映射）。
   */
  async submitApproval(approved) {
    const item = this.data.approvalSheetItem;
    if (!item) return;
    const type = item.approvalType;            // 'borrow' | 'lend' | 'help'
    const id = item.id;
    const url = type === 'help'
      ? '/api/help/applications/' + id + '/approve'
      : '/api/borrow/' + id + '/approve';

    wx.showLoading({ title: '处理中...', mask: true });
    try {
      await api.put(url, { approved: approved });
      wx.hideLoading();
      this.setData({
        showApprovalSheet: false,
        approvalSheetItem: null,
        showConfirmAlert: false
      });
      wx.showToast({ title: approved ? '已同意，已移至进行中' : '已拒绝', icon: 'none' });

      // 从服务端重新拉取，确保列表反映真实状态（同意后申请从审批消失、出现在进行中）
      const reloads = [
        this.loadApprovals('borrow'),
        this.loadApprovals('lend'),
        this.loadApprovals('help')
      ];
      if (approved) {
        // borrow：我出借 → 进入我的「借出中」(lend)
        // lend：  我借入 → 进入我的「借入中」(borrow)
        // help：  我求助 → 进入我的「求助·进行中」(helpReq)
        const ipMap = { borrow: 'lend', lend: 'borrow', help: 'helpReq' };
        reloads.push(this.loadInProgress(ipMap[type] || 'lend'));
      }
      await Promise.all(reloads);
    } catch (e) {
      wx.hideLoading();
      this.setData({ showConfirmAlert: false });
      wx.showToast({ title: (e && e.message) || '操作失败，请重试', icon: 'none' });
    }
  },

  doReject(type, id) {
    const key = type === 'borrow' ? 'borrowApprovals' : 'helpApprovals';
    const list = this.data[key];
    const idx = list.findIndex(item => item.id === id);
    if (idx >= 0) {
      list[idx].status = 'rejected';
      this.setData({ [key]: list });
    }
    wx.showToast({ title: '已拒绝', icon: 'none' });
  },

  // ================================================================
  // 进行中详情弹层
  // ================================================================
  onOpenProgressSheet(e) {
    const id = e.currentTarget.dataset.id;
    const type = e.currentTarget.dataset.type;
    const keyMap = {
      'borrow': 'inProgressBorrows',
      'lend': 'inProgressLends',
      'helpReq': 'inProgressHelpReqs',
      'helpPro': 'inProgressHelpPros'
    };
    const key = keyMap[type];
    if (!key) return;
    const list = this.data[key];
    const item = list.find(i => i.id === id);
    if (item) {
      this.setData({
        showProgressSheet: true,
        progressSheetItem: { ...item },
        progressSheetType: type,
        progressSheetRating: 0,
        progressSheetFeedback: ''
      });
    }
  },

  onCloseProgressSheet() {
    this.setData({
      showProgressSheet: false,
      progressSheetItem: null,
      progressSheetType: '',
      progressSheetRating: 0,
      progressSheetFeedback: ''
    });
  },

  onProgressRatingTap(e) {
    this.setData({ progressSheetRating: e.currentTarget.dataset.star });
  },

  onProgressFeedbackInput(e) {
    this.setData({ progressSheetFeedback: e.detail.value });
  },

  // "归还/结束"按钮 → 显示确认弹窗
  onConfirmReturn() {
    if (!this.data.progressSheetRating) {
      wx.showToast({ title: '请先评分', icon: 'none' });
      return;
    }
    this.setData({
      showReturnConfirmAlert: true,
      returnConfirmData: {
        item: this.data.progressSheetItem,
        type: this.data.progressSheetType
      }
    });
  },

  onCancelReturnConfirm() {
    this.setData({ showReturnConfirmAlert: false, returnConfirmData: null });
  },

  // 已确认 → 执行归还/结束（调用后端，双方状态由「进行中」进入「已完成」）
  async onDoReturn() {
    const data = this.data.returnConfirmData;
    if (!data) return;
    const { item, type } = data;
    const isHelp = type === 'helpReq' || type === 'helpPro';
    const isOverdue = item.isOverdue;
    const rating = this.data.progressSheetRating;
    const feedback = this.data.progressSheetFeedback;

    wx.showLoading({ title: '处理中...', mask: true });
    try {
      // 1. 状态流转：归还 / 结束
      if (isHelp) {
        await api.put('/api/help/applications/' + item.id + '/complete', {});
      } else {
        await api.put('/api/borrow/' + item.id + '/return', {
          returnStatus: 'normal',
          isOnTime: !isOverdue
        });
      }

      // 2. 提交评分（互助双方均需评价对方；后端按发起人身份确定被评人）
      if (rating) {
        try {
          await api.post('/api/rating', {
            targetId: item.id,
            ratingType: isHelp ? 'help' : 'borrow',
            overallScore: rating
          });
        } catch (re) {
          // 评价失败不阻断归还主流程（例如已评价过）
          console.warn('Submit rating failed:', re && re.message);
        }
      }

      wx.hideLoading();
      this.setData({
        showProgressSheet: false,
        progressSheetItem: null,
        progressSheetType: '',
        progressSheetRating: 0,
        progressSheetFeedback: '',
        showReturnConfirmAlert: false,
        returnConfirmData: null
      });

      // 3. 刷新受影响的双方视角列表（进行中 + 已完成）
      const reloads = isHelp
        ? [this.loadInProgress('helpReq'), this.loadInProgress('helpPro'),
           this.loadCompleted('helpReq'), this.loadCompleted('helpPro')]
        : [this.loadInProgress('borrow'), this.loadInProgress('lend'),
           this.loadCompleted('borrow'), this.loadCompleted('lend')];
      await Promise.all(reloads);

      if (isOverdue && !isHelp) {
        this.setData({ showOverdueTipAlert: true });
      } else {
        wx.showToast({ title: isHelp ? '已结束' : '已归还', icon: 'none' });
      }
    } catch (e) {
      wx.hideLoading();
      this.setData({ showReturnConfirmAlert: false, returnConfirmData: null });
      wx.showToast({ title: (e && e.message) || '操作失败，请重试', icon: 'none' });
    }
  },

  onCloseOverdueTip() {
    this.setData({ showOverdueTipAlert: false });
    wx.showToast({ title: '已归还，请注意按时归还', icon: 'none' });
  },

  // ================================================================
  // 已完成详情弹层
  // ================================================================
  onCompletedDetail(e) {
    const id = e.currentTarget.dataset.id;
    const type = e.currentTarget.dataset.type;
    const keyMap = {
      'borrow': 'completedBorrows',
      'lend': 'completedLends',
      'helpReq': 'completedHelpReqs',
      'helpPro': 'completedHelpPros'
    };
    const key = keyMap[type];
    if (!key) return;
    const list = this.data[key];
    const item = list.find(i => i.id === id);
    if (item) {
      this.setData({
        showCompletedSheet: true,
        completedSheetItem: { ...item },
        completedRating: 0,
        completedFeedback: ''
      });
    }
  },

  onCloseCompletedSheet() {
    this.setData({ showCompletedSheet: false, completedSheetItem: null, completedRating: 0, completedFeedback: '' });
  },

  // 已完成页补充评价（供未在归还时评分的一方评价对方）
  onCompletedRatingTap(e) {
    this.setData({ completedRating: e.currentTarget.dataset.star });
  },

  onCompletedFeedbackInput(e) {
    this.setData({ completedFeedback: e.detail.value });
  },

  async onSubmitCompletedRating() {
    const item = this.data.completedSheetItem;
    if (!item) return;
    if (!this.data.completedRating) {
      wx.showToast({ title: '请先评分', icon: 'none' });
      return;
    }
    const isHelp = item.type === 'helpReq' || item.type === 'helpPro';
    wx.showLoading({ title: '提交中...', mask: true });
    try {
      await api.post('/api/rating', {
        targetId: item.id,
        ratingType: isHelp ? 'help' : 'borrow',
        overallScore: this.data.completedRating
      });
      wx.hideLoading();
      // 刷新该角色的已完成列表并回填当前弹框
      await this.loadCompleted(item.type);
      const keyMap = {
        'borrow': 'completedBorrows',
        'lend': 'completedLends',
        'helpReq': 'completedHelpReqs',
        'helpPro': 'completedHelpPros'
      };
      const fresh = (this.data[keyMap[item.type]] || []).find(i => i.id === item.id);
      this.setData({
        completedSheetItem: fresh ? { ...fresh } : this.data.completedSheetItem,
        completedRating: 0,
        completedFeedback: ''
      });
      wx.showToast({ title: '评价成功', icon: 'none' });
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: (e && e.message) || '评价失败，请重试', icon: 'none' });
    }
  },

  // ================================================================
  // 进行中操作（旧版保留作为兜底，现已通过弹层路由）
  // ================================================================

  // 归还 — 借入
  onReturnBorrow(e) {
    const id = e.currentTarget.dataset.id;
    const type = 'borrow';
    const item = this.data.inProgressBorrows.find(i => i.id === id);
    if (item) {
      this.setData({
        showProgressSheet: true,
        progressSheetItem: { ...item },
        progressSheetType: type,
        progressSheetRating: 0,
        progressSheetFeedback: ''
      });
    }
  },

  // 归还 — 借出
  onReturnLend(e) {
    const id = e.currentTarget.dataset.id;
    const type = 'lend';
    const item = this.data.inProgressLends.find(i => i.id === id);
    if (item) {
      this.setData({
        showProgressSheet: true,
        progressSheetItem: { ...item },
        progressSheetType: type,
        progressSheetRating: 0,
        progressSheetFeedback: ''
      });
    }
  },

  // 结束 — 求助
  onEndHelpReq(e) {
    const id = e.currentTarget.dataset.id;
    const type = 'helpReq';
    const item = this.data.inProgressHelpReqs.find(i => i.id === id);
    if (item) {
      this.setData({
        showProgressSheet: true,
        progressSheetItem: { ...item },
        progressSheetType: type,
        progressSheetRating: 0,
        progressSheetFeedback: ''
      });
    }
  },

  // 结束 — 帮助
  onEndHelpPro(e) {
    const id = e.currentTarget.dataset.id;
    const type = 'helpPro';
    const item = this.data.inProgressHelpPros.find(i => i.id === id);
    if (item) {
      this.setData({
        showProgressSheet: true,
        progressSheetItem: { ...item },
        progressSheetType: type,
        progressSheetRating: 0,
        progressSheetFeedback: ''
      });
    }
  },

  // ================================================================
  // 触摸事件拦截 — 防止穿透滚动到页面 scroll-view
  // ================================================================
  preventTouchMove() {},

  // ================================================================
  // 辅助函数
  // ================================================================
  getApplyStatusText(status) {
    const map = {
      'pending': '待处理',
      'approved': '已同意',
      'rejected': '已拒绝',
      'completed': '已完成',
      'cancelled': '已取消'
    };
    return map[status] || status || '待处理';
  },

  getApplyStatusClass(status) {
    const map = {
      'pending': 'post-status-tag-orange',
      'approved': 'post-status-tag-green',
      'rejected': 'post-status-tag-red',
      'completed': 'post-status-tag-green',
      'cancelled': 'post-status-tag-fill'
    };
    return map[status] || 'post-status-tag-orange';
  },

  formatTime(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const M = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    const h = date.getHours().toString().padStart(2, '0');
    const mi = date.getMinutes().toString().padStart(2, '0');
    return M + '-' + d + ' ' + h + ':' + mi;
  },

  formatDate(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const Y = date.getFullYear();
    const M = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return Y + '-' + M + '-' + d;
  },

  formatRelativeTime(timestamp) {
    if (!timestamp) return '';
    const now = Date.now();
    const dt = new Date(timestamp).getTime();
    const diff = now - dt;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return minutes + '分钟前';
    if (hours < 24) return hours + '小时前';
    if (days < 7) return days + '天前';
    if (days < 30) return Math.floor(days / 7) + '周前';
    return this.formatDate(timestamp);
  },

  /**
   * 前端安全兜底：清洗可能为 JSON 的反馈文本。
   * 后端也会做此处理，此处作为边缘情况补充。
   */
  sanitizeFeedback(text) {
    if (!text) return '';
    const trimmed = String(text).trim();
    // 非 JSON 形式时原样返回
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
      return trimmed;
    }
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        // 数字数组 → 只有评分没有评价文字，返回空
        if (parsed.length > 0 && typeof parsed[0] === 'number') return '';
        // 字符串数组 → 拼接
        return parsed.filter(function(v) { return typeof v === 'string' && v.length > 0; }).join('；');
      }
      if (typeof parsed === 'object' && parsed !== null) {
        // 对象 → 提取文本值（跳过纯数字评分）
        var parts = [];
        Object.values(parsed).forEach(function(v) {
          var sv = String(v);
          if (sv.length > 2 && !/^\d+(\.\d+)?$/.test(sv)) {
            parts.push(sv);
          }
        });
        return parts.length > 0 ? parts.join('；') : '';
      }
    } catch (e) {
      // 不是合法 JSON——可能是 Java Map.toString() 格式 {key=value}
      if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
        var inner = trimmed.slice(1, -1);
        var pairs = inner.split(/,\s*/);
        var parts = [];
        pairs.forEach(function(pair) {
          var eqIdx = pair.indexOf('=');
          if (eqIdx >= 0) {
            var value = pair.substring(eqIdx + 1).trim();
            if (value.length > 2 && !/^\d+(\.\d+)?$/.test(value)) {
              parts.push(value);
            }
          }
        });
        return parts.length > 0 ? parts.join('；') : '';
      }
    }
    // 全部提取失败时返回空（不展示原始 JSON）
    return '';
  }
});
