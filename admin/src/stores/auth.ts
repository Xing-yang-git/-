/**
 * 认证状态管理 Store。
 * 管理管理员登录态（token + 用户信息）的持久化存储与清除，
 * 并在登录成功后触发小区数据初始化。
 */

import { defineStore } from 'pinia';
import { useCommunityStore } from './community';
import type { AdminUser } from '../api/auth';

/** 认证 Store 的状态结构 */
interface AuthState {
  /** JWT 令牌，存储在 localStorage */
  token: string;
  /** 当前登录的管理员信息，未登录时为 null */
  user: AdminUser | null;
}

export const useAuthStore = defineStore('auth', {
  /** 初始状态：从 localStorage 恢复登录态 */
  state: (): AuthState => ({
    token: localStorage.getItem('admin_token') || '',
    user: JSON.parse(localStorage.getItem('admin_user') || 'null') as AdminUser | null,
  }),

  getters: {
    /** 是否已登录（token 非空即视为已登录） */
    isLoggedIn: (state: AuthState): boolean => !!state.token,

    /** 当前管理员显示名称，未登录时回退为"管理员" */
    userName: (state: AuthState): string => state.user?.name || '管理员',
  },

  actions: {
    /**
     * 保存登录凭证到 state 和 localStorage。
     * @param token - JWT 令牌
     * @param user - 管理员信息
     */
    login(token: string, user: AdminUser): void {
      this.token = token;
      this.user = user;
      localStorage.setItem('admin_token', token);
      localStorage.setItem('admin_user', JSON.stringify(user));
    },

    /** 登录后获取小区数据，在 LoginView 中的 login() 之后调用。 */
    async initCommunity(): Promise<void> {
      const communityStore = useCommunityStore();
      await communityStore.fetchCommunityData();
    },

    /** 清除登录态，同时重置小区数据 */
    logout(): void {
      this.token = '';
      this.user = null;
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_user');
      const communityStore = useCommunityStore();
      communityStore.clear();
    },
  },
});
