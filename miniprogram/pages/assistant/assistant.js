const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { POST_TYPE, STORAGE_KEY } = require('../../utils/constants');
// 微信同声传译插件（语音转文字），需在 app.json plugins 注册 + 小程序后台添加插件
const speechManager = requirePlugin('WechatSI').getRecordRecognitionManager();

/**
 * 小邻 — AI 智能助手对话页（独立单页面，无底部导航栏，对齐聊天页）。
 *
 * 入口：tabBar「小邻」经中转页 pages/assistant/assistant-entry 重定向进入。
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
    loadingHistory: false,
    /** 录音识别中（按住说话；输入模式切换已内聚到共享组件 chat-input-bar 内部） */
    recording: false,
    /** 上滑取消录音中 */
    recordCancelling: false,
    /** 录音时长（秒） */
    recordDuration: 0,
    /** 输入消息最大长度（对齐后端 @Size 校验；textarea maxlength 与语音转文字共用） */
    msgMaxLength: 500
  },

  /** 消息自增 id */
  _msgSeq: 0,
  /** SSE 原始 buffer（分块可能跨事件边界，需累积解析） */
  _sseBuffer: '',
  /** 流式中止函数 */
  _abortStream: null,
  /** 当前正在接收回复的消息 id */
  _currentMsgId: null,
  /** 上滑取消/手势中断的丢弃标记（置位后 onStop 丢弃识别结果，不发送） */
  _recordCancelled: false,

  onLoad() {
    // 主动进入对话页：清除"从对话页返回"标记，避免中转页误判为返回
    if (getApp().globalData) getApp().globalData.assistantReturning = false;
    if (!auth.ensureLoggedIn()) return;   // 仅登录门禁（审核中新住户也能用，与后端 unapproved 白名单一致）
    const sysInfo = wx.getSystemInfoSync();
    this.setData({ statusBarHeight: sysInfo.statusBarHeight || 20 });
    this.loadSuggestions();
    this.initSpeech();
  },

  // ==================== 语音输入（WechatSI 转文字） ====================

  /** 初始化语音识别回调（只注册一次） */
  initSpeech() {
    if (this._speechInited) return;
    this._speechInited = true;
    speechManager.onStop = (res) => {
      // 识别完成：文字直接发送（不进输入框）；截断到输入上限，避免后端 @Size 拒绝
      this.setData({ recording: false });
      // 上滑取消/手势中断置位后丢弃识别结果（对齐 chat 页 _recordCancelled），避免取消后仍误发
      if (this._recordCancelled) {
        this._recordCancelled = false;
        return;
      }
      const text = (res && res.result) || '';
      if (text && text.trim()) {
        this.sendMessage(text.trim().slice(0, this.data.msgMaxLength));
      } else {
        wx.showToast({ title: '未识别到内容', icon: 'none' });
      }
    };
    speechManager.onError = (err) => {
      // 记录错误码/信息便于定位（如 -2 未授权录音、-30003 数据传输失败等）
      console.error('[assistant] 语音识别失败:', err && err.retcode, err && err.msg);
      this.setData({ recording: false });
      wx.showToast({ title: '语音识别失败，请重试', icon: 'none' });
    };
  },

  /** 输入框失焦（键盘收起；组件内部已处理"发送后立即失焦需重聚焦"） */
  onInputBlur() {
    this.setData({ keyboardHeight: 0 });
  },

  /** 点消息区空白处：收起键盘 + 输入栏回落（对齐聊天页 onChatAreaTap） */
  onMsgAreaTap() {
    wx.hideKeyboard();
    this.setData({ keyboardHeight: 0 });
  },

  /** 按住开始录音识别（voice 模式按压区） */
  onVoiceStart(e) {
    if (this.data.sending) return;
    // 防重复触发录音（对齐 chat 页 onRecordStart；Android touch 事件偶现重复触发）
    if (this.data.recording) return;
    // 记录起始触摸 Y（上滑取消判断基准；触摸信息由组件透传在 e.detail）
    this._recordStartY = (e.detail.touches && e.detail.touches[0]) ? e.detail.touches[0].clientY : 0;
    this._recordCancelled = false;
    this.setData({ recording: true, recordCancelling: false, recordDuration: 0 });
    // start() 在多数 WechatSI 版本返回 Promise，但官方文档类型为 void（报错走 onError）；
    // 防御式处理两种返回类型：返回 Promise 时 catch 拒绝兜底，返回 void 时跳过链式调用避免 TypeError
    // 失败即无录音任务，不可再调 stop()（会报 -30012 当前无识别任务）
    const startResult = speechManager.start({ lang: 'zh_CN', duration: 60000 });
    if (startResult && typeof startResult.catch === 'function') {
      startResult.catch((err) => {
        console.warn('[assistant] 语音识别 start 被拒绝:', err && err.retcode, err && err.msg);
        clearInterval(this._recordTimer);
        this.setData({ recording: false, recordCancelling: false, recordDuration: 0 });
      });
    }
    // 录音计时（浮层显示秒数）
    this._recordTimer = setInterval(() => {
      this.setData({ recordDuration: this.data.recordDuration + 1 });
    }, 1000);
  },

  /** 松开停止录音：上滑取消则丢弃，否则识别并发送 */
  onVoiceEnd() {
    // 发送中被拦截未开启录音时（recording 为 false）不可调 stop()，否则报 -30012 无识别任务 → onError 弹"识别失败"提示（对齐 chat 页 onRecordEnd）
    if (!this.data.recording) return;
    clearInterval(this._recordTimer);
    const cancelled = this.data.recordCancelling;
    if (cancelled) {
      // 上滑取消：置丢弃标记 + 调 stop() 结束识别任务。只弹提示不调 stop() 会拖到 60s 超时，
      // 且 onStop 仍会把已识别文字误发（对齐 chat 页 onRecordEnd 的 _recordCancelled 丢弃机制）
      this._recordCancelled = true;
      this.setData({ recording: false, recordCancelling: false });
      wx.showToast({ title: '已取消', icon: 'none' });
      speechManager.stop();
      return;
    }
    this.setData({ recording: false, recordCancelling: false });
    speechManager.stop();   // 触发 onStop → 识别 → 直接发送
  },

  /** 取消（手势中断） */
  onVoiceCancel() {
    // 同 onVoiceEnd：未真正录音时跳过 stop()，避免无识别任务报错弹提示
    if (!this.data.recording) return;
    // 手势中断同样置丢弃标记，防止 onStop 误发（对齐 chat 页 onRecordCancel）
    this._recordCancelled = true;
    clearInterval(this._recordTimer);
    this.setData({ recording: false, recordCancelling: false });
    speechManager.stop();
  },

  /** 录音中移动：上滑超过阈值进入取消态（参考 chat 页；触摸信息由组件透传在 e.detail） */
  onVoiceTouchMove(e) {
    if (!this.data.recording) return;
    const y = (e.detail.touches && e.detail.touches[0]) ? e.detail.touches[0].clientY : 0;
    const cancelling = this._recordStartY > 0 && (this._recordStartY - y) > 80;
    if (cancelling !== this.data.recordCancelling) {
      this.setData({ recordCancelling: cancelling });
      // 进入取消态即置位丢弃标记（对齐 chat 页 onRecordTouchMove），松手时据此丢弃
      if (cancelling) this._recordCancelled = true;
    }
  },

  onShow() {
    if (!auth.ensureLoggedIn()) return; // 后台切回时兜底鉴权
    // 重新回到对话页（跳发布页返回等）：清除返回标记
    if (getApp().globalData) getApp().globalData.assistantReturning = false;
  },

  onHide() {
    // 对话页被覆盖/弹出（手势返回、‹ 按钮、跳发布页）：标记"从对话页返回"，让中转页决定去向
    if (getApp().globalData) getApp().globalData.assistantReturning = true;
  },

  /** 返回按钮：退回中转页（中转页据此回首页 tab），无栈时兜底直接切首页 */
  onNavBack() {
    wx.navigateBack({
      fail() {
        if (getApp().globalData) getApp().globalData.assistantReturning = false;
        wx.switchTab({ url: '/pages/home/home' });
      }
    });
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

  /** 点击终止图标：中止进行中的助手回复流 */
  onAbort() {
    if (!this.data.sending) return;   // 防连点重复触发
    if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
    if (this._abortStream) {
      this._abortStream();
      this._abortStream = null;
    }
    this._sseBuffer = '';
    const msgId = this._currentMsgId;
    const messages = this.data.messages.map((m) => {
      // aborted 标记由 WXML 渲染成灰色"（已终止）"行，不污染正文
      if (m.id === msgId) return { ...m, streaming: false, aborted: true };
      return m;
    });
    this.setData({ messages, sending: false });
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

    // 兜底看门狗：60s 内未收到 end/error 则强制结束流，避免后端挂起时永久卡在"回复中"
    if (this._streamWatchdog) clearTimeout(this._streamWatchdog);
    this._streamWatchdog = setTimeout(() => {
      if (!this.data.sending) return;
      if (this._abortStream) { this._abortStream(); this._abortStream = null; }
      const messages = this.data.messages.map((m) => {
        if (m.id === this._currentMsgId) {
          return { ...m, content: (m.content || '') + '\n（回复超时，请点击重试）', streaming: false };
        }
        return m;
      });
      this.setData({ messages, sending: false });
    }, 60000);

    this._abortStream = api.requestStream('/api/agent/chat', { message: msg },
      (chunk) => this._onSseChunk(chunk),
      () => this._onStreamError());
  },

  /** SSE 分块累积 + 按事件边界切分（兼容 \r\n 行尾，统一归一化） */
  _onSseChunk(chunk) {
    // 部分环境/代理用 \r\n 行尾，不归一化则 '\n\n' 分隔永远匹配不到
    this._sseBuffer += (chunk || '').replace(/\r\n/g, '\n');
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

  /** 流式结束：清除 streaming 标记 + 停用看门狗 */
  _finishStream() {
    if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
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
    if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
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

  /** 页面卸载时中止未完成流 + 清理看门狗与录音计时器 + 标记"从对话页返回" */
  onUnload() {
    // 对话页被关闭（手势返回 / ‹ 按钮返回中转页）：中转页据此回首页 tab
    if (getApp().globalData) getApp().globalData.assistantReturning = true;
    if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
    if (this._abortStream) {
      this._abortStream();
      this._abortStream = null;
    }
    if (this._recordTimer) {
      clearInterval(this._recordTimer);
      this._recordTimer = null;
    }
  }
});
