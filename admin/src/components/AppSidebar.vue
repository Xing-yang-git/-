<template>
  <el-menu
    :default-active="activeRoute"
    class="admin-sidebar-menu"
    :collapse="false"
    router
  >
    <div class="sidebar-logo">
      <span>翠湖花园物业</span>
    </div>

    <el-menu-item index="/dashboard">
      <el-icon><DataAnalysis /></el-icon>
      <span>数据看板</span>
    </el-menu-item>

    <el-menu-item index="/audit">
      <el-icon><UserFilled /></el-icon>
      <span>住户管理</span>
    </el-menu-item>

    <el-menu-item index="/content">
      <el-icon><EditPen /></el-icon>
      <span>内容管理</span>
    </el-menu-item>

    <el-menu-item index="/records">
      <el-icon><DocumentChecked /></el-icon>
      <span>互助记录</span>
    </el-menu-item>

    <el-menu-item index="/export">
      <el-icon><Download /></el-icon>
      <span>数据导出</span>
    </el-menu-item>

    <el-menu-item index="/settings">
      <el-icon><Setting /></el-icon>
      <span>系统设置</span>
    </el-menu-item>

    <div class="sidebar-footer">物业运营端 v1.0</div>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const activeRoute = computed(() => route.path);
</script>

<style scoped>
/* sidebar styles live in b-end.css */
</style>
