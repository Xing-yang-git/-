const api = require('../../utils/api');

Page({
  data: {
    keyword: '',
    autoFocus: true,
    history: [],
    searching: false,
    currentFilter: 'all',
    idleResults: [],
    helpResults: [],
    filteredResults: []
  },

  onLoad() {
    this.loadHistory();
  },

  loadHistory() {
    const history = wx.getStorageSync('searchHistory') || [];
    this.setData({ history });
  },

  saveHistory(keyword) {
    if (!keyword.trim()) return;
    let history = this.data.history.filter(h => h !== keyword);
    history.unshift(keyword);
    if (history.length > 20) history = history.slice(0, 20);
    wx.setStorageSync('searchHistory', history);
    this.setData({ history });
  },

  onKeywordInput(e) {
    const keyword = e.detail.value;
    this.setData({ keyword });
    if (!keyword.trim()) {
      this.setData({ searching: false, filteredResults: [] });
    }
  },

  onClearKeyword() {
    this.setData({ keyword: '', searching: false, filteredResults: [] });
  },

  onSearch() {
    const keyword = this.data.keyword.trim();
    if (!keyword) return;

    this.saveHistory(keyword);
    this.setData({ searching: true });

    wx.showLoading({ title: '搜索中...' });

    const bgColorsIdle = ['#E8F0FE', '#FFF3E0', '#E8F5E9', '#FCE4EC', '#E3F2FD'];
    const bgColorsHelp = ['#FFEDED', '#FFF3E0', '#E3F2FD', '#F3E5F5'];

    Promise.all([
      api.get('/api/idle/search', { keyword, postType: 'LEND', page: 0, size: 20 }).catch(() => ({ content: [] })),
      api.get('/api/idle/search', { keyword, postType: 'WANTED', page: 0, size: 20 }).catch(() => ({ content: [] })),
      api.get('/api/help/search', { keyword, page: 0, size: 20 }).catch(() => ({ content: [] }))
    ]).then(([lendData, wantedData, helpData]) => {
      wx.hideLoading();

      const lendList = (lendData.content || lendData.list || lendData.records || []).map((item, i) => ({
        ...item,
        type: 'idle',
        bgColor: bgColorsIdle[i % bgColorsIdle.length],
        subtitle: `闲置借出 · ${item.userRoom || ''}`
      }));

      const wantedList = (wantedData.content || wantedData.list || wantedData.records || []).map((item, i) => ({
        ...item,
        type: 'idle',
        bgColor: bgColorsIdle[(i + lendList.length) % bgColorsIdle.length],
        subtitle: `需求借入 · ${item.userRoom || ''}`
      }));

      const idleList = [...lendList, ...wantedList];

      const helpList = (helpData.content || helpData.list || helpData.records || []).map((item, i) => ({
        ...item,
        type: 'help',
        bgColor: bgColorsHelp[i % bgColorsHelp.length],
        subtitle: `求助 · ${item.userRoom || ''}${item.isUrgent ? ' · 紧急' : ''}`
      }));

      this.setData({
        idleResults: idleList,
        helpResults: helpList,
        currentFilter: 'all'
      });
      this.applyFilter();
    }).catch((err) => {
      wx.hideLoading();
      wx.showToast({ title: err.message, icon: 'none' });
    });
  },

  onQuickSearch(e) {
    const keyword = e.currentTarget.dataset.keyword;
    this.setData({ keyword });
    this.onSearch();
  },

  onFilterChange(e) {
    this.setData({ currentFilter: e.currentTarget.dataset.filter });
    this.applyFilter();
  },

  applyFilter() {
    const { currentFilter, idleResults, helpResults } = this.data;
    let filtered;
    if (currentFilter === 'idle') {
      filtered = idleResults;
    } else if (currentFilter === 'help') {
      filtered = helpResults;
    } else {
      filtered = [...idleResults, ...helpResults];
    }
    this.setData({ filteredResults: filtered });
  },

  onResultTap(e) {
    const item = e.currentTarget.dataset.item;
    if (item.type === 'help') {
      wx.navigateTo({ url: `/pages/help-detail/help-detail?id=${item.id}` });
    } else {
      wx.navigateTo({ url: `/pages/idle-detail/idle-detail?id=${item.id}` });
    }
  },

  onClearHistory() {
    wx.setStorageSync('searchHistory', []);
    this.setData({ history: [] });
    wx.showToast({ title: '已清空搜索历史', icon: 'success' });
  },

  onCancel() {
    wx.navigateBack();
  }
});
