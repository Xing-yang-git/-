/**
 * Vue Router 路由配置模块。
 * 定义管理端所有页面的路由映射，并注册全局导航守卫进行登录态检查。
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { USER_TYPE } from '../utils/constants';

/** 根路径重定向 — 超级管理员进系统设置，其余回登录页（供 / 与 path:'' 子路由共用） */
function rootRedirect(): string {
  const ut = getUserType();
  if (ut === USER_TYPE.SUPER_ADMIN) return '/settings';
  return '/login';
}

/** 路由表定义 */
const routes: RouteRecordRaw[] = [
  {
    /** 根路径 — 根据用户角色重定向 */
    path: '/',
    redirect: rootRedirect,
  },
  {
    /** 登录页 — 管理员用户名 + 密码登录，公开访问 */
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
  },
  {
    /** 首页 — 运营端入口布局（侧边栏 + 顶部栏 + 快捷操作入口），需登录 */
    path: '/home',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页' },
  },
  {
    /** 数据看板 — KPI 统计 + 发布趋势图 + 排行榜，需管理员权限 */
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '数据看板' },
  },
  {
    /** 住户审核 — 待审核/已通过/已驳回列表 + 批量审批，需管理员权限 */
    path: '/audit',
    name: 'Audit',
    component: () => import('@/views/AuditView.vue'),
    meta: { title: '住户审核' },
  },
  {
    /** 内容管理 — 闲置/求助帖子列表 + 搜索 + 违规下架 + 代发，需管理员权限 */
    path: '/content',
    name: 'Content',
    component: () => import('@/views/ContentView.vue'),
    meta: { title: '内容管理' },
  },
  {
    /** 互助记录 — 借用/帮助历史记录 + 详情查看，需管理员权限 */
    path: '/records',
    name: 'Records',
    component: () => import('@/views/RecordsView.vue'),
    meta: { title: '互助记录' },
  },
  {
    /** 知识库管理 — AI 助手「小邻」RAG 数据源管理，需管理员权限 */
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: { title: '知识库' },
  },
  {
    /** 敏感词管理 — AI 对话输入前置过滤词库，仅超级管理员可访问 */
    path: '/sensitive-words',
    name: 'SensitiveWords',
    component: () => import('@/views/SensitiveWordView.vue'),
    meta: { title: '敏感词管理' },
  },
  {
    /** 数据导出 — 多维度 Excel 导出 + 导出历史，需高级管理员权限 */
    path: '/export',
    name: 'Export',
    component: () => import('@/views/ExportView.vue'),
    meta: { title: '数据导出' },
  },
  {
    /** 系统设置 — 个人信息 + 管理员账号管理 + 操作日志，超级管理员可管理账号 */
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { title: '系统设置' },
  },
];

/** Vue Router 实例 */
const router = createRouter({
  history: createWebHistory(),
  routes,
});

/** 需要高级管理员及以上权限的路由 */
const SENIOR_ROUTES = ['/settings', '/export'];

/** 仅超级管理员可访问的路由（敏感词管理等平台级配置） */
const SUPER_ONLY_ROUTES = ['/sensitive-words'];

/** 超级管理员无权访问的业务数据路由 */
const DATA_ROUTES = ['/dashboard', '/audit', '/content', '/records', '/knowledge', '/export'];

/** 从 localStorage 读取当前登录用户的 userType，失败时返回 null */
function getUserType(): string | null {
  try {
    const userStr = localStorage.getItem('admin_user');
    return userStr ? JSON.parse(userStr).userType : null;
  } catch { return null; }
}

// 全局导航守卫 — 检查 token 和角色权限
router.beforeEach((to, _from, next) => {
  if (to.path === '/login') {
    next();
    return;
  }
  const token = localStorage.getItem('admin_token');
  if (!token) {
    next('/login');
    return;
  }

  const ut = getUserType();

  // super_admin 仅可访问 /settings、/home 与仅超级管理员路由，其余页面均重定向到 /settings
  if (ut === USER_TYPE.SUPER_ADMIN) {
    if (
      to.path === '/settings' ||
      to.path === '/home' ||
      SUPER_ONLY_ROUTES.includes(to.path)
    ) {
      next();
      return;
    }
    next('/settings');
    return;
  }

  // 仅超级管理员路由：普通/高级管理员一律拒绝
  if (SUPER_ONLY_ROUTES.includes(to.path)) {
    next('/home');
    return;
  }

  // 检查高级路由权限（仅 senior_admin + super_admin 可访问 /settings）
  if (SENIOR_ROUTES.includes(to.path)) {
    if (ut !== USER_TYPE.SENIOR_ADMIN) {
      next('/home');
      return;
    }
  }
  next();
});

export default router;
