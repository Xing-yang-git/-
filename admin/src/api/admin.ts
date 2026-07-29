/**
 * 管理端 API 模块 — 封装 /api/admin/* 全部接口，严格 TypeScript 类型。
 */

import { get, post, put, del } from '../utils/api';
import type { AxiosResponse } from 'axios';

// ============================================================
// 通用类型
// ============================================================

/** 通用分页响应 */
export interface PageDTO<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
}

// ============================================================
// 审核
// ============================================================

export interface AuditListParams {
  status?: string;
  page?: number;
  size?: number;
}

export interface AuditCounts {
  pending: number;
  approved: number;
  rejected: number;
  all: number;
}

export interface AuditUserBody {
  approved: boolean;
  reason?: string;
}

/** 审核列表中的用户条目 */
export interface AuditUserDTO {
  id: number;
  name: string;
  phone: string;
  userType: string;
  userRoom: string;
  authStatus: string;
  rejectReason?: string;
  docImages?: string[];
  auditorName?: string;
  createdAt: string;
}

export function getAudits(params: AuditListParams = {}): Promise<AxiosResponse> {
  return get('/api/admin/audits', { status: params.status, page: params.page ?? 0, size: params.size ?? 200 });
}

export function getAuditCounts(): Promise<AxiosResponse> {
  return get('/api/admin/audits/counts');
}

export function auditUser(userId: number, body: AuditUserBody): Promise<AxiosResponse> {
  return put(`/api/admin/audits/${userId}`, body);
}

// ============================================================
// 内容管理
// ============================================================

export interface ContentListParams {
  status?: string;
  type?: 'idle' | 'help';
  building?: string;
  unit?: string;
  search?: string;
  page: number;
  size: number;
}

/** 对方信息（进行中 / 已完成时填充） */
export interface PeerInfo {
  peerName?: string;
  peerRoom?: string;
  peerRating?: number;
}

/** 违规信息 */
export interface ViolationInfo {
  violationType?: string;
  violationReason?: string;
  violatorName?: string;
  violatedAt?: string;
}

export interface ContentItemDTO {
  id: number;
  type: 'idle' | 'help';
  title: string;
  description: string;
  category: string;
  rawStatus: string;
  displayStatus: string;
  publisherName: string;
  publisherRoom: string;
  isProxy: boolean;
  isUrgent?: boolean;
  createdAt: string;
  // 时间线（已完成时填充）
  timeStart?: string;
  timeEnd?: string;
  applyAt?: string;
  approveAt?: string;
  completeAt?: string;
  // 图片（详情展示用）
  images?: string[];
  // 评价（已完成时填充）
  publisherRatingStars?: string;
  publisherRatingScore?: number;
  peerRatingStars?: string;
  peerRatingScore?: number;
  // 互借专属（详情展示用）
  maxDuration?: number;
  durationUnit?: string;
  // 违规下架信息（已下架时填充）
  violationReason?: string;
  violationType?: string;
  violatedAt?: string;
  violatorName?: string;
  // 审批信息（待审批时填充）
  /** 审批人姓名 */
  approverName?: string;
  /** 申请人姓名（即发布者） */
  applicantName?: string;
}

export interface ContentOfflineBody {
  targetType: string;
  reasons: string[];
  customReason?: string;
}

export interface ContentCounts {
  showing: number;
  pending: number;
  progressing: number;
  completed: number;
  violation: number;
  all: number;
}

export function getContentList(params: ContentListParams): Promise<AxiosResponse> {
  return get('/api/admin/content', params);
}

export function getContentCounts(): Promise<AxiosResponse> {
  return get('/api/admin/content/counts');
}

export function getContentDetail(id: number, type: 'idle' | 'help'): Promise<AxiosResponse> {
  return get(`/api/admin/content/${id}`, { type });
}

export function offlineContent(id: number, body: ContentOfflineBody): Promise<AxiosResponse> {
  return put(`/api/admin/content/${id}/offline`, body);
}

// ============================================================
// 代发
// ============================================================

export interface PublishIdleBody {
  /** 目标住户 ID（管理员代发时指定） */
  userId?: number | null;
  /** 是否为物业代发（管理员代发时设为 true） */
  isProxy?: boolean;
  /** 发布类型：LEND（闲置借出）或 WANTED（需求借入） */
  postType: string;
  /** 物品标题 */
  title: string;
  /** 物品描述（选填） */
  description?: string;
  /** 物品分类 */
  category: string;
  /** 物品成色：like-new / normal / worn */
  condition?: string;
  /** 参考价格（元） */
  price?: number;
  /** 物品图片 URL 的 JSON 数组字符串 */
  images?: string;
  /** 最大借出天数/小时数 */
  maxDuration?: number;
  /** 借出时长单位：day / hour */
  durationUnit?: string;
  /** 借出形式：self_pickup / both */
  pickupMethod?: string;
}

