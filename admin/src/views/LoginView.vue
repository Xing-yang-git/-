<template>
  <div class="auth-wrapper">
    <div class="login-card-wrap">
      <el-card shadow="always" class="login-card">
        <!-- 品牌标识 -->
        <div class="login-brand">
          <div class="brand-icon-el">
            <el-icon size="26"><HomeFilled /></el-icon>
          </div>
          <div class="brand-name">翠湖花园物业</div>
          <div class="brand-sub">社区互助闲置平台 · 物业运营端</div>
        </div>

        <!-- 表单 -->
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入管理员账号"
              :prefix-icon="User"
              size="large"
              autocomplete="username"
              @keydown.enter="focusPassword"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              ref="passwordRef"
              v-model="form.password"
              :type="showPwd ? 'text' : 'password'"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              size="large"
              autocomplete="current-password"
              show-password
              @keydown.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="btn-login"
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 错误信息 -->
        <p v-if="errorMsg" class="login-error-msg">{{ errorMsg }}</p>

        <!-- 页脚 -->
        <div class="login-footer">物业运营端 v1.0 · 仅限授权物业人员使用</div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { HomeFilled, User, Lock } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import { post } from '../utils/api';

const router = useRouter();
const authStore = useAuthStore();

const formRef = ref(null);
const passwordRef = ref(null);
const loading = ref(false);
const errorMsg = ref('');
const showPwd = ref(false);

const form = reactive({
  username: '',
  password: ''
});

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

function focusPassword() {
  passwordRef.value?.focus();
}

async function handleLogin() {
  errorMsg.value = '';

  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;

  try {
    // 后端登录
    const res = await post('/api/auth/login', {
      username: form.username,
      password: form.password
    });
    const { token, user } = res.data.data;
    authStore.login(token, user);
    await authStore.initCommunity();
    ElMessage.success('登录成功，正在跳转...');
    setTimeout(() => router.push('/home'), 300);
  } catch (err) {
    errorMsg.value = err.response?.data?.message || '账号或密码错误';
    form.password = '';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-card-wrap {
  width: 400px;
  max-width: calc(100vw - 48px);
}

.login-card {
  border-radius: 14px;
  padding: 48px 40px 40px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}

.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 36px;
}

.brand-icon-el {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, #0071e3 0%, #2997ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 4px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text);
}

.brand-sub {
  font-size: 13px;
  color: var(--text-secondary);
}

.btn-login {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  margin-top: 8px;
}

.login-error-msg {
  font-size: 12px;
  color: var(--red);
  margin-top: 4px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 12px 0;
}

/* 覆盖 Element Plus 表单标签以对齐原型 */
:deep(.el-form-item) {
  margin-bottom: 20px;
  text-align: left;
}

:deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
  padding-bottom: 6px;
}

/* 覆盖输入框以对齐原型尺寸 */
:deep(.el-input__wrapper) {
  height: 44px;
  padding: 0 14px;
  background: var(--bg);
  border-radius: 6px;
  box-shadow: 0 0 0 0.5px var(--border-soft);
}

:deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--accent);
  background: var(--surface);
}

:deep(.el-input__inner) {
  font-size: 15px;
  color: var(--text);
}

:deep(.el-input__inner::placeholder) {
  color: var(--text-tertiary);
}

/* 覆盖表单元素以适配提交按钮 */
:deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
</style>
