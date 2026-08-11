const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { POST_TYPE, STORAGE_KEY } = require('../../utils/constants');
const { parseMarkdown, stripMarkdown } = require('../../utils/markdown');
// 语音识别管理器实例：不在模块顶层 requirePlugin——微信官方要求插件在运行时动态加载，
// 插件环境未就绪（后台已添加但工具未启用插件调试/缓存未刷新）时顶层调用会让模块加载直接抛
// 「Plugin is not defined」导致整页打不开。改在 initSpeech（onLoad 阶段）动态加载 + try-catch 降级
let speechManager = null;

/** SSE 静默看门狗时长（毫秒）：收到任何分块即重置，仅当流真正静默超时才判超时 */
const WATCHDOG_SILENCE_MS = 45000;
/** 打字机逐段显示的间隔（毫秒） */
const SPREAD_INTERVAL_MS = 80;
/** 打字机每个 tick 批量应用的事件数（2 事件/tick，降低 setData 频次，规避小程序渲染压力） */
const SPREAD_BATCH = 2;
/** 打字机模拟总时长上限（毫秒）：预计模拟时长超过则放弃打字机、一次性全量渲染，避免超长回复慢慢蹦字像卡住 */
const MAX_SPREAD_MS = 2500;

/** 解析后端返回的 sources/actions JSON 字符串为数组（null/非法返回空数组） */
function parseJsonArray(str) {
  if (!str) return [];
  try {
    const v = JSON.parse(str);
    return Array.isArray(v) ? v : [];
  } catch (e) {
    return [];
  }
}

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
    /** 历史悬浮按钮位置（px，可长按拖动并记忆） */
    fabX: 0,
    fabY: 0,
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
    /** scroll-view 滚动位置（scroll-top 大数钳制到底部：比 scroll-into-view 更可靠地滚到含引用/来源的完整最底） */
    scrollTop: 0,
    /** 历史会话弹层可见性 */
    historyVisible: false,
    /** 历史列表 scroll-view 高度（px；0=未校准，由 calibrateHistoryHeight 动态计算，保证内容超出时可滚动） */
    historyListHeight: 0,
    /** 历史会话列表（后端返回字段 id 即会话级 conversationId；含 updatedAtText 显示字段） */
    historyList: [],
    /** 批量选择模式 */
    selecting: false,
    /** 已选会话 conversationId 集合（去重；同一会话多段只选一次） */
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
  /** 打字机逐段显示的定时器（中止/卸载时需清理） */
  _spreadTimer: null,
  /** scroll-top 自增序列（每次滚动值变化，确保每次都触发滚动到底部） */
  _scrollTick: 0,
  /** 上滑取消/手势中断的丢弃标记（置位后 onStop 丢弃识别结果，不发送） */
  _recordCancelled: false,

  onLoad() {
    // 主动进入对话页：清除"从对话页返回"标记，避免中转页误判为返回
    if (getApp().globalData) getApp().globalData.assistantReturning = false;
    if (!auth.ensureLoggedIn()) return;   // 仅登录门禁（审核中新住户也能用，与后端 unapproved 白名单一致）
    // 每次进入对话页 = 新会话：先归档上一轮残留热会话（后台杀小程序等未触发 onUnload 的场景，空闲 15 分钟调度器只是兜底），
    // 避免新消息被静默并进旧对话。fire-and-forget 不阻塞进入、失败不影响页面（对齐 code-standards 禁空 catch）
    api.post('/api/agent/exit').catch(() => {
      console.warn('[assistant] 进入时归档残留会话失败，后端将在空闲后兜底归档');
    });
    const sysInfo = wx.getSystemInfoSync();
    const statusBarHeight = sysInfo.statusBarHeight || 20;
    this.setData({ statusBarHeight });
    // 悬浮按钮拖动边界：X 限制在屏内；Y 上不超导航（statusBar+88rpx）、下不超输入栏（键盘收起时距底 124px）
    // 按钮尺寸 100rpx=50px（对齐首页 + 按钮）
    this._fabMinX = 8;
    this._fabMaxX = sysInfo.windowWidth - 58;
    this._fabMinY = statusBarHeight + 52;
    this._fabBaseMaxY = sysInfo.windowHeight - 124;
    this._fabMaxY = this._fabBaseMaxY;
    // 历史悬浮按钮初始位置：优先记忆值（钳制边界内），否则默认导航下方右上角（50=按钮 100rpx，8=边距）
    const saved = wx.getStorageSync(STORAGE_KEY.HISTORY_FAB_POS);
    const defX = sysInfo.windowWidth - 58;
    const defY = statusBarHeight + 60;
    this.setData({
      fabX: Math.min(this._fabMaxX, Math.max(this._fabMinX, (saved && typeof saved.x === 'number') ? saved.x : defX)),
      fabY: Math.min(this._fabMaxY, Math.max(this._fabMinY, (saved && typeof saved.y === 'number') ? saved.y : defY))
    });
    // 探测 enableChunked 分块通道（幂等，只跑一次）：确认可用后才启用分块流式，
    // 默认非分块直发保证消息只发一次、绝不因降级重试触发服务端重复处理
    api.probeChunked();
    this.loadSuggestions();
    this.initSpeech();
  },

  // ==================== 语音输入（WechatSI 转文字） ====================

  /** 初始化语音识别回调（只注册一次） */
  initSpeech() {
    if (this._speechInited) return;
    this._speechInited = true;
    // 运行时动态加载微信同声传译插件（语音转文字）：模块顶层不调用 requirePlugin
    if (!speechManager) {
      try {
        speechManager = requirePlugin('WechatSI').getRecordRecognitionManager();
      } catch (e) {
        console.warn('[assistant] WechatSI 插件不可用，语音输入已降级:', e && e.message);
      }
    }
    // 插件未启用时跳过回调注册，避免对 null 调用 onStop/onError 抛 TypeError
    if (!speechManager) return;
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

  /**
   * 滚到最底部：scroll-top 用单调递增的大数，超过内容高度即被钳制到真正最底
   * （含底部 padding 间隙），保证气泡最后一行与下方的引用/来源都完整顶到输入框上方。
   * 比 scroll-into-view 更可靠——不依赖锚点定位，直接钳制到最大滚动位置。
   */
  scrollToBottom() {
    this.setData({ scrollTop: 99999 + (++this._scrollTick) });
  },

  /**
   * 消息内容更新并同步重算 AI 回复的 Markdown 块（用户消息不渲染块，仅保留原字段）。
   * 流式片段可能未闭合，解析器按段落兜底；流结束后 replace 事件整体刷新为干净排版。
   *
   * @param {object} m       消息对象
   * @param {string} content 更新后的内容（Markdown 或纯文本）
   * @return {object} 更新 content 与 blocks 后的消息
   */
  _withContent(m, content) {
    return { ...m, content, blocks: m.role === 'assistant' ? parseMarkdown(content) : (m.blocks || []) };
  },

  /** 按住开始录音识别（voice 模式按压区） */
  onVoiceStart(e) {
    if (this.data.sending) return;
    // 插件未启用时语音不可用：给提示而非对 null speechManager 调用 start 抛错
    if (!speechManager) {
      wx.showToast({ title: '语音功能未启用', icon: 'none' });
      return;
    }
    // 防重复触发录音（对齐 chat 页 onRecordStart；Android touch 事件偶现重复触发）
    if (this.data.recording) return;
    // 先申请录音权限再开始：未授权时微信不会录音，start 会走 onError（-2 未授权）
    this._ensureRecordAuth().then((granted) => {
      if (granted) this._beginRecord(e);
    });
  },

  /**
   * 检查并申请麦克风权限（scope.record）。
   *
   * <p>首次请求弹系统授权框；已拒绝过则引导去设置页开启。
   * getSetting 异常时不阻断（由 start 的 onError 兜底提示）。</p>
   *
   * @return {Promise<boolean>} 是否已获得录音权限
   */
  _ensureRecordAuth() {
    return new Promise((resolve) => {
      wx.getSetting({
        success: (res) => {
          if (res.authSetting['scope.record']) {
            resolve(true);
            return;
          }
          wx.authorize({
            scope: 'scope.record',
            success: () => resolve(true),
            fail: () => {
              wx.showModal({
                title: '需要麦克风权限',
                content: '语音输入需要麦克风权限，请在设置中开启',
                confirmText: '去设置',
                success: (r) => {
                  if (r.confirm) wx.openSetting();
                },
              });
              resolve(false);
            },
          });
        },
        fail: () => resolve(true),
      });
    });
  },

  /** 获得录音权限后真正开始录音识别 */
  _beginRecord(e) {
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

  /**
   * 返回手势/返回键处理（Android 系统返回键 + iOS 侧滑返回手势）：
   * 历史弹层打开时先关闭弹层并返回 true 拦截路由回退——避免弹层（自定义全屏覆盖层，
   * 微信不感知）开着时返回手势被当成根页面返回而直接退出小程序；
   * 弹层关闭状态交还系统正常 pop 回中转页（中转页据 assistantReturning 切回首页 tab）。
   */
  onBackPress() {
    if (this.data.historyVisible) {
      this.onCloseHistory();
      return true;
    }
    return false;
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
    if (this._spreadTimer) { clearTimeout(this._spreadTimer); this._spreadTimer = null; }
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
    const aiMsg = { id: 'a' + (++this._msgSeq), role: 'assistant', content: '', blocks: [], sources: [], streaming: true, failed: false, retryText: msg };
    const messages = this.data.messages.concat([userMsg, aiMsg]);
    this._currentMsgId = aiMsg.id;
    this._sseBuffer = '';
    this.setData({ messages, inputText: '', sending: true });
    // 滚到底部锚点（对齐聊天页 scrollToBottom：等渲染完再滚，长消息不被挤出屏外）
    this.scrollToBottom();

    // 静默看门狗：收到任何 SSE 分块即重置，仅当流真正静默超时才判超时（见 _onSseChunk）
    this._armStreamWatchdog();

    this._abortStream = api.requestStream('/api/agent/chat', { message: msg },
      (chunk, isFallback) => this._onSseChunk(chunk, isFallback),
      (err) => this._onStreamError(err));
  },

  /**
   * 挂载/重置静默看门狗：每次收到流数据都会重新计时，
   * 只有连续 WATCHDOG_SILENCE_MS 毫秒没有任何分块到达才判超时，避免误杀正常慢流。
   */
  _armStreamWatchdog() {
    if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
    this._streamWatchdog = setTimeout(() => {
      if (!this.data.sending) return;
      if (this._abortStream) { this._abortStream(); this._abortStream = null; }
      const msgId = this._currentMsgId;
      const messages = this.data.messages.map((m) => {
        if (m.id === msgId) {
          return { ...this._withContent(m, (m.content || '') + '\n（回复超时）'), streaming: false, failed: true };
        }
        return m;
      });
      this.setData({ messages, sending: false });
    }, WATCHDOG_SILENCE_MS);
  },

  /**
   * SSE 分块累积 + 按事件边界切分（兼容 \r\n 行尾，统一归一化）。
   *
   * @param chunk      SSE 文本分块
   * @param isFallback 是否非分块全量体（api.js 传入）：true=整段响应一次到达（开发者工具环境），
   *                   false=真机原生分块流式。只有前者且事件足够多才走打字机模拟，
   *                   避免真机 TCP 粘包（原生分块多事件一次到达）被误判成回落模式
   */
  _onSseChunk(chunk, isFallback) {
    // 部分环境/代理用 \r\n 行尾，不归一化则 '\n\n' 分隔永远匹配不到
    this._sseBuffer += (chunk || '').replace(/\r\n/g, '\n');
    const events = [];
    let idx;
    while ((idx = this._sseBuffer.indexOf('\n\n')) !== -1) {
      events.push(this._sseBuffer.slice(0, idx));
      this._sseBuffer = this._sseBuffer.slice(idx + 2);
    }
    if (events.length === 0) return;

    // 打字机模拟仅用于非分块全量体：事件足够多（>3，问候/拦截只有 3 个事件）
    // 且预计模拟时长未超上限（超长回复直接全量渲染，避免慢慢蹦字像卡住）
    const spreadTicks = Math.ceil(events.length / SPREAD_BATCH);
    const spreadTooLong = spreadTicks * SPREAD_INTERVAL_MS > MAX_SPREAD_MS;
    if (isFallback && events.length > 3 && !spreadTooLong) {
      this._spreadSseEvents(events);
    } else {
      this._applySseEvents(events);
    }
    // 收到任何数据 = 流存活，重置静默看门狗（流结束后不重挂，避免悬空定时器）
    if (this.data.sending) this._armStreamWatchdog();
  },

  /**
   * 批量应用一组 SSE 事件：先累积到局部数组，末尾一次性 setData——
   * 避免非分块全量响应体到达时逐事件 setData 造成渲染风暴（多个气泡内容被渲染成同一份）。
   *
   * @param events SSE 事件文本数组
   */
  _applySseEvents(events) {
    let pending = this.data.messages;
    let mutated = false;
    let needScroll = false;
    let finished = false;
    for (const event of events) {
      const result = this._applySseEvent(event, pending);
      if (result) {
        pending = result.messages;
        mutated = true;
        if (result.scroll) needScroll = true;
        if (result.finish) finished = true;
      }
    }
    if (mutated || finished) {
      const payload = {};
      if (mutated) {
        payload.messages = pending;
      }
      if (finished) {
        if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
        payload.sending = false;
      }
      this.setData(payload);
      // 滚到底部锚点而非消息 id：长回复增长时底部新内容始终可见（对齐聊天页 scrollToBottom）
      if (needScroll) this.scrollToBottom();
    }
  },

  /**
   * 打字机逐段应用一组 SSE 事件（仅非分块全量体场景，由 {@link _onSseChunk} 判定）：
   * 每 {@link SPREAD_BATCH} 个事件经定时器间隔合并一次 setData——间隔分散在各事件循环 turn，
   * 不构成渲染风暴，回复文本逐段出现，模拟真流式观感。end 事件仍负责收尾（sending=false、清看门狗）。
   *
   * @param events SSE 事件文本数组（同一批到达，含大量 answer）
   */
  _spreadSseEvents(events) {
    // 防御：若存在上一轮残留打字机定时器先清掉（sending 门禁已防并发，双保险防竞态）
    if (this._spreadTimer) { clearTimeout(this._spreadTimer); this._spreadTimer = null; }
    let i = 0;
    let batchCount = 0;
    const applyNext = () => {
      // 中止/离开后（sending=false）不再继续显示，避免残留定时器继续 setData
      if (!this.data.sending && i > 0) return;
      let pending = this.data.messages;
      let mutated = false;
      let needScroll = false;
      let finished = false;
      for (let k = 0; k < SPREAD_BATCH && i < events.length; k++, i++) {
        const result = this._applySseEvent(events[i], pending);
        if (result) {
          pending = result.messages;
          mutated = true;
          if (result.scroll) needScroll = true;
          if (result.finish) finished = true;
        }
      }
      if (mutated || finished) {
        const payload = {};
        if (mutated) payload.messages = pending;
        if (finished) {
          if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
          payload.sending = false;
        }
        this.setData(payload);
        // 节流：打字机每 2 批滚一次（减少动画打断），流结束那次必滚保证最终位置到底
        if (needScroll && (finished || batchCount % 2 === 0)) this.scrollToBottom();
      }
      batchCount++;
      if (i < events.length && !finished) {
        this._spreadTimer = setTimeout(applyNext, SPREAD_INTERVAL_MS);
      } else {
        this._spreadTimer = null;
      }
    };
    applyNext();
  },

  /**
   * 处理单个 SSE 事件，返回更新后的消息数组（不直接 setData，供 {@link _onSseChunk} 批量累积）。
   *
   * @param event    SSE 事件文本（event: X + data: JSON）
   * @param messages 当前消息数组
   * @return {null | {messages, scroll?, finish?}} 更新后的数组与标记；无法解析返回 null
   */
  _applySseEvent(event, messages) {
    const dataLine = event.split('\n').find((l) => l.startsWith('data:'));
    if (!dataLine) return null;
    const raw = dataLine.slice(5).trim();
    if (!raw) return null;
    let evt;
    try { evt = JSON.parse(raw); } catch (e) { return null; }

    const msgId = this._currentMsgId;
    const type = evt.type;
    const mapCurrent = (fn) => messages.map((m) => (m.id === msgId ? fn(m) : m));

    if (type === 'answer') {
      return { messages: mapCurrent((m) => this._withContent(m, m.content + (evt.data || ''))), scroll: true };
    } else if (type === 'replace') {
      // 后端在流结束后剔除写操作意图 JSON，用干净文案整体替换气泡内容（重算 Markdown 块）
      return { messages: mapCurrent((m) => this._withContent(m, evt.data || '')) };
    } else if (type === 'sources') {
      // 展示层去重：同一来源文档只保留一条。source 可能带小区名前缀（如"翠湖花园-平台使用帮"与"平台使用帮"是同一文档），
      // 按去掉前缀后的规范化名去重，并保留更具体（更长）的 source 展示
      const srcs = this._dedupSources(evt.data || []);
      return { messages: mapCurrent((m) => ({ ...m, sources: srcs })) };
    } else if (type === 'action') {
      return { messages: mapCurrent((m) => ({ ...m, actions: evt.data || [] })) };
    } else if (type === 'clear') {
      // 后端清空会话：前端同步清空消息列表，回到欢迎态（answer 确认文案同批被清掉，无碍）
      return { messages: [] };
    } else if (type === 'error') {
      return {
        messages: mapCurrent((m) => ({
          ...this._withContent(m, m.content + '（出错了：' + (evt.data || '请稍后重试') + '）'),
          streaming: false, failed: true
        })),
        scroll: true, finish: true
      };
    } else if (type === 'end') {
      return { messages: mapCurrent((m) => ({ ...m, streaming: false })), scroll: true, finish: true };
    }
    return null;
  },

  /** 来源名规范化：去掉开头的"小区名-"前缀（如"翠湖花园-平台使用帮"→"平台使用帮"），用于同文档不同 source 串去重 */
  _normalizeSource(s) {
    return (s || '').replace(/^[^-]+-/, '');
  },

  /**
   * 按规范化来源名去重，保留更具体（更长）的 source 展示。
   * 后端按知识条目 id 去重拦不住同文档多切片；source 可能带小区名前缀导致精确串不同，故去前缀后比较。
   *
   * @param list 原始来源数组
   * @return 去重后的来源数组
   */
  _dedupSources(list) {
    const byKey = {};
    const result = [];
    for (const s of list) {
      if (!s || s.source == null) {
        result.push(s);
        continue;
      }
      const key = this._normalizeSource(s.source);
      const cur = byKey[key];
      if (!cur) {
        byKey[key] = s;
        result.push(s);
      } else if ((s.source || '').length > (cur.source || '').length) {
        // 更具体的来源替换当前展示（保持原顺序位置）
        const idx = result.indexOf(cur);
        result[idx] = s;
        byKey[key] = s;
      }
    }
    return result;
  },

  /** 点击动作卡片：写操作暂存草稿跳转预填；发布指引（goto_publish）清草稿、跳对应 tab 不预填 */
  onActionTap(e) {
    const action = e.currentTarget.dataset.action;
    if (!action || !action.type) return;
    // 发布指引快捷跳转：清掉残留草稿、按 params.type 跳对应 tab（无预填，用户自己填），默认闲置借出
    if (action.type === 'goto_publish') {
      try { wx.removeStorageSync(STORAGE_KEY.AGENT_DRAFT); } catch (e) { /* 忽略清除失败 */ }
      const jumpMap = { help: POST_TYPE.HELP, idle: POST_TYPE.LEND, wanted: POST_TYPE.WANTED };
      const t = (action.params && jumpMap[action.params.type]) || POST_TYPE.LEND;
      wx.navigateTo({ url: '/pages/publish-idle/publish-idle?type=' + t });
      return;
    }
    // 写操作：暂存 AI 生成的草稿参数（发布页 onLoad 读取预填，读后清空）
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

  /** 流式错误：断线提示重试（不自动重发，防重复扣费；透出真实错误便于定位） */
  _onStreamError(err) {
    if (!this.data.sending) return;
    if (this._streamWatchdog) { clearTimeout(this._streamWatchdog); this._streamWatchdog = null; }
    // wx.request fail 的错误只有 errMsg 无 message：区分 timeout / 域名白名单，避免都兜底成"网络中断"
    const raw = err || {};
    const errMsg = raw.errMsg || '';
    const reason = raw.message
      ? raw.message
      : errMsg.indexOf('timeout') !== -1 ? '请求超时，请检查网络'
      : errMsg.indexOf('url not in domain list') !== -1 ? '当前网络环境无法连接服务器'
      : '网络中断';
    console.error('[assistant] SSE 流错误:', err);
    const msgId = this._currentMsgId;
    const messages = this.data.messages.map((m) => {
      if (m.id === msgId) {
        return { ...this._withContent(m, m.content + '\n（回复失败：' + reason + '）'), streaming: false, failed: true };
      }
      return m;
    });
    this.setData({ messages, sending: false });
  },

  /** 点击失败气泡下方的重试入口：原问题重发（不自动重发，仅用户显式触发） */
  onRetryTap(e) {
    const msg = e.currentTarget.dataset.msg;
    if (!msg || this.data.sending) return;
    this.sendMessage(msg);
  },

  /** 长按消息：弹出复制选项，复制成功后提示（内容为空不弹；AI 回复复制时去掉 Markdown 标记为纯文本） */
  onMsgLongPress(e) {
    const content = (e.currentTarget.dataset && e.currentTarget.dataset.content) || '';
    const role = e.currentTarget.dataset.role;
    if (!content) return;
    wx.showActionSheet({
      itemList: ['复制'],
      success: (res) => {
        if (res.tapIndex !== 0) return;
        wx.setClipboardData({
          data: role === 'assistant' ? stripMarkdown(content) : content,
          success: () => wx.showToast({ title: '复制成功', icon: 'success' })
        });
      }
    });
  },

  /** 键盘弹起/收起：输入区跟随抬升；键盘弹起时滚到底，避免最新消息被键盘盖住；悬浮按钮下边界随键盘上移 */
  onKeyboardHeightChange(e) {
    this.setData({ keyboardHeight: e.detail.height });
    if (e.detail.height > 0) this.scrollToBottom();
    // 键盘顶起输入栏时，悬浮按钮最大 Y 随之上移，避免被键盘/输入框盖住；超界则回弹
    this._fabMaxY = this._fabBaseMaxY - e.detail.height;
    if (this.data.fabY > this._fabMaxY) {
      this.setData({ fabY: this._fabMaxY });
    }
  },

  // ==================== 历史会话交互 ====================

  /** 历史悬浮按钮拖动开始 */
  onFabTouchStart(e) {
    const t = e.touches[0];
    this._fabDrag = { startX: t.clientX, startY: t.clientY, baseX: this.data.fabX, baseY: this.data.fabY, moved: false };
  },

  /** 拖动中：实时更新位置并钳制在边界内（左右屏内回弹、上不超导航、下不超输入栏） */
  onFabTouchMove(e) {
    if (!this._fabDrag) return;
    const t = e.touches[0];
    const dx = t.clientX - this._fabDrag.startX;
    const dy = t.clientY - this._fabDrag.startY;
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) this._fabDrag.moved = true;
    const nextX = Math.min(this._fabMaxX, Math.max(this._fabMinX, this._fabDrag.baseX + dx));
    const nextY = Math.min(this._fabMaxY, Math.max(this._fabMinY, this._fabDrag.baseY + dy));
    this.setData({ fabX: nextX, fabY: nextY });
  },

  /** 拖动结束：移动则记忆位置；未移动视为轻点 → 打开历史弹层 */
  onFabTouchEnd() {
    if (!this._fabDrag) return;
    const dragged = this._fabDrag.moved;
    if (dragged) {
      try {
        wx.setStorageSync(STORAGE_KEY.HISTORY_FAB_POS, { x: this.data.fabX, y: this.data.fabY });
      } catch (e) { /* 存储失败不阻断（位置仅本次会话有效） */ }
    }
    this._fabDrag = null;
    if (!dragged) this.onHistoryTap();
  },

  /** 打开历史弹层：加载历史会话列表 */
  onHistoryTap() {
    if (this.data.sending) return;
    this.loadHistory();
    this.setData({ historyVisible: true, selecting: false, selectedIds: [] });
  },

  /** 阻止遮罩层触摸事件穿透（历史弹层打开时配合 msg-area scroll-y=false，避免手势竞争） */
  preventTouchMove() {},

  /** 关闭历史弹层 */
  onCloseHistory() {
    this.setData({ historyVisible: false, selecting: false, selectedIds: [] });
  },

  /**
   * 校准历史列表滚动区高度（px）：测量 scroll-view 内容总高，
   * 目标高度 = min(内容高, 屏幕50% - header)，给 scroll-view 明确 px 高度保证可滚动。
   * 不依赖 flex 高度约束——部分真机对 fixed + flex + max-height 的 scroll-view 高度计算不可靠。
   */
  calibrateHistoryHeight() {
    const query = wx.createSelectorQuery().in(this);
    query.select('#historyScroll').fields({ size: true, scrollOffset: true }).exec((res) => {
      const info = res && res[0];
      if (!info) return;
      const contentH = info.scrollHeight || info.height || 0;
      if (!contentH) return;
      const win = wx.getSystemInfoSync();
      // 上限：屏幕 50% 减 header 估算高度（header 约 100rpx）
      const headerPx = (100 * win.windowWidth) / 750;
      const maxH = Math.max(win.windowHeight * 0.5 - headerPx, 120);
      const target = Math.round(Math.min(contentH, maxH));
      this.setData({ historyListHeight: target });
    });
  },

  /** 加载历史会话列表（分页 20，reset=true 重置第一页） */
  loadHistory(reset = true) {
    if (this.data.loadingHistory) return;
    const page = reset ? 0 : this.data.historyPage + 1;
    this.setData({ loadingHistory: true });
    api.get('/api/agent/history', { page, size: 20 })
      .then((data) => {
        const content = (data && data.content) || [];
        // 展开保留后端全部字段（id 字段即会话级 conversationId）；空标题兜底显示占位
        const items = content.map((c) => ({
          ...c,
          title: c.title || '未命名对话',
          // 更新时间格式化为可读（后端 LocalDateTime，取前 16 位）
          updatedAtText: (c.updatedAt || '').replace('T', ' ').slice(0, 16)
        }));
        this.setData({
          historyList: reset ? items : this.data.historyList.concat(items),
          historyPage: page,
          historyTotal: (data && data.totalElements) || 0
        }, () => {
          // 首屏数据渲染完成后校准滚动区高度（给出明确 px 高度，内容超出时才能滚动）
          if (reset) this.calibrateHistoryHeight();
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

  /** 点击历史项：恢复该会话继续对话（非批量选择模式；dataset.id 为会话级 conversationId） */
  onHistoryItemTap(e) {
    if (this.data.selecting) {
      this.toggleSelect(e.currentTarget.dataset.id);
      return;
    }
    const conversationId = e.currentTarget.dataset.id;
    // 当前有进行中对话时二次确认（后端恢复前会自动归档当前热会话，前端避免误触）
    if (this.data.messages.length > 0) {
      wx.showModal({
        title: '切换对话',
        content: '切换到该历史对话？当前对话将自动保存',
        success: (r) => {
          if (r.confirm) this.doResume(conversationId);
        }
      });
      return;
    }
    this.doResume(conversationId);
  },

  /** 点击历史项右侧「切换」：弹出确认后切换至该对话（catchtap 防止触发整行 onHistoryItemTap） */
  onSwitchTap(e) {
    const conversationId = e.currentTarget.dataset.id;
    wx.showModal({
      title: '切换对话',
      content: '是否要切换至该对话？当前对话将自动保存',
      confirmText: '切换',
      confirmColor: '#0071E3',
      success: (r) => {
        if (r.confirm) this.doResume(conversationId);
      }
    });
  },

  /** 点击历史项右侧「删除」图标：弹确认后删除该会话（catchtap 防止触发整行 onHistoryItemTap） */
  onDeleteTap(e) {
    const conversationId = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除确认',
      content: '删除该对话？删除后不可恢复',
      confirmText: '删除',
      confirmColor: '#FF3B30',
      success: (r) => {
        if (!r.confirm) return;
        api.del('/api/agent/history', { ids: [conversationId] })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            this.loadHistory();
          })
          .catch((err) => wx.showToast({ title: err.message || '删除失败', icon: 'none' }));
      }
    });
  },

  /** 执行恢复：按会话级 conversationId 恢复（后端返回回填的最近消息，前端渲染对话内容） */
  doResume(conversationId) {
    api.post(`/api/agent/history/${conversationId}/resume`)
      .then((data) => {
        // 后端返回恢复的最近消息（sources/actions 为 JSON 字符串或 null），转成前端消息结构展示
        const items = (data && data.messages) || [];
        const messages = items.map((m) => ({
          id: 'r' + (++this._msgSeq),
          role: m.role === 'assistant' ? 'assistant' : 'user',
          content: m.content || '',
          // 恢复的 AI 回复同样按 Markdown 块渲染（用户消息不渲染块）
          blocks: m.role === 'assistant' ? parseMarkdown(m.content || '') : [],
          sources: parseJsonArray(m.sources),
          actions: parseJsonArray(m.actions),
          streaming: false,
          failed: false
        }));
        // 恢复后是全新会话上下文：清空流式状态
        this._currentMsgId = null;
        this._sseBuffer = '';
        this.setData({ messages, historyVisible: false });
        if (messages.length > 0) {
          wx.showToast({ title: '已切换到该对话', icon: 'none' });
          this.scrollToBottom();
        }
      })
      .catch((err) => wx.showToast({ title: err.message || '切换失败', icon: 'none' }));
  },

  /** 长按进入批量选择模式 */
  onHistoryLongPress() {
    if (this.data.historyList.length === 0) return;
    this.setData({ selecting: true, selectedIds: [] });
  },

  /** 批量选择切换：以会话级 conversationId 为单位（indexOf 去重，同一会话多段只选一次） */
  toggleSelect(conversationId) {
    const selected = this.data.selectedIds.slice();
    const idx = selected.indexOf(conversationId);
    if (idx > -1) selected.splice(idx, 1);
    else selected.push(conversationId);
    this.setData({ selectedIds: selected });
  },

  /** 批量删除所选会话：按会话级 conversationId 删除（后端删除该会话全部段 + 压缩段） */
  onBatchDelete() {
    // selectedIds 已按会话去重，此处再兜底 Set 去重，保证传给后端的是去重后的会话级 id 集合
    const ids = [...new Set(this.data.selectedIds)];
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
    if (this._spreadTimer) { clearTimeout(this._spreadTimer); this._spreadTimer = null; }
    if (this._abortStream) {
      this._abortStream();
      this._abortStream = null;
    }
    if (this._recordTimer) {
      clearInterval(this._recordTimer);
      this._recordTimer = null;
    }
    // fire-and-forget 通知后端结束会话并归档剩余对话（空闲 15 分钟只是兜底）。
    // 不等待、不阻塞、不提示：失败不影响页面关闭，仅留 warn 便于定位（对齐 code-standards 禁空 catch）
    api.post('/api/agent/exit').catch(() => {
      console.warn('[assistant] 退出会话通知失败，后端将在空闲后兜底归档');
    });
  }
});
