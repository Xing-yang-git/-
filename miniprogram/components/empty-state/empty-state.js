/**
 * 空状态占位组件 — 列表为空或无数据时展示图标、提示文字和操作按钮。
 *
 * @property {String} icon       — 图标路径
 * @property {String} text       — 提示文字（默认"暂无数据"）
 * @property {String} buttonText — 操作按钮文字（非空时显示按钮）
 * @event action — 点击操作按钮触发
 */
Component({
  properties: {
    /** 图标路径 */
    icon: {
      type: String,
      value: ''
    },
    /** 提示文字 */
    text: {
      type: String,
      value: '暂无数据'
    },
    /** 操作按钮文字（非空时显示按钮） */
    buttonText: {
      type: String,
      value: ''
    }
  },

  methods: {
    onAction() {
      this.triggerEvent('action');
    }
  }
});
