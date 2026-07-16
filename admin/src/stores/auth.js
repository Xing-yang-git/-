import { defineStore } from 'pinia';
import { useCommunityStore } from './community';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('admin_token') || '',
    user: JSON.parse(localStorage.getItem('admin_user') || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    userName: (state) => state.user?.name || '管理员'
  },
  actions: {
    login(token, user) {
      this.token = token;
      this.user = user;
      localStorage.setItem('admin_token', token);
      localStorage.setItem('admin_user', JSON.stringify(user));
    },
    /**
     * 登录后获取小区数据，在 LoginView 中的 login() 之后调用。
     */
    async initCommunity() {
      const communityStore = useCommunityStore();
      await communityStore.fetchCommunityData();
    },
    logout() {
      this.token = '';
      this.user = null;
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_user');
      const communityStore = useCommunityStore();
      communityStore.clear();
    }
  }
});
