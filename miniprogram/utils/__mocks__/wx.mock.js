/**
 * 微信小程序全局 wx 对象 Mock。
 * 在测试文件顶部通过 jest.mock 注入，模拟 wx.getStorageSync / wx.request 等核心 API。
 */

/** 模拟的本地存储 */
const _storage = {};

/** 重置存储和所有 mock 调用记录 */
function resetWxMock() {
  Object.keys(_storage).forEach(k => delete _storage[k]);
}

/** 创建可复用的 wx mock 对象（每次测试前调用 resetWxMock 清空状态） */
function createWxMock() {
  return {
    /** 同步读取存储 */
    getStorageSync: (key) => {
      return _storage[key] !== undefined ? _storage[key] : '';
    },

    /** 同步写入存储 */
    setStorageSync: (key, value) => {
      _storage[key] = value;
    },

    /** 同步删除存储 */
    removeStorageSync: (key) => {
      delete _storage[key];
    },

    /** 微信网络请求 Mock（默认不调用回调，由测试 case 覆盖） */
    request: jest.fn(),

    /** 上传文件 Mock */
    uploadFile: jest.fn(),

    /** WebSocket 连接 */
    connectSocket: jest.fn(() => ({
      onOpen: jest.fn(),
      onMessage: jest.fn(),
      onClose: jest.fn(),
      onError: jest.fn(),
      send: jest.fn(),
      close: jest.fn(),
      readyState: 0
    })),

    /** 页面跳转 */
    reLaunch: jest.fn(({ complete }) => {
      if (complete) complete();
    }),

    /** 导航返回 */
    navigateBack: jest.fn(),

    /** Toast 提示 */
    showToast: jest.fn(),

    /** Loading 提示 */
    showLoading: jest.fn(),
    hideLoading: jest.fn(),

    /** 获取存储信息 */
    getStorageInfoSync: jest.fn(() => ({ keys: [] })),

    /** 获取系统信息 */
    getSystemInfoSync: jest.fn(() => ({
      platform: 'ios',
      statusBarHeight: 44
    })),

    /** 获取账号信息 */
    getAccountInfoSync: jest.fn(() => ({
      miniProgram: { envVersion: 'develop' }
    })),

    /** 设置导航栏颜色 */
    setNavigationBarColor: jest.fn(),

    /** 选择图片 */
    chooseImage: jest.fn(),

    /** 预览图片 */
    previewImage: jest.fn(),

    /** 创建音频播放器 */
    createInnerAudioContext: jest.fn(() => ({
      src: '',
      play: jest.fn(),
      pause: jest.fn(),
      stop: jest.fn(),
      destroy: jest.fn(),
      onPlay: jest.fn(),
      onEnded: jest.fn(),
      onError: jest.fn()
    })),

    /** 创建录音管理器 */
    getRecorderManager: jest.fn(() => ({
      start: jest.fn(),
      stop: jest.fn(),
      onStop: jest.fn(),
      onError: jest.fn()
    })),
  };
}

module.exports = { createWxMock, resetWxMock };
