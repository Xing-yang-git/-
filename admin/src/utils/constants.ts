/**
 * 业务常量 —— 与后端 com.platform.common 包下的常量类保持一致。
 * 这些字符串是前后端 API 契约的一部分，值不可修改；新增状态时需前后端同步。
 */

/** 业务状态（住户认证 authStatus、借用申请 status、物品状态、帮助状态等字段的取值），与后端 BizStatus 保持一致 */
export const STATUS = {
  /** 待审核 / 待审批 */
  PENDING: 'pending',
  /** 已通过 / 审核通过 */
  APPROVED: 'approved',
  /** 已驳回 / 审核驳回 */
  REJECTED: 'rejected',
  /** 已归还（借用流转终态） */
  RETURNED: 'returned',
  /** 已完成（帮助流转终态） */
  COMPLETED: 'completed',
  /** 已预订（闲置物品已被锁定 / 帮助已有人接单） */
  RESERVED: 'reserved',
  /** 上架展示中 */
  ONLINE: 'online',
  /** 已下架 */
  OFFLINE: 'offline',
  /** 已封禁（用户 authStatus） */
  BANNED: 'banned',
  /** 进行中（历史借用记录 status） */
  ACTIVE: 'active',
  /** 注册中（用户已微信登录但尚未完成手机号绑定/实名） */
  REGISTERING: 'registering',
  /** 已删除（软删除标记） */
  DELETED: 'deleted',
  /** 正常（物品成色 condition 默认值） */
  NORMAL: 'normal',
  /** 已取消（仅小程序端使用） */
  CANCELLED: 'cancelled',
  /** 借用中（仅小程序端兼容别名，映射到 active） */
  BORROWING: 'active',
} as const;

/** 闲置发布类型（idle_items.postType 字段取值），与后端 PostType 保持一致 */
export const POST_TYPE = {
  /** 闲置出借 */
  LEND: 'LEND',
  /** 需求借入 */
  WANTED: 'WANTED',
  /** 技能求助 */
  HELP: 'HELP',
} as const;

/** 损坏类型（borrow_requests.damage_type 字段取值），与后端 DamageType 保持一致 */
export const DAMAGE_TYPE = {
  /** 正常损耗 */
  NORMAL: 'normal',
  /** 非正常损坏（数据库值保持 severe） */
  ABNORMAL: 'severe',
  /** 完全损坏 */
  BROKEN: 'broken',
} as const;

/** 归还状态（borrow_requests.return_status 字段取值），与后端 ReturnStatus 保持一致 */
export const RETURN_STATUS = {
  /** 按时归还 */
  ON_TIME: 'ontime',
  /** 逾期归还 */
  DELAYED: 'delayed',
  /** 未归还 */
  NOT_RETURNED: 'not_returned',
} as const;

/** 用户类型（users.user_type 字段取值），与后端 UserType 保持一致 */
export const USER_TYPE = {
  /** 业主 */
  OWNER: 'owner',
  /** 租户 */
  TENANT: 'tenant',
  /** 普通管理员 */
  ADMIN: 'admin',
  /** 高级管理员 */
  SENIOR_ADMIN: 'senior_admin',
  /** 超级管理员 */
  SUPER_ADMIN: 'super_admin',
} as const;

/** 借出时长单位（idle_items.duration_unit 字段取值），与后端 DurationUnit 保持一致 */
export const DURATION_UNIT = {
  /** 按天 */
  DAY: 'day',
  /** 按周 */
  WEEK: 'week',
  /** 按月 */
  MONTH: 'month',
} as const;

/** 取货方式（idle_items.pickup_method 字段取值），与后端 PickupMethod 保持一致 */
export const PICKUP_METHOD = {
  /** 自取 */
  SELF_PICKUP: 'self_pickup',
  /** 快递 */
  EXPRESS: 'express',
} as const;

/** STATUS 值联合类型，供 TypeScript 类型收窄使用 */
export type StatusValue = (typeof STATUS)[keyof typeof STATUS];

/** POST_TYPE 值联合类型，供 TypeScript 类型收窄使用 */
export type PostTypeValue = (typeof POST_TYPE)[keyof typeof POST_TYPE];
