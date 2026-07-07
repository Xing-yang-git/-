/**
 * API utility module for WeChat Mini Program
 * Provides get/post/put/del methods with automatic token injection
 */

const request = (method, url, data) => {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const baseUrl = app.globalData ? app.globalData.baseUrl : '';
    const token = wx.getStorageSync('token') || '';

    wx.request({
      method: method,
      url: baseUrl + url,
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      data: data,
      success: (res) => {
        if (res.statusCode === 200) {
          // Unwrap backend Result wrapper: { code, message, data } → data
          resolve(res.data.data !== undefined ? res.data.data : res.data);
        } else if (res.statusCode === 401) {
          wx.removeStorageSync('token');
          wx.reLaunch({ url: '/pages/login/login' });
          reject(new Error('登录已过期，请重新登录'));
        } else {
          reject(new Error(res.data.message || '请求失败'));
        }
      },
      fail: (err) => {
        reject(new Error('网络连接失败，请检查网络'));
      }
    });
  });
};

/**
 * Upload a file to the backend.
 * @param {string} url  - API path, e.g. '/api/common/upload'
 * @param {string} filePath - local temp file path from wx.chooseImage
 * @returns {Promise<string>} the uploaded file URL
 */
const upload = (url, filePath) => {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const baseUrl = app.globalData ? app.globalData.baseUrl : '';
    const token = wx.getStorageSync('token') || '';

    wx.uploadFile({
      url: baseUrl + url,
      filePath: filePath,
      name: 'file',
      header: {
        'Authorization': token ? 'Bearer ' + token : ''
      },
      success: (res) => {
        if (res.statusCode === 200) {
          try {
            const data = JSON.parse(res.data);
            resolve(data.data !== undefined ? data.data.url : data.url);
          } catch (e) {
            reject(new Error('上传响应解析失败'));
          }
        } else {
          reject(new Error('上传失败'));
        }
      },
      fail: () => {
        reject(new Error('网络连接失败，请检查网络'));
      }
    });
  });
};

const api = {
  get: (url, data) => request('GET', url, data),
  post: (url, data) => request('POST', url, data),
  put: (url, data) => request('PUT', url, data),
  del: (url, data) => request('DELETE', url, data),
  upload: upload
};

module.exports = api;
