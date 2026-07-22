/**
 * 小区数据状态管理 Store。
 * 管理小区楼栋/单元结构数据的获取、缓存与查询，
 * 为各页面的楼栋-单元级联筛选提供数据源。
 */

import { defineStore } from 'pinia';
import { getCommunity } from '../api/admin';
import type { BuildingData } from '../api/admin';

/** 小区 Store 的状态结构 */
interface CommunityState {
  /** 小区/租户名称 */
  tenantName: string;
  /** 楼栋列表（含单元信息） */
  buildings: BuildingData[];
  /** 是否已成功加载过数据 */
  loaded: boolean;
}

/** el-select 下拉框的楼栋选项结构 */
interface BuildingOption {
  id: number;
  name: string;
}

export const useCommunityStore = defineStore('community', {
  /** 初始状态：空数据，等待 fetchCommunityData 填充 */
  state: (): CommunityState => ({
    tenantName: '',
    buildings: [],
    loaded: false,
  }),

  getters: {
    /** el-select 下拉框的楼栋扁平选项 */
    buildingOptions: (state: CommunityState): BuildingOption[] =>
      state.buildings.map((b: BuildingData) => ({ id: b.id, name: b.name })),

    /** 根据楼栋 id 获取单元列表 */
    getUnits: (state: CommunityState) => (buildingId: number) => {
      const building = state.buildings.find((b: BuildingData) => b.id === buildingId);
      return building ? building.units : [];
    },

    /** 根据楼栋名称查找对应的 id */
    getBuildingId: (state: CommunityState) => (buildingName: string) => {
      const building = state.buildings.find((b: BuildingData) => b.name === buildingName);
      return building ? building.id : null;
    },
  },

  actions: {
    /** 从后端获取小区楼栋/单元结构数据，已加载时跳过 */
    async fetchCommunityData(): Promise<void> {
      if (this.loaded) return;
      try {
        const res = await getCommunity();
        const data = res.data.data as CommunityState;
        this.tenantName = data.tenantName;
        this.buildings = data.buildings;
        this.loaded = true;
      } catch (err) {
        console.error('Failed to fetch community data:', err);
      }
    },

    /** 重置 store 状态（退出登录时调用） */
    clear(): void {
      this.tenantName = '';
      this.buildings = [];
      this.loaded = false;
    },
  },
});
