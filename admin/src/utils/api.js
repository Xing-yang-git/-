import axios from 'axios';
import { ElMessage } from 'element-plus';

const api = axios.create({
  baseURL: '',
  timeout: 15000
});

// 请求拦截器 — 附加 token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('admin_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器 — 处理 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 便捷方法
export function get(url, params) {
  return api.get(url, { params });
}

export function post(url, data) {
  return api.post(url, data);
}

export function put(url, data) {
  return api.put(url, data);
}

export function del(url) {
  return api.delete(url);
}

export default api;
