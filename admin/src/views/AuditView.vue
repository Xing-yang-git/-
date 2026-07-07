<template>
  <el-container class="admin-layout">
    <el-aside width="220px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar" height="56px">
        <div class="topbar-left">
          <span class="topbar-title">住户管理</span>
        </div>
        <div class="topbar-right">
          <el-dropdown @command="handleCommand">
            <span style="cursor:pointer;display:flex;align-items:center;gap:4px;">
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
        <div class="panel">
          <!-- Tabs -->
          <div style="padding:14px 20px 0;">
            <div class="segment-row">
              <button
                v-for="t in tabs"
                :key="t.key"
                class="segment-btn"
                :class="{ active: activeTab === t.key }"
                @click="switchTab(t.key)"
              >
                {{ t.label }}
                <span v-if="t.count !== undefined" class="count" style="margin-left:4px;opacity:0.5;font-size:11px;">{{ t.count }}</span>
              </button>
            </div>
          </div>

          <!-- Filters -->
          <div class="filter-row">
            <el-select v-model="filterType" placeholder="全部类型" size="small" style="width:120px;" clearable>
              <el-option label="业主" value="业主" />
              <el-option label="租客" value="租客" />
            </el-select>
            <el-select v-model="filterBuilding" placeholder="全部楼栋" size="small" style="width:120px;" clearable>
              <el-option v-for="i in 8" :key="i" :label="`${i}栋`" :value="`${i}栋`" />
            </el-select>
            <el-input v-model="search" placeholder="搜索姓名或房号..." size="small" style="width:200px;" clearable />
            <span style="flex:1;"></span>
            <template v-if="activeTab === 'pending'">
              <el-button size="small" type="primary" @click="batchAction('approve')">批量通过</el-button>
              <el-button size="small" @click="batchAction('reject')">批量驳回</el-button>
            </template>
          </div>

          <!-- Loading -->
          <div v-if="loading" class="panel-empty" style="padding:48px;text-align:center;color:var(--text-tertiary);">加载中...</div>

          <!-- Error -->
          <div v-else-if="error && !filteredPending.length && !filteredPassed.length && !filteredRejected.length" class="panel-empty" style="padding:48px;text-align:center;color:var(--red);">
            加载失败：{{ error }}
            <el-button size="small" style="margin-left:8px;" @click="loadData">重试</el-button>
          </div>

          <!-- Tables (only when not loading) -->
          <template v-else>
            <!-- Pending -->
            <div v-show="activeTab === 'pending' || activeTab === 'all'">
              <template v-if="activeTab === 'all'">
                <div class="section-label" style="padding:12px 20px 4px;font-size:12px;font-weight:600;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:0.06em;">待审核</div>
              </template>
              <el-table v-if="filteredPending.length" :data="filteredPending" style="width:100%;" @selection-change="handleSelectionChange">
                <el-table-column v-if="activeTab === 'pending'" type="selection" width="40" />
                <el-table-column prop="name" label="申请人" />
                <el-table-column prop="room" label="房号" />
                <el-table-column label="住户类型" align="center" width="100">
                  <template #default="{ row }">
                    <span :class="['badge', row.type === '业主' ? 'badge-info' : 'badge-warning']">{{ row.type }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="time" label="提交时间" width="180" />
                <el-table-column v-if="activeTab !== 'all'" label="操作" align="center" width="160">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" link @click="openAudit(row)">审核</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="activeTab === 'pending' && !filteredPending.length && !loading" class="panel-empty" style="padding:48px;text-align:center;color:var(--text-tertiary);">暂无待审核住户</div>
            </div>

            <!-- Passed -->
            <div v-show="activeTab === 'passed' || activeTab === 'all'">
              <template v-if="activeTab === 'all'">
                <div class="section-label" style="padding:12px 20px 4px;font-size:12px;font-weight:600;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:0.06em;">已通过</div>
              </template>
              <el-table v-if="filteredPassed.length" :data="filteredPassed" style="width:100%;" v-show="activeTab === 'passed' || (activeTab === 'all' && filteredPassed.length)">
                <el-table-column prop="name" label="申请人" />
                <el-table-column prop="room" label="房号" />
                <el-table-column label="类型" align="center" width="100">
                  <template #default="{ row }">
                    <span :class="['badge', row.type === '业主' ? 'badge-info' : 'badge-warning']">{{ row.type }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="time" label="审核时间" width="160" />
              </el-table>
              <div v-if="activeTab === 'passed' && !filteredPassed.length && !loading" class="panel-empty" style="padding:48px;text-align:center;color:var(--text-tertiary);">暂无已通过记录</div>
            </div>

            <!-- Rejected -->
            <div v-show="activeTab === 'rejected' || activeTab === 'all'">
              <template v-if="activeTab === 'all'">
                <div class="section-label" style="padding:12px 20px 4px;font-size:12px;font-weight:600;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:0.06em;">已驳回</div>
              </template>
              <el-table v-if="filteredRejected.length" :data="filteredRejected" style="width:100%;" v-show="activeTab === 'rejected' || (activeTab === 'all' && filteredRejected.length)">
                <el-table-column prop="name" label="申请人" />
                <el-table-column prop="room" label="房号" />
                <el-table-column label="类型" align="center" width="100">
                  <template #default="{ row }">
                    <span :class="['badge', row.type === '业主' ? 'badge-info' : 'badge-warning']">{{ row.type }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="time" label="驳回时间" width="160" />
              </el-table>
              <div v-if="activeTab === 'rejected' && !filteredRejected.length && !loading" class="panel-empty" style="padding:48px;text-align:center;color:var(--text-tertiary);">暂无已驳回记录</div>
            </div>
          </template>
        </div>

        <!-- Audit Dialog -->
        <el-dialog v-model="auditVisible" :title="`审核住户：${auditTarget?.name || ''}`" width="480px">
          <div v-if="auditTarget">
            <div class="detail-row"><span class="dl">房号</span><span class="dv">{{ auditTarget.room }}</span></div>
            <div class="detail-row"><span class="dl">手机</span><span class="dv">{{ auditTarget.phone || '未填写' }}</span></div>
            <div class="detail-row"><span class="dl">姓名</span><span class="dv">{{ auditTarget.name }}</span></div>
            <div class="detail-row"><span class="dl">类型</span><span class="dv">{{ auditTarget.type || '业主' }}</span></div>
            <div v-if="auditTarget.docImages && auditTarget.docImages.length" style="margin-top:8px;">
              <div style="font-size:13px;color:var(--text-secondary);margin-bottom:6px;">证件照：</div>
              <div style="display:flex;gap:8px;flex-wrap:wrap;">
                <img
                  v-for="(img, i) in auditTarget.docImages"
                  :key="i"
                  :src="img"
                  style="width:80px;height:80px;object-fit:cover;border-radius:8px;cursor:pointer;"
                  @click="window.open(img)"
                />
              </div>
            </div>
            <div class="dv-divider"></div>

            <!-- Result -->
            <div style="margin-bottom:8px;font-weight:600;">审核结果：</div>
            <el-radio-group v-model="auditResult" style="display:flex;flex-direction:column;gap:4px;">
              <el-radio value="pass">通过</el-radio>
              <el-radio value="reject">驳回</el-radio>
            </el-radio-group>

            <!-- Reject reasons -->
            <div v-if="auditResult === 'reject'" style="margin-top:12px;">
              <div style="margin-bottom:6px;font-weight:600;">驳回原因（可多选）：</div>
              <el-checkbox-group v-model="rejectReasons" style="display:flex;flex-direction:column;gap:4px;">
                <el-checkbox label="信息不完整" />
                <el-checkbox label="房号与证件不符" />
                <el-checkbox label="非本小区住户" />
              </el-checkbox-group>
              <el-input v-model="rejectCustomReason" placeholder="自定义原因..." size="small" style="margin-top:8px;" />
            </div>
          </div>
          <template #footer>
            <el-button @click="auditVisible = false">取消</el-button>
            <el-button type="primary" @click="submitAudit">确认审核</el-button>
          </template>
        </el-dialog>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowDown } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import { get, put } from '../utils/api';
import AppSidebar from '../components/AppSidebar.vue';

const router = useRouter();
const authStore = useAuthStore();

// --- User type label mapping ---
const TYPE_MAP = { owner: '业主', renter: '租客', tenant: '租客', resident: '居民' };

function mapType(userType) {
  return TYPE_MAP[userType] || userType || '居民';
}

function formatTime(dateStr) {
  if (!dateStr) return '';
  // Convert ISO/array to readable string
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// --- Tabs ---
const activeTab = ref('pending');
const tabCounts = reactive({ pending: 0, approved: 0, rejected: 0, all: 0 });

const tabs = computed(() => [
  { key: 'pending',  label: '待审核', count: tabCounts.pending },
  { key: 'passed',   label: '已通过', count: tabCounts.approved },
  { key: 'rejected', label: '已驳回', count: tabCounts.rejected },
  { key: 'all',      label: '全部',   count: tabCounts.all }
]);

// --- Data ---
const pendingData = ref([]);
const passedData = ref([]);
const rejectedData = ref([]);
const loading = ref(false);
const error = ref('');

/** Load list for the active tab */
async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    let status = null;
    if (activeTab.value === 'pending')  status = 'pending';
    else if (activeTab.value === 'passed')   status = 'approved';
    else if (activeTab.value === 'rejected') status = 'rejected';
    // 'all': status stays null

    const res = await get('/api/admin/audits', { status, page: 0, size: 200 });
    // Backend returns { code:200, data: PageDTO }
    const page = res.data?.data;
    const list = (page?.content || []).map(u => ({
      id: u.id,
      name: u.name || '',
      room: u.userRoom || '',
      type: mapType(u.userType),
      time: formatTime(u.createdAt),
      phone: u.phone || '',
      status: u.authStatus,
      docImages: u.docImages || [],
      rejectReason: u.rejectReason || ''
    }));

    if (activeTab.value === 'pending')  pendingData.value = list;
    else if (activeTab.value === 'passed')   passedData.value = list;
    else if (activeTab.value === 'rejected') rejectedData.value = list;
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载失败';
    ElMessage.error(error.value);
  } finally {
    loading.value = false;
  }
}

/** Load tab counts */
async function loadCounts() {
  try {
    const res = await get('/api/admin/audits/counts');
    const c = res.data?.data;
    if (c) {
      tabCounts.pending  = c.pending  || 0;
      tabCounts.approved = c.approved || 0;
      tabCounts.rejected = c.rejected || 0;
      tabCounts.all      = c.all      || 0;
    }
  } catch { /* counts are non-critical */ }
}

onMounted(() => {
  loadData();
  loadCounts();
});

// Switch tab → reload data
function switchTab(key) {
  activeTab.value = key;
  loadData();
}

// --- Filters ---
const filterType = ref('');
const filterBuilding = ref('');
const search = ref('');
const selectedRows = ref([]);

function matchFilter(item) {
  if (filterType.value && item.type !== filterType.value) return false;
  if (filterBuilding.value && !item.room.startsWith(filterBuilding.value)) return false;
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

// --- Batch actions ---
async function batchAction(action) {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选申请人');
    return;
  }
  const approved = action === 'approve';
  const items = [...selectedRows.value];
  let ok = 0, fail = 0;

  for (const row of items) {
    try {
      await put(`/api/admin/audits/${row.id}`, { approved, reason: approved ? null : '批量驳回' });
      ok++;
    } catch { fail++; }
  }

  selectedRows.value = [];
  if (ok) ElMessage.success(`${action === 'approve' ? '已批量通过' : '已批量驳回'} ${ok} 人`);
  if (fail) ElMessage.warning(`${fail} 人操作失败`);
  loadData();
  loadCounts();
}

// --- Audit modal ---
const auditVisible = ref(false);
const auditTarget = ref(null);
const auditResult = ref('pass');
const rejectReasons = ref([]);
const rejectCustomReason = ref('');

function openAudit(row) {
  auditTarget.value = row;
  auditResult.value = 'pass';
  rejectReasons.value = [];
  rejectCustomReason.value = '';
  auditVisible.value = true;
}

async function submitAudit() {
  const approved = auditResult.value === 'pass';
  const reasons = [...rejectReasons.value];
  if (rejectCustomReason.value.trim()) reasons.push(rejectCustomReason.value.trim());
  const reason = approved ? null : (reasons.join('；') || '未说明');

  try {
    await put(`/api/admin/audits/${auditTarget.value.id}`, { approved, reason });
    auditVisible.value = false;
    ElMessage.success(approved ? '审核通过' : '已驳回');
    loadData();
    loadCounts();
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败');
  }
}

function handleCommand(cmd) {
  if (cmd === 'logout') handleLogout();
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    });
    authStore.logout();
    router.push('/login');
  } catch { /* cancelled */ }
}
</script>

<style scoped>
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
</style>
