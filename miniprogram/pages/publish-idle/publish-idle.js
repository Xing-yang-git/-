const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { POST_TYPE } = require('../../utils/constants');

/**
 * 发布页 — 闲置物品 / 互助求助发布。
 *
 * 功能：发布类型选择（出借/求借/求助）、标题/分类/图片/价格/时长/取货方式填写、
 *        图片上传、发布提交。求助类型额外支持紧急标记和时间范围。
 */
Page({
  data: {
    postType: POST_TYPE.LEND,   // 'LEND' | 'WANTED' | 'HELP'
    title: '',
    category: '',
    customType: '',
    price: '',
    description: '',
    images: [],
    // Duration (shared by LEND & WANTED)
    durationUnit: 'day',
    durationOptions: ['1 天', '2 天', '3 天', '4 天', '5 天', '6 天', '7 天'],
    durationIndex: 6,
    // LEND fields
    pickupMethod: 'self_pickup',
    condition: 'normal',
    // HELP fields
    urgency: 'normal',
    enableTimeRange: false,   // 时间范围为选填，默认关闭，不提交时间
    helpStartDate: '',
    helpStartHour: 9,
    helpEndDate: '',
    helpEndHour: 18,
    hourOptions: []
  },

  onLoad(options) {
    if (!auth.ensureAccess()) return;   // 登录/审核门禁：未通过则已跳转
    // 从页面参数接收 postType
    if (options && options.type) {
      const type = options.type;
      if (type === POST_TYPE.WANTED || type === 'BORROW') {
        this.setData({ postType: POST_TYPE.WANTED });
      } else if (type === POST_TYPE.HELP) {
        this.setData({ postType: POST_TYPE.HELP });
      }
    }

    // 生成 00:00 ~ 23:00 的小时选项
    const hours = [];
    for (let i = 0; i < 24; i++) {
      hours.push(i < 10 ? '0' + i + ':00' : i + ':00');
    }
    this.setData({ hourOptions: hours });

    // 设置求助默认时间范围（HELP 表单：今天 ~ 今天+3 天）
    const now = new Date();
    const y = now.getFullYear();
    const m = (now.getMonth() + 1).toString().padStart(2, '0');
    const d = now.getDate().toString().padStart(2, '0');
    this.setData({ helpStartDate: y + '-' + m + '-' + d });

    const end = new Date(now.getTime() + 3 * 24 * 3600000);
    const ey = end.getFullYear();
    const em = (end.getMonth() + 1).toString().padStart(2, '0');
    const ed = end.getDate().toString().padStart(2, '0');
    this.setData({ helpEndDate: ey + '-' + em + '-' + ed });
  },

  onShow() {
    if (!auth.ensureAccess()) return; // 登录/审核门禁：覆盖 tab 切换与后台切回
  },

  onPostTypeTap(e) {
    this.setData({ postType: e.currentTarget.dataset.value });
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  onCategoryTap(e) {
    const value = e.currentTarget.dataset.value;
    if (value === '其他') {
      if (this.data.category === '其他') {
        this.setData({ category: '', customType: '' });
        return;
      }
      this.setData({ category: '其他' });
    } else {
      this.setData({ category: value, customType: '' });
    }
  },

  onConditionTap(e) {
    this.setData({ condition: e.currentTarget.dataset.value });
  },

  onUnitTap(e) {
    const unit = e.currentTarget.dataset.value;
    if (unit === 'hour') {
      const hours = [];
      for (let i = 1; i <= 23; i++) {
        hours.push(i + ' 小时');
      }
      this.setData({
        durationUnit: 'hour',
        durationOptions: hours,
        durationIndex: 2
      });
    } else {
      const days = [];
      for (let i = 1; i <= 7; i++) {
        days.push(i + ' 天');
      }
      this.setData({
        durationUnit: 'day',
        durationOptions: days,
        durationIndex: 6
      });
    }
  },

  onDurationChange(e) {
    this.setData({ durationIndex: parseInt(e.detail.value) });
  },

  onPickupTap(e) {
    this.setData({ pickupMethod: e.currentTarget.dataset.value });
  },

  onChooseImage() {
    const remaining = 9 - this.data.images.length;
    if (remaining <= 0) {
      wx.showToast({ title: '最多 9 张', icon: 'none' });
      return;
    }
    wx.chooseImage({
      count: remaining,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: res => {
        this.setData({ images: [...this.data.images, ...res.tempFilePaths] });
      }
    });
  },

  onRemoveImage(e) {
    const idx = e.currentTarget.dataset.index;
    const imgs = [...this.data.images];
    imgs.splice(idx, 1);
    this.setData({ images: imgs });
  },

  // HELP: urgency segment
  onUrgencyTap(e) {
    this.setData({ urgency: e.currentTarget.dataset.value });
  },

  // HELP: toggle optional time range
  onToggleTimeRange(e) {
    this.setData({ enableTimeRange: e.detail.value });
  },

  // HELP: time range date/hour pickers
  onHelpStartDateChange(e) {
    this.setData({ helpStartDate: e.detail.value });
  },

  onHelpStartHourChange(e) {
    this.setData({ helpStartHour: parseInt(e.detail.value) });
  },

  onHelpEndDateChange(e) {
    this.setData({ helpEndDate: e.detail.value });
  },

  onHelpEndHourChange(e) {
    this.setData({ helpEndHour: parseInt(e.detail.value) });
  },

  async onSubmit() {
    const postType = this.data.postType;

    // --- HELP: 技能求助 ---
    if (postType === POST_TYPE.HELP) {
      if (!this.data.title || !this.data.title.trim()) {
        wx.showToast({ title: '请填写求助标题', icon: 'none' });
        return;
      }
      if (!this.data.category) {
        wx.showToast({ title: '请选择求助类型', icon: 'none' });
        return;
      }
      if (this.data.category === '其他' && !this.data.customType.trim()) {
        wx.showToast({ title: '请手动输入类型', icon: 'none' });
        return;
      }
      if (!this.data.description || !this.data.description.trim()) {
        wx.showToast({ title: '请填写详细描述', icon: 'none' });
        return;
      }

      // 构建时间范围字符串（选填 — 仅当开关开启时）
      let timeStart = '';
      let timeEnd = '';
      if (this.data.enableTimeRange && this.data.helpStartDate && this.data.helpEndDate) {
        timeStart = this.data.helpStartDate + ' ' + this.data.hourOptions[this.data.helpStartHour];
        timeEnd = this.data.helpEndDate + ' ' + this.data.hourOptions[this.data.helpEndHour];
      }

      wx.showLoading({ title: '发布中' });
      try {
        // 先上传图片
        let imageUrls = [];
        if (this.data.images.length > 0) {
          const uploadTasks = this.data.images.map(filePath => api.upload('/api/common/upload', filePath));
          imageUrls = await Promise.all(uploadTasks);
        }
        await api.post('/api/help-requests', {
          title: this.data.title.trim(),
          category: this.data.category === '其他' ? this.data.customType.trim() : this.data.category,
          description: this.data.description.trim(),
          isUrgent: this.data.urgency === 'urgent',
          timeStart: timeStart,
          timeEnd: timeEnd,
          images: JSON.stringify(imageUrls)
        });
        wx.hideLoading();
        wx.showToast({ title: '发布成功' });
        // 告知首页本次发布的类型，使其 onShow 能按需刷新匹配的 tab
        const app = getApp();
        app.globalData.pendingHomeRefresh = postType;
        setTimeout(() => wx.navigateBack(), 1000);
      } catch (e) {
        wx.hideLoading();
        wx.showToast({ title: e.message || '发布失败', icon: 'none' });
      }
      return;
    }

    // --- WANTED: 需求借入 ---
    if (postType === POST_TYPE.WANTED) {
      if (!this.data.title || !this.data.title.trim()) {
        wx.showToast({ title: '请填写物品名称', icon: 'none' });
        return;
      }
      if (!this.data.category) {
        wx.showToast({ title: '请选择物品类型', icon: 'none' });
        return;
      }
      if (this.data.category === '其他' && !this.data.customType.trim()) {
        wx.showToast({ title: '请手动输入类型', icon: 'none' });
        return;
      }
      if (!this.data.description || !this.data.description.trim()) {
        wx.showToast({ title: '请填写用途说明', icon: 'none' });
        return;
      }

      wx.showLoading({ title: '发布中' });
      try {
        // 先上传图片（需求借入选填）
        let imageUrls = [];
        if (this.data.images.length > 0) {
          const uploadTasks = this.data.images.map(filePath => api.upload('/api/common/upload', filePath));
          imageUrls = await Promise.all(uploadTasks);
        }
        await api.post('/api/idle-items', {
          postType: POST_TYPE.WANTED,
          title: this.data.title.trim(),
          category: this.data.category === '其他' ? this.data.customType.trim() : this.data.category,
          description: this.data.description.trim(),
          images: JSON.stringify(imageUrls),
          maxDuration: Number.parseInt(this.data.durationOptions[this.data.durationIndex]),
          durationUnit: this.data.durationUnit
        });
        wx.hideLoading();
        wx.showToast({ title: '发布成功' });
        // 告知首页本次发布的类型，使其 onShow 能按需刷新匹配的 tab
        const app = getApp();
        app.globalData.pendingHomeRefresh = postType;
        setTimeout(() => wx.navigateBack(), 1000);
      } catch (e) {
        wx.hideLoading();
        wx.showToast({ title: e.message || '发布失败', icon: 'none' });
      }
      return;
    }

    // --- LEND: 闲置借出 ---
    if (!this.data.title || !this.data.title.trim()) {
      wx.showToast({ title: '请填写物品标题', icon: 'none' });
      return;
    }
    if (!this.data.category) {
      wx.showToast({ title: '请选择物品类型', icon: 'none' });
      return;
    }
    if (this.data.category === '其他' && !this.data.customType.trim()) {
      wx.showToast({ title: '请手动输入类型', icon: 'none' });
      return;
    }
    if (!this.data.price || !this.data.price.trim()) {
      wx.showToast({ title: '请填写参考价格', icon: 'none' });
      return;
    }
    const priceVal = Number.parseFloat(this.data.price);
    if (Number.isNaN(priceVal) || priceVal <= 0) {
      wx.showToast({ title: '请输入有效的价格', icon: 'none' });
      return;
    }
    if (priceVal > 99999999.99) {
      wx.showToast({ title: '价格不能超过 99,999,999.99 元', icon: 'none' });
      return;
    }
    if (!this.data.condition) {
      wx.showToast({ title: '请选择物品状况', icon: 'none' });
      return;
    }

    wx.showLoading({ title: '发布中' });
    try {
      // 先上传图片（闲置借出必填）
      const uploadTasks = this.data.images.map(filePath => api.upload('/api/common/upload', filePath));
      const imageUrls = await Promise.all(uploadTasks);
      await api.post('/api/idle-items', {
        postType: this.data.postType,
        title: this.data.title.trim(),
        category: this.data.category === '其他' ? this.data.customType.trim() : this.data.category,
        condition: this.data.condition,
        description: this.data.description,
        price: Number.parseFloat(this.data.price),
        images: JSON.stringify(imageUrls),
        maxDuration: Number.parseInt(this.data.durationOptions[this.data.durationIndex]),
        durationUnit: this.data.durationUnit,
        pickupMethod: this.data.pickupMethod
      });
      wx.hideLoading();
      wx.showToast({ title: '发布成功' });
      // 告知首页本次发布的类型，使其 onShow 能按需刷新匹配的 tab
      const app = getApp();
      app.globalData.pendingHomeRefresh = postType;
      setTimeout(() => wx.navigateBack(), 1000);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message || '发布失败', icon: 'none' });
    }
  }
});
