<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div
          class="topbar-left"
          style="display: flex; align-items: baseline; gap: 8px"
        >
          <span class="topbar-title">物业运营端</span>
          <span
            class="text-sm text-secondary"
            style="white-space: nowrap; font-weight: 400"
            >{{ todayWithTime }}</span
          >
        </div>
        <div class="topbar-right">
          <span class="logout-btn" @click="handleLogout" title="退出登录">
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </span>
        </div>
      </el-header>
      <el-main>
        <!-- 快速统计 -->
        <div class="quick-stats">
          <div class="quick-stat">
            <span class="qs-value text-orange">3</span>
            <span class="qs-label">待审核住户</span>
          </div>
          <div class="quick-stat">
            <span class="qs-value">128</span>
            <span class="qs-label">在线闲置</span>
          </div>
          <div class="quick-stat">
            <span class="qs-value">256</span>
            <span class="qs-label">本月发布</span>
          </div>
          <div class="quick-stat">
            <span class="qs-value">89</span>
            <span class="qs-label">本月活跃住户</span>
          </div>
        </div>

        <!-- 核心操作 -->
        <div class="section-label-title">核心操作</div>
        <div class="launcher-grid">
          <div class="launch-card" @click="$router.push('/audit')">
            <div
              class="card-icon"
              style="background: rgba(0, 113, 227, 0.08); color: var(--accent)"
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="9" cy="7" r="3" />
                <path d="M3 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2" />
                <circle cx="18" cy="7" r="2.5" />
                <path d="M15 14.5a3.5 3.5 0 0 1 4 3.5v2" />
              </svg>
            </div>
            <div class="card-title">住户管理</div>
            <div class="card-desc">审核工作台 · 住户列表 · 封禁管理</div>
            <div class="card-badge tag tag-red">3 人待审</div>
          </div>
          <div class="launch-card" @click="$router.push('/content')">
            <div
              class="card-icon"
              style="background: rgba(255, 149, 0, 0.08); color: var(--orange)"
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M12 20h9" />
                <path
                  d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"
                />
              </svg>
            </div>
            <div class="card-title">内容管理</div>
            <div class="card-desc">巡查 · 违规下架 · 物业代发</div>
          </div>
          <div class="launch-card" @click="$router.push('/dashboard')">
            <div
              class="card-icon"
              style="background: rgba(52, 199, 89, 0.08); color: var(--green)"
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="4" rx="1" />
                <rect x="14" y="11" width="7" height="10" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
              </svg>
            </div>
            <div class="card-title">数据看板</div>
            <div class="card-desc">运营概览 · 互助趋势 · 损坏统计</div>
          </div>
        </div>

        <!-- 数据与记录 -->
        <div class="section-label-title">数据与记录</div>
        <div class="launcher-grid">
          <div class="launch-card" @click="$router.push('/records')">
            <div
              class="card-icon"
              style="background: rgba(0, 113, 227, 0.04); color: var(--accent)"
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"
                />
                <rect x="9" y="3" width="6" height="4" rx="1" />
                <path d="M9 14l2 2 4-4" />
              </svg>
            </div>
            <div class="card-title">互助记录</div>
            <div class="card-desc">住户互助档案 · 按户查询 · 时间筛选</div>
          </div>
          <div class="launch-card" @click="$router.push('/export')">
            <div
              class="card-icon"
              style="background: rgba(0, 113, 227, 0.04); color: var(--accent)"
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
            </div>
            <div class="card-title">数据导出</div>
            <div class="card-desc">报表导出 · 自动备份 · 导出日志</div>
          </div>
        </div>

        <!-- 系统设置 -->
        <div class="section-label-title">系统</div>
        <div class="launcher-grid">
          <div class="launch-card" @click="$router.push('/settings')">
            <div
              class="card-icon"
              style="background: rgba(0, 113, 227, 0.04); color: var(--accent)"
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="3" />
                <path
                  d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"
                />
              </svg>
            </div>
            <div class="card-title">系统设置</div>
            <div class="card-desc">权限管理 · 操作日志 · 系统备用</div>
          </div>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";

import { useAuthStore } from "../stores/auth";
import AppSidebar from "../components/AppSidebar.vue";

const router = useRouter();
const authStore = useAuthStore();

/** 当前日期 + 时间，格式：2026年7月22日周四 14:30 */
const todayWithTime = computed(() => {
  const d = new Date();
  const days = ["日", "一", "二", "三", "四", "五", "六"];
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日周${days[d.getDay()]} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
});

/** 退出登录 — 确认后清除 token 并跳转登录页 */
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
    // 已取消
  }
}
</script>

<style scoped>
.section-label-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 28px 0 12px;
}
.section-label-title:first-of-type {
  margin-top: 0;
}

.launch-card {
  background: var(--surface);
  border-radius: var(--radius);
  padding: 24px;
  box-shadow: var(--shadow);
  cursor: pointer;
  transition:
    transform 150ms var(--ease),
    box-shadow 150ms var(--ease);
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-decoration: none;
  color: inherit;
  position: relative;
  overflow: hidden;
}

.launch-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.launch-card:active {
  transform: translateY(0);
  box-shadow: var(--shadow);
}

.launch-card .card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.launch-card .card-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.launch-card .card-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.55;
}

.launch-card .card-badge {
  position: absolute;
  top: 16px;
  right: 16px;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

/* 顶部栏退出登录按钮 */
:deep(.logout-btn) {
  display: flex;
  align-items: center;
  padding: 4px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: color var(--transition);
}
:deep(.logout-btn:hover) {
  color: var(--red);
}
</style>
