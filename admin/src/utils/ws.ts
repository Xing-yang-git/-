/**
 * WebSocket 连接管理模块 —— 用于仪表盘实时数据推送。
 * 支持指数退避自动重连（最多 10 次），以及手动关闭连接。
 */

/** WebSocket 消息回调函数类型 */
type MessageCallback = (data: unknown) => void;

/** WebSocket 连接实例 */
let socket: WebSocket | null = null;

/** 重连定时器句柄 */
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

/** 外部注册的消息回调 */
let messageCallback: MessageCallback | null = null;

/** 当前已重连次数 */
let reconnectAttempts = 0;

/** 最大重连次数 */
const MAX_RECONNECT = 10;

/**
 * 建立 WebSocket 连接并注册消息回调。
 * @param onMessage - 接收到推送消息时的回调函数
 */
export function connect(onMessage: MessageCallback): void {
  messageCallback = onMessage;

  const token = localStorage.getItem('admin_token');
  if (!token) return;

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const url = `${protocol}//${window.location.hostname}:8080/ws/dashboard?token=${token}`;

  socket = new WebSocket(url);

  socket.onopen = () => {
    console.log('[WS] Dashboard connected');
    reconnectAttempts = 0;
  };

  socket.onmessage = (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data as string) as unknown;
      if (messageCallback) messageCallback(data);
    } catch (e) {
      console.warn('[WS] Failed to parse message:', e);
    }
  };

  socket.onclose = () => {
    console.log('[WS] Disconnected');
    socket = null;
    scheduleReconnect();
  };

  socket.onerror = (err: Event) => {
    console.error('[WS] Error:', err);
  };
}

/** 指数退避重连调度，最大延迟 30 秒 */
function scheduleReconnect(): void {
  if (reconnectTimer) return;
  if (reconnectAttempts >= MAX_RECONNECT) {
    console.warn('[WS] Max reconnect attempts reached');
    return;
  }
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
  reconnectAttempts++;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect(messageCallback!);
  }, delay);
}

/**
 * 关闭 WebSocket 连接并阻止自动重连。
 * 通常在组件卸载或页面离开时调用。
 */
export function close(): void {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (socket) {
    socket.onclose = null; // 阻止自动重连
    socket.close();
    socket = null;
  }
}
