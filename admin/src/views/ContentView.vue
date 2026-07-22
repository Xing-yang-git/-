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
              :page-size="PAGE_SIZE"
              :total="totalCount"
              :pager-count="5"
              layout="prev, pager, next"
              small
              @current-change="(p: number) => goPage(p - 1)"
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
              v-for="reason in OFFLINE_REASON_OPTIONS"
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
          <div style="max-height: 280px; overflow-y: auto" v-loading="loadingResidents">
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

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowDown, ArrowLeft, ArrowRight, Search } from '@element-plus/icons-vue';

import { useAuthStore } from '../stores/auth';
import { useCommunityStore } from '../stores/community';
import {
  getContentList, getContentDetail, offlineContent,
  proxyPublishIdle, proxyPublishHelp, searchResidents,
  type ContentItemDTO, type ContentListParams
} from '../api/admin';
import { POST_TYPE } from '../utils/constants';
import AppSidebar from '../components/AppSidebar.vue';
import type { AxiosError, AxiosResponse } from 'axios';

// --- 本地类型 ---

/** 代发表单数据结构（物品互借 / 技能互助共用） */
interface PublishForm {
  title: string;
  desc: string;
  price: string;
  days: number;
  urgency: string;
  startTime: string;
  endTime: string;
}

/** 内容列表行数据结构 */
interface ContentRow {
  id: number;
  type: 'idle' | 'help';
  title: string;
  status: string;
  publisherName: string;
  createdAt: string;
  images?: string[];
  [key: string]: any;
}

const router = useRouter();
const authStore = useAuthStore();
const communityStore = useCommunityStore();

/** 内容标签页配置 */
const contentTabs = [
  { key: 'show', label: '在线中' },
  { key: 'progress', label: '进行中' },
  { key: 'done', label: '已完成' },
  { key: 'offline', label: '违规下架' },
];

/** 标签页 key → 后端 API status 参数映射 */
const STATUS_PARAM_MAP: Record<string, string> = {
  show: 'showing',
  progress: 'progressing',
  done: 'completed',
  offline: 'violation',
};

/** 当前激活的标签页 */
const activeTab = ref<string>('show');
/** 筛选条件：物品/技能类型 */
const filterType = ref('');
/** 筛选条件：楼栋 */
const filterBuilding = ref('');
/** 筛选条件：单元 */
const filterUnit = ref('');
/** 搜索关键词 */
const search = ref('');
/** 表格加载状态 */
const loading = ref(false);
/** 表格数据 */
const tableData = ref<ContentRow[]>([]);
/** 总记录数 */
const totalCount = ref(0);
/** 当前页码（0-based） */
const currentPage = ref(0);
/** 总页数 */
const totalPages = ref(0);
/** 每页条数 */
const PAGE_SIZE = 10;
/** 表格勾选的行 */
const selectedRows = ref<ContentRow[]>([]);
/** el-table 组件引用 */
const contentTableRef = ref<any>(null);

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

function handleSelectionChange(rows: ContentRow[]): void {
  selectedRows.value = rows;
}

onMounted(() => {
  communityStore.fetchCommunityData();
  fetchContent();
});

/**
 * 解包后端的 Result 包装器，提取 data.data 字段。
 * 兼容 Spring Boot 统一响应格式 { code, data, message }。
 * @returns 解包后的业务数据，不存在时返回 null
 */
function unwrap<T>(response: AxiosResponse): T | null {
  return response?.data?.data !== undefined ? response.data.data as T : null;
}

/** 根据当前筛选条件拉取内容列表 */
async function fetchContent(): Promise<void> {
  loading.value = true;
  try {
    const params: ContentListParams = {
      statusTab: STATUS_PARAM_MAP[activeTab.value],
      type: (filterType.value || undefined) as 'idle' | 'help' | undefined,
      building: filterBuilding.value || undefined,
      unit: filterUnit.value || undefined,
      search: search.value || undefined,
      page: currentPage.value,
      size: PAGE_SIZE,
    };
    const res = await getContentList(params);
    const pageData = unwrap<{ content: ContentRow[]; totalElements: number; totalPages: number }>(res);
    tableData.value = pageData?.content || [];
    totalCount.value = pageData?.totalElements || 0;
    totalPages.value = pageData?.totalPages || 0;
  } catch {
    ElMessage.error('加载内容失败');
    tableData.value = [];
  } finally {
    loading.value = false;
  }
}

