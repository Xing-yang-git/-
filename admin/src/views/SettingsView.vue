<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="topbar-title">系统设置</span>
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
        <!-- 个人信息 -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">个人信息</span>
          </div>
          <div style="padding:20px;">
            <el-form label-width="100px" size="default">
              <el-form-item label="管理员姓名">
                <el-input v-model="profile.name" style="width:260px;" />
              </el-form-item>
              <el-form-item label="角色">
                <el-tag type="primary">{{ userTypeLabel }}</el-tag>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="profileLoading" @click="saveProfile">保存修改</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>

        <!-- 修改密码 -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">修改密码</span>
          </div>
          <div style="padding:20px;">
            <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px" size="default">
              <el-form-item label="当前密码" prop="oldPassword">
                <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入当前密码" style="width:260px;" show-password />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码" style="width:260px;" show-password />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" style="width:260px;" show-password />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="pwdLoading" @click="changePassword">修改密码</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>

        <!-- 管理员列表（仅超级管理员可见）-->
        <div v-if="isSuperAdmin" class="panel">
          <div class="panel-header">
            <span class="panel-title">管理员列表</span>
            <el-button type="primary" size="small" @click="openAddAdmin">+ 添加子账号</el-button>
          </div>
          <el-table :data="adminList" style="width:100%;" v-loading="adminListLoading">
            <el-table-column prop="name" label="姓名" align="center" />
            <el-table-column label="角色" align="center">
              <template #default="{ row }">
                <span :class="['badge', roleBadge(row.userType)]">{{ row.userTypeLabel }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" align="center" />
            <el-table-column label="创建时间" align="center" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="row.userType !== 'super_admin'"
                  size="small"
                  link
                  type="danger"
                  @click="confirmDeleteAdmin(row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 操作日志（仅超级管理员可见）-->
        <div v-if="isSuperAdmin" class="panel">
          <div class="panel-header">
            <span class="panel-title">操作日志</span>
          </div>
          <el-table :data="operationLogs" style="width:100%;" v-loading="logsLoading">
            <el-table-column label="时间" align="center" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="adminName" label="操作人" align="center" width="100" />
            <el-table-column label="操作" align="center" width="120">
              <template #default="{ row }">
                <span :class="['badge', actionBadge(row.action)]">{{ actionLabel(row.action) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="详情" />
          </el-table>
        </div>

        <!-- 添加管理员弹窗 -->
        <el-dialog v-model="addAdminVisible" title="添加子账号" width="440px">
          <el-form ref="addAdminFormRef" :model="addAdminForm" :rules="addAdminRules" label-width="80px" size="default">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="addAdminForm.name" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="addAdminForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="addAdminForm.password" type="password" placeholder="请输入初始密码" show-password />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="addAdminVisible = false">取消</el-button>
            <el-button type="primary" :loading="addAdminLoading" @click="submitAddAdmin">确认添加</el-button>
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
import { put, get, post, del } from '../utils/api';
import AppSidebar from '../components/AppSidebar.vue';

const router = useRouter();
const authStore = useAuthStore();

const isSuperAdmin = computed(() => authStore.user?.userType === 'super_admin');
const userTypeLabel = computed(() => {
  const map = { super_admin: '超级管理员', admin: '普通管理员' };
  return map[authStore.user?.userType] || authStore.user?.userType || '管理员';
});

// ==================== 个人信息 ====================
const profile = reactive({
  name: authStore.user?.name || ''
});
const profileLoading = ref(false);

async function saveProfile() {
  profileLoading.value = true;
  try {
    const res = await put('/api/admin/profile', { name: profile.name });
    const updated = res.data.data;
    // 用新名称更新 auth store
    authStore.login(authStore.token, { ...authStore.user, name: updated.name });
    ElMessage.success('个人信息已保存');
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '保存失败');
  } finally {
    profileLoading.value = false;
  }
}

// ==================== 修改密码 ====================
const pwdFormRef = ref(null);
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});
const pwdLoading = ref(false);

