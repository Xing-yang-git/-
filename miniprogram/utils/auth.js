/**
 * 认证工具模块
 * token 管理、登录检查、JWT 解析。
 */

const TOKEN_KEY = 'token';

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || '';
}

function setToken(token) {
  wx.setStorageSync(TOKEN_KEY, token);
  const app = getApp();
  if (app) {
    app.globalData.token = token;
  }
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY);
  const app = getApp();
  if (app) {
    app.globalData.token = '';
    app.globalData.userId = '';
    app.globalData.userInfo = null;
  }
}

function getUserId() {
  const token = getToken();
  if (!token) return '';
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return '';
    const payload = parts[1];
    const decoded = _base64Decode(payload);
    const json = JSON.parse(decoded);
    return json.userId || json.sub || '';
  } catch (e) {
    console.error('[auth] getUserId error', e);
    return '';
  }
}

function _base64Decode(str) {
  // 替换 URL 安全字符并补齐填充
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';

  // Base64 解码为字节数组——字符表第 65 位必须是 '='（padding 哨兵，index 64），
  // 缺失会导致带 padding 的 token 解出垃圾字节、JSON.parse 失败
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  const bytes = [];
  for (let i = 0; i < str.length; i += 4) {
    const a = chars.indexOf(str[i]);
    const b = chars.indexOf(str[i + 1]);
    const c = chars.indexOf(str[i + 2]);
    const d = chars.indexOf(str[i + 3]);
    bytes.push((a << 2) | (b >> 4));
    if (c !== 64) bytes.push(((b & 15) << 4) | (c >> 2));
    if (d !== 64) bytes.push(((c & 3) << 6) | d);
  }

  // 将字节转换为百分号编码字符串再解码（处理 UTF-8）
  let result = '';
  for (let i = 0; i < bytes.length; i++) {
    result += '%' + ('00' + bytes[i].toString(16)).slice(-2);
  }
  try {
    return decodeURIComponent(result);
  } catch (e) {
    // 兜底：原始 ASCII 字符串
    let ascii = '';
    for (let i = 0; i < bytes.length; i++) {
      ascii += String.fromCharCode(bytes[i]);
    }
    return ascii;
  }
}

// —— 门禁防抖：onLoad+onShow 同帧双触发时只 reLaunch 一次 ——
let _guardPending = false;
function _guardRedirect(url) {
  if (_guardPending) return;
  _guardPending = true;
  wx.reLaunch({
    url,
    complete: () => {
      setTimeout(() => { _guardPending = false; }, 1000);
    }
  });
}

/**
 * 页面访问门禁：业务页面在 onLoad/onShow 首行调用。
 * 无 token → 登录页；authStatus 未通过审核 → 对应状态页（注册页/审核页）。
 * @returns {boolean} true = 放行，false = 已发起跳转（调用方应立即 return）
 */
function ensureAccess() {
  if (!getToken()) {
    _guardRedirect('/pages/login/login');
    return false;
  }
  const userInfo = wx.getStorageSync('userInfo') || {};
  const status = userInfo.authStatus;
  if (!status || status === 'approved') return true; // 旧存储无 authStatus 放行，由服务端 401/403 兜底
  if (status === 'registering') {
    _guardRedirect('/pages/register/register');
    return false;
  }
  // pending / rejected / banned → 审核状态页
  _guardRedirect('/pages/review-status/review-status?state=' + status);
  return false;
}

function checkLogin() {
  return ensureAccess();
}

module.exports = {
  checkLogin,
  ensureAccess,
  getToken,
  setToken,
  clearToken,
  getUserId
};
