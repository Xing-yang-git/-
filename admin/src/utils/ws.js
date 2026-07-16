let socket = null;
let reconnectTimer = null;
let messageCallback = null;
let reconnectAttempts = 0;
const MAX_RECONNECT = 10;

export function connect(onMessage) {
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

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
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

  socket.onerror = (err) => {
    console.error('[WS] Error:', err);
  };
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  if (reconnectAttempts >= MAX_RECONNECT) {
    console.warn('[WS] Max reconnect attempts reached');
    return;
  }
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
  reconnectAttempts++;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect(messageCallback);
  }, delay);
}

export function close() {
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
