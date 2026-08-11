/**
 * el-main 主容器样式类常量 —— 供 AppLayout 的 main-class 属性使用。
 *
 * <p>当前约定：全高透明列表页由「全高面板（panel-fill）」+「透明列表背景（list-transparent）」
 * 两个修饰类组合而成，对应 b-end.css 中 .el-main.panel-fill.list-transparent 的样式块
 * （表头与数据行默认完全透明、透出 .uf-body 的 surface-shallow，hover 数据行变 50% 白色）。</p>
 *
 * <p>住户管理 / 内容管理 / 互助记录 / 知识库四个列表页共用本常量，
 * 后续新增列表页直接引用，避免手写字符串或漏加 list-transparent。</p>
 */
export const LIST_PAGE_MAIN_CLASS = "panel-fill list-transparent";
