<template>
  <el-container class="admin-layout">
    <el-aside width="220px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar" height="56px">
        <div class="topbar-left">
          <span class="topbar-title">内容管理</span>
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
          <!-- Tabs + Publish -->
          <div style="display:flex;align-items:center;padding:14px 20px 0;">
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
            <button class="btn btn-primary btn-sm" style="margin-left:auto;" @click="openPublish">物业代发</button>
          </div>

          <!-- Filters -->
          <div class="filter-row">
            <select class="filter-select" v-model="filterType" @change="applyFilters">
              <option value="">全部类型</option>
              <option value="idle">物品互借</option>
              <option value="help">技能互助</option>
            </select>
            <select class="filter-select" v-model="filterBuilding" @change="applyFilters">
              <option value="">全部楼栋</option>
              <option v-for="b in buildings" :key="b" :value="b">{{ b }}</option>
            </select>
            <input
              class="search-box"
              type="text"
              v-model="search"
              placeholder="搜索内容标题..."
              @keydown.enter="applyFilters"
              @blur="applyFilters"
            />
            <span style="flex:1;"></span>
            <span class="text-sm text-secondary">共 {{ totalCount }} 条</span>
          </div>

          <!-- Loading -->
          <div v-if="loading" class="panel-empty">加载中...</div>

          <!-- Table -->
          <div class="panel-body-scroll" v-else-if="tableData.length">
            <table class="table">
              <thead>
                <tr>
                  <th class="text-center">类型</th>
                  <th>标题</th>
                  <th>发布者</th>
                  <th v-if="activeTab === 'offline'">下架时间</th>
                  <th v-if="activeTab === 'offline'">原因</th>
                  <th v-if="activeTab === 'offline'">操作人</th>
                  <th v-else>发布时间</th>
                  <th v-if="activeTab !== 'offline'" class="text-center">状态</th>
                  <th class="text-center">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, idx) in tableData" :key="idx">
                  <td class="text-center">
                    <span :class="['tag', row.type === 'idle' ? 'tag-blue' : 'tag-orange']">
                      {{ row.type === 'idle' ? '互借' : '互助' }}
                    </span>
                  </td>
                  <td>
                    {{ row.title }}
                    <span v-if="row.isProxy" class="tag tag-orange" style="margin-left:4px;font-size:10px;">物业代发</span>
                  </td>
                  <td>{{ row.publisherName }}</td>
                  <td v-if="activeTab === 'offline'">{{ formatTime(row.violatedAt) }}</td>
                  <td v-if="activeTab === 'offline'">{{ row.violationReason || row.violationType || '—' }}</td>
                  <td v-if="activeTab === 'offline'">{{ row.violatorName || '—' }}</td>
                  <td v-else>{{ formatTime(row.createdAt) }}</td>
                  <td v-if="activeTab !== 'offline'" class="text-center">
                    <span :class="statusTag(row.displayStatus)">{{ row.displayStatus }}</span>
                  </td>
                  <td class="text-center">
                    <button class="btn btn-sm btn-ghost" @click="openDetail(row)">详情</button>
                    <button v-if="row.displayStatus === '展示中'" class="btn btn-sm btn-ghost" style="color:#d44;" @click="openOffline(row)">下架</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="panel-empty">暂无内容</div>

          <!-- Pagination -->
          <div style="display:flex;justify-content:flex-end;padding:12px 20px;" v-if="totalPages > 1">
            <div style="display:flex;gap:4px;align-items:center;">
              <button class="btn btn-sm btn-ghost" :disabled="currentPage <= 0" @click="goPage(currentPage - 1)">上一页</button>
              <span class="text-sm text-secondary">{{ currentPage + 1 }} / {{ totalPages }}</span>
              <button class="btn btn-sm btn-ghost" :disabled="currentPage >= totalPages - 1" @click="goPage(currentPage + 1)">下一页</button>
            </div>
          </div>
        </div>

        <!-- Detail Dialog -->
        <el-dialog v-model="detailVisible" :title="detailTitle" width="560px">
          <div v-if="detailLoading" style="text-align:center;padding:20px;">加载中...</div>
          <div v-else-if="detailItem">
            <div class="detail-row"><span class="dl">标题</span><span class="dv">{{ detailItem.title }}</span></div>
            <div class="detail-row"><span class="dl">发布者</span><span class="dv">{{ detailItem.publisherName }}</span></div>
            <div class="detail-row"><span class="dl">小区</span><span class="dv">翠湖花园</span></div>
            <div class="detail-row"><span class="dl">状态</span><span class="dv">{{ detailItem.displayStatus }}</span></div>

            <template v-if="detailItem.peerName">
              <div class="dv-divider"></div>
              <p class="text-sm text-secondary" style="margin-bottom:8px;">互助双方</p>
              <div class="detail-row">
                <span class="dl">{{ detailItem.type === 'idle' ? '借出方' : '求助方' }}</span>
                <span class="dv">{{ detailItem.publisherName }}
                  <span v-if="detailItem.publisherRatingScore" class="rating-tag">
                    <span class="stars">{{ ratingStars(detailItem.publisherRatingScore) }}</span>
                    <span class="label">互助评价</span>
                  </span>
                </span>
              </div>
              <div class="detail-row">
                <span class="dl">{{ detailItem.type === 'idle' ? '借入方' : '施助方' }}</span>
                <span class="dv">{{ detailItem.peerName }}
                  <span v-if="detailItem.peerRatingScore" class="rating-tag">
                    <span class="stars">{{ ratingStars(detailItem.peerRatingScore) }}</span>
                    <span class="label">互助评价</span>
                  </span>
                </span>
              </div>
              <div v-if="detailItem.timeStart" class="detail-row"><span class="dl">开始时间</span><span class="dv">{{ formatTime(detailItem.timeStart) }}</span></div>
              <div v-if="detailItem.timeEnd" class="detail-row"><span class="dl">结束时间</span><span class="dv">{{ formatTime(detailItem.timeEnd) }}</span></div>
            </template>

            <template v-if="detailItem.displayStatus === '已下架'">
              <div class="dv-divider"></div>
              <p class="text-sm text-secondary" style="margin-bottom:8px;">下架信息</p>
              <div class="detail-row"><span class="dl">下架原因</span><span class="dv">{{ detailItem.violationReason || detailItem.violationType || '—' }}</span></div>
              <div class="detail-row"><span class="dl">下架时间</span><span class="dv">{{ formatTime(detailItem.violatedAt) || '—' }}</span></div>
              <div class="detail-row"><span class="dl">下架管理员</span><span class="dv">{{ detailItem.violatorName || '—' }}</span></div>
            </template>

            <div class="dv-divider"></div>
            <p class="text-sm text-secondary" style="line-height:1.6;">此内容由住户自主发布。物业管理员可对其进行巡查，如发现违规内容可执行下架操作。下架后系统将通知发布者。</p>
          </div>
          <template #footer>
            <el-button @click="detailVisible = false">关闭</el-button>
          </template>
        </el-dialog>

        <!-- Offline Dialog -->
        <el-dialog v-model="offlineVisible" title="确认下架" width="480px">
          <p style="text-align:center;margin-bottom:12px;">
            确认下架「<strong>{{ offlineTarget?.title }}</strong>」？
          </p>
          <div style="margin-bottom:8px;font-weight:600;">下架原因：</div>
          <label
            v-for="reason in offlineReasonOptions"
            :key="reason"
            class="offline-check-label"
            @click="toggleOfflineReason(reason)"
          >
            <div class="check-box" :class="{ checked: offlineReasons.includes(reason) }">
              {{ offlineReasons.includes(reason) ? '✓' : '' }}
            </div>
            {{ reason }}
          </label>
          <div class="mt-8">
            <input
              class="search-box"
              type="text"
              v-model="offlineCustomReason"
              placeholder="自定义原因..."
              style="width:100%;"
            />
          </div>
          <template #footer>
            <el-button @click="offlineVisible = false">取消</el-button>
            <el-button type="danger" :loading="offlineSubmitting" @click="doOffline">确认下架</el-button>
          </template>
        </el-dialog>

        <!-- Publish Dialog -->
        <el-dialog v-model="publishVisible" title="物业代发" width="520px">
          <div style="margin-bottom:16px;">
            <div style="display:flex;align-items:center;margin-bottom:6px;">
              <span class="field-label" style="display:inline;margin-bottom:0;">
                目标住户 <span style="color:var(--red);">*</span>
              </span>
              <span style="flex:1;"></span>
              <div style="display:flex;gap:6px;">
                <button
                  class="btn-sm-toggle"
                  :class="{ active: publishMode === 'idle' }"
                  @click="publishMode = 'idle'"
                >物品互借</button>
                <button
                  class="btn-sm-toggle"
                  :class="{ active: publishMode === 'help' }"
                  @click="publishMode = 'help'"
                >技能互助</button>
              </div>
            </div>
            <div style="display:flex;gap:4px;">
              <input
                class="field-input"
                type="text"
                readonly
                :value="selectedResident"
                style="flex:1;cursor:pointer;background:var(--bg);color:var(--text-secondary);"
                @click="openResidentSearch"
                placeholder="点击检索住户"
              />
              <button
                style="height:32px;width:32px;padding:0;display:flex;align-items:center;justify-content:center;background:var(--bg);border:0.5px solid var(--border-soft);border-radius:6px;cursor:pointer;flex-shrink:0;"
                @click="openResidentSearch"
                title="检索住户"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
              </button>
            </div>
          </div>

          <template v-if="publishMode === 'idle'">
            <div class="publish-field">
              <span class="field-label">物品标题 <span style="color:var(--red);">*</span></span>
              <input class="field-input" type="text" v-model="publishForm.title" placeholder="例：博世冲击钻套装" style="width:100%;" />
            </div>
            <div class="publish-field">
              <span class="field-label" style="display:block;">物品描述</span>
              <textarea class="field-input" v-model="publishForm.desc" placeholder="描述物品现状、使用痕迹等..." style="width:100%;min-height:70px;"></textarea>
            </div>
            <div style="display:flex;gap:12px;margin-bottom:16px;">
              <div style="flex:1;">
                <span class="field-label" style="display:block;">参考价格</span>
                <input class="field-input" type="number" v-model="publishForm.price" placeholder="¥" style="width:100%;" />
              </div>
              <div style="flex:1;">
                <span class="field-label" style="display:block;">借出天数</span>
                <select class="field-input" v-model="publishForm.days" style="width:100%;">
                  <option v-for="d in 7" :key="d" :value="d">{{ d }}天</option>
                </select>
              </div>
            </div>
            <p class="text-xs text-tertiary">发布内容将标注「物业代发」标签，有人联系时通知发送到代发住户</p>
          </template>

          <template v-else>
            <div class="publish-field">
              <span class="field-label">求助标题 <span style="color:var(--red);">*</span></span>
              <input class="field-input" type="text" v-model="publishForm.title" placeholder="简要描述需要的帮助" style="width:100%;" />
            </div>
            <div class="publish-field">
              <span class="field-label" style="display:block;">求助描述</span>
              <textarea class="field-input" v-model="publishForm.desc" placeholder="描述具体情况..." style="width:100%;min-height:70px;"></textarea>
            </div>
            <div class="publish-field">
              <span class="field-label" style="display:block;">紧急程度</span>
              <select class="field-input" v-model="publishForm.urgency" style="width:100%;">
                <option>一般</option>
                <option>紧急</option>
              </select>
            </div>
            <div>
              <span class="field-label" style="display:block;">时间范围</span>
              <div style="display:flex;gap:8px;">
                <input class="field-input" type="datetime-local" v-model="publishForm.startTime" style="flex:1;" />
                <span style="align-self:center;color:var(--text-secondary);">—</span>
                <input class="field-input" type="datetime-local" v-model="publishForm.endTime" style="flex:1;" />
              </div>
            </div>
            <p class="text-xs text-tertiary mt-12">发布内容将标注「物业代发」标签，有人联系时通知发送到代发住户</p>
          </template>

          <template #footer>
            <el-button @click="publishVisible = false">取消</el-button>
            <el-button type="primary" :loading="publishSubmitting" @click="submitPublish">确认发布</el-button>
          </template>
        </el-dialog>

        <!-- Resident Search Dialog -->
        <el-dialog v-model="residentVisible" title="住户查找" width="480px">
          <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;">
            <input class="field-input" type="text" v-model="rsBuilding" placeholder="栋" style="width:60px;" />
            <span>栋</span>
            <input class="field-input" type="text" v-model="rsUnit" placeholder="单元" style="width:60px;" />
            <span>单元</span>
            <input class="field-input" type="text" v-model="rsRoom" placeholder="号" style="width:70px;" />
            <span>号</span>
            <select class="field-input" v-model="rsType" style="width:80px;">
              <option value="">全部</option>
              <option value="业主">业主</option>
              <option value="租客">租客</option>
            </select>
            <input class="search-box" type="text" v-model="rsKeyword" placeholder="姓名或手机号" style="width:140px;" />
            <button class="btn btn-sm btn-primary" @click="searchResidents">搜索</button>
          </div>
          <div style="max-height:220px;overflow-y:auto;margin-top:12px;">
            <template v-if="residentList.length">
              <div v-for="r in residentList" :key="r.id">
                <div
                  class="rs-item"
                  :class="{ selected: tempSelected === r.id }"
                  @click="pickResident(r)"
                >
                  <span style="flex:1;font-size:14px;">{{ r.room }}({{ r.userType }}) - {{ r.name }}</span>
                  <svg
                    v-if="tempSelected === r.id"
                    width="18" height="18" viewBox="0 0 24 24" fill="none"
                    stroke="var(--accent)" stroke-width="2"
                    stroke-linecap="round" stroke-linejoin="round"
                  >
                    <polyline points="20 6 9 17 4 12"/>
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
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowDown } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import { get, post, put } from '../utils/api';
import AppSidebar from '../components/AppSidebar.vue';

