<template>
  <el-container class="admin-layout">
    <el-aside width="220px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar" height="56px">
        <div class="topbar-left">
          <span class="topbar-title">互助记录</span>
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
          <!-- Filters -->
          <div class="filter-row">
            <el-input v-model="search" placeholder="搜索住户名称或房号..." size="small" style="width:240px;" clearable />
            <el-select v-model="filterTime" placeholder="全部时间" size="small" style="width:130px;" clearable>
              <el-option label="本月" value="month" />
              <el-option label="近3个月" value="quarter" />
            </el-select>
            <el-select v-model="filterType" placeholder="全部类型" size="small" style="width:130px;" clearable>
              <el-option label="物品互借" value="idle" />
              <el-option label="技能互助" value="help" />
            </el-select>
            <el-select v-model="filterBuilding" placeholder="全部楼栋" size="small" style="width:120px;" clearable>
              <el-option v-for="b in buildings" :key="b" :label="b" :value="b" />
            </el-select>
            <span style="flex:1;"></span>
            <span class="text-sm text-secondary">共 {{ filteredRecords.length }} 条</span>
          </div>

          <!-- Table -->
          <el-table :data="paginatedRecords" style="width:100%;">
            <el-table-column prop="publisher" label="发布者" />
            <el-table-column prop="peer" label="互助对象" />
            <el-table-column prop="content" label="内容" />
            <el-table-column label="类型" align="center" width="80">
              <template #default="{ row }">
                <span :class="['badge', row.type === 'idle' ? 'badge-info' : 'badge-warning']">
                  {{ row.type === 'idle' ? '互借' : '互助' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="时间范围" width="300">
              <template #default="{ row }">
                {{ row.timeStart }} ~ {{ row.timeEnd }}
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="80">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- Pagination -->
          <div style="display:flex;justify-content:flex-end;padding:12px 20px;">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="filteredRecords.length"
              layout="prev, pager, next"
              small
              background
            />
          </div>
        </div>

        <!-- Detail Dialog -->
        <el-dialog v-model="detailVisible" :title="detailItem?.content || '互助记录详情'" width="520px">
          <div v-if="detailItem">
            <div class="detail-row"><span class="dl">内容</span><span class="dv">{{ detailItem.content }}</span></div>
            <div class="detail-row"><span class="dl">类型</span><span class="dv">
              <span :class="['badge', detailItem.type === 'idle' ? 'badge-info' : 'badge-warning']">
                {{ detailItem.type === 'idle' ? '互借' : '互助' }}
              </span>
            </span></div>
            <div class="detail-row"><span class="dl">小区</span><span class="dv">翠湖花园</span></div>
            <div class="dv-divider"></div>
            <template v-if="detailItem.type === 'idle'">
              <p class="text-sm text-secondary" style="margin-bottom:8px;">互助双方</p>
              <div class="detail-row">
                <span class="dl">借出方</span>
                <span class="dv">{{ detailItem.publisher }}
                  <span v-if="detailItem.pubRating" class="rating-tag" style="margin-left:6px;">{{ detailItem.pubRating }}</span>
                </span>
              </div>
              <div class="detail-row">
                <span class="dl">借入方</span>
                <span class="dv">{{ detailItem.peer }}
                  <span v-if="detailItem.peerRating" class="rating-tag" style="margin-left:6px;">{{ detailItem.peerRating }}</span>
                </span>
              </div>
              <div class="detail-row"><span class="dl">开始时间</span><span class="dv">{{ detailItem.timeStart }}</span></div>
              <div class="detail-row"><span class="dl">结束时间</span><span class="dv">{{ detailItem.timeEnd }}</span></div>
              <div v-if="detailItem.condBefore" class="dv-divider"></div>
              <template v-if="detailItem.condBefore">
                <p class="text-sm text-secondary" style="margin-bottom:8px;">物品状况记录</p>
                <div class="detail-row"><span class="dl">借出前</span><span class="dv">{{ detailItem.condBefore }}</span></div>
                <div class="detail-row"><span class="dl">归还后</span><span class="dv">{{ detailItem.condAfter }}</span></div>
              </template>
            </template>
            <template v-else>
              <p class="text-sm text-secondary" style="margin-bottom:8px;">互助双方</p>
              <div class="detail-row">
                <span class="dl">求助方</span>
                <span class="dv">{{ detailItem.publisher }}
                  <span v-if="detailItem.pubRating" class="rating-tag" style="margin-left:6px;">{{ detailItem.pubRating }}</span>
                </span>
              </div>
              <div class="detail-row">
                <span class="dl">施助方</span>
                <span class="dv">{{ detailItem.peer }}
                  <span v-if="detailItem.peerRating" class="rating-tag" style="margin-left:6px;">{{ detailItem.peerRating }}</span>
                </span>
              </div>
              <div class="detail-row"><span class="dl">开始时间</span><span class="dv">{{ detailItem.timeStart }}</span></div>
              <div class="detail-row"><span class="dl">结束时间</span><span class="dv">{{ detailItem.timeEnd }}</span></div>
            </template>
            <div class="dv-divider"></div>
            <p class="text-sm text-secondary">评分由双方互评完成：发布者评分为互助对象的评价，互助对象评分为发布者的评价。</p>
          </div>
          <template #footer>
            <el-button @click="detailVisible = false">关闭</el-button>
          </template>
        </el-dialog>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { ArrowDown } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import AppSidebar from '../components/AppSidebar.vue';

const router = useRouter();
const authStore = useAuthStore();

const buildings = ['1栋', '2栋', '3栋', '5栋', '6栋', '7栋'];

const search = ref('');
const filterTime = ref('');
const filterType = ref('');
const filterBuilding = ref('');

const records = reactive([
  {
    publisher: '3栋2单元1502号(业主)', pubRating: '★★★★★',
    peer: '5栋1单元802号(租客)', peerRating: '★★★★☆',
    content: '博世冲击钻套装', type: 'idle',
    timeStart: '2026-06-22 09:00', timeEnd: '2026-06-25 14:30',
    condBefore: '正常使用痕迹', condAfter: '正常损耗', room: '3栋'
  },
  {
    publisher: '5栋1单元802号(租客)', pubRating: '★★★★★',
    peer: '7栋1单元1201号(业主)', peerRating: '★★★★★',
    content: '水管漏水维修', type: 'help',
    timeStart: '2026-06-22 10:00', timeEnd: '2026-06-22 11:00', room: '5栋'
  },
  {
    publisher: '3栋2单元1502号(业主)', pubRating: '★★★★☆',
    peer: '2栋1单元301号(租客)', peerRating: '★★★★☆',
    content: '《三体》全套3册', type: 'idle',
    timeStart: '2026-06-15 08:00', timeEnd: '2026-06-20 16:00',
    condBefore: '几乎全新', condAfter: '正常损耗', room: '3栋'
  },
  {
    publisher: '7栋1单元1201号(业主)', pubRating: '★★★★★',
    peer: '3栋2单元1502号(业主)', peerRating: '★★★★★',
    content: '陪老人就诊', type: 'help',
    timeStart: '2026-06-28 08:30', timeEnd: '2026-06-28 12:00', room: '7栋'
  },
  {
    publisher: '7栋1单元1201号(业主)', pubRating: '★★★☆☆',
    peer: '6栋2单元1102号(业主)', peerRating: '★★★☆☆',
    content: '捷安特折叠自行车', type: 'idle',
    timeStart: '2026-06-10 09:00', timeEnd: '2026-06-15 17:00',
    condBefore: '正常使用痕迹', condAfter: '可修复损坏', room: '7栋'
  }
]);

const filteredRecords = computed(() => {
  return records.filter(item => {
    if (filterType.value && item.type !== filterType.value) return false;
    if (filterBuilding.value && !item.room.startsWith(filterBuilding.value)) return false;
    if (filterTime.value === 'month' && item.timeEnd < '2026-06-01') return false;
    if (filterTime.value === 'quarter' && item.timeEnd < '2026-04-01') return false;
    if (search.value) {
      const s = search.value.trim();
      if (!item.publisher.includes(s) && !item.peer.includes(s) && !item.content.includes(s)) return false;
    }
    return true;
  });
});

// Pagination
const currentPage = ref(1);
const pageSize = ref(10);
const paginatedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredRecords.value.slice(start, start + pageSize.value);
});

// Detail
const detailVisible = ref(false);
const detailItem = ref(null);

function openDetail(row) {
  detailItem.value = row;
  detailVisible.value = true;
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
.rating-tag {
  display: inline-flex;
  align-items: center;
  background: var(--bg);
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 11px;
  color: var(--orange);
}
</style>
