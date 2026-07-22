/**
 * 微信小程序 API 工具模块
 * 提供 get/post/put/del 方法，自动注入 token
 *
 * 注意：手机预览模式下 wx.request 会被微信校验请求域名白名单。
 * 如遇 "url not in domain list" 错误，请使用「真机调试」模式（自动代理请求），
 * 或在预览二维码生成时勾选「不校验合法域名」选项。
 */

const { STATUS } = require('./constants');

// —— 防抖：避免并发 401 触发多次 wx.reLaunch ——
let _reauthPending = false;
let _reauthTimer = null;

/** 重置 _reauthPending 状态（安全兜底：最多 3 秒后自动恢复） */
function _resetReauth() {
  _reauthPending = false;
  if (_reauthTimer) {
    clearTimeout(_reauthTimer);
    _reauthTimer = null;
  }
}

/**
 * 强制重新登录：清除 token 并跳转登录页
 * 防抖：只跳转一次；安全兜底 3 秒后自动恢复
 * 供 401 响应及 WebSocket 被挤下线（code 4001）等场景调用
 */
function forceRelogin() {
  if (_reauthPending) return;
  _reauthPending = true;
  wx.removeStorageSync('token');
  wx.removeStorageSync('userInfo');
  // 在 storage 中留下消息，登录页 onLoad 会读取并展示 toast
  wx.setStorageSync('loginMessage', '账号已在其他设备登录，请重新登录');
  wx.reLaunch({
    url: '/pages/login/login',
    fail: () => { _resetReauth(); },
    complete: () => { _resetReauth(); }
  });
  // 安全兜底：3 秒后强制恢复，防止 reLaunch 回调不触发导致永久阻塞
  _reauthTimer = setTimeout(() => { _resetReauth(); }, 3000);
}

/**
 * 未审核账号被服务端 403 拒绝：同步本地审核状态并跳审核状态页。
 * 复用 _reauthPending 防抖（401/403 并发时只跳一次）；
 * 不清 token（审核页轮询 /api/auth/status 需要它）。
 */
function forceReviewStatus(state) {
  if (_reauthPending) return;
  _reauthPending = true;
  // 先同步本地状态，防止页面门禁/登录路由继续用旧状态
  const userInfo = wx.getStorageSync('userInfo') || {};
  userInfo.authStatus = state;
  wx.setStorageSync('userInfo', userInfo);
  const app = getApp();
  if (app) app.globalData.userInfo = userInfo;
  wx.reLaunch({
    url: '/pages/review-status/review-status?state=' + state,
    fail: () => { _resetReauth(); },
    complete: () => { _resetReauth(); }
  });
  // 与 forceRelogin 相同的安全兜底：3 秒后强制恢复，防止 reLaunch 回调不触发导致永久阻塞
  _reauthTimer = setTimeout(() => { _resetReauth(); }, 3000);
}

const request = (method, url, data) => {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const baseUrl = app.globalData ? app.globalData.baseUrl : '';
    const token = wx.getStorageSync('token') || '';

    // POST/PUT/DELETE 显式 JSON 序列化：避免 wx.request 因 content-type
    // 大小写匹配问题而退化为 form-urlencoded，导致后端 @RequestBody 解析失败
    // GET 请求的 data 为 query string 参数，不序列化
    const isJsonBody = method === 'POST' || method === 'PUT' || method === 'DELETE';
    const body = isJsonBody && data != null ? JSON.stringify(data) : data;

    wx.request({
      method: method,
      url: baseUrl + url,
      header: {
        'content-type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      data: body,
      success: (res) => {
        if (res.statusCode === 200) {
          // 解包后端 Result 包装器：{ code, message, data } → data
          resolve(res.data.data !== undefined ? res.data.data : res.data);
        } else if (res.statusCode === 401) {
          forceRelogin();
          reject(new Error('请先登录'));
        } else if (res.statusCode === 403) {
          // 未审核账号被服务端拒绝：body 为 { code: 403, message: '账号未通过审核', data: '<authStatus>' }
          const resBody = res.data || {};
          if (resBody.message === '账号未通过审核') {
            forceReviewStatus(typeof resBody.data === 'string' ? resBody.data : STATUS.PENDING);
            reject(new Error('账号未通过审核'));
          } else {
            reject(new Error(resBody.message || '无权限访问'));
          }
        } else if (res.statusCode >= 500) {
          reject(new Error(res.data.message || '服务器异常，请稍后重试'));
        } else {
          reject(new Error(res.data.message || '请求失败'));
        }
      },
      fail: (err) => {
        // 区分网络错误和域名校验错误
        const errMsg = (err && err.errMsg) || '';
        if (errMsg.indexOf('url not in domain list') !== -1) {
          reject(new Error('当前网络环境无法连接服务器，请使用真机调试模式'));
        } else if (errMsg.indexOf('timeout') !== -1) {
          reject(new Error('请求超时，请检查网络连接'));
        } else {
          reject(new Error('网络连接失败，请检查网络'));
        }
      }
    });
  });
};

/**
 * 上传文件到后端。
 * @param {string} url  - API 路径，例如 '/api/common/upload'
 * @param {string} filePath - wx.chooseImage 返回的本地临时文件路径
 * @returns {Promise<string>} 上传后的文件 URL
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
            // HTTP 200 但业务码非 200（Result.code）也视为失败，透出后端文案
            if (data.code && data.code !== 200) {
              reject(new Error(data.message || '上传失败'));
              return;
            }
            resolve(data.data !== undefined ? data.data.url : data.url);
          } catch (e) {
            reject(new Error('上传响应解析失败'));
          }
        } else if (res.statusCode === 401) {
          forceRelogin();
          reject(new Error('请先登录'));
        } else if (res.statusCode === 403) {
          // 未审核账号被服务端拒绝：uploadFile 响应体为字符串，需先解析
          let body = {};
          try { body = JSON.parse(res.data) || {}; } catch (e) { /* 解析失败则按普通 403 处理 */ }
          if (body.message === '账号未通过审核') {
            forceReviewStatus(typeof body.data === 'string' ? body.data : STATUS.PENDING);
            reject(new Error('账号未通过审核'));
          } else {
            reject(new Error(body.message || '无权限访问'));
          }
        } else {
          // 4xx/5xx：后端业务报错（如图片类型不支持）以 Result JSON 返回，取出 message 给用户看
          let body = {};
          try { body = JSON.parse(res.data) || {}; } catch (e) { /* 非 JSON 响应按通用失败处理 */ }
          reject(new Error(body.message || '上传失败'));
        }
      },
      fail: (err) => {
        const errMsg = (err && err.errMsg) || '';
        if (errMsg.indexOf('url not in domain list') !== -1) {
          reject(new Error('当前网络环境无法连接服务器，请使用真机调试模式'));
        } else {
          reject(new Error('网络连接失败，请检查网络'));
        }
      }
    });
  });
};

const api = {
  get: (url, data) => request('GET', url, data),
  post: (url, data) => request('POST', url, data),
  put: (url, data) => request('PUT', url, data),
  del: (url, data) => request('DELETE', url, data),
  upload: upload,
  forceRelogin: forceRelogin,
  forceReviewStatus: forceReviewStatus
};

module.exports = api;
