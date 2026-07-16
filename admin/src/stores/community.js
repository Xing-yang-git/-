import { defineStore } from 'pinia';
import { get } from '../utils/api';

export const useCommunityStore = defineStore('community', {
  state: () => ({
    tenantName: '',
    buildings: [],
    loaded: false
  }),

  getters: {
    /**
     * el-select 下拉框的楼栋扁平选项：[{id, name}]
     */
    buildingOptions: (state) =>
      state.buildings.map(b => ({ id: b.id, name: b.name })),

    /**
     * 根据楼栋 id 获取单元列表。
     */
    getUnits: (state) => (buildingId) => {
      const building = state.buildings.find(b => b.id === buildingId);
      return building ? building.units : [];
    },

    /**
     * 根据名称查找楼栋 id。
     */
    getBuildingId: (state) => (buildingName) => {
      const building = state.buildings.find(b => b.name === buildingName);
      return building ? building.id : null;
    }
  },

  actions: {
    async fetchCommunityData() {
      if (this.loaded) return;
      try {
        const res = await get('/api/admin/community');
        const data = res.data.data;
        this.tenantName = data.tenantName;
        this.buildings = data.buildings;
        this.loaded = true;
      } catch (err) {
        console.error('Failed to fetch community data:', err);
      }
    },

    /**
     * 重置 store 状态（例如退出登录时）。
     */
    clear() {
      this.tenantName = '';
      this.buildings = [];
      this.loaded = false;
    }
  }
});
