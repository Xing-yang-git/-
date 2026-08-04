/**
 * 共享聊天输入栏组件 — 聊天页与小邻对话页共用。
 *
 * 与设计原型 chat.html 的输入区一致：
 * - 文字模式：mic 按钮 + 输入框（textarea + 发送/终止图标） + 可选附件（+）按钮
 * - 语音模式：键盘切换 + 按住说话 + 可选附件（+）按钮
 * - 键盘弹起/收起跟随（keyboardHeight 属性驱动 fixed bottom 抬升）
 *
 * 页面间差异通过属性/事件交给宿主页面处理：
 * - showPlus：是否显示最右侧 + 按钮（聊天页有附件，小邻无）
 * - sending：发送中把发送图标原位换成红底终止图标（小邻流式回复可中止；聊天页不传）
 * - 语音松手后的真实行为（录音 / 语音转文字）由页面在 voicestart / voiceend 等事件里决定
 */
Component({
  properties: {
    /** 输入框文本 */
    value: { type: String, value: '' },
    /** 键盘高度（输入栏 fixed bottom 抬升量） */
    keyboardHeight: { type: Number, value: 0 },
    /** 是否显示最右侧 + 按钮 */
    showPlus: { type: Boolean, value: true },
    /** 发送中（true 时输入区禁用 + 发送图标换成终止图标） */
    sending: { type: Boolean, value: false },
    /** 录音中（按住说话文字在"松开发送/按住说话"间切换） */
    recording: { type: Boolean, value: false },
    /** 输入最大长度（聊天页 1000；小邻 500 对齐后端 @Size 校验） */
    maxlength: { type: Number, value: 1000 }
  },

  data: {
    /** 输入模式：text(文字输入) / voice(按住说话) */
    inputMode: 'text',
    /** textarea 聚焦 key（数字变化触发重新聚焦，对齐 chat 页 inputFocus 机制） */
    focusKey: 0,
    /** textarea 聚焦态（聚焦时输入框高度翻倍，扩大打字/按压区域） */
    focused: false
  },

  methods: {
    /** 输入内容变化 → 透传宿主页面 */
    onInputChange(e) {
      this.triggerEvent('input', { value: e.detail.value });
    },

    /** 点击发送 / 键盘确认键：保持聚焦 + 通知宿主发送 */
    onSendTap() {
      if (this.data.sending) return;
      // 发送后保持键盘打开（对齐 chat 页 _justSent 机制：发送瞬间的 blur 被捕获重聚焦）
      this._justSent = true;
      if (this._justSentTimer) clearTimeout(this._justSentTimer);
      this._justSentTimer = setTimeout(() => { this._justSent = false; }, 150);
      this.setData({ focusKey: Date.now() });
      this.triggerEvent('send');
    },

    /** 发送中点击终止图标 → 通知宿主中止（小邻流式回复） */
    onStopTap() {
      this.triggerEvent('stop');
    },

    /** 点击 mic：切语音模式（textarea 卸载自然收起键盘） */
    onSwitchToVoice() {
      if (this.data.sending) return;
      this.setData({ inputMode: 'voice', focusKey: 0, focused: false });
      // textarea 卸载不一定触发 blur，显式通知宿主复位键盘高度，避免输入栏停在抬升位置
      this.triggerEvent('switchvoice');
      this.triggerEvent('blur');
    },

    /** 点击键盘：切回文字模式并聚焦输入框 */
    onSwitchToText() {
      this.setData({ inputMode: 'text', focusKey: Date.now() });
      this.triggerEvent('switchtext');
    },

    /** 键盘高度变化 → 透传宿主（宿主驱动输入栏抬升与消息区留白） */
    onKeyboardHeightChange(e) {
      this.triggerEvent('keyboardheightchange', { height: e.detail.height });
    },

    /** textarea 聚焦：输入框高度翻倍 */
    onFocus() {
      this.setData({ focused: true });
    },

    /** 输入框失焦：刚发送后的失焦重新聚焦（保持聚焦态），否则收起键盘并退出聚焦态 */
    onBlur() {
      if (this._justSent) {
        this._justSent = false;
        this.setData({ focusKey: Date.now() });
        return;
      }
      this.setData({ focused: false });
      this.triggerEvent('blur');
    },

    /** textarea 行高变化（对齐 chat 页，无需处理） */
    onLineChange() {},

    /** 点击 + 按钮 → 通知宿主打开附件菜单 */
    onPlus() {
      this.triggerEvent('plus');
    },

    /** 按住说话开始 → 透传触摸信息（宿主决定录音 / 语音转文字） */
    onVoiceStart(e) {
      if (this.data.sending) return;
      this.triggerEvent('voicestart', { touches: e.touches, changedTouches: e.changedTouches });
    },

    /** 松开结束说话 */
    onVoiceEnd(e) {
      this.triggerEvent('voiceend', { touches: e.touches, changedTouches: e.changedTouches });
    },

    /** 说话被手势中断 */
    onVoiceCancel() {
      this.triggerEvent('voicecancel');
    },

    /** 按住说话滑动（上滑取消判断依据） */
    onVoiceTouchMove(e) {
      this.triggerEvent('voicetouchmove', { touches: e.touches, changedTouches: e.changedTouches });
    }
  }
});
