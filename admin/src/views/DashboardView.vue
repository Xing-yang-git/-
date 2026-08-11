<template>
  <AppLayout title="翠湖花园 · 运营看板" main-class="el-main--comfortable" :loading="loading">
    <template #subtitle>{{ today }}</template>
        <!-- KPI 卡片 -->
        <div class="stat-grid-luxe">
          <div class="stat-card-luxe">
            <div class="luxe-label">在线闲置物品</div>
            <div class="luxe-value">{{ animatedStats.idle }}</div>
            <div class="luxe-change" :class="momClass('idle')">{{ momText('idle') }}</div>
          </div>
          <div class="stat-card-luxe">
            <div class="luxe-label">在线技能求助</div>
            <div class="luxe-value">{{ animatedStats.help }}</div>
            <div class="luxe-change" :class="momClass('help')">{{ momText('help') }}</div>
          </div>
          <div class="stat-card-luxe">
            <div class="luxe-label">本月发布总数</div>
            <div class="luxe-value">{{ animatedStats.pub }}</div>
            <div class="luxe-change" :class="momClass('pub')">{{ momText('pub') }}</div>
          </div>
          <div class="stat-card-luxe">
            <div class="luxe-label">本月活跃住户</div>
            <div class="luxe-value">{{ animatedStats.mau }}</div>
            <div class="luxe-change" :class="momClass('mau')">{{ momText('mau') }}</div>
          </div>
        </div>

        <!-- 图表区域 -->
        <div class="luxe-section">
          <div class="luxe-section-header">
            <span class="luxe-section-title">月度互助趋势</span>
            <div class="segment-row">
              <button
                v-for="p in periods"
                :key="p.key"
                class="segment-btn"
                :class="{ active: currentPeriod === p.key }"
                @click="switchPeriod(p.key)"
              >
                {{ p.label }}
              </button>
            </div>
          </div>
          <div class="chart-luxe">
            <div ref="chartRef" style="width: 100%; height: 280px"></div>
          </div>
        </div>

        <!-- 统计 + 排行榜 -->
        <div class="luxe-section">
          <div class="luxe-section-header">
            <span class="luxe-section-title">互助分析</span>
          </div>
          <div class="two-col-luxe">
            <!-- 完成率 -->
            <div class="stat-panel-luxe">
              <div
                style="
                  margin-bottom: 20px;
                  font-size: 15px;
                  font-weight: 600;
                  color: var(--text);
                "
              >
                本月互助完成率
              </div>
              <div class="stat-row">
                <div>
                  <div class="panel-value text-green">
                    {{ completion.completed }}<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">已互助（{{ completionRate }}%）</div>
                </div>
                <div>
                  <div class="panel-value" style="color: var(--text-secondary)">
                    {{ completion.removed }}<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">直接下架（{{ 100 - completionRate }}%）</div>
                </div>
              </div>
            </div>
            <!-- 损坏统计 -->
            <div class="stat-panel-luxe">
              <div
                style="
                  margin-bottom: 20px;
                  font-size: 15px;
                  font-weight: 600;
                  color: var(--text);
                "
              >
                损坏统计
              </div>
              <div class="stat-row">
                <div>
                  <div class="panel-value text-green">
                    {{ damage.normal }}<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">正常耗损</div>
                </div>
                <div>
                  <div class="panel-value text-orange">
                    {{ damage.severe }}<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">非正常损坏</div>
                </div>
                <div>
                  <div class="panel-value text-red">
                    {{ damage.broken }}<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">完全损坏</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 排行榜 -->
        <div class="luxe-section">
          <div class="luxe-section-header">
            <span class="luxe-section-title">互助对象排行</span>
            <span
              class="luxe-section-sub"
              style="cursor: pointer; color: var(--accent); font-weight: 500"
              @click="showAllRecords = true"
              >全部互助 ›</span
            >
          </div>
          <ol class="top-list-luxe">
            <li v-for="(item, i) in topList" :key="i">
              <span class="rank">{{ i + 1 }}.</span>
              <span class="name">{{ item.name }}</span>
              <span class="count">{{ item.count }} 次</span>
            </li>
          </ol>
        </div>

        <!-- 全部互助记录弹窗：仅 排名/住户/互助次数，固定最大高度可滚动 -->
        <el-dialog v-model="showAllRecords" title="全部互助记录" width="560px">
          <el-table :data="allRecords" max-height="420">
            <el-table-column label="排名" width="60">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="name" label="住户" />
            <el-table-column
              prop="count"
              label="互助次数"
              width="100"
              align="center"
            />
          </el-table>
          <template #footer>
            <el-button @click="showAllRecords = false">关闭</el-button>
          </template>
        </el-dialog>
  </AppLayout>