/** 切换标签页，重置筛选并重新加载 */
function switchTab(key: string): void {
  activeTab.value = key;
  currentPage.value = 0;
  selectedRows.value = [];
  if (contentTableRef.value) {
    contentTableRef.value.clearSelection();
  }
  fetchContent();
}

/** 应用筛选条件，重置页码并重新加载 */
function applyFilters(): void {
  search.value = search.value.replace(/\s+/g, '');
  currentPage.value = 0;
  fetchContent();
}

/** 跳转到指定页 */
function goPage(p: number): void {
  currentPage.value = p;
  fetchContent();
}

/** 将后端状态映射为标签 CSS 类名 */
function statusTag(status: string): string {
  const label = statusLabel(status);
  const map: Record<string, string> = {
    在线中: 'tag tag-green',
    进行中: 'tag tag-blue',
    已完成: 'tag tag-gray',
    已下架: 'tag tag-red',
  };
  return map[label] || 'tag tag-gray';
}

function statusLabel(status: string): string {
  return status === '展示中' ? '在线中' : status;
}

/** 格式化 ISO 时间为可读格式，undefined/null 返回空字符串 */
function formatTime(ts?: string): string {
  if (!ts) return '';
  const d = new Date(ts);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 格式化发布者显示名，空名或纯英文名显示为"——" */
function displayName(name: string): string {
  if (!name) return '——';
  if (/^[\x00-\x7F\s]+$/.test(name)) return '——';
  return name;
}

// --- 详情弹窗 ---
const detailVisible = ref(false);
const detailLoading = ref(false);
/** 详情数据（来自 getContentDetail API 的原始 DTO） */
const detailItem = ref<ContentItemDTO | null>(null);

/** 详情中经过有效性过滤的图片 URL 列表 */
const validDetailImages = computed(() => {
  if (!detailItem.value?.images) return [];
  return (detailItem.value.images as string[]).filter(
    (url: string) => url && typeof url === 'string' && url.trim().length > 0,
  );
});

/** 记录加载失败的图片索引，用于显示占位图 */
const failedImageIndexes = ref(new Set<number>());

/** 标记指定索引的图片加载失败 */
function onImageError(index: number): void {
  failedImageIndexes.value.add(index);
}

/** 批量勾选记录列表，用于详情弹窗左右翻页 */
const detailList = ref<ContentRow[]>([]);
/** 当前翻页位置索引 */
const detailPos = ref(0);

/**
 * 打开内容详情弹窗，根据行数据拉取完整详情。
 * @param row - 列表行数据
 */
async function openDetail(row: ContentRow): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  detailItem.value = null;
  failedImageIndexes.value = new Set();
  try {
    const res = await getContentDetail(row.id, row.type as 'idle' | 'help');
    detailItem.value = unwrap<ContentItemDTO>(res);
  } catch {
    ElMessage.error('加载详情失败');
  } finally {
    detailLoading.value = false;
  }
}

/** 从勾选的记录中打开详情弹窗 */
function openDetailFromSelection(): void {
  if (!selectedRows.value.length) return;
  detailList.value = [...selectedRows.value];
  detailPos.value = 0;
  openDetail(detailList.value[0]);
}

/** 详情弹窗左右翻页 */
function detailGo(delta: number): void {
  const next = detailPos.value + delta;
  if (next < 0 || next >= detailList.value.length) return;
  detailPos.value = next;
  openDetail(detailList.value[next]);
}

// --- 下架弹窗 ---
const offlineVisible = ref(false);
/** 待下架的目标行 */
const offlineTarget = ref<ContentRow | null>(null);
/** 选中的下架原因（多选） */
const offlineReasons = ref<string[]>([]);
/** 自定义补充原因 */
const offlineCustomReason = ref('');
/** 下架提交进行中 */
const offlineSubmitting = ref(false);
/** 预设下架原因选项 */
const OFFLINE_REASON_OPTIONS = ['商业广告', '虚假信息', '违规物品', '骚扰内容'];

/** 从列表行打开下架弹窗 */
function openOffline(row: ContentRow): void {
  offlineTarget.value = row;
  offlineReasons.value = [];
  offlineCustomReason.value = '';
  offlineVisible.value = true;
}

/** 从详情弹窗中触发下架 */
function offlineFromDetail(): void {
  if (!detailItem.value) return;
  detailVisible.value = false;
  openOffline(detailItem.value as unknown as ContentRow);
}

