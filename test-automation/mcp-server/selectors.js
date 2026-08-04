/**
 * 小程序页面元素选择器映射表
 * 基于实际 WXML 结构提取，所有自动化操作通过此文件引用选择器
 */

export default {
  // ==================== 登录页 ====================
  login: {
    page: '.login-page',
    communityPicker: '.tenant-picker-inner',
    phoneInput: '.phone-input input',
    passwordInput: '.password-input input',
    passwordToggle: '.toggle-password',
    loginButton: '.login-btn button',
    agreementCheckbox: 'checkbox',
    registerLink: '.switch-login',
    privacyRow: '.privacy-row',
  },

  // ==================== 注册页 ====================
  register: {
    page: '.register-page',
    stepIndicator: '.step-indicator-wrap',
    // Step 0: 选择小区
    searchInput: '.search-input',
    searchClear: '.search-clear',
    tenantListRows: '.list-row',
    tenantCheckmark: '.check-mark',
    // Step 1: 填写房号
    buildingInput: 'input[placeholder*="栋"]',
    unitInput: 'input[placeholder*="单元"]',
    roomInput: 'input[placeholder*="房"]',
    residentTypeSegment: '.segment-item',  // owner / tenant
    nickPreview: '.callout text',
    // Step 2: 账号设置
    stepPhoneInput: 'input[placeholder*="手机号"]',
    stepPasswordInput: 'input[placeholder*="8-20位"]',
    stepPasswordConfirmInput: 'input[placeholder*="再次输入"]',
    stepPasswordToggle: '.password-input-group .toggle-password',
    // Step 3: 上传证件
    realNameInput: 'input[placeholder*="真实姓名"]',
    imageUploadAdd: '.img-upload-add',
    imageUploadItems: '.img-upload-item',
    imageDelete: '.img-delete',
    uploadedImages: '.uploaded-img',
    // 底部导航
    nextButton: '.btn-primary',
    prevButton: '.btn-secondary',
    stepFooter: '.step-footer',
  },

  // ==================== 首页 ====================
  home: {
    page: '.home-page',
    communityName: '.community-name',
    searchBox: '.search-box',
    // Tab 三段控件
    segmentTabs: '.segment-item',
    tabLend: '.segment-item[data-tab="0"]',    // 文本为"闲置借出"
    tabWanted: '.segment-item[data-tab="1"]',   // 文本为"需求借入"
    tabHelp: '.segment-item[data-tab="2"]',     // 文本为"技能求助"
    // 列表
    listCards: '.list-card',
    listEmpty: '.empty-state',
    // FAB
    fab: '.fab',
    fabMenu: '.fab-menu',
    fabPublishIdle: '.fab-menu-item:nth-child(1)',
    fabPublishBorrow: '.fab-menu-item:nth-child(2)',
    fabPublishHelp: '.fab-menu-item:nth-child(3)',
    fabOverlay: '.fab-overlay',
  },

  // ==================== 发布页 ====================
  publish: {
    page: '.page-content',
    // 发布类型三段
    postTypeSegment: '.segment-item',
    typeLend: '.segment-item[data-value="LEND"]',
    typeWanted: '.segment-item[data-value="WANTED"]',
    typeHelp: '.segment-item[data-value="HELP"]',
    // 通用
    titleInput: 'input[placeholder*="标题"], input[placeholder*="名称"]',
    descriptionTextarea: '.textarea',
    category: {
      pills: '.pill',
      pillSelected: '.pill.selected',
      customInput: 'input[placeholder*="手动输入"]',
    },
    // LEND/WANTED 图片
    imageAdd: '.img-add-btn',
    imageItems: '.img-upload-item',
    imageDelete: '.img-delete',
    // LEND 价格
    priceInput: 'input[type="digit"]',
    // 时长
    durationUnitDay: '.segment-item[data-value="day"]',
    durationUnitHour: '.segment-item[data-value="hour"]',
    durationPicker: '.select-wrap .select',
    // 取件方式
    pickupSelfPickup: '.segment-item[data-value="self_pickup"]',
    pickupBoth: '.segment-item[data-value="both"]',
    // 物品状况
    conditionLikeNew: '.segment-item[data-value="like-new"]',
    conditionNormal: '.segment-item[data-value="normal"]',
    conditionWorn: '.segment-item[data-value="worn"]',
    // HELP 紧急程度
    urgencyNormal: '.segment-item[data-value="normal"]',
    urgencyUrgent: '.segment-item[data-value="urgent"]',
    // HELP 时间范围
    timeRangeSwitch: '.time-switch switch',
    timeStartDate: '.time-row:nth-child(1) .time-picker:nth-child(2)',
    timeEndDate: '.time-row:nth-child(2) .time-picker:nth-child(2)',
    // 提交
    submitButton: '.btn-primary.btn-block',
    // 通用按钮
    primaryButton: '.btn-primary',
  },

  // ==================== 闲置详情页 ====================
  idleDetail: {
    page: '', // 动态页面，无固定 class
    images: '.swiper-item image',
    title: '', // 动态
    contactButton: '', // "联系对方"
    borrowButton: '', // "申请借入"
    publisherCard: '', // 发布者信息
    borrowSheet: '', // 申请底部弹出
    borrowNoteInput: '.textarea', // 申请备注
    borrowConfirmButton: '', // 确认申请按钮（在 sheet 中）
  },

  // ==================== 管理页 ====================
  myPosts: {
    page: '',
    // 4 个主 tab
    tabPublish: '', // 发布
    tabApprovals: '', // 待审批
    tabInProgress: '', // 进行中
    tabCompleted: '', // 已完成
    // 审批子 tab
    subTabBorrow: '',
    subTabLend: '',
    subTabHelp: '',
    // 操作
    approveButton: '',
    rejectButton: '',
    // 底部弹窗
    approvalSheet: '',
    progressSheet: '',
  },

  // ==================== 通用 ====================
  common: {
    toast: '.wx-toast, wx-toast',
    loading: '.wx-loading',
    modal: '.wx-modal',
    confirmButton: 'button.btn-primary',
    cancelButton: 'button.btn-secondary',
  },
};