const router = useRouter();
const authStore = useAuthStore();

const contentTabs = [
  { key: 'show', label: '展示中' },
  { key: 'progress', label: '进行中' },
  { key: 'done', label: '已完成' },
  { key: 'offline', label: '违规下架' },
  { key: 'all', label: '全部' }
];

const statusParamMap = {
  show: 'showing',
  progress: 'progressing',
  done: 'completed',
  offline: 'violation',
  all: 'all'
};

const activeTab = ref('show');
const filterType = ref('');
const filterBuilding = ref('');
const search = ref('');
const loading = ref(false);
const tableData = ref([]);
const totalCount = ref(0);
const currentPage = ref(0);
const totalPages = ref(0);
const pageSize = 10;
const buildings = ref([]);

onMounted(() => {
  fetchContent();
  fetchBuildings();
});

// Helper: unwrap backend Result wrapper
function unwrap(response) {
  return response?.data?.data !== undefined ? response.data.data : response?.data;
}

async function fetchContent() {
  loading.value = true;
  try {
    const params = {
      status: statusParamMap[activeTab.value],
      type: filterType.value || undefined,
      building: filterBuilding.value || undefined,
      search: search.value || undefined,
      page: currentPage.value,
      size: pageSize
    };
    const res = await get('/api/admin/content', params);
    const pageData = unwrap(res);
    tableData.value = pageData?.content || [];
    totalCount.value = pageData?.totalElements || 0;
    totalPages.value = pageData?.totalPages || 0;
  } catch (e) {
    ElMessage.error('加载内容失败');
    tableData.value = [];
  } finally {
    loading.value = false;
  }
}

