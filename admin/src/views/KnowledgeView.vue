<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="topbar-title">知识库管理</span>
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
          <!-- 筛选行 -->
          <div class="filter-row">
            <el-select
              class="filter-select"
              v-model="filterCategory"
              placeholder="分类"
              style="width: 130px"
              clearable
            >
              <el-option
                v-for="opt in categoryOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-select
              class="filter-select"
              v-model="filterStatus"
              placeholder="状态"
              style="width: 120px"
              clearable
            >
              <el-option label="启用" value="online" />
              <el-option label="停用" value="offline" />
            </el-select>
            <el-input
              v-model="filterKeyword"
              placeholder="搜索标题 / 内容 / 标签"
              clearable
              style="width: 240px"
              @keyup.enter="handleSearch"
            />
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReindex">批量补齐向量</el-button>
            <div class="filter-spacer" />
            <el-button type="success" @click="openCreate">新增条目</el-button>
          </div>

          <!-- 列表 -->
          <el-table :data="rows" v-loading="loading" stripe>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="分类" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="categoryTagType(row.category)">
                  {{ categoryLabel(row.category) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="130" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === STATUS.ONLINE ? 'success' : 'info'">
                  {{ row.status === STATUS.ONLINE ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="160" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button
                  link
                  :type="row.status === STATUS.ONLINE ? 'danger' : 'success'"
                  @click="toggleStatus(row)"
                >
                  {{ row.status === STATUS.ONLINE ? '下架' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <el-pagination
            class="pagination-row"
            layout="total, prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page + 1"
            @current-change="handlePageChange"
          />
        </div>
      </el-main>
    </el-container>
  </el-container>

  <!-- 新建 / 编辑对话框 -->
  <el-dialog
    v-model="dialogVisible"
    :title="dialogMode === 'create' ? '新增知识条目' : '编辑知识条目'"
    width="560px"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="如：装修施工时间规定" maxlength="200" />
      </el-form-item>
      <el-form-item label="分类" required>
        <el-select v-model="form.category" style="width: 100%">
          <el-option
            v-for="opt in categoryOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-input v-model="form.source" placeholder="如：《小区规章制度》" maxlength="100" />
      </el-form-item>
      <el-form-item label="内容" required>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="7"
          placeholder="条目正文，住户向小邻提问时的回答依据"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="formStatusOnline"
          active-text="启用"
          inactive-text="停用"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 知识库管理视图 — AI 助手「小邻」RAG 数据源管理。
 *
 * 功能：知识条目增删改查（分类/状态/关键词过滤）、软上下架、批量补齐向量。
 * 权限：ROLE_ADMIN / ROLE_SUPER_ADMIN（走既有 /api/admin/** 鉴权）。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppSidebar from '@/components/AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'
import {
  KNOWLEDGE_CATEGORY,
  getKnowledgeList,
  createKnowledge,
  updateKnowledge,
  setKnowledgeStatus,
  reindexKnowledge,
  type KnowledgeItemDTO,
  type KnowledgeBody
} from '@/api/admin'
import { STATUS } from '@/utils/constants'

/** 当前登录管理员信息 */
const authStore = useAuthStore()

/** 表格加载状态 */
const loading = ref<boolean>(false)
/** 知识条目列表 */
const rows = ref<KnowledgeItemDTO[]>([])
/** 分页总数 */
const total = ref<number>(0)

/** 分类过滤值 */
const filterCategory = ref<string>('')
/** 状态过滤值 */
const filterStatus = ref<string>('')
/** 关键词过滤值 */
const filterKeyword = ref<string>('')
/** 当前页码（从 0 开始） */
const page = ref<number>(0)
/** 每页条数 */
const size = ref<number>(10)

/** 对话框可见性 */
const dialogVisible = ref<boolean>(false)
/** 对话框模式：create(新建)/edit(编辑) */
const dialogMode = ref<'create' | 'edit'>('create')
/** 正在编辑的条目 ID（编辑模式用） */
const editingId = ref<number | null>(null)
/** 保存中状态 */
const saving = ref<boolean>(false)
/** 表单数据 */
const form = ref<KnowledgeBody>({
  category: KNOWLEDGE_CATEGORY.RULES,
  title: '',
  content: '',
  source: '',
  status: STATUS.ONLINE
})

/** 表单「启用」开关绑定（映射到 form.status） */
const formStatusOnline = computed<boolean>({
  get: () => form.value.status !== STATUS.OFFLINE,
  set: (val: boolean) => { form.value.status = val ? STATUS.ONLINE : STATUS.OFFLINE }
})

/** 分类下拉选项（从常量派生） */
const categoryOptions = Object.entries(KNOWLEDGE_CATEGORY).map(([, value]) => ({
  label: categoryLabel(value),
  value
}))

/** 分类中文标签映射 */
function categoryLabel(value: string): string {
  const map: Record<string, string> = {
    rules: '规章制度',
    service: '服务手册',
    help: '平台帮助',
    guide: '办事指南'
  }
  return map[value] ?? value
}

/** 分类对应的 el-tag 颜色 */
function categoryTagType(value: string): 'success' | 'warning' | 'primary' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'primary' | 'info'> = {
    rules: 'primary',
    service: 'success',
    help: 'warning',
    guide: 'info'
  }
  return map[value] ?? 'info'
}

/** 加载知识库列表 */
async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await getKnowledgeList({
      page: page.value,
      size: size.value,
      category: filterCategory.value || undefined,
      status: filterStatus.value || undefined,
      keyword: filterKeyword.value || undefined
    })
    const data = res.data?.data
    rows.value = data?.content ?? []
    total.value = data?.totalElements ?? 0
  } finally {
    loading.value = false
  }
}

/** 查询按钮：重置到第一页再加载 */
function handleSearch(): void {
  page.value = 0
  loadList()
}

/** 分页切换 */
function handlePageChange(p: number): void {
  page.value = p - 1
  loadList()
}

/** 批量补齐缺失向量 */
async function handleReindex(): Promise<void> {
  const confirm = await ElMessageBox.confirm('将为所有缺失向量的知识条目重新生成向量，可能耗时较长。继续？', '批量补齐向量', {
    confirmButtonText: '开始补齐',
    cancelButtonText: '取消',
    type: 'warning'
  }).catch(() => null)
  if (!confirm) return
  try {
    const res = await reindexKnowledge()
    const count = res.data?.data?.count ?? 0
    ElMessage.success(`向量补齐完成：${count} 条`)
    loadList()
  } catch {
    ElMessage.error('补齐失败，请稍后重试')
  }
}

/** 打开新建对话框 */
function openCreate(): void {
  dialogMode.value = 'create'
  editingId.value = null
  form.value = { category: KNOWLEDGE_CATEGORY.RULES, title: '', content: '', source: '', status: STATUS.ONLINE }
  dialogVisible.value = true
}

/** 打开编辑对话框 */
function openEdit(row: KnowledgeItemDTO): void {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.value = {
    category: row.category,
    title: row.title,
    content: row.content,
    source: row.source ?? '',
    tags: row.tags ?? '',
    status: row.status
  }
  dialogVisible.value = true
}

/** 提交表单（新建或更新） */
async function submitForm(): Promise<void> {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createKnowledge(form.value)
      ElMessage.success('创建成功')
    } else if (editingId.value !== null) {
      await updateKnowledge(editingId.value, form.value)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    loadList()
  } catch {
    ElMessage.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

/** 软上下架切换 */
async function toggleStatus(row: KnowledgeItemDTO): Promise<void> {
  const target = row.status === STATUS.ONLINE ? STATUS.OFFLINE : STATUS.ONLINE
  try {
    await setKnowledgeStatus(row.id, target)
    ElMessage.success(target === STATUS.ONLINE ? '已启用' : '已下架')
    loadList()
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

/** 退出登录 */
function handleCommand(command: string): void {
  if (command === 'logout') {
    authStore.logout()
    window.location.href = '/login'
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
/* 知识库管理页样式（复用 admin 全局 unified-panel 布局） */
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-spacer {
  flex: 1;
}

.pagination-row {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
