/**
 * ws.js 单元测试 — WebSocket 管理器。
 * 覆盖：连接、认证、心跳、重连、消息分发、手动关闭。
 */

describe('WebSocket', () => {
  let ws;
  let wxMock;
  let mockGetApp;
  let mockSocketTask;

  beforeEach(() => {
    jest.resetModules();
    jest.useFakeTimers();

    const { createWxMock } = require('../__mocks__/wx.mock');
    wxMock = createWxMock();
    global.wx = wxMock;

    mockGetApp = jest.fn(() => ({
      globalData: { baseUrl: 'http://test.local:8080' }
    }));
    global.getApp = mockGetApp;

    // 构造 connectSocket 返回的 socket task
    mockSocketTask = {
      onOpen: jest.fn(),
      onMessage: jest.fn(),
      onClose: jest.fn(),
      onError: jest.fn(),
      send: jest.fn(),
      close: jest.fn(),
      readyState: 1
    };

    wx.connectSocket.mockReturnValue(mockSocketTask);

    ws = require('../ws');
  });

  afterEach(() => {
    delete global.wx;
    delete global.getApp;
    jest.useRealTimers();
  });

  // ==================== connect ====================

  describe('connect', () => {
    it('connect() 应返回 WebSocketManager 实例', () => {
      const manager = ws.connect('/ws/chat');
      expect(manager).toBeDefined();
      expect(typeof manager.send).toBe('function');
      expect(typeof manager.close).toBe('function');
    });

    it('应使用 app.globalData.baseUrl 拼接完整 ws:// 地址', () => {
      ws.connect('/ws/chat');

      expect(wx.connectSocket).toHaveBeenCalledWith(
        expect.objectContaining({
          url: 'ws://test.local:8080/ws/chat'
        })
      );
    });

    it('传入完整 ws:// URL 时不拼接 baseUrl', () => {
      ws.connect('ws://other.local:9090/ws/chat');

      expect(wx.connectSocket).toHaveBeenCalledWith(
        expect.objectContaining({
          url: 'ws://other.local:9090/ws/chat'
        })
      );
    });
  });

  // ==================== onOpen → 认证 + 心跳 ====================

  describe('onOpen', () => {
    it('连接成功后应发送 AUTH 消息', () => {
      wx.setStorageSync('token', 'test-token');
      ws.connect('/ws/chat');

      // 触发 onOpen 回调
      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      expect(mockSocketTask.send).toHaveBeenCalledWith(
        expect.objectContaining({
          data: JSON.stringify({ type: 'AUTH', token: 'test-token' })
        })
      );
    });

    it('无 token 时不发送 AUTH 消息', () => {
      ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      // send 不应被调用（因为没有 token）
      const authCalls = mockSocketTask.send.mock.calls.filter(
        call => call[0].data && call[0].data.includes('AUTH')
      );
      expect(authCalls.length).toBe(0);
    });

    it('应启动心跳定时器', () => {
      ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      // 推进 30 秒（心跳间隔）
      jest.advanceTimersByTime(30000);

      expect(mockSocketTask.send).toHaveBeenCalledWith(
        expect.objectContaining({
          data: JSON.stringify({ type: 'PING' })
        })
      );
    });
  });

  // ==================== onMessage ====================

  describe('onMessage 消息分发', () => {
    it('应解析 JSON 并分发给注册的回调', () => {
      const manager = ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      const receivedMessages = [];
      manager.onMessage((msg) => receivedMessages.push(msg));

      const onMsgFn = mockSocketTask.onMessage.mock.calls[0][0];
      onMsgFn({ data: JSON.stringify({ type: 'CHAT', text: 'hello' }) });

      expect(receivedMessages).toEqual([{ type: 'CHAT', text: 'hello' }]);
    });

    it('PONG 消息不应分发给回调', () => {
      const manager = ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      const receivedMessages = [];
      manager.onMessage((msg) => receivedMessages.push(msg));

      const onMsgFn = mockSocketTask.onMessage.mock.calls[0][0];
      onMsgFn({ data: JSON.stringify({ type: 'PONG' }) });

      expect(receivedMessages).toEqual([]);
    });

    it('非 JSON 消息不触发回调也不崩溃', () => {
      const manager = ws.connect('/ws/chat');
      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      const receivedMessages = [];
      manager.onMessage((msg) => receivedMessages.push(msg));

      expect(() => {
        const onMsgFn = mockSocketTask.onMessage.mock.calls[0][0];
        onMsgFn({ data: 'not-json' });
      }).not.toThrow();

      expect(receivedMessages).toEqual([]);
    });
  });

  // ==================== close ====================

  describe('close 手动关闭', () => {
    it('应设置 intentionalClose 标志，停止心跳，关闭连接', () => {
      const manager = ws.connect('/ws/chat');

      // 先 open
      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      manager.close();

      expect(mockSocketTask.close).toHaveBeenCalledWith(
        expect.objectContaining({ code: 1000 })
      );
    });

    it('手动关闭后不应触发重连', () => {
      const manager = ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      manager.close();

      // 触发 onClose，不应重连
      const onCloseFn = mockSocketTask.onClose.mock.calls[0][0];
      onCloseFn({ code: 1000 });

      // 推进时间，不应有新连接
      jest.advanceTimersByTime(10000);
      expect(wx.connectSocket).toHaveBeenCalledTimes(1);
    });
  });

  // ==================== onClose 自动重连 ====================

  describe('onClose 自动重连', () => {
    it('非主动断开时应触发重连', () => {
      ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      // 触发异常断开
      const onCloseFn = mockSocketTask.onClose.mock.calls[0][0];
      onCloseFn({ code: 1006 });

      // 推进 2000ms（重连延迟）
      jest.advanceTimersByTime(2000);

      expect(wx.connectSocket).toHaveBeenCalledTimes(2);
    });

    it('超过最大重连次数后不再重连', () => {
      ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      // 连续断开 5 次（MAX_RECONNECT = 5）
      for (let i = 0; i < 5; i++) {
        const onCloseFn = mockSocketTask.onClose.mock.calls[0][0];
        onCloseFn({ code: 1006 });
        // 推进足够的重连时间
        jest.advanceTimersByTime(2000 * Math.pow(1.5, i));
      }

      // 第 6 次不应再重连
      const onCloseFn = mockSocketTask.onClose.mock.calls[0][0];
      onCloseFn({ code: 1006 });
      jest.advanceTimersByTime(20000);

      // connectSocket 被调用：1 次初始 + 5 次重连 = 6 次
      expect(wx.connectSocket).toHaveBeenCalledTimes(6);
    });
  });

  // ==================== send ====================

  describe('send 消息发送', () => {
    it('readyState === 1 时应发送消息', () => {
      const manager = ws.connect('/ws/chat');

      const onOpenFn = mockSocketTask.onOpen.mock.calls[0][0];
      onOpenFn();

      manager.send({ type: 'CHAT', text: 'hi' });

      expect(mockSocketTask.send).toHaveBeenCalledWith(
        expect.objectContaining({
          data: JSON.stringify({ type: 'CHAT', text: 'hi' })
        })
      );
    });

    it('readyState !== 1 时不发送也不崩溃', () => {
      const manager = ws.connect('/ws/chat');
      // 不触发 onOpen，readyState 保持 0

      expect(() => {
        manager.send({ type: 'CHAT', text: 'hi' });
      }).not.toThrow();
    });
  });
});
