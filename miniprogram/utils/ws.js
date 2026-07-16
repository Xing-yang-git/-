/**
 * WebSocket 管理器
 * 处理连接、重连、心跳和消息分发。
 */

const HEARTBEAT_INTERVAL = 30000;
const MAX_RECONNECT = 5;
const RECONNECT_DELAY = 2000;

class WebSocketManager {
  constructor() {
    this._socket = null;
    this._url = '';
    this._messageCallbacks = [];
    this._heartbeatTimer = null;
    this._reconnectCount = 0;
    this._reconnectTimer = null;
    this._intentionalClose = false;
  }

  connect(url) {
    this._url = url;
    this._intentionalClose = false;
    this._reconnectCount = 0;
    this._doConnect(url);
  }

  _doConnect(url) {
    const app = getApp();
    const baseWsUrl = app.globalData.baseUrl
      .replace(/^http/, 'ws')
      .replace(/\/$/, '');

    const fullUrl = url.startsWith('ws') ? url : baseWsUrl + url;

    this._socket = wx.connectSocket({
      url: fullUrl,
      success: () => {
        console.log('[WS] connecting to', fullUrl);
      },
      fail: (err) => {
        console.error('[WS] connect fail', err);
        this._tryReconnect();
      }
    });

    this._socket.onOpen(() => {
      console.log('[WS] open');
      this._reconnectCount = 0;
      this._startHeartbeat();
      this._authenticate();
    });

    this._socket.onMessage((res) => {
      try {
        const msg = JSON.parse(res.data);
        if (msg.type === 'PONG') return;
        this._messageCallbacks.forEach(cb => {
          try { cb(msg); } catch (e) { console.error('[WS] callback error', e); }
        });
      } catch (e) {
        console.error('[WS] parse error', e);
      }
    });

    this._socket.onClose((res) => {
      console.log('[WS] close', res.code, res.reason);
      this._stopHeartbeat();
      if (!this._intentionalClose) {
        this._tryReconnect();
      }
    });

    this._socket.onError((err) => {
      console.error('[WS] error', err);
    });
  }

  _authenticate() {
    const token = wx.getStorageSync('token');
    if (token) {
      this.send({ type: 'AUTH', token });
    }
  }

  _startHeartbeat() {
    this._stopHeartbeat();
    this._heartbeatTimer = setInterval(() => {
      this.send({ type: 'PING' });
    }, HEARTBEAT_INTERVAL);
  }

  _stopHeartbeat() {
    if (this._heartbeatTimer) {
      clearInterval(this._heartbeatTimer);
      this._heartbeatTimer = null;
    }
  }

  _tryReconnect() {
    if (this._reconnectCount >= MAX_RECONNECT) {
      console.log('[WS] max reconnect reached');
      return;
    }
    this._reconnectCount++;
    const delay = RECONNECT_DELAY * Math.pow(1.5, this._reconnectCount - 1);
    console.log('[WS] reconnect #' + this._reconnectCount + ' in ' + delay + 'ms');
    this._reconnectTimer = setTimeout(() => {
      this._doConnect(this._url);
    }, delay);
  }

  send(data) {
    if (this._socket && this._socket.readyState === 1) {
      this._socket.send({
        data: JSON.stringify(data),
        fail: (err) => { console.error('[WS] send fail', err); }
      });
    }
  }

  onMessage(callback) {
    if (typeof callback === 'function') {
      this._messageCallbacks.push(callback);
    }
  }

  close() {
    this._intentionalClose = true;
    this._stopHeartbeat();
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    if (this._socket) {
      this._socket.close({ code: 1000, reason: 'client close' });
      this._socket = null;
    }
  }
}

module.exports = {
  connect(url) {
    const ws = new WebSocketManager();
    ws.connect(url);
    return ws;
  }
};
