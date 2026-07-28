<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span
            class="topbar-title"
            style="display: flex; align-items: baseline; gap: 8px"
          >
            <span>翠湖花园 · 运营看板</span>
            <span
              class="text-sm text-secondary"
              style="white-space: nowrap; font-weight: 400"
              >{{ today }}</span
            >
          </span>
        </div>
        <div class="topbar-right">
          <el-dropdown @command="handleCommand">
            <span
              style="
                cursor: pointer;
                display: flex;
                align-items: center;
                gap: 4px;
              "
            >
              {{ authStore.userName }} <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main>
        <!-- KPI 卡片 -->
        <div class="stat-grid-luxe">
          <div class="stat-card-luxe">
            <div class="luxe-label">在线闲置物品</div>
            <div class="luxe-value">{{ animatedStats.idle }}</div>
            <div class="luxe-change up">↑ 12% 较上月</div>
          </div>
          <div class="stat-card-luxe">
            <div class="luxe-label">在线技能求助</div>
            <div class="luxe-value">{{ animatedStats.help }}</div>
            <div class="luxe-change up">↑ 8% 较上月</div>
          </div>
          <div class="stat-card-luxe">
            <div class="luxe-label">本月发布总数</div>
            <div class="luxe-value">{{ animatedStats.pub }}</div>
            <div class="luxe-change up">↑ 15% 较上月</div>
          </div>
          <div class="stat-card-luxe">
            <div class="luxe-label">本月活跃住户</div>
            <div class="luxe-value">{{ animatedStats.mau }}</div>
            <div class="luxe-change up">↑ 10% 较上月</div>
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
                    52<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">已互助（68%）</div>
                </div>
                <div>
                  <div class="panel-value" style="color: var(--text-secondary)">
                    24<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">直接下架（32%）</div>
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
                    38<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">正常耗损</div>
                </div>
                <div>
                  <div class="panel-value text-orange">
                    2<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">损坏已赔偿</div>
                </div>
                <div>
                  <div class="panel-value text-red">
                    1<span style="font-size: 18px">次</span>
                  </div>
                  <div class="panel-label">损坏未赔偿</div>
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

        <!-- 全部记录弹窗 -->
        <el-dialog v-model="showAllRecords" title="全部互助记录" width="640px">
          <div class="filter-row" style="padding: 0; margin-bottom: 8px">
            <el-select
              v-model="arFilterType"
              placeholder="全部类型"
              size="small"
              style="width: 120px"
              clearable
              @change="filteredAllRecords"
            >
              <el-option label="闲置" value="idle" />
              <el-option label="技能" value="help" />
            </el-select>
            <span style="flex: 1"></span>
            <span class="text-sm text-secondary"
              >共 {{ filteredAllRecords.length }} 条</span
            >
          </div>
          <el-table :data="filteredAllRecords" size="small" max-height="400">
            <el-table-column label="排名" width="60">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="name" label="住户" />
            <el-table-column prop="room" label="房号" width="80" />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <span
                  :class="[
                    'badge',
                    row.type === 'idle' ? 'badge-info' : 'badge-warning',
                  ]"
                >
                  {{ row.type === "idle" ? "闲置" : "技能" }}
                </span>
              </template>
            </el-table-column>
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
      </el-main>
    </el-container>
  </el-container>
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
import { useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import { ArrowDown } from "@element-plus/icons-vue";
import * as echarts from "echarts";

import { useAuthStore } from "../stores/auth";
import AppSidebar from "../components/AppSidebar.vue";
import { connect, close } from "../utils/ws";

const router = useRouter();
const authStore = useAuthStore();

/** echarts 图表容器 DOM 引用 */
const chartRef = ref<HTMLElement | null>(null);
/** echarts 实例 */
let chartInstance: echarts.ECharts | null = null;

/** 四个统计卡片的动画数值 */
const animatedStats = reactive({ idle: 0, help: 0, pub: 0, mau: 0 });
/** 目标数值（动画终点） */
const targets = { idle: 128, help: 35, pub: 256, mau: 89 };

/** 时间周期选项 */
const periods = [
  { key: "week", label: "周" },
  { key: "month", label: "月" },
  { key: "quarter", label: "季" },
] as const;
/** 当前选中时间周期 */
const currentPeriod = ref<'week' | 'month' | 'quarter'>('week');

/** 不同时间周期的图表数据 */
const chartData = {
  week: {
    labels: ["6/24", "6/25", "6/26", "6/27", "6/28", "6/29", "今日"],
    pub: [40, 55, 70, 50, 65, 80, 72],
    done: [25, 32, 45, 38, 42, 55, 48],
  },
  month: {
    labels: ["第1周", "第2周", "第3周", "第4周"],
    pub: [180, 210, 256, 190],
    done: [120, 145, 170, 135],
  },
  quarter: {
    labels: ["4月", "5月", "6月"],
    pub: [620, 710, 780],
    done: [430, 490, 540],
  },
};

/** 住户排行榜（Top 5） */
const topList = [
  { name: "3栋2单元1502号(业主)", count: 12, room: "3栋", type: "idle" },
  { name: "6栋1单元401号(业主)", count: 9, room: "6栋", type: "idle" },
  { name: "7栋1单元1201号(业主)", count: 8, room: "7栋", type: "idle" },
  { name: "5栋1单元802号(租客)", count: 7, room: "5栋", type: "help" },
  { name: "2栋1单元301号(租客)", count: 6, room: "2栋", type: "idle" },
];

/** 完整记录列表（"查看全部"弹窗用） */
const allRecords = [
  { name: "3栋2单元1502号(业主)", room: "3栋", type: "idle", count: 12 },
  { name: "6栋1单元401号(业主)", room: "6栋", type: "idle", count: 9 },
  { name: "7栋1单元1201号(业主)", room: "7栋", type: "idle", count: 8 },
  { name: "5栋1单元802号(租客)", room: "5栋", type: "help", count: 7 },
  { name: "2栋1单元301号(租客)", room: "2栋", type: "idle", count: 6 },
  { name: "1栋2单元303号(业主)", room: "1栋", type: "help", count: 5 },
  { name: "4栋1单元502号(租客)", room: "4栋", type: "idle", count: 5 },
  { name: "8栋3单元1502号(业主)", room: "8栋", type: "help", count: 4 },
];

/** "查看全部"弹窗可见性 */
const showAllRecords = ref(false);
/** 全部记录的类型筛选 */
const arFilterType = ref("");

/** 按类型筛选后的完整记录 */
const filteredAllRecords = computed(() => {
  if (!arFilterType.value) return allRecords;
  return allRecords.filter((r) => r.type === arFilterType.value);
});

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
  const d = chartData[currentPeriod.value];
  chartInstance.setOption({
    tooltip: { trigger: "axis" },
    legend: {
      data: ["发布数", "完成互助"],
      bottom: 0,
    },
    grid: { left: 20, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: "category",
      data: d.labels,
      axisLine: { lineStyle: { color: "#e8e8ed" } },
      axisLabel: { color: "#525256" },
    },
    yAxis: { type: "value", splitLine: { lineStyle: { color: "#f5f5f7" } } },
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

/** 顶部下拉菜单命令处理 */
function handleCommand(cmd: string): void {
  if (cmd === "logout") handleLogout();
}

/** 退出登录确认 */
async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm("确认退出登录？", "提示", {
      confirmButtonText: "退出",
      cancelButtonText: "取消",
      type: "warning",
    });
    authStore.logout();
    router.push("/login");
  } catch {
    /* 已取消 */
  }
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

onMounted(() => {
  nextTick(() => {
    initChart();
    animateStats();
  });
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
:deep(.el-main) {
  padding: 32px 40px !important;
}

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
