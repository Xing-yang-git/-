const api = require('../../utils/api');
const auth = require('../../utils/auth');

Page({
  data: {
    item: {},
    images: [],
    showSheet: false,
    showHistorySheet: false,
    historyData: {
      address: '',
      userType: '',
      rating: '5.0',
      borrowCount: 0,
      borrowReturnRate: 0,
      lendCount: 0,
      helpReqCount: 0,
      helpProCount: 0
    },
    today: '',
    userId: '',
    borrowForm: {
      description: '',
      durationUnit: 'day',
      durationOptions: [],
      durationIndex: 0
    }
  },

  onLoad(options) {
    const id = options.id;
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      return;
    }
    // Set today's date for picker min
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    // Get current user id: JWT first, then storage fallback
    const userId = auth.getUserId() || (wx.getStorageSync('userInfo') || {}).id || '';
    this.setData({ today, userId });
    this.loadItem(id);
  },

  loadItem(id) {
    wx.showLoading({ title: '加载中...' });
    api.get(`/api/idle/${id}`)
      .then((data) => {
        wx.hideLoading();
        const item = this.formatItem(data);
        const historyData = this.buildHistoryData(data);
        this.setData({ item, images: item.images || [], historyData });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  formatItem(item) {
    // Parse images from JSON string to array, cap at 9
    let imageList = [];
    if (item.images) {
      try {
        imageList = typeof item.images === 'string' ? JSON.parse(item.images) : item.images;
        if (!Array.isArray(imageList)) imageList = [];
        imageList = imageList.slice(0, 9);
      } catch (e) {
        imageList = [];
      }
    }
    // Prepend baseUrl for relative paths
    const app = getApp();
    const baseUrl = (app && app.globalData && app.globalData.baseUrl) ? app.globalData.baseUrl : '';
    imageList = imageList.map(url => {
      if (url && url.startsWith('/uploads/') && baseUrl) {
        return baseUrl + url;
      }
      return url;
    });

    // Format rating to always show 1 decimal place (e.g. 5.0, not 5)
    const ratingText = item.rating != null ? item.rating.toFixed(1) : null;

    const isWanted = item.postType === 'WANTED';

    return {
      ...item,
      images: imageList,
      isWanted: isWanted,
      durationLabel: this.formatDuration(item.maxDuration, item.durationUnit, isWanted),
      conditionLabel: isWanted ? null : this.formatCondition(item.condition),
      pickupLabel: isWanted ? null : this.formatPickupMethod(item.pickupMethod),
      timeAgo: this.formatTime(item.createdAt),
      priceText: item.price != null ? '¥' + item.price : '未设置',
      ratingText: ratingText,
      lendCount: item.lendCount != null ? item.lendCount : 0,
      returnRate: item.returnRate || ''
    };
  },

  buildHistoryData(data) {
    // Parse userRoom: "1栋2单元301号(业主)" → address + userType
    const userRoom = data.userRoom || '';
    const typeMatch = userRoom.match(/\((.+)\)$/);
    const userType = typeMatch ? typeMatch[1] : '';
    const address = typeMatch ? userRoom.replace(/\(.+\)$/, '') : userRoom;

    const rating = data.rating != null ? data.rating.toFixed(1) : '5.0';
    const lendCount = data.lendCount != null ? data.lendCount : 0;
    const returnRate = data.returnRate ? parseInt(data.returnRate) || 0 : 0;

    return {
      address: address || userRoom || '—',
      userType: userType || '居民',
      rating: rating,
      borrowCount: 0,
      borrowReturnRate: returnRate,
      lendCount: lendCount,
      helpReqCount: 0,
      helpProCount: 0
    };
  },

  formatDuration(maxDuration, durationUnit, isWanted) {
    const prefix = isWanted ? '需借' : '可借';
    const operator = isWanted ? ' ≥' : ' ≤';
    if (!maxDuration) return prefix;
    const unitLabel = durationUnit === 'hour' ? '小时' : '天';
    return prefix + operator + maxDuration + unitLabel;
  },

  formatCondition(condition) {
    const map = {
      'like-new': '几乎全新',
      'normal': '正常使用痕迹',
      'good': '正常使用痕迹',
      'worn': '有明显磨损'
    };
    return map[condition] || condition || '';
  },

  formatPickupMethod(method) {
    const map = {
      'self_pickup': '需自提',
      'both': '自提 / 可送上门'
    };
    return map[method] || method || '';
  },

  formatTime(time) {
    if (!time) return '';
    const now = Date.now();
    const postTime = new Date(time).getTime();
    const diff = now - postTime;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return minutes + '分钟前';
    if (hours < 24) return hours + '小时前';
    if (days < 7) return days + '天前';
    return '7天前';
  },

  onViewHistory() {
    this.setData({ showHistorySheet: true });
  },

  onCloseHistorySheet() {
    this.setData({ showHistorySheet: false });
  },

  onContactTap() {
    const { item } = this.data;
    // Navigate to chat or create session
    wx.showLoading({ title: '创建会话...' });
    api.post('/api/chat/create', { targetUserId: item.userId, postId: item.id, postType: 'IDLE' })
      .then((data) => {
        wx.hideLoading();
        wx.navigateTo({ url: `/pages/chat/chat?sessionId=${data.sessionId}` });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  onBorrowTap() {
    const { item, userId } = this.data;
    if (item.status === 'borrowed' || item.status === 'reserved') return;

    // 自己发布的物品/需求不可操作
    if (userId && item.userId === userId) {
      wx.showModal({
        title: '提示',
        content: item.isWanted ? '无法借出自己的需求' : '无法借入自己的物品',
        showCancel: false,
        confirmText: '知道了'
      });
      return;
    }

    // 初始化表单：根据时长生成选项
    const maxDuration = item.maxDuration || 7;
    const unit = item.durationUnit || 'day';
    const durationOptions = this.buildDurationOptions(unit, maxDuration);
    // 默认选中最大时长
    const durationIndex = durationOptions.length - 1;

    this.setData({
      showSheet: true,
      borrowForm: {
        description: '',
        durationUnit: unit,
        durationOptions: durationOptions,
        durationIndex: durationIndex
      }
    });
  },

  // 根据单位和最大值生成时长选项（参照发布页 onUnitTap）
  buildDurationOptions(unit, max) {
    const options = [];
    if (unit === 'day') {
      const cap = Math.min(max, 30);
      for (let i = 1; i <= cap; i++) options.push(i + ' 天');
    } else {
      const cap = Math.min(max, 24);
      for (let i = 1; i <= cap; i++) options.push(i + ' 小时');
    }
    return options;
  },

  onCloseSheet() {
    this.setData({ showSheet: false });
  },

  // 切换时长单位（参照发布页 onUnitTap）
  onBorrowUnitTap(e) {
    const unit = e.currentTarget.dataset.value;
    const itemUnit = this.data.item.durationUnit || 'day';
    const itemMax = this.data.item.maxDuration || 7;
    // Convert max when switching unit: day→hour multiplies by 24, hour→day divides
    let max;
    if (unit === itemUnit) {
      max = itemMax;
    } else if (unit === 'hour') {
      max = Math.min(itemMax * 24, 24);
    } else {
      max = Math.max(1, Math.floor(itemMax / 24));
    }
    const cap = unit === 'day' ? Math.min(max, 30) : Math.min(max, 24);
    const options = [];
    if (unit === 'day') {
      for (let i = 1; i <= cap; i++) options.push(i + ' 天');
    } else {
      for (let i = 1; i <= cap; i++) options.push(i + ' 小时');
    }
    this.setData({
      'borrowForm.durationUnit': unit,
      'borrowForm.durationOptions': options,
      'borrowForm.durationIndex': 0
    });
  },

  // 时长选择器变化（参照发布页 onDurationChange）
  onBorrowDurationChange(e) {
    this.setData({ 'borrowForm.durationIndex': e.detail.value });
  },

  // 说明输入
  onBorrowDescInput(e) {
    this.setData({ 'borrowForm.description': e.detail.value });
  },

  onSubmitBorrow() {
    const { borrowForm, item } = this.data;

    // 校验说明
    if (!borrowForm.description || !borrowForm.description.trim()) {
      wx.showToast({ title: item.isWanted ? '请填写借出说明' : '请填写借用说明', icon: 'none' });
      return;
    }

    // 解析选中时长
    const selected = borrowForm.durationOptions[borrowForm.durationIndex];
    const durationDays = parseInt(selected) || 1;

    wx.showLoading({ title: '提交中...' });

    api.post('/api/borrow', {
      idleId: item.id,
      durationType: borrowForm.durationUnit,
      durationDays: durationDays,
      startDate: this.data.today,
      note: borrowForm.description.trim()
    })
      .then(() => {
        wx.hideLoading();
        this.setData({ showSheet: false });
        wx.showToast({ title: item.isWanted ? '借出意向已发送' : '借入申请已发送', icon: 'success' });
        // Update item status locally
        this.setData({ 'item.status': 'reserved' });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  onViewProfile() {
    const { item } = this.data;
    if (item.userId) {
      wx.navigateTo({ url: `/pages/profile/profile?userId=${item.userId}` });
    }
  },

  preventTouchMove() {}
});
