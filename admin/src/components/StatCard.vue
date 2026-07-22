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
import { computed } from 'vue';

/**
 * StatCard 统计卡片组件 Props。
 * 展示单个统计指标，含图标、数值、单位、趋势标签。
 */
const props = defineProps({
  /** 卡片标题 */
  title: { type: String, required: true },
  /** 统计数值 */
  value: { type: [Number, String], required: true },
  /** 数值单位 */
  unit: { type: String, default: '' },
  /** 趋势方向：'up' | 'down' */
  trend: { type: String, default: '' },
  /** 趋势变化文本 */
  trendValue: { type: String, default: '' },
  /** Element Plus 图标组件名 */
  icon: { type: [String, Object], default: 'TrendCharts' },
  /** 数值颜色 */
  color: { type: String, default: '#1d1d1f' },
  /** 图标容器背景色 */
  bgColor: { type: String, default: 'rgba(0,122,255,0.08)' },
});

/** 格式化展示数值（千位分隔符） */
const displayValue = computed(() => {
  const v = props.value;
  if (typeof v === 'number') return v.toLocaleString();
  return v;
});
</script>
