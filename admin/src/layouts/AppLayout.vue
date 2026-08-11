<!-- 管理端整体布局（侧边栏 + 内容区）。本组件无局部 <style>：布局/主题样式统一走全局 b-end.css，
     与全站暖色主题保持一致（刻意不设 scoped，避免样式分叉）。 -->
<template>
  <el-container class="admin-layout">
    <el-aside width="240px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="topbar-title">{{ title }}</span>
          <span v-if="$slots.subtitle" class="topbar-subtitle">
            <slot name="subtitle" />
          </span>
        </div>
        <div class="topbar-right">
          <slot name="actions" :logout="handleLogout">
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
          </slot>
        </div>
      </el-header>
      <el-main :class="mainClass" v-loading="loading">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import AppSidebar from "@/components/AppSidebar.vue";
import { useAuthStore } from "@/stores/auth";

/**
 * 管理后台统一布局 — 侧边栏 + 顶部栏 + 内容区。
 *
 * <p>收敛各页面重复的布局骨架：侧边栏固定为 AppSidebar；顶部栏默认渲染
 * 「标题 + 用户名下拉退出登录」，可用 {@code #actions} 作用域插槽整体替换右侧；
 * 退出登录逻辑（确认弹窗 → 清登录态 → 跳登录页）统一在此实现。</p>
 */
defineProps<{
  /** 顶部栏标题 */
  title: string;
  /** el-main 追加的 class（如 panel-fill / el-main--comfortable） */
  mainClass?: string;
  /** el-main 加载遮罩（v-loading） */
  loading?: boolean;
}>();

const router = useRouter();
const authStore = useAuthStore();

/** 顶部下拉命令处理 */
function handleCommand(cmd: string): void {
  if (cmd === "logout") handleLogout();
}

/** 退出登录 — 确认后清除登录态并跳转登录页 */
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
