/**
 * 共享录音浮层组件 — 聊天页与小邻对话页共用。
 *
 * 按住说话时的全屏视觉反馈：8 方位果冻流体形变 + 中心白底圆 + 时长/提示文字。
 * 页面只负责传录音状态，音量模拟在组件内部自维护，保证两页表现完全一致。
 *
 * 属性：
 * - recording：录音中（显示浮层）
 * - recordCancelling：上滑取消态（浮层变红）
 * - recordDuration：录音时长秒数
 */
Component({
  properties: {
    /** 录音中（显示浮层） */
    recording: { type: Boolean, value: false },
    /** 上滑取消中（浮层变红） */
    recordCancelling: { type: Boolean, value: false },
    /** 录音时长秒数 */
    recordDuration: { type: Number, value: 0 }
  },

  data: {
    /** 8 方位音量模拟（果冻形变驱动） */
    vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0
  },

  observers: {
    /** recording 变化：启动/停止果冻波纹音量模拟 */
    'recording': function (recording) {
      if (recording) {
        this._startWaveSimulation();
      } else {
        this._stopWaveSimulation();
        this.setData({ vN: 0, vNE: 0, vE: 0, vSE: 0, vS: 0, vSW: 0, vW: 0, vNW: 0 });
      }
    }
  },

  lifetimes: {
    detached() {
      // 页面卸载时停止音量模拟，避免定时器泄漏
      this._stopWaveSimulation();
    }
  },

  methods: {
    /** 8 方位独立音量模拟 → 360° 方向性不对称果冻形变（对齐聊天页原实现） */
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

    /** 停止果冻波纹音量模拟 */
    _stopWaveSimulation() {
      if (this._waveTimer) {
        clearInterval(this._waveTimer);
        this._waveTimer = null;
      }
    }
  }
});
