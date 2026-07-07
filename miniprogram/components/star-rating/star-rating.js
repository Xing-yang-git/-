Component({
  properties: {
    score: {
      type: Number,
      value: 0
    },
    readonly: {
      type: Boolean,
      value: false
    },
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
