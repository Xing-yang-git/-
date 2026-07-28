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
  ACTIVE: 'active',           // 进行中（借用/帮助进行中，统一替换旧值 borrowing/helping）
  // 以下为兼容旧值的别名，取值已与上方统一，避免存量代码报错
  RESERVED: 'pending',         // 已合并到 pending（申请提交后帖子标记为待审批）
  BORROWING: 'active'          // 已合并到 active（旧名 borrowing → 统一为进行中）
};

/**
 * 发布类型，与后端 PostType 一致
 */
const POST_TYPE = {
  LEND: 'LEND',     // 闲置借出
  WANTED: 'WANTED', // 需求借入
  HELP: 'HELP'      // 技能求助
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

module.exports = { STATUS, POST_TYPE, DAMAGE_TYPE, RETURN_STATUS };
