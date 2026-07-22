/**
 * 管理端应用入口。
 * 初始化 Vue 应用、Pinia 状态管理、Element Plus UI 库和全局图标注册。
 */

import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/dist/locale/zh-cn.mjs';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import App from './App.vue';
import router from './router';
import './styles/b-end.css';

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(ElementPlus, { locale: zhCn });

// 全局注册所有 Element Plus 图标，模板中可直接使用 <el-icon><IconName /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as Parameters<typeof app.component>[1]);
}

app.mount('#app');
