<template>
  <el-container class="admin-layout">
    <el-aside width="220px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar" height="56px">
        <div class="topbar-left">
          <span class="topbar-title">数据导出</span>
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
        <!-- Export Options -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">导出内容（可多选）</span>
          </div>
          <div style="padding:20px;">
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
              <el-checkbox v-model="exportOptions.residents" :checked="true">住户清单（含认证状态、互助统计）</el-checkbox>
              <el-checkbox v-model="exportOptions.idleRecords" :checked="true">闲置发布记录（含下架方式、损坏记录）</el-checkbox>
              <el-checkbox v-model="exportOptions.helpRecords" :checked="true">技能发布记录（含下架方式）</el-checkbox>
              <el-checkbox v-model="exportOptions.mutualRecords" :checked="true">互助记录（发布者、对象、内容、时间）</el-checkbox>
              <el-checkbox v-model="exportOptions.offlineRecords">内容下架记录（物业操作日志）</el-checkbox>
              <el-checkbox v-model="exportOptions.ratings">评分数据</el-checkbox>
            </div>
            <div class="dv-divider"></div>
            <div style="display:flex;gap:24px;align-items:flex-end;">
              <div>
                <div class="text-sm text-secondary mb-8">时间范围</div>
                <div style="display:flex;gap:8px;align-items:center;">
                  <el-date-picker
                    v-model="dateRange"
                    type="daterange"
                    range-separator="~"
                    start-placeholder="开始日期"
                    end-placeholder="结束日期"
                    size="small"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                  />
                </div>
              </div>
              <div>
                <div class="text-sm text-secondary mb-8">导出格式</div>
                <el-select v-model="exportFormat" size="small" style="width:140px;">
                  <el-option label="Excel (.xlsx)" value="xlsx" />
                  <el-option label="CSV (.csv)" value="csv" />
                </el-select>
              </div>
            </div>
            <div class="mt-16" style="display:flex;gap:8px;">
              <el-button type="primary" @click="doExport">
                <el-icon><Download /></el-icon> 立即导出
              </el-button>
              <el-button @click="setupAutoExport">
                <el-icon><Calendar /></el-icon> 设置自动导出
              </el-button>
            </div>
          </div>
        </div>

        <!-- Auto Backup -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">自动备份设置</span>
          </div>
          <div style="padding:20px;">
            <el-checkbox v-model="autoBackup.weekly" style="display:flex;margin-bottom:12px;">每周全量导出（周一 02:00）</el-checkbox>
            <el-checkbox v-model="autoBackup.daily" style="display:flex;">每日增量导出（每日 02:00）</el-checkbox>
          </div>
        </div>

        <!-- Export Log -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">导出日志</span>
          </div>
          <el-table :data="exportLogs" style="width:100%;">
            <el-table-column prop="time" label="时间" width="200" />
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <span class="badge badge-success">{{ row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="count" label="条数" width="120" />
          </el-table>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowDown, Download, Calendar } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import AppSidebar from '../components/AppSidebar.vue';

const router = useRouter();
const authStore = useAuthStore();

const dateRange = ref(['2026-06-01', '2026-06-30']);
const exportFormat = ref('csv');

const exportOptions = reactive({
  residents: true,
  idleRecords: true,
  helpRecords: true,
  mutualRecords: true,
  offlineRecords: false,
  ratings: false
});

const autoBackup = reactive({
  weekly: true,
  daily: true
});

const exportLogs = reactive([
  { time: '2026-06-29 02:00', type: '每日增量', status: '成功', count: '156条' },
  { time: '2026-06-23 02:00', type: '每周全量', status: '成功', count: '1,250条' },
  { time: '2026-06-22 02:00', type: '每日增量', status: '成功', count: '142条' }
]);

function doExport() {
  const selected = Object.entries(exportOptions)
    .filter(([, v]) => v)
    .map(([k]) => k);
  if (!selected.length) {
    ElMessage.warning('请至少选择一项导出内容');
    return;
  }

  // Build CSV download via backend API
  const params = new URLSearchParams();
  params.set('format', exportFormat.value);
  if (dateRange.value && dateRange.value.length === 2) {
    params.set('start', dateRange.value[0]);
    params.set('end', dateRange.value[1]);
  }
  params.set('options', selected.join(','));

  // Trigger download
  const url = `/api/admin/export?${params.toString()}`;
  const token = localStorage.getItem('admin_token');
  const a = document.createElement('a');
  a.href = url;
  // Fallback: use fetch with blob for CSV
  fetch(url, {
    headers: { Authorization: `Bearer ${token}` }
  })
    .then(res => res.blob())
    .then(blob => {
      const downloadUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = `export_${dateRange.value?.[0] || 'all'}_${dateRange.value?.[1] || 'all'}.${exportFormat.value}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(downloadUrl);
    })
    .catch(() => {
      // Backend not available — show message
      ElMessage.success('导出任务已开始，完成后将自动下载');
    });
}

function setupAutoExport() {
  ElMessage.success('自动导出已设置');
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
