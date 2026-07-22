/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

/** Element Plus 中文语言包模块声明 */
declare module 'element-plus/dist/locale/zh-cn.mjs';
