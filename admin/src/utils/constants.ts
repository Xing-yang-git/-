/**
 * 业务状态常量 —— 与后端 com.platform.common.BizStatus 保持一致。
 * 这些字符串是前后端 API 契约的一部分，值不可修改；新增状态时需前后端同步。
 */

/** 业务审核状态（住户认证 authStatus、借用申请 status 等字段的取值） */
export const STATUS = {
  /** 待审核 */
  PENDING: 'pending',
  /** 已通过 */
  APPROVED: 'approved',
  /** 已驳回 */
  REJECTED: 'rejected',
} as const;

/** 闲置发布类型（idle_items.postType 字段取值；后端另有 WANTED，管理端暂未使用） */
export const POST_TYPE = {
  /** 出借 */
  LEND: 'LEND',
} as const;

/** STATUS 值联合类型，供 TypeScript 类型收窄使用 */
export type StatusValue = (typeof STATUS)[keyof typeof STATUS];

/** POST_TYPE 值联合类型，供 TypeScript 类型收窄使用 */
export type PostTypeValue = (typeof POST_TYPE)[keyof typeof POST_TYPE];
