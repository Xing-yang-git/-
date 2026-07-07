<template>
  <el-container class="admin-layout">
    <el-aside width="220px">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="topbar" height="56px">
        <div class="topbar-left">
          <span class="topbar-title">物业运营端</span>
          <span class="text-sm text-secondary">{{ today }}</span>
        </div>
        <div class="topbar-right">
          <span class="text-sm text-secondary">{{ authStore.userName }}，你好</span>
          <el-button type="danger" link @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </el-header>
      <el-main>
        <!-- Welcome -->
        <div class="section-title" style="margin-bottom:20px;">
          欢迎回来，{{ authStore.userName }}
        </div>

        <!-- Quick Stats -->
        <div class="quick-stats">
          <div class="quick-stat">
            <span class="qs-value" style="color:var(--orange);">3</span>
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

        <!-- Stat Cards -->
        <div class="stat-grid">
          <StatCard title="在线闲置数" :value="128" unit="件" trend="up" trendValue="12% 较上月" color="#007AFF" bgColor="rgba(0,122,255,0.08)" icon="Present" />
          <StatCard title="在线技能求助数" :value="35" unit="条" trend="up" trendValue="8% 较上月" color="#FF9500" bgColor="rgba(255,149,0,0.08)" icon="Service" />
          <StatCard title="本月互助完成数" :value="52" unit="次" trend="up" trendValue="15% 较上月" color="#34C759" bgColor="rgba(52,199,89,0.08)" icon="CircleCheckFilled" />
          <StatCard title="待审核住户数" :value="3" unit="人" trend="down" trendValue="2人已处理" color="#FF3B30" bgColor="rgba(255,59,48,0.08)" icon="UserFilled" />
        </div>

        <!-- Quick Links -->
        <div class="section-title" style="margin-bottom:12px;font-size:12px;text-transform:uppercase;color:var(--text-tertiary);letter-spacing:0.06em;">核心操作</div>
        <div class="launcher-grid" style="margin-bottom:24px;">
          <div class="panel" style="padding:20px;cursor:pointer;" @click="$router.push('/audit')">
            <div style="display:flex;align-items:center;gap:12px;">
              <div style="width:40px;height:40px;border-radius:10px;background:rgba(0,122,255,0.08);display:flex;align-items:center;justify-content:center;">
                <el-icon size="20" color="#007AFF"><UserFilled /></el-icon>
              </div>
              <div>
                <div style="font-weight:600;font-size:15px;">住户审核</div>
                <div style="font-size:12px;color:var(--text-secondary);">审核工作台 · 住户列表</div>
              </div>
            </div>
          </div>
          <div class="panel" style="padding:20px;cursor:pointer;" @click="$router.push('/content')">
            <div style="display:flex;align-items:center;gap:12px;">
              <div style="width:40px;height:40px;border-radius:10px;background:rgba(255,149,0,0.08);display:flex;align-items:center;justify-content:center;">
                <el-icon size="20" color="#FF9500"><EditPen /></el-icon>
              </div>
              <div>
                <div style="font-weight:600;font-size:15px;">内容管理</div>
                <div style="font-size:12px;color:var(--text-secondary);">巡查 · 违规下架 · 物业代发</div>
              </div>
            </div>
          </div>
          <div class="panel" style="padding:20px;cursor:pointer;" @click="$router.push('/dashboard')">
            <div style="display:flex;align-items:center;gap:12px;">
              <div style="width:40px;height:40px;border-radius:10px;background:rgba(52,199,89,0.08);display:flex;align-items:center;justify-content:center;">
                <el-icon size="20" color="#34C759"><DataAnalysis /></el-icon>
              </div>
              <div>
                <div style="font-weight:600;font-size:15px;">数据看板</div>
                <div style="font-size:12px;color:var(--text-secondary);">运营概览 · 互助趋势</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Recent Activity -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">最近动态</span>
          </div>
          <div style="padding:16px 20px;">
            <el-timeline>
              <el-timeline-item timestamp="2026-07-01 10:30" placement="top">
                张三（3栋2单元1502号）提交业主认证申请
              </el-timeline-item>
              <el-timeline-item timestamp="2026-07-01 09:00" placement="top">
                钱主管下架了违规内容「微商推广信息」
              </el-timeline-item>
              <el-timeline-item timestamp="2026-06-30 16:00" placement="top">
                12笔闲置物品互助在本周完成
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import {
  SwitchButton, UserFilled, EditPen, DataAnalysis
} from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
import AppSidebar from '../components/AppSidebar.vue';
import StatCard from '../components/StatCard.vue';

const router = useRouter();
const authStore = useAuthStore();

const today = computed(() => {
  const d = new Date();
  const days = ['日', '一', '二', '三', '四', '五', '六'];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 周${days[d.getDay()]}`;
});

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    });
    authStore.logout();
    router.push('/login');
  } catch {
    // cancelled
  }
}
</script>
