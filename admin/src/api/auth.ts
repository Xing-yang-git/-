/**
 * 认证 API 模块
 */

import { post } from '../utils/api';
import type { AxiosResponse } from 'axios';

/** 登录请求参数 */
export interface LoginParams {
  username: string;
  password: string;
}

/** 登录响应中返回的管理员信息 */
export interface AdminUser {
  id: number;
  name: string;
  userType: string;
  authStatus: string;
}

/** 登录接口完整响应 */
export interface LoginResult {
  token: string;
  user: AdminUser;
}

/**
 * 管理员登录。
 * @returns AxiosResponse，业务数据在 res.data.data
 */
export function login(username: string, password: string): Promise<AxiosResponse> {
  return post('/api/auth/login', { username, password });
}