/** 提交下架请求 */
async function doOffline(): Promise<void> {
  if (!offlineTarget.value) return;
  offlineSubmitting.value = true;
  try {
    await offlineContent(offlineTarget.value.id, {
      targetType: offlineTarget.value.type,
      reasons: [...offlineReasons.value],
      customReason: offlineCustomReason.value.trim() || undefined,
    });
    offlineVisible.value = false;
    ElMessage.success(`「${offlineTarget.value.title}」已下架，通知已发送给发布者`);
    fetchContent();
  } catch {
    ElMessage.error('下架失败');
  } finally {
    offlineSubmitting.value = false;
  }
}

// --- 代发弹窗 ---
/** 代发是否可见 */
const publishVisible = ref(false);
/** 代发模式：'idle'（物品互借）或 'help'（技能互助） */
const publishMode = ref<string>('idle');
/** 代发提交中 */
const publishSubmitting = ref(false);
/** 代发表单数据 */
const publishForm = reactive<PublishForm>({
  title: '',
  desc: '',
  price: '',
  days: 7,
  urgency: '一般',
  startTime: '',
  endTime: '',
});
/** 选中的住户名称（展示用） */
const selectedResident = ref('');
/** 选中的住户 ID（提交时用） */
const selectedResidentId = ref<number | null>(null);

/** 打开代发弹窗并重置表单 */
function openPublish(): void {
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

/** 提交代发请求 */
async function submitPublish(): Promise<void> {
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
      await proxyPublishIdle({
        userId: selectedResidentId.value,
        postType: POST_TYPE.LEND,
        title: publishForm.title,
        description: publishForm.desc,
        category: 'other',
        price: Number.parseFloat(publishForm.price) || 0,
        maxDuration: publishForm.days,
      });
    } else {
      await proxyPublishHelp({
        userId: selectedResidentId.value,
        title: publishForm.title,
        description: publishForm.desc,
        category: 'other',
        isUrgent: publishForm.urgency === '紧急',
        timeStart: publishForm.startTime || undefined,
        timeEnd: publishForm.endTime || undefined,
      });
    }
    publishVisible.value = false;
    ElMessage.success('代发内容已发布，将标注「物业代发」标签');
    fetchContent();
  } catch {
    ElMessage.error('代发失败');
  } finally {
    publishSubmitting.value = false;
  }
}

// 住户检索
const residentVisible = ref(false);
const tempSelected = ref<number | null>(null);
const residentList = ref<any[]>([]);
const loadingResidents = ref(false);
const residentFilterType = ref('');
const residentFilterBuilding = ref('');
const residentFilterUnit = ref('');
const residentKeyword = ref('');

/**
 * 加载住户列表（支持筛选）。
 * 从后端分页拉取住户数据，供代发弹窗中选择目标住户使用。
 */
async function loadAllResidents(): Promise<void> {
  if (loadingResidents.value) return;
  loadingResidents.value = true;
  try {
    const params = {
      page: 0,
      size: 200,
      userType: residentFilterType.value || undefined,
      building: residentFilterBuilding.value || undefined,
      unit: residentFilterUnit.value || undefined,
      keyword: residentKeyword.value || undefined,
    };
    const res = await searchResidents(params);
    const data = unwrap<{ content: unknown[] }>(res);
    residentList.value = data?.content
      ? data.content
      : Array.isArray(data)
        ? data
        : [];
  } catch (e) {
    residentList.value = [];
  } finally {
    loadingResidents.value = false;
  }
}

/** 打开住户检索弹窗并加载列表 */
function openResidentSearch(): void {
  tempSelected.value = selectedResidentId.value;
  residentFilterType.value = "";
  residentFilterBuilding.value = "";
  residentFilterUnit.value = "";
  residentKeyword.value = "";
  residentVisible.value = true;
  loadAllResidents();
}

/** 住户行数据（来自 searchResidents API） */
interface ResidentRow {
  id: number;
  name: string;
  room: string;
  userType: string;
}

/**
 * 在住户检索弹窗中临时选中某住户。
 * @param r - 住户行数据
 */
function pickResident(r: ResidentRow): void {
  tempSelected.value = r.id;
  selectedResident.value = `${r.room}(${r.userType}) - ${displayName(r.name)}`;
}

/** 确认选择住户，关闭检索弹窗 */
function selectResident(): void {
  if (tempSelected.value) selectedResidentId.value = tempSelected.value;
  residentVisible.value = false;
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
