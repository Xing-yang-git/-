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
  statusTab?: string;
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
}

export interface ContentOfflineBody {
  targetType: string;
  reasons: string[];
  customReason?: string;
}

export interface ContentCounts {
  showing: number;
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

export interface ProxyIdleBody {
  userId: number | null;
  postType: string;
  title: string;
  description: string;
  category: string;
  price: number;
  maxDuration: number;
}

export interface ProxyHelpBody {
  userId: number | null;
  title: string;
  description: string;
  category: string;
  isUrgent: boolean;
  timeStart?: string;
  timeEnd?: string;
}

export function proxyPublishIdle(body: ProxyIdleBody): Promise<AxiosResponse> {
  return post('/api/admin/proxy/idle', body);
}

export function proxyPublishHelp(body: ProxyHelpBody): Promise<AxiosResponse> {
  return post('/api/admin/proxy/help', body);
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

// ============================================================
// 管理员管理
// ============================================================

export interface CreateAdminBody {
  name: string;
  phone: string;
  password: string;
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
// 数据导出（原生 fetch，文件下载不走 axios JSON 流）
// ============================================================

export interface ExportParams {
  format: 'csv' | 'xlsx';
  start?: string;
  end?: string;
  options: string[];
}

export function exportData(params: ExportParams): Promise<Response> {
  const token = localStorage.getItem('admin_token');
  const searchParams = new URLSearchParams();
  searchParams.set('format', params.format);
  if (params.start) searchParams.set('start', params.start);
  if (params.end) searchParams.set('end', params.end);
  if (params.options.length > 0) {
    searchParams.set('options', params.options.join(','));
  }
  return fetch(`/api/admin/export?${searchParams.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
}
