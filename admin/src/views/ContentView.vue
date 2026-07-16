<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="topbar-title">内容管理</span>
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
          <!-- 标签页 -->
          <div style="display: flex; align-items: center; padding: 14px 20px 0">
            <div class="segment-row">
              <button
                v-for="t in contentTabs"
                :key="t.key"
                class="segment-btn"
                :class="{ active: activeTab === t.key }"
                @click="switchTab(t.key)"
              >
                {{ t.label }}
              </button>
            </div>
          </div>

          <!-- 筛选 -->
          <div class="filter-row">
            <el-select
              class="filter-select"
              v-model="filterType"
              placeholder="类型"
              style="width: 120px"
              @change="applyFilters"
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
              @change="applyFilters"
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
              @change="applyFilters"
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
              placeholder="搜索内容标题..."
              style="width: 150px"
              @keydown.enter="applyFilters"
              @blur="applyFilters"
            />
            <el-button
              size="default"
              type="primary"
              style="margin-left: 8px"
              :disabled="!selectedRows.length"
              @click="openDetailFromSelection"
              >详细</el-button
            >
            <el-button type="primary" size="default" @click="openPublish"
              >物业代发</el-button
            >
            <span style="flex: 1"></span>
            <span class="text-sm text-secondary">共 {{ totalCount }} 条</span>
          </div>

          <!-- 面板主体（填充面板高度，内部滚动）-->
          <div class="uf-body">
            <!-- 加载中 -->
            <div v-if="loading" class="panel-empty">加载中...</div>

            <!-- 表格 -->
            <div class="panel-body-scroll" v-else-if="tableData.length">
              <el-table
                ref="contentTableRef"
                :data="tableData"
                style="width: 100%"
                @selection-change="handleSelectionChange"
              >
                <el-table-column type="selection" width="30" />
                <el-table-column label="类型" align="center" min-width="30">
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
                <el-table-column
                  prop="publisherRoom"
                  label="发布者"
                  min-width="100"
                />
                <el-table-column label="标题" min-width="100">
                  <template #default="{ row }">
                    {{ row.title }}
                    <span
                      v-if="row.isProxy"
                      class="tag tag-orange"
                      style="margin-left: 4px; font-size: 10px"
                      >物业代发</span
                    >
                  </template>
                </el-table-column>

                <template v-if="activeTab === 'offline'">
                  <el-table-column label="下架时间" min-width="80">
                    <template #default="{ row }">{{
                      formatTime(row.violatedAt)
                    }}</template>
                  </el-table-column>
                  <el-table-column label="原因" min-width="130">
                    <template #default="{ row }">{{
                      row.violationReason || row.violationType || "—"
                    }}</template>
                  </el-table-column>
                  <el-table-column label="操作人" min-width="80">
                    <template #default="{ row }">{{
                      row.violatorName || "—"
                    }}</template>
                  </el-table-column>
                </template>
                <template v-else>
                  <el-table-column label="发布时间" min-width="100">
                    <template #default="{ row }">{{
                      formatTime(row.createdAt)
                    }}</template>
                  </el-table-column>
                  <el-table-column label="状态" align="center" min-width="56">
                    <template #default="{ row }">
                      <span :class="statusTag(row.displayStatus)">{{
                        statusLabel(row.displayStatus)
                      }}</span>
                    </template>
                  </el-table-column>
                </template>
              </el-table>
            </div>
            <div v-else class="panel-empty">暂无内容</div>
          </div>

          <!-- 分页 -->
          <div
            style="display: flex; justify-content: flex-end; padding: 12px 20px"
            v-if="totalPages > 1"
          >
            <el-pagination
              :current-page="currentPage + 1"
              :page-size="pageSize"
              :total="totalCount"
              :pager-count="5"
              layout="prev, pager, next"
              small
              @current-change="(p) => goPage(p - 1)"
            />
          </div>
        </div>

        <!-- 详情弹窗 -->
        <el-dialog
          v-model="detailVisible"
          title="发布内容详情"
          width="560px"
          top="10vh"
        >
          <div v-if="detailLoading" style="text-align: center; padding: 20px">
            加载中...
          </div>
          <div v-else-if="detailItem">
            <div class="detail-row">
              <span class="dl">标题</span
              ><span class="dv">{{ detailItem.title }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">发布者</span
              ><span class="dv">{{ detailItem.publisherRoom }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">小区</span><span class="dv">翠湖花园</span>
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
            <!-- 详情描述：C端选填，未获取到时显示「无」 -->
            <div class="detail-row" style="align-items: flex-start">
              <span class="dl">详情描述</span>
              <span class="dv" style="white-space: pre-wrap">{{
                detailItem.description || "无"
              }}</span>
            </div>
            <!-- 互助：预计开始/预计结束（来自发布时填写的值） -->
            <template v-if="detailItem.type === 'help'">
              <div class="detail-row">
                <span class="dl">预计开始</span
                ><span class="dv">{{
                  formatTime(detailItem.timeStart) || "--"
                }}</span>
              </div>
              <div class="detail-row">
                <span class="dl">预计结束</span
                ><span class="dv">{{
                  formatTime(detailItem.timeEnd) || "--"
                }}</span>
              </div>
            </template>
            <!-- 互借：借用时长（来自发布时填写的最大借出/需要借入时长） -->
            <template
              v-if="
                detailItem.type === 'idle' && detailItem.maxDuration != null
              "
            >
              <div class="detail-row">
                <span class="dl">借用时长</span
                ><span class="dv">{{
                  detailItem.maxDuration +
                  (detailItem.durationUnit === "hour" ? "小时" : "天")
                }}</span>
              </div>
            </template>
            <!-- 图片：C端选填，未获取到时显示「无」 -->
            <div class="detail-row" style="align-items: flex-start">
              <span class="dl">图片</span>
              <span class="dv">
                <span
                  v-if="validDetailImages.length"
                  style="display: flex; gap: 8px; flex-wrap: wrap"
                >
                  <el-image
                    v-for="(img, i) in validDetailImages"
                    :key="i"
                    :src="img"
                    :preview-src-list="validDetailImages"
                    :initial-index="i"
                    fit="cover"
                    preview-teleported
                    hide-on-click-modal
                    @error="onImageError(i)"
                    style="
                      width: 80px;
                      height: 80px;
                      border-radius: 8px;
                      cursor: pointer;
                    "
                  />
                </span>
                <template v-else>无</template>
              </span>
            </div>

            <template v-if="detailItem.displayStatus === '已下架'">
              <div class="dv-divider"></div>
              <p class="text-sm text-secondary" style="margin-bottom: 8px">
                下架信息
              </p>
              <div class="detail-row">
                <span class="dl">下架原因</span
                ><span class="dv">{{
                  detailItem.violationReason || detailItem.violationType || "—"
                }}</span>
              </div>
              <div class="detail-row">
                <span class="dl">下架时间</span
                ><span class="dv">{{
                  formatTime(detailItem.violatedAt) || "—"
                }}</span>
              </div>
              <div class="detail-row">
                <span class="dl">下架管理员</span
                ><span class="dv">{{ detailItem.violatorName || "—" }}</span>
              </div>
            </template>

            <div class="dv-divider"></div>
            <p class="text-sm text-secondary" style="line-height: 1.6">
              此内容由住户自主发布。物业管理员可对其进行巡查，如发现违规内容可执行下架操作。下架后系统将通知发布者。
            </p>
          </div>
          <template #footer>
            <div style="display: flex; align-items: center; gap: 8px">
              <template v-if="detailList.length > 1">
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
              </template>
              <span style="flex: 1"></span>
              <el-button @click="detailVisible = false">关闭</el-button>
              <el-button
                v-if="detailItem && detailItem.displayStatus !== '已下架'"
                type="danger"
                @click="offlineFromDetail"
                >下架</el-button
              >
            </div>
          </template>
        </el-dialog>

        <!-- 下架弹窗 -->
        <el-dialog v-model="offlineVisible" title="确认下架" width="480px">
          <p style="text-align: center; margin-bottom: 12px">
            确认下架「<strong>{{ offlineTarget?.title }}</strong
            >」？
          </p>
          <div style="margin-bottom: 8px; font-weight: 600">下架原因：</div>
          <el-checkbox-group
            v-model="offlineReasons"
            style="display: flex; flex-direction: column; gap: 4px"
          >
            <el-checkbox
              v-for="reason in offlineReasonOptions"
              :key="reason"
              :value="reason"
              :label="reason"
            />
          </el-checkbox-group>
          <div class="mt-8">
            <el-input
              v-model="offlineCustomReason"
              placeholder="自定义原因..."
              style="width: 100%"
            />
          </div>
          <template #footer>
            <el-button @click="offlineVisible = false">取消</el-button>
            <el-button
              type="danger"
              :loading="offlineSubmitting"
              @click="doOffline"
              >确认下架</el-button
            >
          </template>
        </el-dialog>

        <!-- 代发弹窗 -->
        <el-dialog v-model="publishVisible" title="物业代发" width="520px">
          <div style="margin-bottom: 16px">
            <div style="display: flex; align-items: center; margin-bottom: 6px">
              <span
                class="field-label"
                style="display: inline; margin-bottom: 0"
              >
                目标住户 <span style="color: var(--red)">*</span>
              </span>
              <span style="flex: 1"></span>
              <div style="display: flex; gap: 6px">
                <button
                  class="btn-sm-toggle"
                  :class="{ active: publishMode === 'idle' }"
                  @click="publishMode = 'idle'"
                >
                  物品互借
                </button>
                <button
                  class="btn-sm-toggle"
                  :class="{ active: publishMode === 'help' }"
                  @click="publishMode = 'help'"
                >
                  技能互助
                </button>
              </div>
            </div>
            <div style="display: flex; gap: 4px">
              <el-input
                class="field-input"
                readonly
                :model-value="selectedResident"
                style="flex: 1; cursor: pointer"
                @click="openResidentSearch"
                placeholder="点击检索住户"
              />
              <el-button
                style="height: 32px; width: 32px; padding: 0; flex-shrink: 0"
                @click="openResidentSearch"
                title="检索住户"
              >
                <el-icon><Search /></el-icon>
              </el-button>
            </div>
          </div>

          <template v-if="publishMode === 'idle'">
            <div class="publish-field">
              <span class="field-label"
                >物品标题 <span style="color: var(--red)">*</span></span
              >
              <el-input
                class="field-input"
                v-model="publishForm.title"
                placeholder="例：博世冲击钻套装"
                style="width: 100%"
              />
            </div>
            <div class="publish-field">
              <span class="field-label" style="display: block">物品描述</span>
              <el-input
                type="textarea"
                :rows="3"
                v-model="publishForm.desc"
                placeholder="描述物品现状、使用痕迹等..."
                style="width: 100%"
              />
            </div>
            <div style="display: flex; gap: 12px; margin-bottom: 16px">
              <div style="flex: 1">
                <span class="field-label" style="display: block">参考价格</span>
                <el-input
                  class="field-input"
                  type="number"
                  v-model="publishForm.price"
                  placeholder="¥"
                  style="width: 100%"
                />
              </div>
              <div style="flex: 1">
                <span class="field-label" style="display: block">借出天数</span>
                <el-select
                  class="field-input"
                  v-model="publishForm.days"
                  style="width: 100%"
                >
                  <el-option
                    v-for="d in 7"
                    :key="d"
                    :label="`${d}天`"
                    :value="d"
                  />
                </el-select>
              </div>
            </div>
            <p class="text-xs text-tertiary">
              发布内容将标注「物业代发」标签，有人联系时通知发送到代发住户
            </p>
          </template>

          <template v-else>
            <div class="publish-field">
              <span class="field-label"
                >求助标题 <span style="color: var(--red)">*</span></span
              >
              <el-input
                class="field-input"
                v-model="publishForm.title"
                placeholder="简要描述需要的帮助"
                style="width: 100%"
              />
            </div>
            <div class="publish-field">
              <span class="field-label" style="display: block">求助描述</span>
              <el-input
                type="textarea"
                :rows="3"
                v-model="publishForm.desc"
                placeholder="描述具体情况..."
                style="width: 100%"
              />
            </div>
            <div class="publish-field">
              <span class="field-label" style="display: block">紧急程度</span>
              <el-select
                class="field-input"
                v-model="publishForm.urgency"
                style="width: 100%"
              >
                <el-option label="一般" value="一般" />
                <el-option label="紧急" value="紧急" />
              </el-select>
            </div>
            <div>
              <span class="field-label" style="display: block">时间范围</span>
              <div style="display: flex; gap: 8px">
                <el-date-picker
                  v-model="publishForm.startTime"
                  type="datetime"
                  placeholder="起始时间"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DD HH:mm"
                  style="flex: 1"
                />
                <span style="align-self: center; color: var(--text-secondary)"
                  >—</span
                >
                <el-date-picker
                  v-model="publishForm.endTime"
                  type="datetime"
                  placeholder="终了时间"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DD HH:mm"
                  style="flex: 1"
                />
              </div>
            </div>
            <p class="text-xs text-tertiary mt-12">
              发布内容将标注「物业代发」标签，有人联系时通知发送到代发住户
            </p>
          </template>

          <template #footer>
            <el-button @click="publishVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="publishSubmitting"
              @click="submitPublish"
              >确认发布</el-button
            >
          </template>
        </el-dialog>

        <!-- 住户检索弹窗 -->
        <el-dialog v-model="residentVisible" title="住户查找" width="620px">
          <!-- 筛选 -->
          <div
            style="
              display: flex;
              gap: 8px;
              margin-bottom: 12px;
              flex-wrap: wrap;
            "
          >
            <el-select
              class="filter-select"
              v-model="residentFilterType"
              placeholder="类型"
              style="width: 90px"
              @change="loadAllResidents"
            >
              <el-option label="全部" value="" />
              <el-option label="业主" value="业主" />
              <el-option label="租客" value="租客" />
            </el-select>
            <el-select
              class="filter-select"
              v-model="residentFilterBuilding"
              placeholder="楼栋"
              style="width: 90px"
              @change="loadAllResidents"
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
              v-model="residentFilterUnit"
              placeholder="单元"
              style="width: 100px"
              :disabled="!residentFilterBuilding"
              @change="loadAllResidents"
            >
              <el-option label="全部" value="" />
              <el-option
                v-for="u in residentUnitOptions"
                :key="u.id"
                :label="u.name"
                :value="u.name"
              />
            </el-select>
            <el-input
              class="search-box"
              v-model="residentKeyword"
              placeholder="搜索姓名或房号..."
              style="width: 170px"
              @keydown.enter="loadAllResidents"
              @blur="loadAllResidents"
            />
          </div>
          <div style="max-height: 280px; overflow-y: auto">
            <template v-if="residentList.length">
              <div v-for="r in residentList" :key="r.id">
                <div
                  class="rs-item"
                  :class="{ selected: tempSelected === r.id }"
                  @click="pickResident(r)"
                >
                  <span style="flex: 1; font-size: 14px"
                    >{{ r.room }}({{ r.userType }}) -
                    {{ displayName(r.name) }}</span
                  >
                  <svg
                    v-if="tempSelected === r.id"
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="var(--accent)"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                </div>
                <div class="rs-separator"></div>
              </div>
            </template>
            <div v-else class="panel-empty">未找到匹配住户</div>
          </div>
          <template #footer>
            <el-button @click="residentVisible = false">取消</el-button>
            <el-button type="primary" @click="selectResident">确认</el-button>
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
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Search,
} from "@element-plus/icons-vue";
import { useAuthStore } from "../stores/auth";
import { useCommunityStore } from "../stores/community";
import { get, post, put } from "../utils/api";
import AppSidebar from "../components/AppSidebar.vue";

const router = useRouter();
const authStore = useAuthStore();
const communityStore = useCommunityStore();

const contentTabs = [
  { key: "show", label: "在线中" },
  { key: "progress", label: "进行中" },
  { key: "done", label: "已完成" },
  { key: "offline", label: "违规下架" },
];

const statusParamMap = {
  show: "showing",
  progress: "progressing",
  done: "completed",
  offline: "violation",
};

const activeTab = ref("show");
const filterType = ref("");
const filterBuilding = ref("");
const filterUnit = ref("");
const search = ref("");
const loading = ref(false);
const tableData = ref([]);
const totalCount = ref(0);
const currentPage = ref(0);
const totalPages = ref(0);
const pageSize = 10;
const selectedRows = ref([]);
const contentTableRef = ref(null);

// 根据所选楼栋计算单元筛选选项（主筛选）
const filterUnitOptions = computed(() => {
  if (!filterBuilding.value) return [];
  const buildingId = communityStore.getBuildingId(filterBuilding.value);
  return buildingId ? communityStore.getUnits(buildingId) : [];
});

// 住户检索弹窗的单元筛选选项
const residentUnitOptions = computed(() => {
  if (!residentFilterBuilding.value) return [];
  const buildingId = communityStore.getBuildingId(residentFilterBuilding.value);
  return buildingId ? communityStore.getUnits(buildingId) : [];
});

function handleSelectionChange(rows) {
  selectedRows.value = rows;
}

onMounted(() => {
  communityStore.fetchCommunityData();
  fetchContent();
});

// 辅助函数：解包后端的 Result 包装
function unwrap(response) {
  return response?.data?.data !== undefined
    ? response.data.data
    : response?.data;
}

async function fetchContent() {
  loading.value = true;
  try {
    const params = {
      status: statusParamMap[activeTab.value],
      type: filterType.value || undefined,
      building: filterBuilding.value || undefined,
      unit: filterUnit.value || undefined,
      search: search.value || undefined,
      page: currentPage.value,
      size: pageSize,
    };
    const res = await get("/api/admin/content", params);
    const pageData = unwrap(res);
    tableData.value = pageData?.content || [];
    totalCount.value = pageData?.totalElements || 0;
    totalPages.value = pageData?.totalPages || 0;
  } catch (e) {
    ElMessage.error("加载内容失败");
    tableData.value = [];
  } finally {
    loading.value = false;
  }
}

function switchTab(key) {
  activeTab.value = key;
  currentPage.value = 0;
  selectedRows.value = [];
  if (contentTableRef.value) {
    contentTableRef.value.clearSelection();
  }
  fetchContent();
}

function applyFilters() {
  // 去除空格、换行符等空白字符；仅当仍有有效内容时才作为搜索条件
  search.value = search.value.replace(/\s+/g, "");
  currentPage.value = 0;
  fetchContent();
}

function goPage(p) {
  currentPage.value = p;
  fetchContent();
}

function statusTag(status) {
  const map = {
    展示中: "tag tag-green",
    进行中: "tag tag-blue",
    已完成: "tag tag-gray",
    已下架: "tag tag-red",
  };
  return map[status] || "tag tag-gray";
}

// 展示中 → 在线中：后端沿用「展示中」状态值，B端统一展示为「在线中」
function statusLabel(status) {
  return status === "展示中" ? "在线中" : status;
}

function formatTime(ts) {
  if (!ts) return "";
  const d = new Date(ts);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function displayName(name) {
  if (!name) return "——";
  // 如果名称是纯 ASCII（英文/数字），返回占位符，避免住户列表中出现英文单词
  if (/^[\x00-\x7F\s]+$/.test(name)) return "——";
  return name;
}

// 详情
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailItem = ref(null);

// 从详情图片中过滤掉空白/无效的图片 URL
const validDetailImages = computed(() => {
  if (!detailItem.value?.images) return [];
  return detailItem.value.images.filter(
    (url) => url && typeof url === "string" && url.trim().length > 0,
  );
});

// 跟踪加载失败的图片；若全部失败则回退显示「无」
const failedImageIndexes = ref(new Set());
function onImageError(index) {
  failedImageIndexes.value.add(index);
}

// 详细弹窗的选中项导航：detailList 为打开时的勾选记录快照，detailPos 为当前位置
const detailList = ref([]);
const detailPos = ref(0);

async function openDetail(row) {
  detailVisible.value = true;
  detailLoading.value = true;
  detailItem.value = null;
  failedImageIndexes.value = new Set();
  try {
    const res = await get(`/api/admin/content/${row.id}`, { type: row.type });
    detailItem.value = unwrap(res);
  } catch (e) {
    ElMessage.error("加载详情失败");
  } finally {
    detailLoading.value = false;
  }
}

// 由表格勾选项打开详细弹窗（快照全部勾选项，支持左右箭头切换）
function openDetailFromSelection() {
  if (!selectedRows.value.length) return;
  detailList.value = [...selectedRows.value];
  detailPos.value = 0;
  openDetail(detailList.value[0]);
}

// 在勾选的多条记录间切换（delta = -1 上一条 / +1 下一条），越界忽略
function detailGo(delta) {
  const next = detailPos.value + delta;
  if (next < 0 || next >= detailList.value.length) return;
  detailPos.value = next;
  openDetail(detailList.value[next]);
}

// 下架
const offlineVisible = ref(false);
const offlineTarget = ref(null);
const offlineReasons = ref([]);
const offlineCustomReason = ref("");
const offlineSubmitting = ref(false);
const offlineReasonOptions = ["商业广告", "虚假信息", "违规物品", "骚扰内容"];

function openOffline(row) {
  offlineTarget.value = row;
  offlineReasons.value = [];
  offlineCustomReason.value = "";
  offlineVisible.value = true;
}

// 详细弹窗底部「下架」：关闭详细弹窗并打开下架确认弹窗
function offlineFromDetail() {
  if (!detailItem.value) return;
  detailVisible.value = false;
  openOffline(detailItem.value);
}

async function doOffline() {
  if (!offlineTarget.value) return;
  offlineSubmitting.value = true;
  try {
    await put(`/api/admin/content/${offlineTarget.value.id}/offline`, {
      targetType: offlineTarget.value.type,
      reasons: [...offlineReasons.value],
      customReason: offlineCustomReason.value.trim() || undefined,
    });
    offlineVisible.value = false;
    ElMessage.success(
      `「${offlineTarget.value.title}」已下架，通知已发送给发布者`,
    );
    fetchContent();
  } catch (e) {
    ElMessage.error("下架失败");
  } finally {
    offlineSubmitting.value = false;
  }
}

// 代发
const publishVisible = ref(false);
const publishMode = ref("idle");
const publishSubmitting = ref(false);
const publishForm = reactive({
  title: "",
  desc: "",
  price: "",
  days: 7,
  urgency: "一般",
  startTime: "",
  endTime: "",
});
const selectedResident = ref("");
const selectedResidentId = ref(null);

function openPublish() {
  publishMode.value = "idle";
  publishForm.title = "";
  publishForm.desc = "";
  publishForm.price = "";
  publishForm.days = 7;
  publishForm.urgency = "一般";
  publishForm.startTime = "";
  publishForm.endTime = "";
  selectedResident.value = "";
  selectedResidentId.value = null;
  publishVisible.value = true;
}

async function submitPublish() {
  if (!publishForm.title.trim()) {
    ElMessage.warning("请输入标题");
    return;
  }
  if (!selectedResidentId.value) {
    ElMessage.warning("请选择目标住户");
    return;
  }
  publishSubmitting.value = true;
  try {
    if (publishMode.value === "idle") {
      await post("/api/admin/proxy/idle", {
        userId: selectedResidentId.value,
        postType: "LEND",
        title: publishForm.title,
        description: publishForm.desc,
        category: "other",
        price: parseFloat(publishForm.price) || 0,
        maxDuration: publishForm.days,
      });
    } else {
      await post("/api/admin/proxy/help", {
        userId: selectedResidentId.value,
        title: publishForm.title,
        description: publishForm.desc,
        category: "other",
        isUrgent: publishForm.urgency === "紧急",
        timeStart: publishForm.startTime || undefined,
        timeEnd: publishForm.endTime || undefined,
      });
    }
    publishVisible.value = false;
    ElMessage.success("代发内容已发布，将标注「物业代发」标签");
    fetchContent();
  } catch (e) {
    ElMessage.error("代发失败");
  } finally {
    publishSubmitting.value = false;
  }
}

// 住户检索
const residentVisible = ref(false);
const tempSelected = ref(null);
const residentList = ref([]);
const residentFilterType = ref("");
const residentFilterBuilding = ref("");
const residentFilterUnit = ref("");
const residentKeyword = ref("");

async function loadAllResidents() {
  try {
    const params = {
      page: 0,
      size: 200,
      userType: residentFilterType.value || undefined,
      building: residentFilterBuilding.value || undefined,
      unit: residentFilterUnit.value || undefined,
      keyword: residentKeyword.value || undefined,
    };
    const res = await get("/api/admin/residents/search", params);
    const data = unwrap(res);
    residentList.value = data?.content
      ? data.content
      : Array.isArray(data)
        ? data
        : [];
  } catch (e) {
    residentList.value = [];
  }
}

function openResidentSearch() {
  tempSelected.value = selectedResidentId.value;
  residentFilterType.value = "";
  residentFilterBuilding.value = "";
  residentFilterUnit.value = "";
  residentKeyword.value = "";
  residentVisible.value = true;
  loadAllResidents();
}

function pickResident(r) {
  tempSelected.value = r.id;
  selectedResident.value = `${r.room}(${r.userType}) - ${displayName(r.name)}`;
}

function selectResident() {
  if (tempSelected.value) selectedResidentId.value = tempSelected.value;
  residentVisible.value = false;
}

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
    /* 已取消 */
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100%;
}
.tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.tag-blue {
  background: rgba(0, 113, 227, 0.1);
  color: var(--accent);
}
.tag-red {
  background: rgba(255, 59, 48, 0.1);
  color: var(--red);
}
.tag-orange {
  background: rgba(255, 149, 0, 0.1);
  color: var(--orange);
}
.tag-green {
  background: rgba(52, 199, 89, 0.1);
  color: var(--green);
}
.tag-gray {
  background: var(--bg);
  color: var(--text-secondary);
}
.panel-empty {
  padding: 48px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--text-tertiary);
}
:deep(.el-dialog__body) {
  padding-top: 0;
}
/* 发布者是第3列 */
:deep(.el-table__body tr td:nth-child(3)) {
  padding-left: 70px !important;
}
:deep(.el-table__header tr th:nth-child(3)) {
  padding-left: 70px !important;
}
</style>
