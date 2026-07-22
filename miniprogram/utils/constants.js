/**
 * 业务常量定义 — 状态与发布类型的唯一来源
 *
 * 与后端 com.platform.common.BizStatus / PostType 保持一致：
 * 这些字符串值即前后端通信的字面值（DTO 字段、URL 参数），不得随意改动。
 */

/**
 * 业务状态（账号审核 / 帖子上下架 / 借用申请流转共用同一组小写字符串）
 */
const STATUS = {
  PENDING: 'pending',         // 待审核 / 待处理
  APPROVED: 'approved',       // 已通过 / 已同意
  REJECTED: 'rejected',       // 已拒绝
  RETURNED: 'returned',       // 已归还
  COMPLETED: 'completed',     // 已完成
  ONLINE: 'online',           // 在线（帖子）
  OFFLINE: 'offline',         // 已下架（帖子）
  REGISTERING: 'registering', // 注册中（账号审核流程，注册资料未提交完成）
  // 账号状态（与后端 BizStatus 对齐）
  BANNED: 'banned',           // 已封禁
  CANCELLED: 'cancelled',     // 已取消（借用申请取消）
  // 借用交互 UI 状态（与后端 idleItem.status 对齐）
  RESERVED: 'reserved',       // 已被预定（申请提交后后端会同步设为 reserved）
  BORROWING: 'borrowing'      // 已借出（owner 同意借出申请后后端设为 borrowing）
};

/**
 * 发布类型，与后端 PostType 一致
 */
const POST_TYPE = {
  LEND: 'LEND',     // 闲置借出
  WANTED: 'WANTED', // 需求借入
  HELP: 'HELP'      // 技能求助
};

module.exports = { STATUS, POST_TYPE };