async function fetchBuildings() {
  try {
    const res = await get('/api/admin/buildings');
    const data = unwrap(res);
    buildings.value = Array.isArray(data) ? data.map(b => b.name || b) : [];
  } catch (e) { /* use empty list */ }
}

function switchTab(key) {
  activeTab.value = key;
  currentPage.value = 0;
  fetchContent();
}

function applyFilters() {
  currentPage.value = 0;
  fetchContent();
}

function goPage(p) {
  currentPage.value = p;
  fetchContent();
}

function statusTag(status) {
  const map = {
    '展示中': 'tag tag-green',
    '进行中': 'tag tag-blue',
    '已完成': 'tag tag-gray',
    '已下架': 'tag tag-red'
  };
  return map[status] || 'tag tag-gray';
}

function formatTime(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function ratingStars(score) {
  if (!score) return '';
  const full = Math.round(score);
  return '★'.repeat(full) + '☆'.repeat(5 - full);
}

// Detail
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailItem = ref(null);
const detailTitle = computed(() => detailItem.value?.title || '内容详情');

async function openDetail(row) {
  detailVisible.value = true;
  detailLoading.value = true;
  detailItem.value = null;
  try {
    const res = await get(`/api/admin/content/${row.id}`, { type: row.type });
    detailItem.value = unwrap(res);
  } catch (e) {
    ElMessage.error('加载详情失败');
  } finally {
    detailLoading.value = false;
  }
}

// Offline
const offlineVisible = ref(false);
const offlineTarget = ref(null);
const offlineReasons = ref([]);
const offlineCustomReason = ref('');
const offlineSubmitting = ref(false);
const offlineReasonOptions = ['商业广告', '虚假信息', '违规物品', '骚扰内容'];

function toggleOfflineReason(reason) {
  const idx = offlineReasons.value.indexOf(reason);
  if (idx > -1) offlineReasons.value.splice(idx, 1);
  else offlineReasons.value.push(reason);
}

function openOffline(row) {
  offlineTarget.value = row;
  offlineReasons.value = [];
  offlineCustomReason.value = '';
  offlineVisible.value = true;
}

async function doOffline() {
  if (!offlineTarget.value) return;
  offlineSubmitting.value = true;
  try {
    await put(`/api/admin/content/${offlineTarget.value.id}/offline`, {
      targetType: offlineTarget.value.type,
      reasons: [...offlineReasons.value],
      customReason: offlineCustomReason.value.trim() || undefined
    });
    offlineVisible.value = false;
    ElMessage.success(`「${offlineTarget.value.title}」已下架，通知已发送给发布者`);
    fetchContent();
  } catch (e) {
    ElMessage.error('下架失败');
  } finally {
    offlineSubmitting.value = false;
  }
}

// Publish
const publishVisible = ref(false);
const publishMode = ref('idle');
const publishSubmitting = ref(false);
const publishForm = reactive({
  title: '', desc: '', price: '', days: 7, urgency: '一般', startTime: '', endTime: ''
});
const selectedResident = ref('1栋1单元101号(业主)');
const selectedResidentId = ref(null);

function openPublish() {
  publishMode.value = 'idle';
  publishForm.title = '';
  publishForm.desc = '';
  publishForm.price = '';
  publishForm.days = 7;
  publishForm.urgency = '一般';
  publishForm.startTime = '';
  publishForm.endTime = '';
  publishVisible.value = true;
}

async function submitPublish() {
  if (!publishForm.title.trim()) {
    ElMessage.warning('请输入标题');
    return;
  }
  if (!selectedResidentId.value) {
    ElMessage.warning('请选择目标住户');
    return;
  }
  publishSubmitting.value = true;
  try {
    if (publishMode.value === 'idle') {
      await post('/api/admin/proxy/idle', {
        userId: selectedResidentId.value,
        postType: 'LEND',
        title: publishForm.title,
        description: publishForm.desc,
        category: 'other',
        price: parseFloat(publishForm.price) || 0,
        maxDuration: publishForm.days
      });
    } else {
      await post('/api/admin/proxy/help', {
        userId: selectedResidentId.value,
        title: publishForm.title,
        description: publishForm.desc,
        category: 'other',
        isUrgent: publishForm.urgency === '紧急',
        timeStart: publishForm.startTime || undefined,
        timeEnd: publishForm.endTime || undefined
      });
    }
    publishVisible.value = false;
    ElMessage.success('代发内容已发布，将标注「物业代发」标签');
    fetchContent();
  } catch (e) {
    ElMessage.error('代发失败');
  } finally {
    publishSubmitting.value = false;
  }
}

// Resident search
const residentVisible = ref(false);
const tempSelected = ref(null);
const rsBuilding = ref('');
const rsUnit = ref('');
const rsRoom = ref('');
const rsType = ref('');
const rsKeyword = ref('');
const residentList = ref([]);

async function searchResidents() {
  try {
    const res = await get('/api/admin/residents/search', {
      building: rsBuilding.value || undefined,
      unit: rsUnit.value || undefined,
      room: rsRoom.value || undefined,
      userType: rsType.value || undefined,
      keyword: rsKeyword.value || undefined
    });
    const data = unwrap(res);
    residentList.value = (data?.content) ? data.content : (Array.isArray(data) ? data : []);
  } catch (e) {
    residentList.value = [];
  }
}

function openResidentSearch() {
  tempSelected.value = selectedResidentId.value;
  rsBuilding.value = '';
  rsUnit.value = '';
  rsRoom.value = '';
  rsType.value = '';
  residentList.value = [];
  residentVisible.value = true;
  searchResidents();
}

function pickResident(r) {
  tempSelected.value = r.id;
  selectedResident.value = `${r.room}(${r.userType}) - ${r.name}`;
}

function selectResident() {
  if (tempSelected.value) selectedResidentId.value = tempSelected.value;
  residentVisible.value = false;
}

function handleCommand(cmd) {
  if (cmd === 'logout') handleLogout();
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning'
    });
    authStore.logout();
    router.push('/login');
  } catch { /* cancelled */ }
}
</script>

