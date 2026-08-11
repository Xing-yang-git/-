/**
 * 业务常量定义 — 状态与发布类型的唯一来源
 *
 * 与后端 com.platform.common.BizStatus / PostType 保持一致：
 * 这些字符串值即前后端通信的字面值（DTO 字段、URL 参数），不得随意改动。
 */

// ========== 帖子状态（与后端 BizStatus 对齐）==========
const POST_STATUS = {
  ONLINE: 'online',                     // 在线中
  DRAFT: 'draft',                       // 草稿（用户下架后的中间态，C端显示为"已下架"）
  OFFLINE: 'offline',                   // 已下架（管理员下架/AI驳回/用户删除草稿的终态）
  PENDING_REVIEW: 'pending_review',     // 审核中
  PENDING: 'pending',                   // 待审批（借用/帮助申请提交后，帖子标记为此状态）
  ACTIVE: 'active',                     // 进行中（借用/帮助进行中）
  COMPLETED: 'completed'                // 已完成
};

// ========== 帖子类型（与后端 PostType 一致）==========
const POST_TYPE = {
  LEND: 'LEND',       // 闲置借出
  WANTED: 'WANTED',   // 需求借入
  HELP: 'HELP'        // 技能求助
};

// ========== 账户审核状态（与后端 BizStatus 对齐）==========
const AUTH_STATUS = {
  PENDING: 'pending',           // 待审核
  APPROVED: 'approved',         // 已通过
  REJECTED: 'rejected',         // 已驳回
  REGISTERING: 'registering',   // 注册中（注册资料未提交完成）
  BANNED: 'banned'              // 已封禁
};

// ========== 借用申请状态（与后端 borrow_requests.status 对齐）==========
const BORROW_STATUS = {
  PENDING: 'pending',       // 待审批
  APPROVED: 'approved',     // 已同意
  REJECTED: 'rejected',     // 已拒绝
  RETURNED: 'returned',     // 已归还
  CANCELLED: 'cancelled',   // 已取消
  COMPLETED: 'completed'    // 已完成
};

// ========== 帮助申请状态（与后端 help_applications.status 对齐）==========
const HELP_APPLICATION_STATUS = {
  PENDING: 'pending',       // 待审批
  APPROVED: 'approved',     // 已同意
  REJECTED: 'rejected',     // 已拒绝
  COMPLETED: 'completed'    // 已完成
};

/**
 * 损坏类型 — borrow_requests.damage_type 字段的唯一合法取值。
 * 与后端 com.platform.common.DamageType 保持一致。
 */
const DAMAGE_TYPE = {
  NORMAL: 'normal',    // 正常损耗
  ABNORMAL: 'severe',  // 非正常损坏（数据库值保持 severe）
  BROKEN: 'broken'     // 完全损坏
};

/**
 * 归还状态 — borrow_requests.return_status 字段的唯一合法取值。
 * 与后端 com.platform.common.ReturnStatus 保持一致。
 */
const RETURN_STATUS = {
  ON_TIME: 'ontime',           // 按时归还
  DELAYED: 'delayed',          // 逾期归还
  NOT_RETURNED: 'not_returned' // 未归还
};

/**
 * 通知类型 — 与后端 Notification.type 字段一致
 */
const NOTIFICATION_TYPE = {
  MATCH_DEMAND: 'match_demand',          // 供需匹配：有人需要你出借过的物品
  CONTENT_REJECTED: 'content_rejected',  // AI 内容审核驳回
  CONTENT_APPROVED: 'content_approved'   // AI 内容审核通过
};

/**
 * 本地存储键 — 跨页面共享的 storage key 统一管理，改名安全。
 */
const STORAGE_KEY = {
  AGENT_DRAFT: 'agent_draft',            // AI 助手动作卡片发布草稿（assistant 写入 → publish-idle 读取）
  HISTORY_FAB_POS: 'history_fab_pos',    // 小邻历史悬浮按钮位置（拖动记忆，跨会话保留）
  HOME_FAB_POS: 'home_fab_pos'           // 首页 + 悬浮按钮位置（拖动记忆，跨会话保留）
};

module.exports = {
  POST_STATUS,
  POST_TYPE,
  AUTH_STATUS,
  BORROW_STATUS,
  HELP_APPLICATION_STATUS,
  DAMAGE_TYPE,
  RETURN_STATUS,
  NOTIFICATION_TYPE,
  STORAGE_KEY
};
