/**
 * 星级评分组件 — 1-5 星交互式评分或只读展示。
 *
 * @property {Number}  score    — 当前评分（0-5）
 * @property {Boolean} readonly — 是否只读模式（true 时不可点击评分）
 * @property {String}  size     — 星星尺寸：s(小) / m(中) / l(大)
 * @event change — 评分变更时触发，detail = { score }
 */
Component({
  properties: {
    /** 当前评分（0-5 星） */
    score: {
      type: Number,
      value: 0
    },
    /** 是否只读（true 时不可点击评分） */
    readonly: {
      type: Boolean,
      value: false
    },
    /** 星星尺寸：s(小) / m(中) / l(大) */
    size: {
      type: String,
      value: 'm'
    }
  },

  methods: {
    onTap(e) {
      if (this.properties.readonly) return;
      const idx = e.currentTarget.dataset.score;
      const score = idx + 1;
      this.triggerEvent('change', { score: score });
    }
  }
});