<style scoped>
.admin-layout { height: 100vh; }
.panel { background: var(--surface); border-radius: var(--radius); box-shadow: var(--shadow); overflow: hidden; }
.panel-body-scroll { overflow-x: auto; max-height: calc(100vh - 280px); overflow-y: auto; }
.table { width: 100%; border-collapse: collapse; font-size: 14px; }
.table th { text-align: left; padding: 10px 16px; font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: none; background: var(--surface); border-bottom: 0.5px solid var(--border-soft); white-space: nowrap; }
.table td { padding: 12px 16px; border-bottom: 0.5px solid var(--border-soft); white-space: nowrap; vertical-align: middle; }
.table tr:last-child td { border-bottom: none; }
.table tr:hover td { background: rgba(0,0,0,0.02); }
.table td .btn + .btn { margin-left: 4px; }
.tag { display: inline-flex; align-items: center; height: 20px; padding: 0 8px; border-radius: 4px; font-size: 11px; font-weight: 500; }
.tag-blue { background: rgba(0,113,227,0.1); color: var(--accent); }
.tag-red { background: rgba(255,59,48,0.1); color: var(--red); }
.tag-orange { background: rgba(255,149,0,0.1); color: var(--orange); }
.tag-green { background: rgba(52,199,89,0.1); color: var(--green); }
.tag-gray { background: var(--bg); color: var(--text-secondary); }
.panel-empty { padding: 48px 20px; text-align: center; font-size: 13px; color: var(--text-tertiary); }
.filter-select { padding: 6px 12px; border-radius: 6px; background: var(--bg); font-size: 13px; color: var(--text); cursor: pointer; border: 0.5px solid var(--border-soft); appearance: none; -webkit-appearance: none; padding-right: 28px; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M1 1l4 4 4-4' stroke='%2386868b' fill='none' stroke-width='1.5'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 8px center; }
.search-box { padding: 6px 12px; border-radius: 6px; background: var(--bg); font-size: 13px; border: 0.5px solid var(--border-soft); width: 180px; color: var(--text); outline: none; }
.search-box:focus { border-color: var(--accent); background: var(--surface); }
:deep(.el-dialog__body) { padding-top: 0; }
</style>
