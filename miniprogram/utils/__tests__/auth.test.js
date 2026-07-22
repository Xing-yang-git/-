/**
 * auth.js 单元测试 — 认证工具模块。
 * 覆盖：token 存取、JWT 解析、页面门禁 ensureAccess。
 */

describe('auth', () => {
  let auth;
  let wxMock;
  let mockGetApp;

  /** 构造一个合法的 JWT token（模拟后端签发） */
  function makeToken(payload) {
    const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
    const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
    const sig = Buffer.from('fake-signature').toString('base64url');
    return `${header}.${body}.${sig}`;
  }

  beforeEach(() => {
    jest.resetModules();

    // 构造 wx mock
    const { createWxMock } = require('../__mocks__/wx.mock');
    wxMock = createWxMock();
    global.wx = wxMock;

    // 构造 getApp mock — 必须返回同一引用，setToken/clearToken 修改后外部可见
    const appState = {
      globalData: {
        token: '',
        userId: '',
        userInfo: null,
        baseUrl: 'http://localhost:8080'
      }
    };
    mockGetApp = jest.fn(() => appState);
    global.getApp = mockGetApp;

    auth = require('../auth');
  });

  afterEach(() => {
    delete global.wx;
    delete global.getApp;
  });

  // ==================== getToken ====================

  describe('getToken', () => {
    it('存储中有 token 时应返回 token 值', () => {
      wx.setStorageSync('token', 'test-token-abc');
      expect(auth.getToken()).toBe('test-token-abc');
    });

    it('存储中无 token 时应返回空字符串', () => {
      expect(auth.getToken()).toBe('');
    });
  });

  // ==================== setToken ====================

  describe('setToken', () => {
    it('应将 token 写入本地存储', () => {
      auth.setToken('new-token');
      expect(wx.getStorageSync('token')).toBe('new-token');
    });

    it('应同步更新 getApp().globalData.token', () => {
      auth.setToken('new-token');
      const app = getApp();
      expect(app.globalData.token).toBe('new-token');
    });
  });

  // ==================== clearToken ====================

  describe('clearToken', () => {
    it('应清除本地存储中的 token', () => {
      wx.setStorageSync('token', 'to-be-cleared');
      auth.clearToken();
      expect(wx.getStorageSync('token')).toBeFalsy();
    });

    it('应清除 globalData 中的 token、userId 和 userInfo', () => {
      const app = getApp();
      app.globalData.token = 'old-token';
      app.globalData.userId = '123';
      app.globalData.userInfo = { name: 'test' };

      auth.clearToken();

      expect(app.globalData.token).toBe('');
      expect(app.globalData.userId).toBe('');
      expect(app.globalData.userInfo).toBeNull();
    });
  });

  // ==================== getUserId ====================

  describe('getUserId', () => {
    it('应从 JWT payload 中提取 userId', () => {
      const token = makeToken({ userId: '42', name: '张三' });
      wx.setStorageSync('token', token);
      expect(auth.getUserId()).toBe('42');
    });

    it('当 JWT 中没有 userId 时应回退到 sub 字段', () => {
      const token = makeToken({ sub: '99', name: '李四' });
      wx.setStorageSync('token', token);
      expect(auth.getUserId()).toBe('99');
    });

    it('无 token 时应返回空字符串', () => {
      expect(auth.getUserId()).toBe('');
    });

    it('token 格式非法（非三段式）时应返回空字符串', () => {
      wx.setStorageSync('token', 'invalid-token');
      expect(auth.getUserId()).toBe('');
    });

    it('token payload 非合法 JSON 时应返回空字符串', () => {
      // 构造一个 payload 不是合法 base64 JSON 的 token
      const badPayload = Buffer.from('not-json').toString('base64url');
      wx.setStorageSync('token', `header.${badPayload}.sig`);
      expect(auth.getUserId()).toBe('');
    });
  });

  // ==================== ensureAccess ====================

  describe('ensureAccess', () => {
    it('无 token 时跳转登录页并返回 false', () => {
      const result = auth.ensureAccess();
      expect(result).toBe(false);
      expect(wx.reLaunch).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/login/login' })
      );
    });

    it('审核通过的账号应返回 true', () => {
      wx.setStorageSync('token', makeToken({ userId: '1' }));
      wx.setStorageSync('userInfo', { authStatus: 'approved' });
      expect(auth.ensureAccess()).toBe(true);
    });

    it('authStatus 为 registering 时跳转注册页并返回 false', () => {
      wx.setStorageSync('token', makeToken({ userId: '1' }));
      wx.setStorageSync('userInfo', { authStatus: 'registering' });
      const result = auth.ensureAccess();
      expect(result).toBe(false);
      expect(wx.reLaunch).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/register/register' })
      );
    });

    it('authStatus 为 pending 时跳转审核状态页并返回 false', () => {
      wx.setStorageSync('token', makeToken({ userId: '1' }));
      wx.setStorageSync('userInfo', { authStatus: 'pending' });
      const result = auth.ensureAccess();
      expect(result).toBe(false);
      expect(wx.reLaunch).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/review-status/review-status?state=pending' })
      );
    });

    it('authStatus 为 rejected 时跳转审核状态页并返回 false', () => {
      wx.setStorageSync('token', makeToken({ userId: '1' }));
      wx.setStorageSync('userInfo', { authStatus: 'rejected' });
      const result = auth.ensureAccess();
      expect(result).toBe(false);
      expect(wx.reLaunch).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/review-status/review-status?state=rejected' })
      );
    });

    it('authStatus 为 banned 时跳转审核状态页并返回 false', () => {
      wx.setStorageSync('token', makeToken({ userId: '1' }));
      wx.setStorageSync('userInfo', { authStatus: 'banned' });
      const result = auth.ensureAccess();
      expect(result).toBe(false);
      expect(wx.reLaunch).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/review-status/review-status?state=banned' })
      );
    });

    it('旧存储无 authStatus 字段时放行', () => {
      wx.setStorageSync('token', makeToken({ userId: '1' }));
      wx.setStorageSync('userInfo', {});
      expect(auth.ensureAccess()).toBe(true);
    });
  });

  // ==================== checkLogin ====================

  describe('checkLogin', () => {
    it('应委托给 ensureAccess 并返回相同结果', () => {
      expect(auth.checkLogin()).toBe(false);
    });
  });
});
