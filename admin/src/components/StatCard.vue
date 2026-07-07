<template>
  <div class="stat-card">
    <div class="stat-icon" :style="{ background: bgColor }">
      <el-icon :size="20"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-info">
      <div class="stat-value" :style="{ color: color }">{{ displayValue }}<span v-if="unit" style="font-size:14px;font-weight:400;">{{ unit }}</span></div>
      <div class="stat-label">{{ title }}</div>
      <div v-if="trendValue" class="stat-change" :class="trend">
        {{ trend === 'up' ? '↑' : '↓' }} {{ trendValue }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, shallowRef } from 'vue';

const props = defineProps({
  title: { type: String, required: true },
  value: { type: [Number, String], required: true },
  unit: { type: String, default: '' },
  trend: { type: String, default: '' },       // 'up' | 'down'
  trendValue: { type: String, default: '' },
  icon: { type: [String, Object], default: 'TrendCharts' },
  color: { type: String, default: '#1d1d1f' },
  bgColor: { type: String, default: 'rgba(0,122,255,0.08)' }
});

const displayValue = computed(() => {
  const v = props.value;
  if (typeof v === 'number') return v.toLocaleString();
  return v;
});
</script>
