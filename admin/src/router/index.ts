/**
 * Vue Router 路由配置模块。
 * 定义管理端所有页面的路由映射，并注册全局导航守卫进行登录态检查。
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

/** 路由表定义 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/login',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('../views/HomeView.vue'),
    meta: { title: '首页' },
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '数据看板' },
  },
  {
    path: '/audit',
    name: 'Audit',
    component: () => import('../views/AuditView.vue'),
    meta: { title: '住户审核' },
  },
  {
    path: '/content',
    name: 'Content',
    component: () => import('../views/ContentView.vue'),
    meta: { title: '内容管理' },
  },
  {
    path: '/records',
    name: 'Records',
    component: () => import('../views/RecordsView.vue'),
    meta: { title: '互助记录' },
  },
  {
    path: '/export',
    name: 'Export',
    component: () => import('../views/ExportView.vue'),
    meta: { title: '数据导出' },
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/SettingsView.vue'),
    meta: { title: '系统设置' },
  },
];

/** Vue Router 实例 */
const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 全局导航守卫 — 检查 token，未登录跳转登录页
router.beforeEach((to, _from, next) => {
  if (to.path === '/login') {
    next();
    return;
  }
  const token = localStorage.getItem('admin_token');
  if (!token) {
    next('/login');
  } else {
    next();
  }
});

export default router;