export interface PublishHelpBody {
  /** 目标住户 ID（管理员代发时指定） */
  userId?: number | null;
  /** 是否为物业代发（管理员代发时设为 true） */
  isProxy?: boolean;
  /** 求助标题 */
  title: string;
  /** 求助描述（选填） */
  description?: string;
  /** 求助分类 */
  category: string;
  /** 是否紧急 */
  isUrgent?: boolean;
  /** 预计开始时间（格式：yyyy-MM-dd HH:mm） */
  timeStart?: string;
  /** 预计结束时间（格式：yyyy-MM-dd HH:mm） */
  timeEnd?: string;
  /** 图片 URL 的 JSON 数组字符串 */
  images?: string;
}

export function publishIdle(body: PublishIdleBody): Promise<AxiosResponse> {
  return post('/api/idle-items', body);
}

export function publishHelp(body: PublishHelpBody): Promise<AxiosResponse> {
  return post('/api/help-requests', body);
}

// ============================================================
// 住户搜索
// ============================================================

export interface ResidentSearchParams {
  page?: number;
  size?: number;
  userType?: string;
  building?: string;
  unit?: string;
  keyword?: string;
}

export function searchResidents(params: ResidentSearchParams): Promise<AxiosResponse> {
  return get('/api/admin/residents/search', params);
}

// ============================================================
// 仪表盘
// ============================================================

export interface CategoryStat {
  category: string;
  count: number;
}

export interface ItemStat {
  label: string;
  value: number;
}

export interface DashboardDTO {
  onlineIdleCount: number;
  onlineHelpCount: number;
  monthlyPublishes: number;
  monthlyCompletedBorrows: number;
  completionRate: number;
  monthlyActiveUsers: number;
  damageCount: number;
  categoryStats: CategoryStat[];
  itemStats: ItemStat[];
}

export function getDashboard(): Promise<AxiosResponse> {
  return get('/api/admin/dashboard');
}

// ============================================================
// 互助记录
// ============================================================

/** 互助记录列表查询参数 */
export interface RecordsListParams {
  type?: string;
  page?: number;
  size?: number;
}

/** 互助记录条目（后端 getRecords 返回的单条记录） */
export interface RecordItemDTO {
  id: number;
  type: 'borrow' | 'help';
  title: string;
  publisher: string;
  peer: string;
  content: string;
  room: string;
  /** 格式化时间 "yyyy-MM-dd HH:mm" */
  timeStart: string;
  timeEnd: string | null;
  status: string;
  createdAt: string;
  // 时间线（5 节点）
  publishedAt: string | null;
  applyAt: string | null;
  approveAt: string | null;
  rating1Label?: string | null;
  rating1Time?: string | null;
  rating2Label?: string | null;
  rating2Time?: string | null;
  // 借用详情（已中文化）
  lendDuration?: string;
  condBefore?: string | null;
  condAfter?: string | null;
  returnStatus?: string | null;
  damageType?: string | null;
  // 评分（1-5 数字）
  pubRatingScore?: number | null;
  pubComment?: string | null;
  peerRatingScore?: number | null;
  peerComment?: string | null;
}

export function getRecords(params: RecordsListParams = {}): Promise<AxiosResponse> {
  return get('/api/admin/records', { type: params.type ?? 'all', page: params.page ?? 0, size: params.size ?? 200 });
}

// ============================================================
// 小区数据
// ============================================================

export interface UnitData {
  id: number;
  name: string;
}

export interface BuildingData {
  id: number;
  name: string;
  units: UnitData[];
}

export interface CommunityData {
  tenantName: string;
  buildings: BuildingData[];
}

export function getCommunity(): Promise<AxiosResponse> {
  return get('/api/admin/community');
}

export function getBuildings(): Promise<AxiosResponse> {
  return get('/api/admin/buildings');
}

export function getTenants(): Promise<AxiosResponse> {
  return get('/api/admin/tenants');
}

// ============================================================
// 管理员管理
// ============================================================

export interface CreateAdminBody {
  name: string;
  phone: string;
  password: string;
  /** 目标小区ID（super_admin 创建时必须指定） */
  tenantId: number;
  /** 管理员类型：admin 或 senior_admin */
  userType: string;
}

export function getAdmins(): Promise<AxiosResponse> {
  return get('/api/admin/admins');
}

