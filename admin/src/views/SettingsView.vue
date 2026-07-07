<template>
  <el-container class="admin-layout">
    <el-aside width="220px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar" height="56px">
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
        <!-- Profile -->
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
                <el-tag type="primary">{{ profile.role }}</el-tag>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveProfile">保存修改</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>

        <!-- Change Password -->
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
                <el-button type="primary" @click="changePassword">修改密码</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>

        <!-- Admin List -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">管理员列表</span>
            <el-button type="primary" size="small" @click="openAddAdmin">+ 添加子账号</el-button>
          </div>
          <el-table :data="adminList" style="width:100%;">
            <el-table-column prop="name" label="姓名" align="center" />
            <el-table-column label="角色" align="center">
              <template #default="{ row }">
                <span :class="['badge', roleBadge(row.role)]">{{ row.role }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" align="center" />
            <el-table-column prop="createdAt" label="创建时间" align="center" width="140" />
            <el-table-column label="操作" align="center" width="100">
              <template #default>
                <el-button size="small" link type="primary">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- Operation Log -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">操作日志</span>
          </div>
          <el-table :data="operationLogs" style="width:100%;">
            <el-table-column prop="time" label="时间" align="center" width="140" />
            <el-table-column prop="operator" label="操作人" align="center" width="100" />
            <el-table-column label="操作" align="center" width="120">
              <template #default="{ row }">
                <span :class="['badge', row.tagClass]">{{ row.action }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="详情" />
          </el-table>
        </div>

        <!-- System Config -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">系统备用</span>
          </div>
          <div style="padding:20px;">
            <p class="text-sm text-secondary" style="line-height:1.8;">
              小程序不可用时：物业 PC 后台可独立操作（查看住户、内容管理）<br />
              完全断网：提供可打印的纸质登记表模板（住户审核登记表、代发登记表）<br />
              恢复后：手动补录纸质记录<br />
              定期离线备份：每周自动导出本小区数据
            </p>
            <div class="mt-12" style="display:flex;gap:8px;">
              <el-button size="small" @click="downloadTemplate('register')">
                <el-icon><Document /></el-icon> 下载登记表模板
              </el-button>
              <el-button size="small" @click="downloadTemplate('proxy')">
                <el-icon><Document /></el-icon> 下载代发登记表
              </el-button>
              <el-button size="small" @click="manualBackup">
                <el-icon><FolderOpened /></el-icon> 手动备份数据
              </el-button>
            </div>
          </div>
        </div>

        <!-- Add Admin Dialog -->
        <el-dialog v-model="addAdminVisible" title="添加子账号" width="440px">
          <el-form ref="addAdminFormRef" :model="addAdminForm" label-width="80px" size="default">
            <el-form-item label="姓名" prop="name" :rules="[{ required: true, message: '请输入姓名' }]">
              <el-input v-model="addAdminForm.name" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="角色" prop="role">
              <el-select v-model="addAdminForm.role" style="width:100%;">
                <el-option label="普通管理员" value="普通管理员" />
                <el-option label="超级管理员" value="超级管理员" />
                <el-option label="只读观察员" value="只读观察员" />
              </el-select>
            </el-form-item>
            <el-form-item label="手机号" prop="phone" :rules="[{ required: true, message: '请输入手机号' }]">
              <el-input v-model="addAdminForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="性别">
              <el-select v-model="addAdminForm.gender" style="width:100%;">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="addAdminVisible = false">取消</el-button>
            <el-button type="primary" @click="submitAddAdmin">确认添加</el-button>
          </template>
        </el-dialog>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowDown, Document, FolderOpened } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import AppSidebar from '../components/AppSidebar.vue';

const router = useRouter();
const authStore = useAuthStore();

// Profile
const profile = reactive({
  name: authStore.user?.name || '赵经理',
  role: authStore.user?.role || '超级管理员'
});

function saveProfile() {
  authStore.login(authStore.token, { name: profile.name, role: profile.role });
  ElMessage.success('个人信息已保存');
}

// Change password
const pwdFormRef = ref(null);
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

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
  ElMessage.success('密码修改成功');
  pwdForm.oldPassword = '';
  pwdForm.newPassword = '';
  pwdForm.confirmPassword = '';
}

// Admin list
const adminList = reactive([
  { name: '赵经理', role: '超级管理员', phone: '138****8888', createdAt: '2026-01-15' },
  { name: '钱主管', role: '普通管理员', phone: '139****6666', createdAt: '2026-01-20' },
  { name: '孙社工', role: '只读观察员', phone: '136****5555', createdAt: '2026-03-05' }
]);

function roleBadge(role) {
  const map = {
    '超级管理员': 'badge-info',
    '普通管理员': 'badge-success',
    '只读观察员': 'badge-default'
  };
  return map[role] || 'badge-default';
}

// Operation logs
const operationLogs = reactive([
  { time: '06-30 10:15', operator: '钱主管', action: '下架内容', detail: '下架"微商推广"原因：商业广告', tagClass: 'badge-danger' },
  { time: '06-30 09:30', operator: '赵经理', action: '审核通过', detail: '通过张三(3-2-1502)业主认证', tagClass: 'badge-success' },
  { time: '06-29 16:00', operator: '钱主管', action: '调阅聊天', detail: '查看电钻相关聊天记录', tagClass: 'badge-info' },
  { time: '06-29 14:20', operator: '赵经理', action: '驳回审核', detail: '驳回吴九(6-3-603)原因：证件模糊', tagClass: 'badge-warning' }
]);

// Add admin dialog
const addAdminVisible = ref(false);
const addAdminFormRef = ref(null);
const addAdminForm = reactive({
  name: '',
  role: '普通管理员',
  phone: '',
  gender: '男'
});

function openAddAdmin() {
  addAdminForm.name = '';
  addAdminForm.role = '普通管理员';
  addAdminForm.phone = '';
  addAdminForm.gender = '男';
  addAdminVisible.value = true;
}

async function submitAddAdmin() {
  const valid = await addAdminFormRef.value.validate().catch(() => false);
  if (!valid) return;

  const now = new Date();
  const dateStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const maskedPhone = addAdminForm.phone.length >= 11
    ? addAdminForm.phone.substring(0, 3) + '****' + addAdminForm.phone.substring(7)
    : addAdminForm.phone;

  adminList.push({
    name: addAdminForm.name,
    role: addAdminForm.role,
    phone: maskedPhone,
    createdAt: dateStr
  });

  addAdminVisible.value = false;
  ElMessage.success(`子账号「${addAdminForm.name}」已添加`);
}

// System
function downloadTemplate(type) {
  ElMessage.success(type === 'register' ? '已下载登记表模板' : '已下载代发登记表');
}

function manualBackup() {
  ElMessage.success('手动备份已开始');
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
