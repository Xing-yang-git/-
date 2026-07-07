/**
 * Auth Utility
 * Token management, login check, JWT parsing.
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
  // Replace URL-safe chars and add padding
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';

  // Base64 decode to byte array
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
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

  // Convert bytes to percent-encoded string, then decode (handles UTF-8)
  let result = '';
  for (let i = 0; i < bytes.length; i++) {
    result += '%' + ('00' + bytes[i].toString(16)).slice(-2);
  }
  try {
    return decodeURIComponent(result);
  } catch (e) {
    // Fallback: raw ASCII string
    let ascii = '';
    for (let i = 0; i < bytes.length; i++) {
      ascii += String.fromCharCode(bytes[i]);
    }
    return ascii;
  }
}

function checkLogin() {
  const token = getToken();
  if (!token) {
    wx.reLaunch({ url: '/pages/login/login' });
    return false;
  }
  return true;
}

module.exports = {
  checkLogin,
  getToken,
  setToken,
  clearToken,
  getUserId
};
