const api = require('../../utils/api');
const auth = require('../../utils/auth');

/**
 * 小邻 — AI 智能助手对话页（tabBar 第 3 项）。
 *
 * 功能：小区知识问答（RAG）、流式对话（SSE 伪流式）、快捷问题、引用来源展示。
 * 鉴权：仅登录即可用（后端 /api/agent/** authenticated，不要求认证通过），
 * 审核中新住户也能问小区规则。
 */
Page({
  data: {
    /** 系统状态栏高度（自定义导航偏移） */
    statusBarHeight: 0,
    /** 消息列表：{id, role: user|assistant, content, sources, streaming} */
    messages: [],
    /** 输入框文本 */
    inputText: '',
    /** 快捷问题（来自 /api/agent/suggestions） */
    suggestions: [],
    /** 键盘高度（输入区抬升） */
    keyboardHeight: 0,
    /** 发送中（流式接收未结束） */
    sending: false,
    /** scroll-view 滚动目标 */
    scrollToView: 'msg-bottom'
  },

  /** 消息自增 id */
  _msgSeq: 0,
  /** SSE 原始 buffer（分块可能跨事件边界，需累积解析） */
  _sseBuffer: '',
  /** 流式中止函数 */
  _abortStream: null,
  /** 当前正在接收回复的消息 id */
  _currentMsgId: null,

  onLoad() {
    if (!auth.ensureLoggedIn()) return;   // 仅登录门禁（审核中新住户也能用，与后端 unapproved 白名单一致）
    const sysInfo = wx.getSystemInfoSync();
    this.setData({ statusBarHeight: sysInfo.statusBarHeight || 20 });
    this.loadSuggestions();
  },

  onShow() {
    if (!auth.ensureLoggedIn()) return; // 覆盖 tab 切换与后台切回
  },

  /** 加载快捷问题 */
  loadSuggestions() {
    api.get('/api/agent/suggestions')
      .then((data) => this.setData({ suggestions: data || [] }))
      .catch(() => {});
  },

  /** 点击快捷问题 chips */
  onSuggestionTap(e) {
    const msg = e.currentTarget.dataset.msg;
    if (this.data.sending) return;
    this.setData({ inputText: msg });
    this.sendMessage(msg);
  },

  onInputChange(e) {
    this.setData({ inputText: e.detail.value });
  },

  /** 点击发送按钮 / 键盘确认键 */
  onSend() {
    const msg = (this.data.inputText || '').trim();
    if (!msg || this.data.sending) return;
    this.sendMessage(msg);
  },

  /**
   * 发送消息并建立 SSE 流式接收。
   * @param {string} msg 用户消息
   */
  sendMessage(msg) {
    const userMsg = { id: 'u' + (++this._msgSeq), role: 'user', content: msg, sources: [], streaming: false };
    const aiMsg = { id: 'a' + (++this._msgSeq), role: 'assistant', content: '', sources: [], streaming: true };
    const messages = this.data.messages.concat([userMsg, aiMsg]);
    this._currentMsgId = aiMsg.id;
    this._sseBuffer = '';
    this.setData({ messages, inputText: '', sending: true, scrollToView: aiMsg.id });

    this._abortStream = api.requestStream('/api/agent/chat', { message: msg },
      (chunk) => this._onSseChunk(chunk),
      () => this._onStreamError());
  },

  /** SSE 分块累积 + 按事件边界切分 */
  _onSseChunk(chunk) {
    this._sseBuffer += chunk;
    let idx;
    while ((idx = this._sseBuffer.indexOf('\n\n')) !== -1) {
      const event = this._sseBuffer.slice(0, idx);
      this._sseBuffer = this._sseBuffer.slice(idx + 2);
      this._handleSseEvent(event);
    }
  },

  /** 处理单个 SSE 事件（event: X + data: JSON） */
  _handleSseEvent(event) {
    const dataLine = event.split('\n').find((l) => l.startsWith('data:'));
    if (!dataLine) return;
    const raw = dataLine.slice(5).trim();
    if (!raw) return;
    let evt;
    try { evt = JSON.parse(raw); } catch (e) { return; }

    const type = evt.type;
    if (type === 'answer') {
      this._appendToCurrent(evt.data || '');
    } else if (type === 'sources') {
      this._setSources(evt.data || []);
    } else if (type === 'error') {
      this._appendToCurrent('（出错了：' + (evt.data || '请稍后重试') + '）');
      this._finishStream();
    } else if (type === 'end') {
      this._finishStream();
    }
  },

  /** 向当前 AI 消息追加文本分块 */
  _appendToCurrent(text) {
    const msgId = this._currentMsgId;
    const messages = this.data.messages.map((m) => {
      if (m.id === msgId) return { ...m, content: m.content + text };
      return m;
    });
    this.setData({ messages, scrollToView: msgId });
  },

  /** 为当前 AI 消息设置引用来源 */
  _setSources(sources) {
    const msgId = this._currentMsgId;
    const messages = this.data.messages.map((m) => {
      if (m.id === msgId) return { ...m, sources };
      return m;
    });
    this.setData({ messages });
  },

  /** 流式结束：清除 streaming 标记 */
  _finishStream() {
    const msgId = this._currentMsgId;
    const messages = this.data.messages.map((m) => {
      if (m.id === msgId) return { ...m, streaming: false };
      return m;
    });
    this.setData({ messages, sending: false });
  },

  /** 流式错误：断线提示重试（不自动重发，防重复扣费） */
  _onStreamError() {
    if (!this.data.sending) return;
    const msgId = this._currentMsgId;
    const messages = this.data.messages.map((m) => {
      if (m.id === msgId) return { ...m, content: m.content + '\n（网络中断，请点击重试）', streaming: false };
      return m;
    });
    this.setData({ messages, sending: false });
  },

  /** 键盘弹起/收起：输入区跟随抬升 */
  onKeyboardHeightChange(e) {
    this.setData({ keyboardHeight: e.detail.height });
  },

  /** 页面卸载时中止未完成流 */
  onUnload() {
    if (this._abortStream) {
      this._abortStream();
      this._abortStream = null;
    }
  }
});
