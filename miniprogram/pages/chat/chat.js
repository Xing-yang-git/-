const api = require('../../utils/api');

Page({
  data: {
    sessionId: '',
    otherName: '',
    roomInfo: '',
    aboutTitle: '',
    aboutId: '',
    messages: [],
    inputText: '',
    scrollToView: '',
    templates: ['还在吗？', '好的', '谢谢'],
    userId: '',
    socketOpen: false
  },

  onLoad(options) {
    const sessionId = options.sessionId || '';
    const otherName = decodeURIComponent(options.name || '用户');
    const roomInfo = decodeURIComponent(options.room || '');
    const aboutTitle = decodeURIComponent(options.about || '');
    const aboutId = options.aboutId || '';
    const userId = wx.getStorageSync('userId') || '';

    this.setData({ sessionId, otherName, roomInfo, aboutTitle, aboutId, userId });

    if (sessionId) {
      this.loadMessages();
    }
    this.connectWebSocket();
  },

  onShow() {
    if (this.data.sessionId) {
      this.markRead();
    }
  },

  onUnload() {
    if (this.socketTask) {
      this.socketTask.close();
    }
  },

  async loadMessages() {
    try {
      const res = await api.get('/api/chat/sessions/' + this.data.sessionId + '/messages');
      const msgs = (res.data && res.data.list) ? res.data.list : (res.data || []);
      this.processMessages(msgs);
      this.scrollToBottom();
    } catch (e) {
      console.error('Load messages failed:', e);
    }
  },

  processMessages(list) {
    const messages = list.map((msg, idx) => {
      const prev = list[idx - 1];
      const showTime = !prev || (msg.createTime - prev.createTime > 300000);
      const date = new Date(msg.createTime);
      const timeText = this.formatTime(date);
      return {
        id: msg.id || idx,
        content: msg.content,
        isMine: msg.senderId === this.data.userId,
        avatarText: (msg.senderName || '?')[0],
        showTime,
        timeText: showTime ? timeText : ''
      };
    });
    this.setData({ messages });
  },

  formatTime(date) {
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();
    const h = date.getHours().toString().padStart(2, '0');
    const m = date.getMinutes().toString().padStart(2, '0');
    if (isToday) return h + ':' + m;
    const M = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return M + '/' + d + ' ' + h + ':' + m;
  },

  scrollToBottom() {
    setTimeout(() => {
      this.setData({ scrollToView: 'msg-bottom' });
    }, 100);
  },

  connectWebSocket() {
    const wsUrl = 'ws://localhost:8080/ws/chat?userId=' + this.data.userId;
    this.socketTask = wx.connectSocket({ url: wsUrl });

    this.socketTask.onOpen(() => {
      this.setData({ socketOpen: true });
    });

    this.socketTask.onMessage(res => {
      try {
        const msg = JSON.parse(res.data);
        if (msg.sessionId === this.data.sessionId) {
          const messages = [...this.data.messages, {
            id: msg.id || Date.now(),
            content: msg.content,
            isMine: msg.senderId === this.data.userId,
            avatarText: (msg.senderName || '?')[0],
            showTime: true,
            timeText: this.formatTime(new Date())
          }];
          this.setData({ messages });
          this.scrollToBottom();
        }
      } catch (e) {
        console.error('WS message parse error:', e);
      }
    });

    this.socketTask.onError(err => {
      console.error('WebSocket error:', err);
      this.setData({ socketOpen: false });
    });

    this.socketTask.onClose(() => {
      this.setData({ socketOpen: false });
    });
  },

  async markRead() {
    try {
      await api.put('/api/chat/sessions/' + this.data.sessionId + '/read');
    } catch (e) {
      console.error('Mark read failed:', e);
    }
  },

  onInputChange(e) {
    this.setData({ inputText: e.detail.value });
  },

  onSend() {
    const text = this.data.inputText.trim();
    if (!text) return;

    const msg = {
      id: Date.now(),
      content: text,
      isMine: true,
      avatarText: '我',
      showTime: true,
      timeText: this.formatTime(new Date())
    };

    const messages = [...this.data.messages, msg];
    this.setData({ messages, inputText: '' });
    this.scrollToBottom();

    // Send via WebSocket
    if (this.data.socketOpen && this.socketTask) {
      this.socketTask.send({
        data: JSON.stringify({
          sessionId: this.data.sessionId,
          content: text,
          type: 'text'
        })
      });
    }

    // Also persist via REST
    api.post('/api/chat/sessions/' + this.data.sessionId + '/messages', {
      content: text,
      type: 'text'
    }).catch(e => {
      console.error('Send message failed:', e);
    });
  },

  onTemplateTap(e) {
    this.setData({ inputText: e.currentTarget.dataset.text });
  },

  onAboutTap() {
    if (this.data.aboutId) {
      wx.navigateTo({ url: '/pages/detail/detail?id=' + this.data.aboutId });
    }
  }
});
