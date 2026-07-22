/**
 * Axios 实例与通用 HTTP 方法模块。
 * 封装 token 注入、401 响应拦截，导出语义化的 get/post/put/del 方法。
 */

import axios, { type AxiosResponse, type AxiosInstance } from "axios";

/** 全局 Axios 实例，所有 HTTP 请求共享此实例的拦截器配置 */
const api: AxiosInstance = axios.create({
  baseURL: "",
  timeout: 15000,
});

// 请求拦截器 — 附加 token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("admin_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// 响应拦截器 — 处理 401 / 403，清除登录态并跳转
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      error.response &&
      (error.response.status === 401 || error.response.status === 403)
    ) {
      localStorage.removeItem("admin_token");
      localStorage.removeItem("admin_user");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  },
);

/**
 * 发送 GET 请求。
 * @param url - 请求路径
 * @param params - 查询参数（可选）
 * @returns AxiosResponse，可通过泛型指定业务数据类型
 */
export function get<T = unknown>(
  url: string,
  params?: Record<string, any>,
): Promise<AxiosResponse<T>> {
  return api.get(url, { params });
}

/**
 * 发送 POST 请求。
 * @param url - 请求路径
 * @param data - 请求体
 * @returns AxiosResponse，可通过泛型指定业务数据类型
 */
export function post<T = unknown>(
  url: string,
  data?: unknown,
): Promise<AxiosResponse<T>> {
  return api.post(url, data);
}

/**
 * 发送 PUT 请求。
 * @param url - 请求路径
 * @param data - 请求体
 * @returns AxiosResponse，可通过泛型指定业务数据类型
 */
export function put<T = unknown>(
  url: string,
  data?: unknown,
): Promise<AxiosResponse<T>> {
  return api.put(url, data);
}

/**
 * 发送 DELETE 请求。
 * @param url - 请求路径
 * @returns AxiosResponse，可通过泛型指定业务数据类型
 */
export function del<T = unknown>(url: string): Promise<AxiosResponse<T>> {
  return api.delete(url);
}

export default api;
