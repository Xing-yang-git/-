const api = require('../../utils/api');

Page({
  data: {
    // Main tab: publish | approval | inProgress | completed
    mainTab: 'publish',

    // Publish sub-tabs
    publishSubTab: 'online',
    onlineCount: 0,
    offlineCount: 0,
    onlinePosts: [],
    offlinePosts: [],

    // Approval sub-tabs
    approvalSubTab: 'borrow',
    borrowApprovals: [],
    helpApprovals: [],

    // In-Progress sub-tabs: borrow | lend | helpReq | helpPro
    inProgressSubTab: 'borrow',
    inProgressBorrows: [],
    inProgressLends: [],
    inProgressHelpReqs: [],
    inProgressHelpPros: [],

    // Completed sub-tabs: borrow | lend | helpReq | helpPro
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
    const hours = [];
    for (let i = 0; i < 24; i++) {
      hours.push((i < 10 ? '0' : '') + i + ':00');
    }
    this.setData({ hourOptions: hours });

    this.loadAllData();
  },

  onShow() {
    this.loadAllData();
  },

  // ================================================================
  // Data Loading (API-driven)
  // ================================================================
  async loadAllData() {
    try {
      await Promise.all([
        this.loadMyPosts(),
        this.loadApprovals('borrow'),
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
    } catch (e) {
      console.error('Load my-posts data failed:', e);
    }
  },

  async loadMyPosts() {
    try {
      const data = await api.get('/api/user/posts');
      const allPosts = Array.isArray(data) ? data : [];
      const onlinePosts = allPosts.filter(p => p.displayStatus === '在线').map(p => this.formatPostFromDTO(p, 'online'));
      const offlinePosts = allPosts.filter(p => p.displayStatus === '已下架').map(p => this.formatPostFromDTO(p, 'offline'));
      this.setData({ onlinePosts, onlineCount: onlinePosts.length, offlinePosts, offlineCount: offlinePosts.length });
    } catch (e) {
      console.error('Load my posts failed:', e);
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
  // Approval data
  // ================================================================
  async loadApprovals(type) {
    try {
      const data = await api.get('/api/user/approvals', { type });
      const items = (Array.isArray(data) ? data : []).map(dto => ({
        id: dto.id,
        itemTitle: dto.title || '',
        applicantName: dto.personName || '',
        applicantAddress: dto.personRoom || '',
        applicantType: dto.personType || '',
        applicantRating: dto.personRating || 0,
        borrowCount: dto.borrowCount || 0,
        borrowReturnRate: dto.borrowReturnRate || 0,
        lendCount: dto.lendCount || 0,
        helpReqCount: dto.helpReqCount || 0,
        helpProCount: dto.helpProCount || 0,
        status: dto.status || 'pending'
      }));
      this.setData({ [type === 'borrow' ? 'borrowApprovals' : 'helpApprovals']: items });
    } catch (e) {
      console.error('Load ' + type + ' approvals failed:', e);
    }
  },

  // ================================================================
  // In-Progress data
  // ================================================================
  async loadInProgress(role) {
    try {
      const data = await api.get('/api/user/in-progress', { role });
      const items = (Array.isArray(data) ? data : []).map(dto => ({
        id: dto.id,
        personName: dto.personName || '',
        personAddress: dto.personRoom || '',
        personType: dto.personType || '',
        personRating: dto.personRating || 0,
        itemTitle: dto.title || '',
        metaText: dto.metaText || '',
        remainingDays: dto.remainingDays || 0,
        expectedReturnDays: dto.expectedReturnDays || 0,
        isOverdue: dto.isOverdue || false,
        roleLabel: dto.roleLabel || ''
      }));
      const keyMap = { borrow: 'inProgressBorrows', lend: 'inProgressLends', helpReq: 'inProgressHelpReqs', helpPro: 'inProgressHelpPros' };
      this.setData({ [keyMap[role]]: items });
    } catch (e) {
      console.error('Load in-progress ' + role + ' failed:', e);
    }
  },

  // ================================================================
  // Completed data
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
        completedTime: dto.completedAt ? new Date(dto.completedAt).getTime() : Date.now(),
        isOverdue: dto.isOverdue || false,
        overdueDays: dto.isOverdue ? (dto.expectedReturnDays || 0) - (dto.remainingDays || 0) : 0,
        myRating: dto.myRating,
        myFeedback: dto.myFeedback || '',
        theirRating: dto.theirRating,
        theirFeedback: dto.theirFeedback || ''
      }, {
        type: typeMap[role],
        personName: dto.personName || '',
        personAddress: dto.personRoom || '',
        personType: dto.personType || '',
        roleLabel: roleLabelMap[role]
      }));
      this.setData({ [keyMap[role]]: items });
    } catch (e) {
      console.error('Load completed ' + role + ' failed:', e);
    }
  },

  // Common formatter for completed items (list + detail sheet)
  formatCompletedItem(item, opts) {
    const isBorrow = opts.type === 'borrow' || opts.type === 'lend';
    const metaText = isBorrow
      ? (item.isOverdue ? ('超时 ' + item.overdueDays + ' 天归还') : ('已于 ' + this.formatDate(item.completedTime) + ' 归还'))
      : ('已于 ' + this.formatDate(item.completedTime) + ' 结束');
    return {
      id: item.id,
      type: opts.type,
      personName: opts.personName,
      personAddress: opts.personAddress,
      personType: opts.personType,
      roleLabel: opts.roleLabel,
      itemTitle: item.itemTitle || item.helpTitle,
      metaText: metaText,
      statusText: item.isOverdue ? '超时归还' : '已完成',
      statusTagClass: item.isOverdue ? 'post-status-tag-red' : 'post-status-tag-green',
      // Detail fields for completed sheet
      myRating: item.myRating != null ? item.myRating : null,
      myFeedback: item.myFeedback || '',
      theirRating: item.theirRating != null ? item.theirRating : null,
      theirFeedback: item.theirFeedback || '',
    };
  },

  // ================================================================
  // Tab Switching
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
  // Drawer
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
  // Offline Flow
  // ================================================================
  onConfirmOffline(e) {
    this.setData({
      showOfflineAlert: true,
      offlineTargetId: e.currentTarget.dataset.id
    });
  },

  onCloseOfflineAlert() {
    this.setData({ showOfflineAlert: false, offlineTargetId: null });
  },

  onCancelOffline() {
    this.setData({ showOfflineAlert: false, offlineTargetId: null });
  },

  async onDoOffline() {
    const id = this.data.offlineTargetId;
    this.setData({ showOfflineAlert: false, offlineTargetId: null });
    if (!id) return;

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
    wx.showToast({ title: '已下架', icon: 'none' });
  },

  // ================================================================
  // Edit Sheet
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
  // Approval Sheet
  // ================================================================
  onApprovalDetail(e) {
    const id = e.currentTarget.dataset.id;
    const type = e.currentTarget.dataset.type;
    const key = type === 'borrow' ? 'borrowApprovals' : 'helpApprovals';
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

  // "同意" button → show confirmation alert
  onConfirmApproval() {
    const item = this.data.approvalSheetItem;
    const type = item ? item.approvalType : 'borrow';
    const isBorrow = type === 'borrow';
    this.setData({
      showConfirmAlert: true,
      confirmAction: 'approve',
      confirmAlertTitle: '确认同意',
      confirmAlertBody: isBorrow ? '是否确认借出？' : '是否确认接受帮助？'
    });
  },

  // "拒绝" button → show confirmation alert
  onRejectClick() {
    const item = this.data.approvalSheetItem;
    const type = item ? item.approvalType : 'borrow';
    const isBorrow = type === 'borrow';
    this.setData({
      showConfirmAlert: true,
      confirmAction: 'reject',
      confirmAlertTitle: '确认拒绝',
      confirmAlertBody: isBorrow ? '是否确认拒绝借出？' : '是否确认拒绝接受帮助？'
    });
  },

  onCancelConfirm() {
    this.setData({ showConfirmAlert: false });
  },

  // Confirm → route to approve or reject
  onDoConfirm() {
    if (this.data.confirmAction === 'approve') {
      this.onDoApprove();
    } else {
      this.onDoReject();
    }
  },
  onDoApprove() {
    const item = this.data.approvalSheetItem;
    if (!item) return;
    const type = item.approvalType;
    const id = item.id;
    const key = type === 'borrow' ? 'borrowApprovals' : 'helpApprovals';
    const list = [...this.data[key]];
    const idx = list.findIndex(i => i.id === id);
    if (idx < 0) return;

    // Remove from approval list
    const approvedItem = list[idx];
    list.splice(idx, 1);

    if (type === 'borrow') {
      // Move to inProgressLends (借出 — I'm lending my item to this person)
      const newLend = {
        id: 'ipl-' + id,
        personName: approvedItem.applicantName,
        personAddress: approvedItem.applicantAddress,
        personType: approvedItem.applicantType,
        personRating: approvedItem.applicantRating,
        itemTitle: approvedItem.itemTitle,
        metaText: '剩余 ' + (approvedItem.expectedReturnDays || 7) + ' 天归还',
        remainingDays: approvedItem.expectedReturnDays || 7,
        expectedReturnDays: approvedItem.expectedReturnDays || 7,
        isOverdue: false,
        roleLabel: '借走住户'
      };
      this.setData({
        [key]: list,
        showApprovalSheet: false,
        approvalSheetItem: null,
        showConfirmAlert: false,
        inProgressLends: [newLend, ...this.data.inProgressLends]
      });
    } else {
      // Move to inProgressHelpPros (帮助 — I'm helping this person)
      const newHelp = {
        id: 'iphp-' + id,
        personName: approvedItem.applicantName,
        personAddress: approvedItem.applicantAddress,
        personType: approvedItem.applicantType,
        personRating: approvedItem.applicantRating,
        itemTitle: approvedItem.itemTitle,
        metaText: '进行中 · 刚刚开始',
        roleLabel: '求助住户'
      };
      this.setData({
        [key]: list,
        showApprovalSheet: false,
        approvalSheetItem: null,
        showConfirmAlert: false,
        inProgressHelpPros: [newHelp, ...this.data.inProgressHelpPros]
      });
    }
    wx.showToast({ title: '已同意，已移至进行中', icon: 'none' });
  },

  // Confirmed reject → remove from approval list
  onDoReject() {
    const item = this.data.approvalSheetItem;
    if (!item) return;
    const type = item.approvalType;
    const id = item.id;
    const key = type === 'borrow' ? 'borrowApprovals' : 'helpApprovals';
    const list = [...this.data[key]];
    const idx = list.findIndex(i => i.id === id);
    if (idx < 0) return;

    list.splice(idx, 1);

    this.setData({
      [key]: list,
      showApprovalSheet: false,
      approvalSheetItem: null,
      showConfirmAlert: false
    });

    wx.showToast({ title: '已拒绝', icon: 'none' });
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
  // Progress Sheet (进行中 detail)
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

  // "归还/结束" button → show confirmation alert
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

  // Confirmed → execute return/end
  onDoReturn() {
    const data = this.data.returnConfirmData;
    if (!data) return;
    const { item, type } = data;
    const isOverdue = item.isOverdue;

    const keyMap = {
      'borrow': 'inProgressBorrows',
      'lend': 'inProgressLends',
      'helpReq': 'inProgressHelpReqs',
      'helpPro': 'inProgressHelpPros'
    };
    const key = keyMap[type];
    const list = [...this.data[key]];
    const idx = list.findIndex(i => i.id === item.id);
    if (idx >= 0) {
      list.splice(idx, 1);
    }

    this.setData({
      [key]: list,
      showProgressSheet: false,
      progressSheetItem: null,
      progressSheetType: '',
      showReturnConfirmAlert: false,
      returnConfirmData: null
    });

    if (isOverdue) {
      // Show overdue warning after return
      this.setData({ showOverdueTipAlert: true });
    } else {
      wx.showToast({ title: type === 'helpReq' || type === 'helpPro' ? '已结束' : '已归还', icon: 'none' });
    }
  },

  onCloseOverdueTip() {
    this.setData({ showOverdueTipAlert: false });
    wx.showToast({ title: '已归还，请注意按时归还', icon: 'none' });
  },

  // ================================================================
  // Completed Sheet (已完成 detail)
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
        completedSheetItem: { ...item }
      });
    }
  },

  onCloseCompletedSheet() {
    this.setData({ showCompletedSheet: false, completedSheetItem: null });
  },

  // ================================================================
  // In-Progress Actions (legacy — kept as fallback, now routed through sheet)
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
  // Touch Event Guard — prevents scroll-through to page scroll-view
  // ================================================================
  preventTouchMove() {},

  // ================================================================
  // Helpers
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
  }
});