export function createAdmin(body: CreateAdminBody): Promise<AxiosResponse> {
  return post('/api/admin/admins', body);
}

export function deleteAdmin(id: number): Promise<AxiosResponse> {
  return del(`/api/admin/admins/${id}`);
}

// ============================================================
// 个人设置
// ============================================================

export interface UpdateProfileBody {
  name: string;
}

export interface UpdatePasswordBody {
  oldPassword: string;
  newPassword: string;
}

export function updateProfile(body: UpdateProfileBody): Promise<AxiosResponse> {
  return put('/api/admin/profile', body);
}

export function updatePassword(body: UpdatePasswordBody): Promise<AxiosResponse> {
  return put('/api/admin/password', body);
}

// ============================================================
// 操作日志
// ============================================================

export interface LogListParams {
  page?: number;
  size?: number;
}

export interface OperationLogDTO {
  id: number;
  adminName: string;
  action: string;
  targetType: string;
  targetId: number;
  detail: string;
  createdAt: string;
}

export function getLogs(params: LogListParams = {}): Promise<AxiosResponse> {
  return get('/api/admin/logs', { page: params.page ?? 0, size: params.size ?? 20 });
}

// ============================================================
// 数据导出（原生 fetch，POST JSON body，文件下载不走 axios JSON 流）
// ============================================================

/** 导出请求参数 */
export interface ExportParams {
  /** 导出格式，固定 "xlsx" */
  format: 'xlsx';
  /** 筛选开始日期（yyyy-MM-dd），可选 */
  dateStart?: string;
  /** 筛选结束日期（yyyy-MM-dd），可选 */
  dateEnd?: string;
  /** 勾选的导出项目列表：residents / posts / borrows / removals / ratings */
  options: string[];
}

/** 导出日志条目 */
export interface ExportLogItem {
  /** 日志ID */
  id: number;
  /** 操作人姓名 */
  adminName: string;
  /** 导出时间 */
  createdAt: string;
  /** 导出格式 */
  exportFormat: string;
  /** 勾选项目（JSON数组字符串） */
  selectedOptions: string;
  /** 各 Sheet 记录数汇总描述 */
  countSummary: string;
  /** 文件名 */
  fileName: string;
}

/** 导出日志查询参数 */
export interface ExportLogParams {
  page?: number;
  size?: number;
}

/**
 * 执行数据导出，发送 POST 请求到后端生成多 Sheet Excel 文件。
 * @param params - 导出参数（options、日期范围、格式）
 * @returns 包含 Blob 和文件名的对象
 */
export async function exportData(params: ExportParams): Promise<{ blob: Blob; fileName: string }> {
  const token = localStorage.getItem('admin_token');
  const response = await fetch('/api/admin/exports', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({
      options: params.options,
      dateStart: params.dateStart || null,
      dateEnd: params.dateEnd || null,
      format: params.format,
    }),
  });

  if (!response.ok) {
    throw new Error(`导出失败: HTTP ${response.status}`);
  }

  const blob = await response.blob();
  // 从 Content-Disposition 响应头提取后端生成的文件名（兼容 RFC 5987 filename*= 格式）
  const disposition = response.headers.get('Content-Disposition');
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?(.+)/i);
  const fileName = match ? decodeURIComponent(match[1]) : `export.xlsx`;

  return { blob, fileName };
}

/**
 * 查询导出日志列表（分页）。
 * @param params - 分页参数
 * @returns AxiosResponse，data 为分页日志数据
 */
export function getExportLogs(params: ExportLogParams = {}): Promise<AxiosResponse> {
  return get('/api/admin/exports/logs', { page: params.page ?? 0, size: params.size ?? 10 });
}

/**
 * 导出导出日志为 Excel 文件，触发浏览器下载。
 */
export async function exportExportLogs(): Promise<void> {
  const token = localStorage.getItem('admin_token');
  const response = await fetch('/api/admin/exports/logs/export', {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({ message: '导出失败' }));
    throw new Error(err.message || '导出失败');
  }
  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition');
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?(.+)/i);
  const fileName = match ? decodeURIComponent(match[1]) : '导出日志.xlsx';
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}

/**
 * 导出操作日志为 Excel 文件，触发浏览器下载。
 */
export async function exportOperationLogs(): Promise<void> {
  const token = localStorage.getItem('admin_token');
  const response = await fetch('/api/admin/logs/export', {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({ message: '导出失败' }));
    throw new Error(err.message || '导出失败');
  }
  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition');
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?(.+)/i);
  const fileName = match ? decodeURIComponent(match[1]) : '操作日志.xlsx';
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}
