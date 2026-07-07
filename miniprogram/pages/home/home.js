const api = require('../../utils/api');

// Map category keywords to prototype-style feather icons
const IDLE_ICONS = [
  'icon-wrench.svg',
  'icon-book.svg',
  'icon-bike.svg',
  'icon-gamepad.svg',
  'icon-heart.svg',
  'icon-monitor.svg',
];

const HELP_ICONS = [
  'icon-wrench.svg',
  'icon-users.svg',
  'icon-download.svg',
  'icon-monitor.svg',
  'icon-book.svg',
  'icon-file-text.svg',
  'icon-heart.svg',
];

function pickIcon(item, index, iconList) {
  // Use item.category or fall back to index-based rotation (matching prototype bgColor rotation)
  if (item.category) {
    const cat = item.category;
    if (cat.includes('维修') || cat.includes('修理')) return 'icon-wrench.svg';
    if (cat.includes('书籍') || cat.includes('书') || cat.includes('辅导')) return 'icon-book.svg';
    if (cat.includes('运动') || cat.includes('健身')) return 'icon-bike.svg';
    if (cat.includes('玩具') || cat.includes('游戏')) return 'icon-gamepad.svg';
    if (cat.includes('陪护') || cat.includes('陪伴')) return 'icon-users.svg';
    if (cat.includes('代取') || cat.includes('搬运') || cat.includes('快递')) return 'icon-download.svg';
    if (cat.includes('电脑') || cat.includes('电子') || cat.includes('IT')) return 'icon-monitor.svg';
    if (cat.includes('烹饪') || cat.includes('遛宠')) return 'icon-heart.svg';
  }
  return iconList[index % iconList.length];
}

Page({
  data: {
    communityName: '社区互助闲置',
    currentTab: 0,
    idleList: [],
    helpList: [],
    page: 0,
    hasMore: true,
    loading: false,
    refreshing: false,
    fabOpen: false
  },

  onLoad() {
    // Get user's community name: priority: backend tenantName > registration storage > fallback
    try {
      const userInfo = wx.getStorageSync('userInfo');
      if (userInfo && userInfo.tenantName) {
        this.setData({ communityName: userInfo.tenantName });
      } else {
        const communityName = wx.getStorageSync('communityName');
        if (communityName) {
          this.setData({ communityName: communityName });
        } else if (userInfo && userInfo.userRoom) {
          this.setData({ communityName: userInfo.userRoom });
        }
      }
    } catch (e) {
      // Fall back to default
    }
    this.loadData();
  },

  onPullDownRefresh() {
    this.setData({ refreshing: true, page: 0, idleList: [], helpList: [], hasMore: true });
    this.loadData().finally(() => {
      this.setData({ refreshing: false });
    });
  },

  onTabChange(e) {
    const tab = parseInt(e.currentTarget.dataset.tab);
    if (tab === this.data.currentTab) return;
    this.setData({ currentTab: tab, page: 0, idleList: [], helpList: [], hasMore: true });
    this.loadData();
  },

  loadData() {
    if (this.data.loading || !this.data.hasMore) return Promise.resolve();

    this.setData({ loading: true });
    wx.showLoading({ title: '加载中...' });

    let promise;
    if (this.data.currentTab === 2) {
      promise = this.loadHelpList();
    } else {
      const postType = this.data.currentTab === 0 ? 'LEND' : 'WANTED';
      promise = this.loadIdleList(postType);
    }

    return promise.finally(() => {
      wx.hideLoading();
      this.setData({ loading: false });
    });
  },

  loadIdleList(postType) {
    const isLend = postType === 'LEND';
    return api.get('/api/idle/home', { postType, page: this.data.page })
      .then((data) => {
        const newList = data.content || data.list || data.records || [];
        const bgColors = ['#E8F0FE', '#FFF3E0', '#E8F5E9', '#FCE4EC', '#E3F2FD', '#F3E5F5'];
        const items = newList.map((item, index) => ({
          ...item,
          bgColor: bgColors[index % bgColors.length],
          icon: pickIcon(item, index, IDLE_ICONS),
          durationLabel: this.formatDuration(item.maxDuration, item.durationUnit, isLend ? '可借' : '需借'),
          conditionLabel: isLend ? this.formatCondition(item.condition) : null,
          timeAgo: this.formatTime(item.createTime || item.createdAt)
        }));
        this.setData({
          idleList: this.data.page === 1 ? items : this.data.idleList.concat(items),
          hasMore: newList.length >= 10,
          page: this.data.page + 1
        });
      })
      .catch((err) => {
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  formatDuration(maxDuration, durationUnit, prefix) {
    if (!maxDuration) return prefix;
    const unitLabel = durationUnit === 'hour' ? '小时' : '天';
    return prefix + maxDuration + unitLabel;
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

  loadHelpList() {
    return api.get('/api/help/home', { page: this.data.page })
      .then((data) => {
        const newList = data.content || data.list || data.records || [];
        const bgColors = ['#FFEDED', '#FFF3E0', '#E3F2FD', '#F3E5F5', '#E8F5E9'];
        const items = newList.map((item, index) => ({
          ...item,
          bgColor: bgColors[index % bgColors.length],
          icon: pickIcon(item, index, HELP_ICONS),
          timeAgo: this.formatTime(item.createTime || item.createdAt)
        }));
        this.setData({
          helpList: this.data.page === 1 ? items : this.data.helpList.concat(items),
          hasMore: newList.length >= 10,
          page: this.data.page + 1
        });
      })
      .catch((err) => {
        wx.showToast({ title: err.message, icon: 'none' });
      });
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
    if (minutes < 60) return `${minutes}分钟前`;
    if (hours < 24) return `${hours}小时前`;
    if (days < 7) return `${days}天前`;
    return '7天前';
  },

  onCardTap(e) {
    const item = e.currentTarget.dataset.item;
    wx.navigateTo({ url: `/pages/idle-detail/idle-detail?id=${item.id}` });
  },

  onHelpCardTap(e) {
    const item = e.currentTarget.dataset.item;
    wx.navigateTo({ url: `/pages/help-detail/help-detail?id=${item.id}` });
  },

  onSearchTap() {
    wx.navigateTo({ url: '/pages/search/search' });
  },

  onFabTap() {
    this.setData({ fabOpen: !this.data.fabOpen });
  },

  onCloseFab() {
    this.setData({ fabOpen: false });
  },

  onPublishIdle() {
    this.setData({ fabOpen: false });
    wx.navigateTo({ url: '/pages/publish-idle/publish-idle?type=LEND' });
  },

  onPublishBorrow() {
    this.setData({ fabOpen: false });
    wx.navigateTo({ url: '/pages/publish-idle/publish-idle?type=WANTED' });
  },

  onPublishHelp() {
    this.setData({ fabOpen: false });
    wx.navigateTo({ url: '/pages/publish-idle/publish-idle?type=HELP' });
  }
});
