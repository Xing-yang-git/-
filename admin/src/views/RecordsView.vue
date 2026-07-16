<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="topbar-title">互助记录</span>
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
      <el-main class="panel-fill">
        <div class="unified-panel">
          <!-- 筛选 — 第1行：仅日期选择器 -->
          <div class="filter-row" style="width: 500px; padding-bottom: 2px">
            <el-date-picker
              class="filter-select date-picker-no-arrow"
              v-model="filterDateRange"
              type="daterange"
              range-separator="~"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 240px"
            />
          </div>

          <!-- 筛选 — 第2行：其他筛选 + 操作 -->
          <div class="filter-row">
            <el-select
              class="filter-select"
              v-model="filterType"
              placeholder="类型"
              style="width: 110px"
            >
              <el-option label="全部" value="" />
              <el-option label="物品互借" value="idle" />
              <el-option label="技能互助" value="help" />
            </el-select>
            <el-select
              class="filter-select"
              v-model="filterBuilding"
              placeholder="楼栋"
              style="width: 110px"
            >
              <el-option label="全部" value="" />
              <el-option
                v-for="b in communityStore.buildingOptions"
                :key="b.id"
                :label="b.name"
                :value="b.name"
              />
            </el-select>
            <el-select
              class="filter-select"
              v-model="filterUnit"
              placeholder="单元"
              style="width: 100px"
              :disabled="!filterBuilding"
            >
              <el-option label="全部" value="" />
              <el-option
                v-for="u in filterUnitOptions"
                :key="u.id"
                :label="u.name"
                :value="u.name"
              />
            </el-select>
            <el-input
              class="search-box"
              v-model="search"
              placeholder="搜索住户名称或房号..."
              style="width: 210px"
            />
            <el-button
              type="primary"
              size="default"
              style="margin-left: 8px"
              :disabled="!selectedRows.length"
              @click="openDetailFromSelection"
              >详细</el-button
            >
            <span style="flex: 1"></span>
            <span class="text-sm text-secondary"
              >共 {{ filteredRecords.length }} 条</span
            >
          </div>

          <!-- 表格（填充面板高度，内部滚动）-->
          <div class="uf-body">
            <div class="panel">
              <el-table
                :data="paginatedRecords"
                style="width: 100%"
                @selection-change="handleSelectionChange"
              >
                <el-table-column type="selection" width="50" />
                <el-table-column label="类型" align="center" width="70">
                  <template #default="{ row }">
                    <span
                      :class="[
                        'tag',
                        row.type === 'idle' ? 'tag-blue' : 'tag-orange',
                      ]"
                    >
                      {{ row.type === "idle" ? "互借" : "互助" }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="publisher" label="发布者" />
                <el-table-column prop="peer" label="互助对象" />
                <el-table-column prop="content" label="内容" />
                <el-table-column label="时间范围" width="300">
                  <template #default="{ row }">
                    {{ row.timeStart }} ~ {{ row.timeEnd }}
                  </template>
                </el-table-column>
              </el-table>

              <!-- 分页 -->
              <div
                v-if="filteredRecords.length > pageSize"
                style="
                  display: flex;
                  justify-content: flex-end;
                  padding: 12px 20px;
                "
              >
                <el-pagination
                  v-model:current-page="currentPage"
                  :page-size="pageSize"
                  :total="filteredRecords.length"
                  :pager-count="5"
                  layout="prev, pager, next"
                  small
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 详情弹窗 -->
        <el-dialog v-model="detailVisible" title="互助记录详情" width="680px" top="10vh">
          <div v-if="detailItem">
            <!-- 基本信息 -->
            <div class="detail-row">
              <span class="dl">内容</span
              ><span class="dv">{{ detailItem.content }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">类型</span
              ><span class="dv">
                <span
                  :class="[
                    'tag',
                    detailItem.type === 'idle' ? 'tag-blue' : 'tag-orange',
                  ]"
                >
                  {{ detailItem.type === "idle" ? "互借" : "互助" }}
                </span>
              </span>
            </div>
            <div class="detail-row">
              <span class="dl">小区</span><span class="dv">翠湖花园</span>
            </div>
            <!-- 互助：预计开始 / 预计结束（发布时选填，未填显示 --） -->
            <template v-if="detailItem.type === 'help'">
              <div class="detail-row">
                <span class="dl">预计开始</span
                ><span class="dv">{{ detailItem.timeStart || "--" }}</span>
              </div>
              <div class="detail-row">
                <span class="dl">预计结束</span
                ><span class="dv">{{ detailItem.timeEnd || "--" }}</span>
              </div>
            </template>
            <!-- 互借：借出时长 -->
            <template v-if="detailItem.type === 'idle'">
              <div class="detail-row">
                <span class="dl">借出时长</span
                ><span class="dv">{{ detailItem.lendDuration || "--" }}</span>
              </div>
            </template>

            <div class="dv-divider"></div>

            <!-- 互助双方 + 时间线（左右布局） -->
            <div style="display: flex; gap: 20px">
              <!-- 左侧：互助双方信息 -->
              <div style="flex: 1; min-width: 0">
                <p class="text-sm text-secondary" style="margin-bottom: 8px">
                  互助双方
                </p>
                <template v-if="detailItem.type === 'idle'">
                  <div class="detail-row">
                    <span class="dl">借出方</span>
                    <span class="dv"
                      >{{ detailItem.publisher }}
                      <span
                        v-if="detailItem.pubRating"
                        class="rating-tag"
                        style="margin-left: 6px"
                      >
                        <span class="stars">{{ detailItem.pubRating }}</span>
                        <span class="rlabel">获评</span>
                      </span>
                    </span>
                  </div>
                  <div class="detail-row" style="align-items: flex-start">
                    <span class="dl">互助感想</span>
                    <span class="dv" style="white-space: pre-wrap">{{
                      detailItem.pubComment || "无"
                    }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="dl">借入方</span>
                    <span class="dv"
                      >{{ detailItem.peer }}
                      <span
                        v-if="detailItem.peerRating"
                        class="rating-tag"
                        style="margin-left: 6px"
                      >
                        <span class="stars">{{ detailItem.peerRating }}</span>
                        <span class="rlabel">获评</span>
                      </span>
                    </span>
                  </div>
                  <div class="detail-row" style="align-items: flex-start">
                    <span class="dl">互助感想</span>
                    <span class="dv" style="white-space: pre-wrap">{{
                      detailItem.peerComment || "无"
                    }}</span>
                  </div>
                </template>
                <template v-else>
                  <div class="detail-row">
                    <span class="dl">求助方</span>
                    <span class="dv"
                      >{{ detailItem.publisher }}
                      <span
                        v-if="detailItem.pubRating"
                        class="rating-tag"
                        style="margin-left: 6px"
                      >
                        <span class="stars">{{ detailItem.pubRating }}</span>
                        <span class="rlabel">获评</span>
                      </span>
                    </span>
                  </div>
                  <div class="detail-row" style="align-items: flex-start">
                    <span class="dl">互助感想</span>
                    <span class="dv" style="white-space: pre-wrap">{{
                      detailItem.pubComment || "无"
                    }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="dl">相助方</span>
                    <span class="dv"
                      >{{ detailItem.peer }}
                      <span
                        v-if="detailItem.peerRating"
                        class="rating-tag"
                        style="margin-left: 6px"
                      >
                        <span class="stars">{{ detailItem.peerRating }}</span>
                        <span class="rlabel">获评</span>
                      </span>
                    </span>
                  </div>
                  <div class="detail-row" style="align-items: flex-start">
                    <span class="dl">互助感想</span>
                    <span class="dv" style="white-space: pre-wrap">{{
                      detailItem.peerComment || "无"
                    }}</span>
                  </div>
                </template>
              </div>

              <!-- 右侧：互助进度时间线 -->
              <div style="width: 190px; flex-shrink: 0; padding-top: 4px">
                <el-timeline>
                  <el-timeline-item
                    v-for="node in recordTimelineNodes"
                    :key="node.label"
                    :color="node.color"
                    :hollow="!node.time"
                    :timestamp="node.time || '—'"
                  >
                    {{ node.label }}
                  </el-timeline-item>
                </el-timeline>
              </div>
            </div>

            <!-- 互借：物品状况记录 -->
            <template v-if="detailItem.type === 'idle' && detailItem.condBefore">
              <div class="dv-divider"></div>
              <p class="text-sm text-secondary" style="margin-bottom: 8px">
                物品状况记录
              </p>
              <div class="detail-row">
                <span class="dl">借出前</span
                ><span class="dv">{{ detailItem.condBefore }}</span>
              </div>
              <div class="detail-row">
                <span class="dl">归还后</span
                ><span class="dv">{{ detailItem.condAfter }}</span>
              </div>
              <div class="detail-row">
                <span class="dl">归还情况</span
                ><span class="dv">{{ detailItem.returnStatus || "--" }}</span>
              </div>
            </template>

            <div class="dv-divider"></div>
            <p class="text-sm text-secondary">
              评分由双方互评完成：发布者评分为互助对象的评价，互助对象评分为发布者的评价。
            </p>
          </div>
          <template #footer>
            <div style="display: flex; align-items: center; gap: 8px">
              <el-button
                :icon="ArrowLeft"
                circle
                :disabled="detailPos === 0"
                @click="detailGo(-1)"
              />
              <span class="text-sm text-secondary"
                >{{ detailPos + 1 }} / {{ detailList.length }}</span
              >
              <el-button
                :icon="ArrowRight"
                circle
                :disabled="detailPos === detailList.length - 1"
                @click="detailGo(1)"
              />
              <span style="flex: 1"></span>
              <el-button @click="detailVisible = false">关闭</el-button>
            </div>
          </template>
        </el-dialog>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowDown, ArrowLeft, ArrowRight } from "@element-plus/icons-vue";
import { useAuthStore } from "../stores/auth";
import { useCommunityStore } from "../stores/community";
import AppSidebar from "../components/AppSidebar.vue";

const router = useRouter();
const authStore = useAuthStore();
const communityStore = useCommunityStore();

onMounted(() => {
  communityStore.fetchCommunityData();
});

const search = ref("");
const filterDateRange = ref(null);
const filterType = ref("");
const filterBuilding = ref("");
const filterUnit = ref("");

const records = reactive([
  {
    publisher: "3栋2单元1502号(业主)",
    pubRating: "★★★★★",
    pubComment: "物品保管得很好",
    peer: "5栋1单元802号(租客)",
    peerRating: "★★★★☆",
    peerComment: "东西很好用，谢谢",
    content: "博世冲击钻套装",
    type: "idle",
    timeStart: "2026-06-22 09:00",
    timeEnd: "2026-06-25 14:30",
    lendDuration: "3天",
    condBefore: "正常使用痕迹",
    condAfter: "正常损耗",
    returnStatus: "按时归还",
    room: "3栋",
    createdAt: "2026-06-20 15:30",
    applyAt: "2026-06-21 10:00",
    approveAt: "2026-06-21 14:00",
    completeAt: "2026-06-25 14:30",
  },
  {
    publisher: "5栋1单元802号(租客)",
    pubRating: "★★★★★",
    pubComment: "维修师傅很专业",
    peer: "7栋1单元1201号(业主)",
    peerRating: "★★★★★",
    peerComment: "",
    content: "水管漏水维修",
    type: "help",
    timeStart: "2026-06-22 10:00",
    timeEnd: "2026-06-22 11:00",
    room: "5栋",
    createdAt: "2026-06-20 08:00",
    applyAt: "2026-06-21 16:00",
    approveAt: "2026-06-22 08:30",
    completeAt: "2026-06-22 11:00",
  },
  {
    publisher: "3栋2单元1502号(业主)",
    pubRating: "★★★★☆",
    pubComment: "书很新，读得很愉快",
    peer: "2栋1单元301号(租客)",
    peerRating: "★★★★☆",
    peerComment: "",
    content: "《三体》全套3册",
    type: "idle",
    timeStart: "2026-06-15 08:00",
    timeEnd: "2026-06-20 16:00",
    lendDuration: "5天",
    condBefore: "几乎全新",
    condAfter: "正常损耗",
    returnStatus: "按时归还",
    room: "3栋",
    createdAt: "2026-06-13 10:00",
    applyAt: "2026-06-14 09:00",
    approveAt: "2026-06-14 16:00",
    completeAt: "2026-06-20 16:00",
  },
  {
    publisher: "7栋1单元1201号(业主)",
    pubRating: "★★★★★",
    pubComment: "",
    peer: "3栋2单元1502号(业主)",
    peerRating: "★★★★★",
    peerComment: "辛苦了，非常感谢",
    content: "陪老人就诊",
    type: "help",
    timeStart: "2026-06-28 08:30",
    timeEnd: "2026-06-28 12:00",
    room: "7栋",
    createdAt: "2026-06-25 14:00",
    applyAt: "2026-06-27 09:00",
    approveAt: "2026-06-27 18:00",
    completeAt: "2026-06-28 12:00",
  },
  {
    publisher: "7栋1单元1201号(业主)",
    pubRating: "★★★☆☆",
    pubComment: "归还时有轻微划痕",
    peer: "6栋2单元1102号(业主)",
    peerRating: "★★★☆☆",
    peerComment: "",
    content: "捷安特折叠自行车",
    type: "idle",
    timeStart: "2026-06-10 09:00",
    timeEnd: "2026-06-15 17:00",
    lendDuration: "5天",
    condBefore: "正常使用痕迹",
    condAfter: "可修复损坏",
    returnStatus: "超时归还",
    room: "7栋",
    createdAt: "2026-06-08 10:00",
    applyAt: "2026-06-09 08:00",
    approveAt: "2026-06-09 15:00",
    completeAt: "2026-06-15 17:00",
  },
]);

const filteredRecords = computed(() => {
  return records.filter((item) => {
    if (filterType.value && item.type !== filterType.value) return false;
    if (filterBuilding.value && !item.room.startsWith(filterBuilding.value))
      return false;
    if (filterUnit.value && !matchUnit(item.room, filterUnit.value))
      return false;
    // 时间区间筛选：按记录开始日期（精确到日）落在所选起止范围内
    if (filterDateRange.value && filterDateRange.value.length === 2) {
      const [start, end] = filterDateRange.value;
      const d = (item.timeStart || "").slice(0, 10);
      if (d < start || d > end) return false;
    }
    if (search.value) {
      const s = search.value.trim();
      if (
        !item.publisher.includes(s) &&
        !item.peer.includes(s) &&
        !item.content.includes(s)
      )
        return false;
    }
    return true;
  });
});

// 分页
const currentPage = ref(1);
const pageSize = ref(10);
const paginatedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredRecords.value.slice(start, start + pageSize.value);
});