const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'));
  } else {
    callback();
  }
};

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
};

async function changePassword() {
  const valid = await pwdFormRef.value.validate().catch(() => false);
  if (!valid) return;

  pwdLoading.value = true;
  try {
    await put('/api/admin/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    });
    ElMessage.success('密码修改成功');
    pwdForm.oldPassword = '';
    pwdForm.newPassword = '';
    pwdForm.confirmPassword = '';
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '密码修改失败');
  } finally {
    pwdLoading.value = false;
  }
}

// ==================== 管理员列表 ====================
const adminList = ref([]);
const adminListLoading = ref(false);

async function fetchAdmins() {
  adminListLoading.value = true;
  try {
    const res = await get('/api/admin/admins');
    adminList.value = res.data.data;
  } catch (err) {
    ElMessage.error('获取管理员列表失败');
  } finally {
    adminListLoading.value = false;
  }
}

function roleBadge(userType) {
  return userType === 'super_admin' ? 'badge-info' : 'badge-success';
}

async function confirmDeleteAdmin(row) {
  try {
    await ElMessageBox.confirm(`确认删除子账号「${row.name}」？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    });
    await del(`/api/admin/admins/${row.id}`);
    ElMessage.success('已删除');
    fetchAdmins();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.response?.data?.message || '删除失败');
    }
  }
}

// ==================== 添加管理员弹窗 ====================
const addAdminVisible = ref(false);
const addAdminFormRef = ref(null);
const addAdminForm = reactive({
  name: '',
  phone: '',
  password: ''
});
const addAdminLoading = ref(false);

const addAdminRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
};

function openAddAdmin() {
  addAdminForm.name = '';
  addAdminForm.phone = '';
  addAdminForm.password = '';
  addAdminVisible.value = true;
}

async function submitAddAdmin() {
  const valid = await addAdminFormRef.value.validate().catch(() => false);
  if (!valid) return;

  addAdminLoading.value = true;
  try {
    await post('/api/admin/admins', {
      name: addAdminForm.name,
      phone: addAdminForm.phone,
      password: addAdminForm.password
    });
    addAdminVisible.value = false;
    ElMessage.success(`子账号「${addAdminForm.name}」已添加`);
    fetchAdmins();
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '添加失败');
  } finally {
    addAdminLoading.value = false;
  }
}

// ==================== 操作日志 ====================
const operationLogs = ref([]);
const logsLoading = ref(false);

async function fetchLogs() {
  logsLoading.value = true;
  try {
    const res = await get('/api/admin/logs', { page: 0, size: 20 });
    operationLogs.value = res.data.data.content || [];
  } catch (err) {
    // silent
  } finally {
    logsLoading.value = false;
  }
}

function actionLabel(action) {
  const map = {
    approve_user: '审核通过',
    reject_user: '驳回审核',
    remove_content: '下架内容',
    proxy_publish_idle: '代发闲置',
    proxy_publish_help: '代发求助',
    create_admin: '添加账号',
    delete_admin: '删除账号'
  };
  return map[action] || action;
}

function actionBadge(action) {
  if (action === 'approve_user') return 'badge-success';
  if (action === 'reject_user') return 'badge-warning';
  if (action === 'remove_content') return 'badge-danger';
  if (action === 'delete_admin') return 'badge-danger';
  if (action === 'create_admin') return 'badge-success';
  return 'badge-info';
}

// ==================== 辅助函数 ====================
function formatTime(t) {
  if (!t) return '';
  if (typeof t === 'string') {
    // "2026-07-15T10:30:00" → "07-15 10:30"
    const d = new Date(t);
    if (isNaN(d.getTime())) return t.substring(0, 16);
    return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
  return '';
}

// ==================== 生命周期 ====================
onMounted(() => {
  profile.name = authStore.user?.name || '';
  if (isSuperAdmin.value) {
    fetchAdmins();
    fetchLogs();
  }
});

// ==================== 退出登录 ====================
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
