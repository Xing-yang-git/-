<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="topbar-title">住户管理</span>
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
          <div style="padding: 14px 20px 0">
            <div class="segment-row">
              <button
                v-for="t in tabs"
                :key="t.key"
                class="segment-btn"
                :class="{ active: activeTab === t.key }"
                @click="switchTab(t.key)"
              >
                {{ t.label }}
                <span
                  v-if="t.count !== undefined"
                  class="count"
                  style="margin-left: 4px; opacity: 0.5; font-size: 11px"
                  >{{ t.count }}</span
                >
              </button>
            </div>
          </div>

          <!-- 筛选 -->
          <div class="filter-row">
            <el-select
              class="filter-select"
              v-model="filterType"
              placeholder="住户类型"
              style="width: 110px"
            >
              <el-option label="全部" value="" />
              <el-option label="业主" value="业主" />
              <el-option label="租客" value="租客" />
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
              placeholder="搜索后按回车或点击空白处..."
              style="width: 190px"
            />
            <el-button
              v-if="activeTab === 'pending'"
              type="primary"
              size="default"
              style="margin-left: 8px"
              :disabled="!selectedRows.length"
              @click="openApproval"
              >审批</el-button
            >
            <el-button
              v-else-if="activeTab !== 'pending'"
              type="primary"
              size="default"
              style="margin-left: 8px"
              :disabled="!selectedRows.length"
              @click="openResidentDetail"
              >详细</el-button
            >
            <span style="flex: 1"></span>
          </div>

          <!-- 面板主体（填充面板高度，内部滚动）-->
          <div class="uf-body">
            <!-- 加载中 -->
            <div
              v-if="loading"
              class="panel-empty"
              style="
                padding: 48px;
                text-align: center;
                color: var(--text-tertiary);
              "
            >
              加载中...
            </div>

            <!-- 错误提示 -->
            <div
              v-else-if="
                error &&
                !filteredPending.length &&
                !filteredPassed.length &&
                !filteredRejected.length
              "
              class="panel-empty"
              style="padding: 48px; text-align: center; color: var(--red)"
            >
              加载失败：{{ error }}
              <el-button
                size="default"
                style="margin-left: 8px"
                @click="loadData"
                >重试</el-button
              >
            </div>

            <!-- 表格（仅在非加载且无错误时显示）-->
            <template v-else>
              <!-- 待审核 -->
              <div v-show="activeTab === 'pending'" class="panel">
                <el-table
                  v-if="filteredPending.length"
                  :data="filteredPending"
                  style="width: 100%"
                  @selection-change="handleSelectionChange"
                >
                  <el-table-column type="selection" width="50" />
                  <el-table-column prop="name" label="申请人" min-width="80" />
                  <el-table-column prop="room" label="房号" min-width="100" />
                  <el-table-column
                    label="住户类型"
                    align="center"
                    min-width="60"
                  >
                    <template #default="{ row }">
                      <span
                        :class="[
                          'tag',
                          row.type === 'owner' ? 'tag-blue' : 'tag-orange',
                        ]"
                        >{{ row.type }}</span
                      >
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="time"
                    label="提交时间"
                    min-width="120"
                    align="center"
                  />
                  <el-table-column label="状态" align="center" min-width="60">
                    <template #default="{ row }">
                      <span
                        :class="
                          row.statusClass === 'tag-red'
                            ? 'tag tag-red'
                            : 'tag tag-green'
                        "
                        >{{ row.status || "正常" }}</span
                      >
                    </template>
                  </el-table-column>
                </el-table>
                <div
                  v-if="!filteredPending.length && !loading"
                  class="panel-empty"
                >
                  暂无待审核住户
                </div>
              </div>

              <!-- 全部住户 -->
              <div v-show="activeTab === 'all'" class="panel">
                <el-table
                  v-if="filteredPassed.length"
                  :data="filteredPassed"
                  style="width: 100%"
                  @selection-change="handleSelectionChange"
                >
                  <el-table-column type="selection" width="50" />
                  <el-table-column prop="name" label="住户" min-width="80" />
                  <el-table-column prop="room" label="房号" min-width="100" />
                  <el-table-column label="类型" align="center" min-width="60">
                    <template #default="{ row }">
                      <span
                        :class="[
                          'tag',
                          row.type === 'owner' ? 'tag-blue' : 'tag-orange',
                        ]"
                        >{{ row.type }}</span
                      >
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="time"
                    label="审核时间"
                    align="center"
                    min-width="120"
                  />
                  <el-table-column
                    prop="auditor"
                    label="审核人"
                    min-width="60"
                  />
                </el-table>
                <div
                  v-if="!filteredPassed.length && !loading"
                  class="panel-empty"
                >
                  暂无住户
                </div>
              </div>

              <!-- 已驳回 -->
              <div v-show="activeTab === 'rejected'" class="panel">
                <el-table
                  v-if="filteredRejected.length"
                  :data="filteredRejected"
                  style="width: 100%"
                  @selection-change="handleSelectionChange"
                >
                  <el-table-column type="selection" width="50" />
                  <el-table-column prop="name" label="申请人" min-width="80" />
                  <el-table-column prop="room" label="房号" min-width="100" />
                  <el-table-column
                    label="住户类型"
                    align="center"
                    min-width="60"
                  >
                    <template #default="{ row }">
                      <span
                        :class="[
                          'tag',
                          row.type === 'owner' ? 'tag-blue' : 'tag-orange',
                        ]"
                        >{{ row.type }}</span
                      >
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="time"
                    label="驳回时间"
                    min-width="120"
                    align="center"
                  />
                  <el-table-column prop="rejectReason" label="驳回原因" />
                </el-table>
                <div
                  v-if="!filteredRejected.length && !loading"
                  class="panel-empty"
                >
                  暂无已驳回记录
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 审核弹窗 -->
        <el-dialog
          v-model="auditVisible"
          :title="`审核住户：${auditTarget?.name || ''}`"
          width="480px"
          @closed="onAuditClosed"
        >
          <div v-if="auditTarget">
            <div class="detail-row">
              <span class="dl">小区</span><span class="dv">翠湖花园</span>
            </div>
            <div class="detail-row">
              <span class="dl">房号</span
              ><span class="dv">{{ auditTarget.room }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">手机</span
              ><span class="dv">{{ auditTarget.phone || "未填写" }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">姓名</span
              ><span class="dv">{{ auditTarget.name }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">类型</span
              ><span class="dv">{{ auditTarget.type || "owner" }}</span>
            </div>
            <div class="detail-row" style="align-items: flex-start">
              <span class="dl">{{
                auditTarget.type === "tenant"
                  ? "租房合同"
                  : auditTarget.type === "owner"
                    ? "房产证"
                    : "证件照"
              }}</span>
              <span class="dv">
                <span
                  v-if="auditTarget.docImages && auditTarget.docImages.length"
                  style="display: flex; gap: 8px; flex-wrap: wrap"
                >
                  <el-image
                    v-for="(img, i) in auditTarget.docImages"
                    :key="i"
                    :src="img"
                    :preview-src-list="auditTarget.docImages"
                    :initial-index="i"
                    fit="cover"
                    preview-teleported
                    hide-on-click-modal
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
            <div class="dv-divider"></div>

            <!-- 驳回原因（点「驳回」时填写） -->
            <div style="margin-top: 4px">
              <div style="margin-bottom: 6px; font-weight: 600">
                驳回原因（驳回时填写，可多选）：
              </div>
              <el-checkbox-group
                v-model="rejectReasons"
                style="
                  display: flex;
                  flex-direction: column;
                  align-items: flex-start;
                  gap: 4px;
                "
                @change="rejectError = ''"
              >
                <el-checkbox label="信息不完整" />
                <el-checkbox label="房号与证件不符" />
                <el-checkbox label="非本小区住户" />
              </el-checkbox-group>
              <el-input
                v-model="rejectCustomReason"
                type="textarea"
                :rows="3"
                resize="none"
                placeholder="自定义原因..."
                style="margin-top: 8px"
                @input="rejectError = ''"
              />
              <p v-if="rejectError" class="field-error">{{ rejectError }}</p>
            </div>
          </div>
          <template #footer>
            <div style="display: flex; align-items: center; gap: 8px">
              <el-button
                :icon="ArrowLeft"
                circle
                :disabled="batchPos === 0"
                @click="batchGo(-1)"
              />
              <span class="text-sm text-secondary"
                >{{ batchPos + 1 }} / {{ batchList.length }}</span
              >
              <el-button
                :icon="ArrowRight"
                circle
                :disabled="batchPos === batchList.length - 1"
                @click="batchGo(1)"
              />
              <span style="flex: 1"></span>
              <el-button @click="auditVisible = false">取消</el-button>
              <el-button type="primary" :disabled="hasRejectContent" @click="submitAudit(true)"
                >同意</el-button
              >
              <el-button type="danger" @click="submitAudit(false)"
                >驳回</el-button
              >
            </div>
          </template>
        </el-dialog>

        <!-- 住户详情弹窗（已通过 / 已驳回） -->
        <el-dialog v-model="detailVisible" title="住户详情" width="480px">
          <div v-if="detailRow">
            <div class="detail-row">
              <span class="dl">姓名</span
              ><span class="dv">{{ detailRow.name || "—" }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">房号</span
              ><span class="dv">{{ detailRow.room || "—" }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">小区</span><span class="dv">翠湖花园</span>
            </div>
            <div class="detail-row">
              <span class="dl">住户类型</span
              ><span class="dv">{{ detailRow.type || "—" }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">手机</span
              ><span class="dv">{{ detailRow.phone || "未填写" }}</span>
            </div>
            <div class="detail-row">
              <span class="dl">审核结果</span>
              <span class="dv">
                <span
                  :class="
                    detailTab === 'passed' ? 'tag tag-green' : 'tag tag-red'
                  "
                  >{{ detailTab === "all" ? "已通过" : "已驳回" }}</span
                >
              </span>
            </div>
            <div class="detail-row">
              <span class="dl">{{
                detailTab === "all" ? "审核时间" : "驳回时间"
              }}</span
              ><span class="dv">{{ detailRow.time || "—" }}</span>
            </div>
            <div v-if="detailTab === 'passed'" class="detail-row">
              <span class="dl">审核人</span
              ><span class="dv">{{ detailRow.auditor || "—" }}</span>
            </div>
            <div v-else class="detail-row" style="align-items: flex-start">
              <span class="dl">驳回原因</span
              ><span class="dv" style="white-space: pre-wrap">{{
                detailRow.rejectReason || "—"
              }}</span>
            </div>
            <div class="detail-row" style="align-items: flex-start">
              <span class="dl">{{
                detailRow.type === "tenant"
                  ? "租房合同"
                  : detailRow.type === "owner"
                    ? "房产证"
                    : "证件照"
              }}</span>
              <span class="dv">
                <span
                  v-if="detailRow.docImages && detailRow.docImages.length"
                  style="display: flex; gap: 8px; flex-wrap: wrap"
                >
                  <el-image
                    v-for="(img, i) in detailRow.docImages"
                    :key="i"
                    :src="img"
                    :preview-src-list="detailRow.docImages"
                    :initial-index="i"
                    fit="cover"
                    preview-teleported
                    hide-on-click-modal
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
import { get, put } from "../utils/api";
import AppSidebar from "../components/AppSidebar.vue";

const router = useRouter();
const authStore = useAuthStore();
const communityStore = useCommunityStore();

// --- 用户类型标签映射 ---
// 用户类型: 数据库存储中文值（业主/租客/admin/super_admin）
const TYPE_MAP = {
  业主: "业主",
  租客: "租客",
  admin: "管理员",
  super_admin: "超级管理员",
};

function mapType(userType) {
  return TYPE_MAP[userType] || userType || "—";
}

function formatTime(dateStr) {
  if (!dateStr) return "";
  // 将 ISO 格式/数组转换为可读字符串
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// 超时状态：待审核提交超过 24 小时未处理视为「超时」（红），否则「正常」（绿）。
// 对齐原型的 status / statusClass 字段（原型为写死的 mock 数据，此处按提交时间实时计算）。
function computeOvertime(dateStr) {
  const normal = { status: "正常", statusClass: "tag-green" };
  if (!dateStr) return normal;
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return normal;
  const hours = (Date.now() - d.getTime()) / 3600000;
  return hours > 24 ? { status: "超时", statusClass: "tag-red" } : normal;
}

// --- 标签页 ---
const activeTab = ref("pending");
const tabCounts = reactive({ pending: 0, approved: 0, rejected: 0 });

const tabs = computed(() => [
  { key: "pending", label: "待审核", count: tabCounts.pending },
  { key: "rejected", label: "已驳回", count: tabCounts.rejected },
  { key: "all", label: "全部住户", count: tabCounts.approved },
]);

// --- 数据 ---
const pendingData = ref([]);
const passedData = ref([]);
const rejectedData = ref([]);
const loading = ref(false);
const error = ref("");

/** 加载当前标签页的数据列表 */
async function loadData() {
  loading.value = true;
  error.value = "";
  try {
    let status = null;
    if (activeTab.value === "pending") status = "pending";
    else if (activeTab.value === "all") status = "approved";
    else if (activeTab.value === "rejected") status = "rejected";
    // 'all' 时不传 status

    const res = await get("/api/admin/audits", { status, page: 0, size: 200 });
    // 后端返回 { code:200, data: PageDTO }
    const page = res.data?.data;
    const list = (page?.content || []).map((u) => {
      const overtime = computeOvertime(u.createdAt);
      return {
        id: u.id,
        name: u.name || "",
        room: u.userRoom || "",
        type: mapType(u.userType),
        time: formatTime(u.createdAt),
        phone: u.phone || "",
        status: overtime.status,
        statusClass: overtime.statusClass,
        docImages: u.docImages || [],
        rejectReason: u.rejectReason || "",
        auditor: u.auditor || u.auditorName || "",
      };
    });

    if (activeTab.value === "pending") pendingData.value = list;
    else if (activeTab.value === "all") passedData.value = list;
    else if (activeTab.value === "rejected") rejectedData.value = list;
  } catch (e) {
    error.value = e.response?.data?.message || e.message || "加载失败";
    ElMessage.error(error.value);
  } finally {
    loading.value = false;
  }
}

/** 加载各标签页计数 */
async function loadCounts() {
  try {
    const res = await get("/api/admin/audits/counts");
    const c = res.data?.data;
    if (c) {
      tabCounts.pending = c.pending || 0;
      tabCounts.approved = c.approved || 0;
      tabCounts.rejected = c.rejected || 0;
    }
  } catch {
    /* 计数加载失败不影响主要功能 */
  }
}

onMounted(() => {
  communityStore.fetchCommunityData();
  loadData();
  loadCounts();
});

// 切换标签页 → 重新加载数据
function switchTab(key) {
  activeTab.value = key;
  selectedRows.value = [];
  loadData();
}

// --- 筛选 ---
const filterType = ref("");
const filterBuilding = ref("");
const filterUnit = ref("");
const search = ref("");
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

function matchFilter(item) {
  if (filterType.value && item.type !== filterType.value) return false;
  if (filterBuilding.value && !item.room.startsWith(filterBuilding.value))
      return false;
  if (filterUnit.value && !matchUnit(item.room, filterUnit.value))
      return false;
  if (search.value) {
    const s = search.value.trim();
    if (!item.name.includes(s) && !item.room.includes(s)) return false;
  }
  return true;
}

const filteredPending = computed(() => pendingData.value.filter(matchFilter));
const filteredPassed = computed(() => passedData.value.filter(matchFilter));
const filteredRejected = computed(() => rejectedData.value.filter(matchFilter));

function handleSelectionChange(rows) {
  selectedRows.value = rows;
}

// --- 住户详情（已通过 / 已驳回 tab 的「详细」）---
const detailVisible = ref(false);
const detailList = ref([]);
const detailPos = ref(0);
const detailTab = ref("passed"); // 打开时的 tab：passed | rejected
const detailRow = computed(() => detailList.value[detailPos.value] || null);

// 以勾选的住户打开详情弹窗（快照全部勾选项，支持左右箭头切换）
function openResidentDetail() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先勾选住户");
    return;
  }
  detailList.value = [...selectedRows.value];
  detailPos.value = 0;
  detailTab.value = activeTab.value;
  detailVisible.value = true;
}

// 在勾选的多条住户间切换（delta = -1 上一条 / +1 下一条），越界忽略。
function detailGo(delta) {
  const next = detailPos.value + delta;
  if (next < 0 || next >= detailList.value.length) return;
  detailPos.value = next;
}

// --- Approval ---
// 审批：以勾选的申请人打开审核弹窗，底部箭头可在选中项间上/下翻，
// 弹窗内「同意 / 驳回」直接对当前记录做出审核结论。
async function openApproval() {
  if (!selectedRows.value.length) {
    ElMessage.warning("请先勾选申请人");
    return;
  }
  batchList.value = [...selectedRows.value];
  batchPos.value = 0;
  batchMode.value = true;
  setAuditTarget(batchList.value[0]);
  auditVisible.value = true;
}

// 在选中项之间跳转（delta = -1 上一条 / +1 下一条），越界忽略。
function batchGo(delta) {
  const next = batchPos.value + delta;
  if (next < 0 || next >= batchList.value.length) return;
  batchPos.value = next;
  setAuditTarget(batchList.value[next]);
}

// --- 审核弹窗 ---
const auditVisible = ref(false);
const auditTarget = ref(null);
const rejectReasons = ref([]);
const rejectCustomReason = ref("");
const rejectError = ref("");

// 若勾选了驳回原因或填写了自定义原因，则禁用"同意"按钮
const hasRejectContent = computed(() => {
  return rejectReasons.value.length > 0 || rejectCustomReason.value.trim().length > 0;
});

// 批量审核导航状态
const batchMode = ref(false);
const batchList = ref([]);
const batchPos = ref(0);

// 载入一条待审核数据到表单并重置校验
function setAuditTarget(row) {
  auditTarget.value = row;
  rejectReasons.value = [];
  rejectCustomReason.value = "";
  rejectError.value = "";
}

async function submitAudit(approved) {
  // 驳回时：驳回原因必填 —— 至少勾选一项，或自定义原因去除空白/换行后非空
  if (!approved) {
    const hasCustom = rejectCustomReason.value.trim().length > 0;
    if (!rejectReasons.value.length && !hasCustom) {
      rejectError.value = "请至少选择一项或填写有效的驳回原因";
      return;
    }
  }
  rejectError.value = "";

  const reasons = [...rejectReasons.value];
  const customText = rejectCustomReason.value.trim();
  if (customText) reasons.push(customText);
  const reason = approved ? null : reasons.join("；") || "未说明";

  try {
    await put(`/api/admin/audits/${auditTarget.value.id}`, {
      approved,
      reason,
    });
    ElMessage.success(approved ? "审核通过" : "已驳回");
    // 批量模式下：还有下一条则前进继续审核，否则关闭（关闭时统一刷新）
    if (batchMode.value && batchPos.value < batchList.value.length - 1) {
      batchGo(1);
    } else {
      auditVisible.value = false;
      if (!batchMode.value) {
        loadData();
        loadCounts();
      }
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || "操作失败");
  }
}

// 弹窗关闭后：批量模式统一刷新数据与计数并复位状态
function onAuditClosed() {
  if (batchMode.value) {
    batchMode.value = false;
    batchList.value = [];
    batchPos.value = 0;
    loadData();
    loadCounts();
  }
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