// 选择
const selectedRows = ref([]);
// 根据所选楼栋计算单元筛选选项
const filterUnitOptions = computed(() => {
  if (!filterBuilding.value) return [];
  const buildingId = communityStore.getBuildingId(filterBuilding.value);
  return buildingId ? communityStore.getUnits(buildingId) : [];
});

// 单元筛选匹配：直接字符串匹配（单元使用阿拉伯数字，如 "1单元"/"2单元"）
function matchUnit(room, unit) {
  if (!room || !unit) return false;
  return room.includes(unit);
}

function handleSelectionChange(rows) {
  selectedRows.value = rows;
}

// 详情（勾选后点「详细」打开，支持左右箭头切换）
const detailVisible = ref(false);
const detailList = ref([]);
const detailPos = ref(0);
const detailItem = computed(() => detailList.value[detailPos.value] || null);

function openDetailFromSelection() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先勾选记录");
    return;
  }
  detailList.value = [...selectedRows.value];
  detailPos.value = 0;
  detailVisible.value = true;
}

function detailGo(delta) {
  const next = detailPos.value + delta;
  if (next < 0 || next >= detailList.value.length) return;
  detailPos.value = next;
}

// 互助进度时间线
const recordTimelineNodes = computed(() => {
  const it = detailItem.value;
  if (!it) return [];
  const isIdle = it.type === "idle";
  return [
    { label: "发布时间", time: it.createdAt || "", color: "#909399" },
    {
      label: isIdle ? "借入申请" : "帮忙申请",
      time: it.applyAt || "",
      color: "#409eff",
    },
    {
      label: isIdle ? "同意借出" : "同意帮忙",
      time: it.approveAt || "",
      color: "#e6a23c",
    },
    { label: "已完成", time: it.completeAt || "", color: "#67c23a" },
  ];
});

function handleCommand(cmd) {
  if (cmd === "logout") handleLogout();
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm("确认退出登录？", "提示", {
      confirmButtonText: "退出",
      cancelButtonText: "取消",
      type: "warning",
    });
    authStore.logout();
    router.push("/login");
  } catch {
    /* cancelled */
  }
}
</script>

<style scoped>
.rating-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: var(--bg);
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 11px;
  color: var(--orange);
  line-height: 1.3;
}
.rating-tag .stars {
  white-space: nowrap;
}
.rating-tag .rlabel {
  font-size: 9px;
  color: var(--text-tertiary);
}
.date-picker-no-arrow {
  background-image: none !important;
  padding-right: 12px !important;
}
/* 发布者是第3列 */
:deep(.el-table__body tr td:nth-child(3)) {
  padding-left: 40px !important;
}
:deep(.el-table__header tr th:nth-child(3)) {
  padding-left: 40px !important;
}
</style>
