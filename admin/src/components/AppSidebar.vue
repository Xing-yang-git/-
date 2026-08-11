<template>
  <div class="admin-sidebar">
    <a class="sidebar-logo" @click="$router.push('/home')">
      <svg
        width="22"
        height="22"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
        <polyline points="9 22 9 12 15 12 15 22" />
      </svg>
      <span>翠湖花园物业</span>
    </a>

    <div class="sidebar-nav">
      <div
        v-if="!isSuperAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/dashboard' }"
        @click="$router.push('/dashboard')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <rect x="3" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="3" width="7" height="4" rx="1" />
            <rect x="14" y="11" width="7" height="10" rx="1" />
            <rect x="3" y="14" width="7" height="7" rx="1" />
          </svg>
        </span>
        <span>数据看板</span>
      </div>

      <div
        v-if="!isSuperAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/audit' }"
        @click="$router.push('/audit')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <circle cx="9" cy="7" r="3" />
            <path d="M3 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2" />
            <circle cx="18" cy="7" r="2.5" />
            <path d="M15 14.5a3.5 3.5 0 0 1 4 3.5v2" />
          </svg>
        </span>
        <span>住户管理</span>
      </div>

      <div
        v-if="!isSuperAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/content' }"
        @click="$router.push('/content')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
          </svg>
        </span>
        <span>内容管理</span>
      </div>

      <div
        v-if="!isSuperAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/records' }"
        @click="$router.push('/records')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path
              d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"
            />
            <rect x="9" y="3" width="6" height="4" rx="1" />
            <path d="M9 14l2 2 4-4" />
          </svg>
        </span>
        <span>互助记录</span>
      </div>

      <div
        v-if="!isSuperAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/knowledge' }"
        @click="$router.push('/knowledge')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            <path
              d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
            />
          </svg>
        </span>
        <span>知识库</span>
      </div>

      <div
        v-if="isSuperAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/sensitive-words' }"
        @click="$router.push('/sensitive-words')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M12 2l7 3v6c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V5l7-3z" />
            <path d="M9 12l2 2 4-4" />
          </svg>
        </span>
        <span>敏感词管理</span>
      </div>

      <div
        v-if="isSeniorAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/export' }"
        @click="$router.push('/export')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="7 10 12 15 17 10" />
            <line x1="12" y1="15" x2="12" y2="3" />
          </svg>
        </span>
        <span>数据导出</span>
      </div>

      <div
        v-if="isSeniorAdmin"
        class="sidebar-item"
        :class="{ active: activeRoute === '/settings' }"
        @click="$router.push('/settings')"
      >
        <span class="icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <circle cx="12" cy="12" r="3" />
            <path
              d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"
            />
          </svg>
        </span>
        <span>系统设置</span>
      </div>
    </div>

    <div class="sidebar-footer">物业运营端 v1.0 · {{ authStore.userName }}</div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import { useAuthStore } from "../stores/auth";
import { USER_TYPE } from "../utils/constants";

const route = useRoute();
const authStore = useAuthStore();

/** 当前激活的路由路径，用于高亮侧边栏菜单项 */
const activeRoute = computed(() => route.path);

/** 是否为超级管理员（仅系统设置，无业务数据权限） */
const isSuperAdmin = computed(
  () => authStore.user?.userType === USER_TYPE.SUPER_ADMIN,
);
/** 是否为高级管理员及以上（可访问系统设置、管理员列表、操作日志等） */
const isSeniorAdmin = computed(() => {
  const ut = authStore.user?.userType;
  return ut === USER_TYPE.SUPER_ADMIN || ut === USER_TYPE.SENIOR_ADMIN;
});
</script>

<style scoped>
.admin-sidebar {
  width: 240px;
  height: 100%;
  /* 与 .el-aside 同款渐变，避免本层白底盖住侧边栏渐变 */
  background: linear-gradient(to right, #ebe2d8 0%, #f7f2ed 100%);
  color: var(--text);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 0.5px solid var(--border-soft);
}

.sidebar-logo {
  padding: 28px 24px 24px;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  text-decoration: none;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin: 2px 0;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 450;
  color: var(--text-secondary);
  transition: all 0.25s var(--ease);
  cursor: pointer;
}

.sidebar-item:hover {
  color: var(--sidebar-item-hover);
  background: var(--sidebar-item-hover-bg);
}

.sidebar-item.active {
  color: var(--sidebar-item-active);
  background: var(--sidebar-item-active-bg);
  font-weight: 550;
}

.sidebar-item .icon {
  font-size: 20px;
  width: 28px;
  text-align: center;
}

.sidebar-footer {
  padding: 16px 24px;
  border-top: 0.5px solid var(--border-soft);
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
