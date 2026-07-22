/**
 * api.js 单元测试 — HTTP 请求工具模块。
 * 覆盖：GET/POST/PUT/DELETE 请求、文件上传、401/403 防抖重登录。
 */

describe('api', () => {
  let api;
  let wxMock;
  let mockGetApp;

  beforeEach(() => {
    jest.resetModules();

    const { createWxMock } = require('../__mocks__/wx.mock');
    wxMock = createWxMock();
    global.wx = wxMock;

    mockGetApp = jest.fn(() => ({
      globalData: { baseUrl: 'http://test.local:8080' }
    }));
    global.getApp = mockGetApp;

    api = require('../api');
  });

  afterEach(() => {
    delete global.wx;
    delete global.getApp;
  });

  // ==================== request — 成功路径 ====================

  describe('GET 请求', () => {
    it('应在成功响应后解包 Result.data', async () => {
      wx.request.mockImplementation(({ success }) => {
        success({
          statusCode: 200,
          data: { code: 200, message: 'OK', data: { id: 1, name: 'test' } }
        });
      });

      const result = await api.get('/api/items', { page: 0 });
      expect(result).toEqual({ id: 1, name: 'test' });
    });

    it('应自动拼接 baseUrl', async () => {
      wx.request.mockImplementation(({ success, url }) => {
        expect(url).toBe('http://test.local:8080/api/items');
        success({ statusCode: 200, data: { data: [] } });
      });

      await api.get('/api/items');
    });

    it('响应无 data 字段时应返回整个响应体', async () => {
      wx.request.mockImplementation(({ success }) => {
        success({ statusCode: 200, data: { code: 200, message: 'OK' } });
      });

      const result = await api.get('/api/items');
      expect(result).toEqual({ code: 200, message: 'OK' });
    });
  });

  describe('POST 请求', () => {
    it('应将 body 序列化为 JSON 字符串', async () => {
      wx.request.mockImplementation(({ success, data }) => {
        // data 应为 JSON 字符串
        const parsed = JSON.parse(data);
        expect(parsed).toEqual({ title: 'hello', price: 100 });
        success({ statusCode: 200, data: { data: { id: 1 } } });
      });

      const result = await api.post('/api/items', { title: 'hello', price: 100 });
      expect(result).toEqual({ id: 1 });
    });

    it('data 为 null 时不序列化', async () => {
      wx.request.mockImplementation(({ success, data }) => {
        expect(data).toBeNull();
        success({ statusCode: 200, data: { data: 'ok' } });
      });

      await api.post('/api/logout', null);
    });
  });

  // ==================== request — 错误路径 ====================

  describe('401 未授权', () => {
    it('收到 401 时应触发 forceRelogin 并拒绝', async () => {
      wx.request.mockImplementation(({ success }) => {
        success({ statusCode: 401, data: {} });
      });

      await expect(api.get('/api/protected')).rejects.toThrow('请先登录');
      expect(wx.reLaunch).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/login/login' })
      );
    });
  });

  describe('403 未审核', () => {
    it('账号未通过审核时应触发 forceReviewStatus 并拒绝', async () => {
      wx.request.mockImplementation(({ success }) => {
        success({
          statusCode: 403,
          data: { code: 403, message: '账号未通过审核', data: 'pending' }
        });
      });

      await expect(api.get('/api/items')).rejects.toThrow('账号未通过审核');
    });

    it('普通 403（非审核相关）时返回无权限错误', async () => {
      wx.request.mockImplementation(({ success }) => {
        success({ statusCode: 403, data: { message: '无权限访问' } });
      });

      await expect(api.get('/api/admin')).rejects.toThrow('无权限访问');
    });
  });

  describe('500 服务端错误', () => {
    it('应返回友好错误提示', async () => {
      wx.request.mockImplementation(({ success }) => {
        success({ statusCode: 500, data: {} });
      });

      await expect(api.get('/api/items')).rejects.toThrow('服务器异常，请稍后重试');
    });
  });

  describe('4xx 客户端错误', () => {
    it('应透传后端错误消息', async () => {
      wx.request.mockImplementation(({ success }) => {
        success({ statusCode: 400, data: { message: '参数错误' } });
      });

      await expect(api.get('/api/items')).rejects.toThrow('参数错误');
    });
  });

  // ==================== request — 网络错误 ====================

  describe('网络连接失败', () => {
    it('域名不在白名单时返回引导提示', async () => {
      wx.request.mockImplementation(({ fail }) => {
        fail({ errMsg: 'request:fail url not in domain list' });
      });

      await expect(api.get('/api/items')).rejects.toThrow('真机调试');
    });

    it('请求超时时返回超时提示', async () => {
      wx.request.mockImplementation(({ fail }) => {
        fail({ errMsg: 'request:fail timeout' });
      });

      await expect(api.get('/api/items')).rejects.toThrow('请求超时');
    });

    it('通用网络错误时返回网络提示', async () => {
      wx.request.mockImplementation(({ fail }) => {
        fail({ errMsg: 'request:fail' });
      });

      await expect(api.get('/api/items')).rejects.toThrow('网络连接失败');
    });
  });

  // ==================== upload — 成功上传 ====================

  describe('upload 文件上传', () => {
    it('上传成功应返回文件 URL', async () => {
      wx.uploadFile.mockImplementation(({ success }) => {
        success({
          statusCode: 200,
          data: JSON.stringify({ code: 200, data: { url: '/uploads/test.jpg' } })
        });
      });

      const result = await api.upload('/api/common/upload', '/tmp/photo.jpg');
      expect(result).toBe('/uploads/test.jpg');
    });

    it('HTTP 200 但业务 code 非 200 时应拒绝', async () => {
      wx.uploadFile.mockImplementation(({ success }) => {
        success({
          statusCode: 200,
          data: JSON.stringify({ code: 400, message: '文件格式不支持' })
        });
      });

      await expect(api.upload('/api/common/upload', '/tmp/test.exe'))
        .rejects.toThrow('文件格式不支持');
    });

    it('401 时应触发重登录', async () => {
      wx.uploadFile.mockImplementation(({ success }) => {
        success({ statusCode: 401, data: '{}' });
      });

      await expect(api.upload('/api/common/upload', '/tmp/photo.jpg'))
        .rejects.toThrow('请先登录');
    });

    it('网络错误时应返回友好提示', async () => {
      wx.uploadFile.mockImplementation(({ fail }) => {
        fail({ errMsg: 'uploadFile:fail' });
      });

      await expect(api.upload('/api/common/upload', '/tmp/photo.jpg'))
        .rejects.toThrow('网络连接失败');
    });
  });

  // ==================== 门禁函数导出 ====================

  describe('导出验证', () => {
    it('应导出全部 API 方法', () => {
      expect(typeof api.get).toBe('function');
      expect(typeof api.post).toBe('function');
      expect(typeof api.put).toBe('function');
      expect(typeof api.del).toBe('function');
      expect(typeof api.upload).toBe('function');
      expect(typeof api.forceRelogin).toBe('function');
      expect(typeof api.forceReviewStatus).toBe('function');
    });
  });
});
