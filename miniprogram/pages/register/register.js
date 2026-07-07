const api = require('../../utils/api');

Page({
  data: {
    currentStep: 0,
    searchKeyword: '',
    tenants: [],
    filteredTenants: [],
    selectedTenant: '',
    selectedTenantObj: null,
    building: '',
    unit: '',
    room: '',
    residentType: '业主',
    nickPreview: '1栋1单元1001号(业主)',
    realName: '',
    docImages: [],
    docLabel: '上传房产证',
    docHint: '请上传清晰完整的房产证照片（支持拍照或相册选取）'
  },

  onLoad() {
    this.loadTenants();
  },

  loadTenants() {
    wx.showLoading({ title: '加载中...' });
    api.get('/api/common/tenants')
      .then((data) => {
        wx.hideLoading();
        this.setData({ tenants: data.list || data, filteredTenants: data.list || data });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  onSearchInput(e) {
    const keyword = e.detail.value.toLowerCase();
    this.setData({ searchKeyword: e.detail.value });
    const filtered = this.data.tenants.filter(t =>
      t.name.toLowerCase().includes(keyword) || (t.address || '').toLowerCase().includes(keyword)
    );
    this.setData({ filteredTenants: filtered });
  },

  onClearSearch() {
    this.setData({
      searchKeyword: '',
      filteredTenants: this.data.tenants
    });
  },

  onSelectTenant(e) {
    const tenantId = e.currentTarget.dataset.tenantId;
    const item = this.data.filteredTenants.find(t => t.id === tenantId);
    if (!item) return;
    // Store community name for home page display
    wx.setStorageSync('communityName', item.name);
    this.setData({
      selectedTenant: tenantId,
      selectedTenantObj: item,
      searchKeyword: item.name
    });
  },

  onBuildingInput(e) {
    this.setData({ building: e.detail.value });
    this.updateNick();
  },

  onUnitInput(e) {
    this.setData({ unit: e.detail.value });
    this.updateNick();
  },

  onRoomInput(e) {
    this.setData({ room: e.detail.value });
    this.updateNick();
  },

  onSetType(e) {
    const type = e.currentTarget.dataset.type;
    const docLabel = type === '业主' ? '上传房产证' : '上传租赁合同';
    const docHint = type === '业主'
      ? '请上传清晰完整的房产证照片（支持拍照或相册选取）'
      : '请上传清晰完整的租赁合同照片，需包含租期起止日期';
    this.setData({ residentType: type, docLabel, docHint });
    this.updateNick();
  },

  updateNick() {
    const b = this.data.building || '1';
    const u = this.data.unit || '1';
    const r = this.data.room || '1001';
    const typeLabel = this.data.residentType === '业主' ? '业主' : '租客';
    this.setData({ nickPreview: `${b}栋${u}单元${r}号(${typeLabel})` });
  },

  onNameInput(e) {
    this.setData({ realName: e.detail.value });
  },

  onAddImage() {
    wx.chooseImage({
      count: 3 - this.data.docImages.length,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const newImages = this.data.docImages.concat(res.tempFilePaths);
        this.setData({ docImages: newImages.slice(0, 3) });
      }
    });
  },

  onPreviewImage(e) {
    const index = e.currentTarget.dataset.index;
    wx.previewImage({
      current: this.data.docImages[index],
      urls: this.data.docImages
    });
  },

  onDeleteImage(e) {
    const index = e.currentTarget.dataset.index;
    const images = this.data.docImages;
    images.splice(index, 1);
    this.setData({ docImages: images });
  },

  onPrevStep() {
    if (this.data.currentStep > 0) {
      this.setData({ currentStep: this.data.currentStep - 1 });
    }
  },

  onNextStep() {
    const { currentStep, selectedTenant, building, unit, room, realName, docImages, residentType } = this.data;

    // Step 1 validation
    if (currentStep === 0) {
      if (!selectedTenant) {
        wx.showToast({ title: '请选择小区', icon: 'none' });
        return;
      }
      this.setData({ currentStep: 1 });
      return;
    }

    // Step 2 validation
    if (currentStep === 1) {
      if (!building || !/^\d{1,2}$/.test(building) || parseInt(building) < 1 || parseInt(building) > 99) {
        wx.showToast({ title: '请填写正确的栋号（1-99）', icon: 'none' });
        return;
      }
      if (!unit || !/^\d{1}$/.test(unit) || parseInt(unit) < 1 || parseInt(unit) > 9) {
        wx.showToast({ title: '请填写正确的单元号（1-9）', icon: 'none' });
        return;
      }
      if (!room || !/^\d{1,4}$/.test(room) || parseInt(room) < 1 || parseInt(room) > 9999) {
        wx.showToast({ title: '请填写正确的房号（1-9999）', icon: 'none' });
        return;
      }
      this.updateNick();
      this.setData({ currentStep: 2 });
      return;
    }

    // Step 3: Submit
    if (currentStep === 2) {
      if (!realName.trim()) {
        wx.showToast({ title: '请填写真实姓名', icon: 'none' });
        return;
      }
      if (docImages.length === 0) {
        wx.showToast({ title: '请上传证件照片', icon: 'none' });
        return;
      }

      wx.showLoading({ title: '上传证件照...' });

      // Upload all docImages first, then submit with URLs
      const uploadTasks = docImages.map(filePath => api.upload('/api/common/upload', filePath));
      Promise.all(uploadTasks)
        .then((urls) => {
          wx.showLoading({ title: '提交中...' });
          return api.post('/api/auth/register', {
            tenantId: selectedTenant,
            building: building,
            unit: unit,
            room: room,
            name: realName.trim(),
            userType: residentType,
            docImages: urls
          });
        })
        .then((userData) => {
          wx.hideLoading();
          // Store updated user info (with room) for home page community name
          if (userData) {
            wx.setStorageSync('userInfo', userData);
            const app = getApp();
            app.globalData.userInfo = userData;
          }
          wx.showToast({ title: '提交成功，请等待物业审核', icon: 'success' });
          setTimeout(() => {
            wx.redirectTo({ url: '/pages/review-status/review-status?state=pending' });
          }, 1200);
        })
        .catch((err) => {
          wx.hideLoading();
          wx.showToast({ title: err.message || '提交失败', icon: 'none' });
        });
    }
  }
});
