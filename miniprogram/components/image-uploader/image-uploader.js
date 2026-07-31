/**
 * 图片上传组件 — 多图选择 + 预览 + 删除管理。
 *
 * @property {Array}  images — 当前已选图片列表（tempFilePath 数组）
 * @property {Number} max    — 最大可选图片数量（默认 9）
 * @event change — 图片列表变更时触发，detail = { images }
 */
Component({
  properties: {
    /** 当前已选图片列表（tempFilePath 数组） */
    images: {
      type: Array,
      value: []
    },
    /** 最大可选图片数量 */
    max: {
      type: Number,
      value: 4
    }
  },

  methods: {
    onChoose() {
      const remaining = this.properties.max - this.properties.images.length;
      if (remaining <= 0) {
        wx.showToast({ title: '最多上传' + this.properties.max + '张图片', icon: 'none' });
        return;
      }

      wx.chooseMedia({
        count: remaining,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        sizeType: ['compressed'],
        success: (res) => {
          const newImages = res.tempFiles.map(f => f.tempFilePath);
          const updated = [...this.properties.images, ...newImages];
          this.triggerEvent('change', { images: updated });
        }
      });
    },

    onRemove(e) {
      const index = e.currentTarget.dataset.index;
      const updated = this.properties.images.filter((_, i) => i !== index);
      this.triggerEvent('change', { images: updated });
    }
  }
});
