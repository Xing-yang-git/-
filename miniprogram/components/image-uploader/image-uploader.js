Component({
  properties: {
    images: {
      type: Array,
      value: []
    },
    max: {
      type: Number,
      value: 9
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
