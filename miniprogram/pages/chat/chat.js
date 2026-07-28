const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { POST_TYPE } = require('../../utils/constants');

const app = getApp();

const CACHE_PREFIX = 'chat_cache_';

/**
 * 聊天页 — 一对一即时通讯。
 *
 * 功能：WebSocket 实时消息收发、消息历史加载、消息撤回（2分钟内）、
 *        本地缓存加速、下拉加载历史消息。
 */
Page({
  data: {
    statusBarHeight: 0,
    sessionId: '',
    otherName: '',
    roomInfo: '',
    aboutTitle: '',
    aboutId: '',
    aboutType: '',
    otherUserId: '',
    userId: '',
    messages: [],
    inputText: '',
    inputFocus: 0,
    scrollToView: '',
    keyboardHeight: 0,
    chatPaddingBottom: 70,
    oldestId: null,
    hasMore: true,
    loadingHistory: false,
    loadingOlder: false,
    // 输入模式
    inputMode: 'text',
    // 录音状态
    recording: false,
    recordDuration: 0,
    recordCancelling: false,
    vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0,
    // 播放状态
    playingId: null,
    // 悬浮菜单
    showMsgMenu: false,
    menuX: 0, menuY: 0,
    menuItems: [],
    menuTarget: null
  },

  onLoad(options) {
    if (!auth.ensureAccess()) return;

    const nameParam = decodeURIComponent(options.name || '用户');
    const roomInfo = decodeURIComponent(options.room || '');
    const aboutTitle = decodeURIComponent(options.about || '');
    const aboutId = options.aboutId || '';
    const aboutType = options.aboutType || '';
    const otherUserId = options.otherUserId || '';
    const userId = auth.getUserId() || (wx.getStorageSync('userInfo') || {}).id || '';

    const ids = [String(userId), String(otherUserId)].sort();
    const sessionId = (userId && otherUserId) ? ('USER_' + ids[0] + '_' + ids[1]) : (options.sessionId || '');

    const sysInfo = wx.getSystemInfoSync();
    const statusBarHeight = sysInfo.statusBarHeight || 20;

    const hasAddress = /[栋号楼]/.test(nameParam);
    const displayName = hasAddress ? nameParam : (roomInfo || nameParam);

    this.setData({ statusBarHeight, sessionId, otherName: displayName, roomInfo, aboutTitle, aboutId, aboutType, otherUserId, userId });

    if (sessionId && userId) {
      const metaKey = 'chat_meta_' + userId + '_' + sessionId;
      wx.setStorageSync(metaKey, {
        otherName: displayName, room: roomInfo, about: aboutTitle,
        aboutId: aboutId, aboutType: aboutType, otherUserId: otherUserId
      });
    }

    this.loadCache();
    this.loadHistory();
    app.ensureWebSocket();

    if (sessionId) {
      app.markSessionRead(sessionId);
    }

    // WS 回调 — 处理新消息 + 撤回
    this._wsHandler = (newMsg) => {
      if (newMsg.type === 'recall') {
        const messages = this.data.messages.map(m =>
          m.id === newMsg.id ? { ...m, recalled: true, content: '' } : m
        );
        this.setData({ messages });
        this.saveCache(messages);
        return;
      }
      if (newMsg.isMine) return;
      // voice/image WS消息解析（管道符格式等已在 app.js 中处理，此处直接用）
      const messages = this.data.messages;
      const updated = [...messages, newMsg];
      this.setData({ messages: updated });
      this.saveCache(updated);
      this.scrollToBottom();
    };
    if (sessionId) {
      app.onSessionMessage(sessionId, this._wsHandler);
    }

    // 初始化录音管理器
    this._recorder = wx.getRecorderManager();
    this._recorder.onStart(() => {
      console.log('[chat] recording started');
      this._recordingStarting = false;
      this._recordDelayTimer = null;
      // 开始录音时振动反馈
      wx.vibrateShort({ type: 'light' }).catch(() => {});
    });
    this._recorder.onStop((res) => { this._handleRecordComplete(res); });
    this._recorder.onError((err) => {
      console.error('[chat] recorder error:', JSON.stringify(err));
      clearTimeout(this._recordDelayTimer);
      this._recordDelayTimer = null;
      clearInterval(this._recordTimer);
      clearInterval(this._waveTimer);
      this._recordingStarting = false;
      this.setData({ recording: false, recordDuration: 0, recordCancelling: false, vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0 });
      // 权限问题 → 引导用户
      if (err && err.errMsg && err.errMsg.indexOf('permission') >= 0) {
        wx.showModal({
          title: '需要录音权限',
          content: '请在设置中开启麦克风权限后重试',
          showCancel: true, confirmText: '去设置',
          success: (r) => { if (r.confirm) wx.openSetting(); }
        });
      } else {
        wx.showToast({ title: '录音失败，请重试', icon: 'none' });
      }
    });

    // 初始化音频播放器（全局单例）
    const audioCtx = wx.createInnerAudioContext();
    audioCtx.onEnded(() => { this.setData({ playingId: null }); });
    audioCtx.onError((err) => {
      console.error('[chat] audio playback error:', err);
      this.setData({ playingId: null });
    });
    this._audioCtx = audioCtx;

    // 页面生命周期
    this._pageActive = true;
    this._onAppHide = () => {
      if (this._recordDelayTimer) {
        clearTimeout(this._recordDelayTimer);
        this._recordDelayTimer = null;
        this._recordingStarting = false;
      }
      if (this.data.recording) {
        clearInterval(this._recordTimer);
        clearInterval(this._waveTimer);
        try { this._recorder.stop(); } catch (e) {}
        this.setData({ recording: false, recordDuration: 0, recordCancelling: false, vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0 });
      }
      if (this._audioCtx && this.data.playingId) {
        this._audioCtx.stop();
        this.setData({ playingId: null });
      }
    };
    wx.onAppHide(this._onAppHide);
  },

  onShow() {
    if (!auth.ensureAccess()) return;
    if (this.data.sessionId) {
      app.markSessionRead(this.data.sessionId);
    }
  },

  onUnload() {
    if (this._wsHandler && this.data.sessionId) {
      app.offSessionMessage(this.data.sessionId, this._wsHandler);
      this._wsHandler = null;
    }
    app.clearActiveSession();
    if (this.data.recording) {
      clearTimeout(this._recordDelayTimer);
      clearInterval(this._recordTimer);
      clearInterval(this._waveTimer);
      try { this._recorder.stop(); } catch (e) {}
    }
    if (this._audioCtx) {
      this._audioCtx.stop();
      this._audioCtx.destroy();
      this._audioCtx = null;
    }
    if (this._onAppHide) { wx.offAppHide(this._onAppHide); }
    this._pageActive = false;
  },

  // ============================================================
  // 数据加载（不变）
  // ============================================================

  _cacheKey() {
    if (!this.data.userId || !this.data.sessionId) return null;
    return CACHE_PREFIX + this.data.userId + '_' + this.data.sessionId;
  },

  loadCache() {
    const key = this._cacheKey();
    if (!key) return;
    try {
      const cached = wx.getStorageSync(key);
      if (cached && cached.messages && cached.messages.length > 0) {
        this.setData({
          messages: cached.messages,
          oldestId: cached.oldestId || null,
          hasMore: cached.hasMore !== false
        });
        this.scrollToBottom();
      }
    } catch (e) { console.error('[chat] load cache failed:', e); }
  },

  saveCache(messages) {
    const key = this._cacheKey();
    if (!key) return;
    try {
      wx.setStorageSync(key, {
        messages: messages,
        oldestId: this.data.oldestId,
        hasMore: this.data.hasMore
      });
      const uid = this.data.userId;
      const sid = this.data.sessionId;
      if (uid && sid && messages.length > 0) {
        wx.setStorageSync('chat_msgs_' + uid + '_' + sid, messages);
      }
    } catch (e) { console.error('[chat] save cache failed:', e); }
  },

  loadHistory() {
    if (!this.data.sessionId) return;
    this.setData({ loadingHistory: true });
    api.get('/api/chats/history', { sessionId: this.data.sessionId, size: 30 })
      .then((res) => {
        const data = this._unwrapRes(res);
        // 保留本地已播放的语音状态，避免 loadHistory 覆盖 loadCache 恢复的 voicePlayed
        const existingMap = {};
        this.data.messages.forEach(m => {
          if (m.id && m.voicePlayed) existingMap[m.id] = true;
        });
        const serverMessages = this._normalizeMessages(data.messages || []);
        serverMessages.forEach(m => {
          if (m.id && existingMap[m.id]) m.voicePlayed = true;
        });
        if (serverMessages.length > 0) {
          serverMessages.sort((a, b) => (a.id || 0) - (b.id || 0));
          this.setData({
            messages: serverMessages,
            hasMore: data.hasMore !== false,
            oldestId: data.oldestId || null,
            loadingHistory: false
          });
          this.saveCache(serverMessages);
        } else {
          this.setData({ hasMore: false, loadingHistory: false });
          if (this.data.messages.length > 0) {
            this.saveCache(this.data.messages);
          }
        }
        this.scrollToBottom();
      })
      .catch((err) => {
        console.error('[chat] load history failed:', err);
        this.setData({ loadingHistory: false });
      });
  },

  loadOlder() {
    if (!this.data.sessionId || !this.data.hasMore || this.data.loadingOlder) return;
    const beforeId = this.data.oldestId;
    if (!beforeId) return;
    this.setData({ loadingOlder: true });
    api.get('/api/chats/history', { sessionId: this.data.sessionId, beforeId: beforeId, size: 30 })
      .then((res) => {
        const data = this._unwrapRes(res);
        // 保留本地已播放的语音状态
        const existingMap = {};
        this.data.messages.forEach(m => {
          if (m.id && m.voicePlayed) existingMap[m.id] = true;
        });
        const olderMessages = this._normalizeMessages(data.messages || []);
        olderMessages.forEach(m => {
          if (m.id && existingMap[m.id]) m.voicePlayed = true;
        });
        olderMessages.sort((a, b) => (a.id || 0) - (b.id || 0));
        const merged = [...olderMessages, ...this.data.messages];
        this.setData({
          messages: merged,
          hasMore: data.hasMore !== false,
          oldestId: data.oldestId || null,
          loadingOlder: false
        });
        this.saveCache(merged);
      })
      .catch((err) => {
        console.error('[chat] load older failed:', err);
        this.setData({ loadingOlder: false });
      });
  },

  _normalizeMessages(list) {
    const userId = this.data.userId;
    return list.map(m => {
      let type = m.messageType || 'text';
      let voiceDuration = 0;
      let content = m.content;

      // 语音消息：管道符格式 "duration|url"
      if (type === 'voice' && content) {
        const idx = content.indexOf('|');
        if (idx > 0) {
          voiceDuration = parseInt(content.substring(0, idx)) || 0;
          content = content.substring(idx + 1);
        }
      }

      return {
        id: m.id,
        content: content || '',
        isMine: String(m.fromUserId) === String(userId),
        type: type,
        systemIcon: type === 'system' ? 'ⓘ' : '',
        timestamp: m.createdAt ? new Date(m.createdAt).getTime() : Date.now(),
        showTime: false,
        timeText: '',
        status: m.status || 'sent',
        recalled: !!m.recalledAt,
        sendFailed: false,
        voiceDuration: voiceDuration,
        voicePlayed: type === 'voice' && String(m.fromUserId) === String(userId)
      };
    }).map((m, i, arr) => {
      if (i === 0) {
        m.showTime = true;
        m.timeText = this.formatTime(new Date(m.timestamp));
      } else {
        const prev = arr[i - 1];
        if (m.timestamp - prev.timestamp > 300000) {
          m.showTime = true;
          m.timeText = this.formatTime(new Date(m.timestamp));
        }
      }
      return m;
    });
  },

  _unwrapRes(res) {
    if (res && res.data && res.data.data !== undefined) return res.data.data;
    if (res && res.data) return res.data;
    return res || {};
  },

  // ============================================================
  // 文字消息发送
  // ============================================================

  onSend() {
    const text = this.data.inputText.trim();
    if (!text) return;

    const now = Date.now();
    const messages = this.data.messages;
    const tempId = 'local_' + now;

    const optimistic = {
      id: tempId, content: text, isMine: true, type: 'text',
      timestamp: now,
      showTime: messages.length === 0 || (now - (messages[messages.length - 1].timestamp || 0) > 300000),
      timeText: this.formatTime(new Date()),
      status: 'sending', recalled: false, sendFailed: false
    };

    const updated = [...messages, optimistic];
    this._justSent = true;
    if (this._justSentTimer) clearTimeout(this._justSentTimer);
    this._justSentTimer = setTimeout(() => { this._justSent = false; }, 150);
    const focusKey = Date.now();
    this.setData({ messages: updated, inputText: '', inputFocus: focusKey });
    this.saveCache(updated);
    this.scrollToBottom();

    api.post('/api/chats/send', {
      toUserId: this.data.otherUserId,
      sessionId: this.data.sessionId,
      content: text,
      messageType: 'text'
    }).then((res) => {
      const sent = this._unwrapRes(res);
      const realId = sent.id;
      const createdAt = sent.createdAt;
      const latest = this.data.messages.map(m => {
        if (m.id === tempId) {
          return { ...m, id: realId, timestamp: createdAt ? new Date(createdAt).getTime() : m.timestamp, status: 'sent', sendFailed: false };
        }
        return m;
      });
      this.setData({ messages: latest });
      this.saveCache(latest);
    }).catch((e) => {
      console.error('[chat] send failed:', e);
      const latest = this.data.messages.map(m => {
        if (m.id === tempId) return { ...m, status: 'failed', sendFailed: true };
        return m;
      });
      this.setData({ messages: latest });
      this.saveCache(latest);
    });
  },

  // ============================================================
  // 模式切换
  // ============================================================

  onSwitchToVoice() {
    this.setData({ inputMode: 'voice', inputFocus: 0 });
  },

  onSwitchToText() {
    this.setData({ inputMode: 'text', inputFocus: Date.now() });
  },

  // ============================================================
  // 录音
  // ============================================================

  onRecordStart(e) {
    // 防止重复启动录音（Android touch 事件偶现重复触发）
    if (this.data.recording || this._recordingStarting) return;

    const touch = e.touches[0];
    this._recordStartY = touch.clientY;
    this._recordCancelled = false;
    this._recordingStarting = true;

    // 延迟启动录音（200ms 阈值）：区分单击和长按
    // 单击不到 200ms 就松手 → 取消定时器，不启动录音
    // 长按超过 200ms → 启动录音管理器
    this._recordDelayTimer = setTimeout(() => {
      this._recordDelayTimer = null;
      this._recorder.start({
        duration: 60000,
        sampleRate: 44100,
        numberOfChannels: 1,
        encodeBitRate: 96000,
        format: 'mp3'
      });
      this.setData({ recording: true, recordDuration: 0, recordCancelling: false, vN: 0.15, vNE: 0.12, vE: 0.18, vSE: 0.10, vS: 0.14, vSW: 0.11, vW: 0.16, vNW: 0.13 });
      this._recordTimer = setInterval(() => {
        const d = this.data.recordDuration + 1;
        if (d >= 60) {
          this._recorder.stop();
          clearInterval(this._recordTimer);
          clearInterval(this._waveTimer);
        }
        this.setData({ recordDuration: d });
      }, 1000);
      // 模拟音量波动 — 果冻流体形变驱动
      this._startWaveSimulation();
    }, 200);
  },

  onRecordEnd(e) {
    // 如果延迟定时器还在（未到 200ms），取消录音启动
    if (this._recordDelayTimer) {
      clearTimeout(this._recordDelayTimer);
      this._recordDelayTimer = null;
      this._recordingStarting = false;
      return;
    }
    if (!this.data.recording) return;
    clearInterval(this._recordTimer);
    clearInterval(this._waveTimer);

    const touch = e.changedTouches[0];
    const slideDistance = this._recordStartY - touch.clientY;
    if (slideDistance > 80 || this._recordCancelled) {
      this._recordCancelled = true;
      this._recorder.stop();
      this.setData({ recording: false, recordDuration: 0, recordCancelling: false, vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0 });
      return;
    }
    this._recorder.stop();
  },

  onRecordCancel() {
    // 如果延迟定时器还在（未到 200ms），取消录音启动
    if (this._recordDelayTimer) {
      clearTimeout(this._recordDelayTimer);
      this._recordDelayTimer = null;
      this._recordingStarting = false;
      return;
    }
    this._recordCancelled = true;
    clearInterval(this._recordTimer);
    clearInterval(this._waveTimer);
    try { this._recorder.stop(); } catch (e) {}
    this.setData({ recording: false, recordDuration: 0, recordCancelling: false, vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0 });
  },

  onRecordTouchMove(e) {
    if (!this.data.recording) return;
    const touch = e.touches[0];
    const cancelling = (this._recordStartY - touch.clientY) > 80;
    if (cancelling !== this.data.recordCancelling) {
      this.setData({ recordCancelling: cancelling });
      if (cancelling) this._recordCancelled = true;
    }
  },

  /** 8 方位独立音量模拟 → 360° 方向性不对称果冻形变 */
  _startWaveSimulation() {
    // 8 个独立通道，各自随机游走 + 偶尔尖峰
    const channels = [
      { v: 0.15, t: 0 },  // N
      { v: 0.12, t: 0 },  // NE
      { v: 0.18, t: 0 },  // E
      { v: 0.10, t: 0 },  // SE
      { v: 0.14, t: 0 },  // S
      { v: 0.11, t: 0 },  // SW
      { v: 0.16, t: 0 },  // W
      { v: 0.13, t: 0 },  // NW
    ];
    const keys = ['vN', 'vNE', 'vE', 'vSE', 'vS', 'vSW', 'vW', 'vNW'];
    this._waveTimer = setInterval(() => {
      const data = {};
      for (let i = 0; i < 8; i++) {
        const c = channels[i];
        const spike = Math.random() < 0.10 ? (Math.random() * 0.55 + 0.25) : 0;
        c.t += (Math.random() - 0.5) * 0.22;
        c.t = Math.max(-0.35, Math.min(0.35, c.t));
        c.v = c.v * 0.68 + (0.22 + c.t + spike) * 0.32;
        c.v = Math.max(0.04, Math.min(1.0, c.v));
        data[keys[i]] = Math.round(c.v * 100) / 100;
      }
      this.setData(data);
    }, 110);
  },

  _handleRecordComplete(res) {
    this._recordingStarting = false;
    this.setData({ recording: false, recordCancelling: false, vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0 });
    clearInterval(this._recordTimer);
    clearInterval(this._waveTimer);
    if (this._recordCancelled) { this._recordCancelled = false; return; }
    if (res.duration < 1000) {
      wx.showToast({ title: '录音时间太短', icon: 'none' });
      return;
    }

    const tempFilePath = res.tempFilePath;
    const voiceDuration = Math.round(res.duration / 1000);
    const now = Date.now();
    const tempId = 'local_' + now;

    const messages = this.data.messages;
    const optimistic = {
      id: tempId,
      content: tempFilePath,
      isMine: true,
      type: 'voice',
      timestamp: now,
      showTime: messages.length === 0 || (now - (messages[messages.length - 1].timestamp || 0) > 300000),
      timeText: this.formatTime(new Date()),
      status: 'sending', recalled: false, sendFailed: false,
      voiceDuration: voiceDuration,
      voicePlayed: true
    };

    const updated = [...messages, optimistic];
    this.setData({ messages: updated });
    this.saveCache(updated);
    this.scrollToBottom();

    // 上传 → 发送
    api.upload('/api/common/upload-voice', tempFilePath)
      .then((fileUrl) => {
        if (!this._pageActive) return;
        // 更新本地消息的 content 为服务端 URL
        let msgs = this.data.messages.map(m => {
          if (m.id === tempId) return { ...m, content: fileUrl };
          return m;
        });
        this.setData({ messages: msgs });
        this.saveCache(msgs);
        // 发送：管道符编码时长
        return api.post('/api/chats/send', {
          toUserId: this.data.otherUserId,
          sessionId: this.data.sessionId,
          content: voiceDuration + '|' + fileUrl,
          messageType: 'voice'
        });
      })
      .then((sent) => {
        if (!this._pageActive) return;
        const realId = sent ? sent.id : null;
        if (!realId) return;
        const latest = this.data.messages.map(m => {
          if (m.id === tempId) {
            return { ...m, id: realId, timestamp: sent.createdAt ? new Date(sent.createdAt).getTime() : m.timestamp, status: 'sent', sendFailed: false };
          }
          return m;
        });
        this.setData({ messages: latest });
        this.saveCache(latest);
      })
      .catch((err) => {
        if (!this._pageActive) return;
        console.error('[chat] voice send failed:', err);
        wx.showToast({ title: (err && err.message) || '发送失败', icon: 'none' });
        const latest = this.data.messages.map(m => {
          if (m.id === tempId) return { ...m, status: 'failed', sendFailed: true };
          return m;
        });
        this.setData({ messages: latest });
        this.saveCache(latest);
      });
  },

  // ============================================================
  // 附件菜单 → 图片发送
  // ============================================================

  onAttachMenu() {
    wx.showActionSheet({
      itemList: ['相册', '拍摄'],
      success: (res) => {
        const sourceType = res.tapIndex === 0 ? ['album'] : ['camera'];
        wx.chooseMedia({
          count: 1,
          mediaType: ['image'],
          sourceType: sourceType,
          sizeType: ['compressed'],
          success: (chooseRes) => {
            this._sendImage(chooseRes.tempFiles[0].tempFilePath);
          }
        });
      }
    });
  },

  _sendImage(tempFilePath) {
    const now = Date.now();
    const tempId = 'local_' + now;
    const messages = this.data.messages;

    const optimistic = {
      id: tempId, content: tempFilePath, isMine: true, type: 'image',
      timestamp: now,
      showTime: messages.length === 0 || (now - (messages[messages.length - 1].timestamp || 0) > 300000),
      timeText: this.formatTime(new Date()),
      status: 'sending', recalled: false, sendFailed: false
    };

    const updated = [...messages, optimistic];
    this.setData({ messages: updated });
    this.saveCache(updated);
    this.scrollToBottom();

    api.upload('/api/common/upload', tempFilePath)
      .then((fileUrl) => {
        if (!this._pageActive) return;
        let msgs = this.data.messages.map(m => {
          if (m.id === tempId) return { ...m, content: fileUrl };
          return m;
        });
        this.setData({ messages: msgs });
        this.saveCache(msgs);
        return api.post('/api/chats/send', {
          toUserId: this.data.otherUserId,
          sessionId: this.data.sessionId,
          content: fileUrl,
          messageType: 'image'
        });
      })
      .then((sent) => {
        if (!this._pageActive) return;
        const realId = sent ? sent.id : null;
        if (!realId) return;
        const latest = this.data.messages.map(m => {
          if (m.id === tempId) {
            return { ...m, id: realId, timestamp: sent.createdAt ? new Date(sent.createdAt).getTime() : m.timestamp, status: 'sent', sendFailed: false };
          }
          return m;
        });
        this.setData({ messages: latest });
        this.saveCache(latest);
      })
      .catch((err) => {
        if (!this._pageActive) return;
        console.error('[chat] image send failed:', err);
        wx.showToast({ title: (err && err.message) || '发送失败', icon: 'none' });
        const latest = this.data.messages.map(m => {
          if (m.id === tempId) return { ...m, status: 'failed', sendFailed: true };
          return m;
        });
        this.setData({ messages: latest });
        this.saveCache(latest);
      });
  },

  // ============================================================
  // 语音播放
  // ============================================================

  onPlayVoice(e) {
    const msgId = e.currentTarget.dataset.id;
    const index = e.currentTarget.dataset.index;
    const msg = this.data.messages[index];
    if (!msg || msg.type !== 'voice') return;

    // 切换：同一消息在播放 → 暂停
    if (this.data.playingId === msgId) {
      this._audioCtx.pause();
      this.setData({ playingId: null });
      return;
    }

    // 停止当前播放
    this._audioCtx.stop();

    // 播放新语音 — 相对路径补全为绝对 URL
    let src = msg.content;
    if (src && src.startsWith('/uploads/')) {
      src = app.globalData.baseUrl + src;
    }
    if (!src) return;
    this._audioCtx.src = src;
    this._audioCtx.play();
    this.setData({ playingId: msgId });

    // 标记已播放（消除红点）
    if (!msg.voicePlayed && !msg.isMine) {
      const messages = this.data.messages.map(m =>
        m.id === msgId ? { ...m, voicePlayed: true } : m
      );
      this.setData({ messages });
      this.saveCache(messages);
    }
  },

  // ============================================================
  // 图片预览
  // ============================================================

  onPreviewImage(e) {
    const item = e.currentTarget.dataset.item;
    if (!item || item.type !== 'image' || !item.content) return;
    const urls = this.data.messages
      .filter(m => m.type === 'image' && m.content && !m.recalled)
      .map(m => m.content);
    wx.previewImage({ current: item.content, urls: urls });
  },

  // ============================================================
  // 长按菜单（文字消息）
  // ============================================================

  onMsgLongPress(e) {
    const item = e.currentTarget.dataset.item;
    if (!item || item.recalled) return;

    const index = e.currentTarget.dataset.index;
    const isMine = item.isMine;
    const canRecall = isMine && (Date.now() - item.timestamp < 2 * 60 * 1000);

    // 构建菜单项
    const items = [];
    if (item.type === 'voice' || item.type === 'image') {
      // 语音/图片：仅撤回+删除（无复制）
      if (canRecall) items.push({ label: '撤回', action: 'recall' });
      items.push({ label: '删除', action: 'delete' });
    } else {
      // 文字：撤回+复制+删除
      if (canRecall) items.push({ label: '撤回', action: 'recall' });
      items.push({ label: '复制', action: 'copy' });
      items.push({ label: '删除', action: 'delete' });
    }
    if (items.length === 0) return;

    // 对方消息：仅复制
    if (!isMine) {
      if (item.type === 'voice' || item.type === 'image') return; // 对方语音/图片无操作
      items.length = 0;
      items.push({ label: '复制', action: 'copy' });
    }

    // 通过元素 id 获取气泡精确位置，菜单置于气泡左上方，并确保不超出屏幕
    const sysInfo = wx.getSystemInfoSync();
    const screenWidth = sysInfo.windowWidth;
    const screenHeight = sysInfo.windowHeight;
    const menuItemCount = items.length;
    const menuEstWidth = menuItemCount * 62;   // 每项约 62px（padding 28rpx×2 + 文字 ~26px）
    const menuEstHeight = 40;                   // 菜单项约 40px 高
    const menuGap = 8;                          // 菜单与气泡间距
    const safeTop = 80;                         // 顶部安全区（状态栏 + 导航栏）
    const safeBottom = screenHeight - 20;       // 底部安全区
    const safeSide = 16;                        // 左右安全边距

    const query = wx.createSelectorQuery();
    query.select('#msg-bubble-' + index).boundingClientRect();
    query.exec((res) => {
      if (!res || !res[0]) {
        // 降级：无法获取元素位置时使用旧逻辑
        const fallbackX = isMine ? 280 : 30;
        const fallbackY = Math.max(80, (e.detail.y || 200) - 20);
        this.setData({
          showMsgMenu: true,
          menuX: fallbackX,
          menuY: fallbackY,
          menuItems: items,
          menuTarget: item
        });
        this._menuJustShown = Date.now();
        return;
      }

      const rect = res[0];

      // 默认：菜单在气泡左上方
      let menuX = rect.left;
      let menuY = rect.top - menuEstHeight - menuGap;

      // 上方空间不足 → 改为下方显示
      if (menuY < safeTop) {
        menuY = rect.bottom + menuGap;
      }
      // 下方也超出屏幕 → 贴底
      if (menuY + menuEstHeight > safeBottom) {
        menuY = safeBottom - menuEstHeight;
      }
      // 上方兜底：如果上方被拒、下方溢出、回退到上方贴安全区
      if (menuY < safeTop) {
        menuY = safeTop;
      }

      // 水平方向：保证菜单不超出屏幕左右
      const maxX = screenWidth - menuEstWidth - safeSide;
      if (menuX > maxX) menuX = maxX;
      if (menuX < safeSide) menuX = safeSide;

      this.setData({
        showMsgMenu: true,
        menuX: menuX,
        menuY: menuY,
        menuItems: items,
        menuTarget: item
      });
      this._menuJustShown = Date.now();
    });
  },

  onMsgMenuAction(e) {
    const action = e.currentTarget.dataset.action;
    const item = this.data.menuTarget;
    this._menuJustShown = 0;
    this.setData({ showMsgMenu: false, menuTarget: null });

    if (!item) return;
    if (action === 'copy') { wx.setClipboardData({ data: item.content || '' }); }
    else if (action === 'delete') { this._deleteMsg(item.id); }
    else if (action === 'recall') { this._recallMsg(item.id); }
  },

  onMsgMenuDismiss() {
    this._menuJustShown = 0;
    this.setData({ showMsgMenu: false, menuTarget: null });
  },

  _deleteMsg(msgId) {
    const messages = this.data.messages.filter(m => m.id !== msgId);
    this.setData({ messages });
    this.saveCache(messages);
  },

  _recallMsg(msgId) {
    wx.showLoading({ title: '撤回中...' });
    api.post('/api/chats/recall/' + msgId, {})
      .then(() => {
        wx.hideLoading();
        const messages = this.data.messages.map(m =>
          m.id === msgId ? { ...m, recalled: true, content: '' } : m
        );
        this.setData({ messages });
        this.saveCache(messages);
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: (err && err.message) || '撤回失败', icon: 'none' });
      });
  },

  // ============================================================
  // 重发
  // ============================================================

  onRetrySend(e) {
    const msgId = e.currentTarget.dataset.id;
    const msg = this.data.messages.find(m => m.id === msgId);
    if (!msg) return;

    if (msg.type === 'voice') {
      const filtered = this.data.messages.filter(m => m.id !== msgId);
      this.setData({ messages: filtered });
      this.saveCache(filtered);
      wx.showToast({ title: '请重新录制', icon: 'none' });
      return;
    }

    if (msg.type === 'image') {
      const filtered = this.data.messages.filter(m => m.id !== msgId);
      this.setData({ messages: filtered });
      this.saveCache(filtered);
      // content 可能是服务端URL（上传成功但POST失败）→ 直接重发POST
      if (msg.content && msg.content.startsWith('/uploads/')) {
        const now = Date.now();
        const tempId = 'local_' + now;
        const optimistic = {
          ...msg, id: tempId, status: 'sending', sendFailed: false,
          timestamp: now, showTime: false
        };
        const updated = [...this.data.messages, optimistic];
        this.setData({ messages: updated });
        this.saveCache(updated);
        api.post('/api/chats/send', {
          toUserId: this.data.otherUserId, sessionId: this.data.sessionId,
          content: msg.content, messageType: 'image'
        }).then((sent) => {
          const realId = sent ? sent.id : null;
          if (!realId) return;
          const latest = this.data.messages.map(m => {
            if (m.id === tempId) return { ...m, id: realId, status: 'sent' };
            return m;
          });
          this.setData({ messages: latest });
          this.saveCache(latest);
        }).catch(() => {
          const latest = this.data.messages.map(m => {
            if (m.id === tempId) return { ...m, status: 'failed', sendFailed: true };
            return m;
          });
          this.setData({ messages: latest });
          this.saveCache(latest);
        });
      } else {
        this._sendImage(msg.content);
      }
      return;
    }

    // 文字消息：放回输入框
    const filtered = this.data.messages.filter(m => m.id !== msgId);
    this.setData({ messages: filtered, inputText: msg.content });
    this.saveCache(filtered);
    this.onSend();
  },

  // ============================================================
  // UI 辅助
  // ============================================================

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
    setTimeout(() => { this.setData({ scrollToView: 'msg-bottom' }); }, 100);
  },

  onInputChange(e) {
    const value = (e.detail.value || '').replace(/\n/g, '');
    this.setData({ inputText: value });
  },

  onInputLineChange() {},

  onKeyboardHeightChange(e) {
    const h = e.detail.height;
    this.setData({ keyboardHeight: h, chatPaddingBottom: 70 + h });
    if (h > 0) this.scrollToBottom();
  },

  onInputBlur() {
    if (this._justSent) {
      this._justSent = false;
      this.setData({ inputFocus: Date.now() });
      return;
    }
    this.setData({ keyboardHeight: 0, chatPaddingBottom: 70 });
  },

  onChatAreaTap() {
    // 长按松手后 300ms 内的 tap → 不关菜单（防止长按 → 菜单闪现即消失）
    if (this._menuJustShown && Date.now() - this._menuJustShown < 300) return;
    wx.hideKeyboard();
    this.onMsgMenuDismiss();
    this.setData({ keyboardHeight: 0, chatPaddingBottom: 70 });
  },

  preventTouchMove() {},

  onScrollToUpper() { this.loadOlder(); },

  onNavBack() {
    wx.navigateBack({ fail() { wx.switchTab({ url: '/pages/messages/messages' }); } });
  },

  onAboutTap() {
    if (this.data.aboutId && this.data.aboutType) {
      const page = this.data.aboutType === POST_TYPE.HELP ? 'help-detail' : 'idle-detail';
      wx.navigateTo({ url: '/pages/' + page + '/' + page + '?id=' + this.data.aboutId });
    }
  }
});
