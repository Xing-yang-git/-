import axios from 'axios';
import { ElMessage } from 'element-plus';

const api = axios.create({
  baseURL: '',
  timeout: 15000
});

// Request interceptor — attach token
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

// Response interceptor — handle 401
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

// Convenience methods
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