</template>

<!--
  DashboardView.vue — 运营数据看板

  功能：KPI 统计卡片（在线闲置/求助数量、本月发布总数）、近 12 月发布趋势图、最新待办列表。
  权限：需管理员 / 超级管理员登录。
-->
<script setup lang="ts">
import {
  ref,
  reactive,
  computed,
  onMounted,
  onUnmounted,
  nextTick,
} from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";

import AppLayout from "@/layouts/AppLayout.vue";
import { connect, close } from "@/utils/ws";
import {
  getDashboard,
  type DashboardDTO,
  type DashboardKpi,
  type DashboardTrendData,
} from "@/api/admin";

/** echarts 图表容器 DOM 引用 */
const chartRef = ref<HTMLElement | null>(null);
/** echarts 实例 */
let chartInstance: echarts.ECharts | null = null;

/** 看板数据（后端 /api/admin/dashboard） */
const dashboard = ref<DashboardDTO | null>(null);
/** 加载中 */
const loading = ref(false);

/** 四个统计卡片的动画数值 */
const animatedStats = reactive({ idle: 0, help: 0, pub: 0, mau: 0 });
/** 目标数值（动画终点，由后端 KPI 填充） */
const targets = reactive({ idle: 0, help: 0, pub: 0, mau: 0 });

/** 时间周期选项 */
const periods = [
  { key: "week", label: "周" },
  { key: "month", label: "月" },
  { key: "quarter", label: "季" },
] as const;
/** 当前选中时间周期 */
const currentPeriod = ref<'week' | 'month' | 'quarter'>('week');

/** 由后端 trends 映射的图表数据（done ← 后端 completed 数组） */
const chartData = computed(() => {
  const t = dashboard.value?.trends;
  const empty = { labels: [], pub: [], done: [] };
  if (!t) {
    return { week: empty, month: empty, quarter: empty };
  }
  const map = (d: DashboardTrendData) => ({
    labels: d.labels,
    pub: d.publish,
    done: d.completed,
  });
  return { week: map(t.week), month: map(t.month), quarter: map(t.quarter) };
});

/** 本月互助完成率（后端数据，缺失时兜底 0） */
const completion = computed(
  () => dashboard.value?.completion ?? { completed: 0, removed: 0, rate: 0 },
);
/** 完成率百分比（取整展示） */
const completionRate = computed(() => Math.round(completion.value.rate));

/** 损坏三态统计 */
const damage = computed(
  () => dashboard.value?.damage ?? { normal: 0, severe: 0, broken: 0 },
);

/** 住户排行 Top 5（后端返回全量，前端切片） */
const topList = computed(() => (dashboard.value?.ranking ?? []).slice(0, 5));
/** 完整排行记录（"查看全部"弹窗用） */
const allRecords = computed(() => dashboard.value?.ranking ?? []);

/** 查询指定 key 的 KPI 项 */
function kpi(key: DashboardKpi["key"]): DashboardKpi | undefined {
  return dashboard.value?.kpis.find((k) => k.key === key);
}

/** 较上月文案：↑/↓ + 绝对值百分比（无数据时占位 —） */
function momText(key: DashboardKpi["key"]): string {
  const k = kpi(key);
  if (!k) return "—";
  return `${k.momChange >= 0 ? "↑" : "↓"} ${Math.abs(k.momChange)}% 较上月`;
}

/** 较上月方向样式：上升 up（绿）/ 下降 down（红） */
function momClass(key: DashboardKpi["key"]): string {
  const k = kpi(key);
  return k && k.momChange < 0 ? "down" : "up";
}

/** "查看全部"弹窗可见性 */
const showAllRecords = ref(false);

