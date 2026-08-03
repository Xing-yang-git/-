const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { POST_TYPE, STORAGE_KEY } = require('../../utils/constants');

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
    /** 消息列表：{id, role: user|assistant, content, sources, actions, streaming} */
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
    scrollToView: 'msg-bottom',
    /** 历史会话弹层可见性 */
    historyVisible: false,
    /** 历史会话列表（含 updatedAtText 显示字段） */
    historyList: [],
    /** 批量选择模式 */
    selecting: false,
    /** 已选会话 ID 集合 */
    selectedIds: [],
    /** 历史分页页码 */
    historyPage: 0,
    /** 历史总数（分页判断） */
    historyTotal: 0,
    /** 历史加载中（防重复请求） */
    loadingHistory: false
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
    } else if (type === 'action') {
      this._setActions(evt.data || []);
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

  /** 为当前 AI 消息设置动作卡片（写操作，需用户确认） */
  _setActions(actions) {
    const msgId = this._currentMsgId;
    const messages = this.data.messages.map((m) => {
      if (m.id === msgId) return { ...m, actions };
      return m;
    });
    this.setData({ messages });
  },

  /** 点击动作卡片：暂存发布草稿 → 跳转发布页预填 */
  onActionTap(e) {
    const action = e.currentTarget.dataset.action;
    if (!action || !action.type) return;
    // 暂存 AI 生成的草稿参数（发布页 onLoad 读取预填，读后清空）
    try {
      wx.setStorageSync(STORAGE_KEY.AGENT_DRAFT, { type: action.type, params: action.params || {} });
    } catch (e) {
      // 存储写满等异常不阻断跳转（草稿丢失不致命，发布页手填即可）
      console.warn('[assistant] 暂存发布草稿失败:', e);
    }
    // 映射动作类型到发布页 postType 参数
    const typeMap = {
      publish_help: POST_TYPE.HELP,
      publish_idle: POST_TYPE.LEND,
      publish_wanted: POST_TYPE.WANTED
    };
    const t = typeMap[action.type] || POST_TYPE.LEND;
    wx.navigateTo({ url: '/pages/publish-idle/publish-idle?type=' + t });
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

  // ==================== 历史会话交互 ====================

  /** 打开历史弹层：加载历史会话列表 */
  onHistoryTap() {
    if (this.data.sending) return;
    this.loadHistory();
    this.setData({ historyVisible: true, selecting: false, selectedIds: [] });
  },

  /** 关闭历史弹层 */
  onCloseHistory() {
    this.setData({ historyVisible: false, selecting: false, selectedIds: [] });
  },

  /** 加载历史会话列表（分页 20，reset=true 重置第一页） */
  loadHistory(reset = true) {
    if (this.data.loadingHistory) return;
    const page = reset ? 0 : this.data.historyPage + 1;
    this.setData({ loadingHistory: true });
    api.get('/api/agent/history', { page, size: 20 })
      .then((data) => {
        const content = (data && data.content) || [];
        // 更新时间格式化为可读（后端 LocalDateTime，取前 16 位）
        const items = content.map((c) => ({
          ...c,
          updatedAtText: (c.updatedAt || '').replace('T', ' ').slice(0, 16)
        }));
        this.setData({
          historyList: reset ? items : this.data.historyList.concat(items),
          historyPage: page,
          historyTotal: (data && data.totalElements) || 0
        });
      })
      .catch(() => {
        if (reset) this.setData({ historyList: [] });
      })
      .finally(() => this.setData({ loadingHistory: false }));
  },

  /** 历史列表滚动到底部：加载更多 */
  onHistoryScrollLower() {
    if (this.data.historyList.length < this.data.historyTotal) {
      this.loadHistory(false);
    }
  },

  /** 点击历史项：恢复该会话继续对话（非批量选择模式） */
  onHistoryItemTap(e) {
    if (this.data.selecting) {
      this.toggleSelect(e.currentTarget.dataset.id);
      return;
    }
    const id = e.currentTarget.dataset.id;
    // 当前有进行中对话时二次确认（后端恢复前会自动归档当前热会话，前端避免误触）
    if (this.data.messages.length > 0) {
      wx.showModal({
        title: '切换对话',
        content: '切换到该历史对话？当前对话将自动保存',
        success: (r) => {
          if (r.confirm) this.doResume(id);
        }
      });
      return;
    }
    this.doResume(id);
  },

  /** 执行恢复 */
  doResume(id) {
    api.post(`/api/agent/history/${id}/resume`)
      .then(() => {
        // 恢复成功：清空当前对话（历史上下文在服务端回填），提示后重新进入
        this.setData({ messages: [], historyVisible: false });
        wx.showToast({ title: '已恢复上次对话', icon: 'none' });
      })
      .catch((err) => wx.showToast({ title: err.message || '恢复失败', icon: 'none' }));
  },

  /** 长按进入批量选择模式 */
  onHistoryLongPress() {
    if (this.data.historyList.length === 0) return;
    this.setData({ selecting: true, selectedIds: [] });
  },

  /** 批量选择切换 */
  toggleSelect(id) {
    const selected = this.data.selectedIds.slice();
    const idx = selected.indexOf(id);
    if (idx > -1) selected.splice(idx, 1);
    else selected.push(id);
    this.setData({ selectedIds: selected });
  },

  /** 批量删除所选会话 */
  onBatchDelete() {
    const ids = this.data.selectedIds;
    if (ids.length === 0) {
      wx.showToast({ title: '请先选择会话', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '删除确认',
      content: `删除所选 ${ids.length} 个会话？删除后不可恢复`,
      confirmColor: '#FF3B30',
      success: (r) => {
        if (!r.confirm) return;
        api.del('/api/agent/history', { ids })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            this.setData({ selecting: false, selectedIds: [] });
            this.loadHistory();
          })
          .catch((err) => wx.showToast({ title: err.message || '删除失败', icon: 'none' }));
      }
    });
  },

  /** 页面卸载时中止未完成流 */
  onUnload() {
    if (this._abortStream) {
      this._abortStream();
      this._abortStream = null;
    }
  }
});