/** 格式化的今日日期（含星期） */
const today = computed(() => {
  const d = new Date();
  const days = ["日", "一", "二", "三", "四", "五", "六"];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 周${days[d.getDay()]}`;
});

/** 统计数字滚动动画（ease-out cubic） */
function animateStats(): void {
  const duration = 1200;
  const start = performance.now();
  function tick(now: number): void {
    const p = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - p, 3);
    for (const key of Object.keys(targets) as (keyof typeof targets)[]) {
      animatedStats[key] = Math.round(targets[key] * eased);
    }
    if (p < 1) requestAnimationFrame(tick);
    else Object.assign(animatedStats, targets);
  }
  requestAnimationFrame(tick);
}

/** 初始化 echarts 图表并绑定 resize 事件 */
function initChart(): void {
  if (!chartRef.value) return;
  chartInstance = echarts.init(chartRef.value);
  renderChart();
  window.addEventListener("resize", handleResize);
}

/** 根据当前时间周期渲染图表 */
function renderChart(): void {
  if (!chartInstance) return;
  const d = chartData.value[currentPeriod.value];
  chartInstance.setOption({
    tooltip: { trigger: "axis" },
    legend: {
      data: ["发布数", "完成互助"],
      bottom: 0,
    },
    // containLabel: 自动为纵轴刻度标签预留空间，防止数值被图表左边缘截断
    grid: { left: 20, right: 20, top: 20, bottom: 40, containLabel: true },
    xAxis: {
      type: "category",
      data: d.labels,
      axisLine: { lineStyle: { color: "#e8e8ed" } },
      axisLabel: { color: "#525256" },
    },
    // minInterval: 1 — 次数轴数据为整数，强制刻度间隔不小于 1，避免出现 0.5 这类小数刻度
    yAxis: {
      type: "value",
      minInterval: 1,
      splitLine: { lineStyle: { color: "#f5f5f7" } },
    },
    series: [
      {
        name: "发布数",
        type: "bar",
        data: d.pub,
        itemStyle: { color: "#0071e3", borderRadius: [4, 4, 0, 0] },
        barWidth: 16,
      },
      {
        name: "完成互助",
        type: "bar",
        data: d.done,
        itemStyle: {
          color: "rgba(0,113,227,0.25)",
          borderRadius: [4, 4, 0, 0],
        },
        barWidth: 16,
      },
    ],
  });
}

/** 切换时间周期并重新渲染图表 */
function switchPeriod(key: string): void {
  currentPeriod.value = key as 'week' | 'month' | 'quarter';
  renderChart();
}

/** 窗口 resize 时更新图表尺寸 */
function handleResize(): void {
  chartInstance?.resize();
}

// WebSocket 消息处理函数
function onWsMessage(data: Record<string, number>): void {
  // 从实时数据更新统计数值
  if (data.idle) targets.idle = data.idle;
  if (data.help) targets.help = data.help;
  if (data.pub) targets.pub = data.pub;
  if (data.mau) targets.mau = data.mau;
  animateStats();
}

/** 拉取看板数据并刷新全部展示（KPI 动画、趋势图、完成率/损坏、排行） */
async function fetchDashboard(): Promise<void> {
  loading.value = true;
  try {
    const res = await getDashboard();
    const d = res?.data?.data as DashboardDTO;
    dashboard.value = d;
    if (d?.kpis) {
      const byKey = Object.fromEntries(d.kpis.map((k) => [k.key, k]));
      targets.idle = byKey.idle?.value ?? 0;
      targets.help = byKey.help?.value ?? 0;
      targets.pub = byKey.pub?.value ?? 0;
      targets.mau = byKey.mau?.value ?? 0;
    }
    animateStats();
    renderChart();
  } catch {
    ElMessage.error("看板数据加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  nextTick(() => {
    initChart();
  });
  fetchDashboard();
  // 取消注释以启用 WebSocket 实时更新
  // connect(onWsMessage);
});

onUnmounted(() => {
  close();
  window.removeEventListener("resize", handleResize);
  chartInstance?.dispose();
});
</script>

<style scoped>
/* 不设最大宽度限制 —— 4列 KPI 网格和2列分析区域在任何宽度下等比缩放，不会显得稀疏。 */
.panel-value {
  font-size: 40px;
  font-weight: 300;
  letter-spacing: -0.02em;
  line-height: 1;
  margin-bottom: 4px;
}

.panel-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 450;
}

.text-green {
  color: var(--green);
}
.text-orange {
  color: var(--orange);
}
.text-red {
  color: var(--red);
}
</style>
