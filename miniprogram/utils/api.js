/**
 * 微信小程序 API 工具模块
 * 提供 get/post/put/del 方法，自动注入 token
 *
 * 注意：手机预览模式下 wx.request 会被微信校验请求域名白名单。
 * 如遇 "url not in domain list" 错误，请使用「真机调试」模式（自动代理请求），
 * 或在预览二维码生成时勾选「不校验合法域名」选项。
 */

const { AUTH_STATUS } = require('./constants');

// —— 防抖：避免并发 401 触发多次 wx.reLaunch ——
let _reauthPending = false;
let _reauthTimer = null;

/** 本会话内 enableChunked 分块通道是否已确认可用（由 {@link probeChunked} 探测得出）。
 * 默认 false = 非分块直发（消息只发一次，绝无降级重试导致的双发）；探测确认分块可用后才启用分块流式。 */
let _chunkedUsable = false;
/** 是否已探测过分块通道（防止重复探测） */
let _chunkProbed = false;

/**
 * 将 string / ArrayBuffer 统一解码为字符串（SSE 文本）。模块级复用（requestStream 与探测共用）。
 *
 * @param d 原始数据（string 或 ArrayBuffer）
 * @return 解码后的文本
 */
function toText(d) {
  if (typeof d === 'string') return d;
  if (d && d.byteLength) {
    const bytes = new Uint8Array(d);
    // 优先 TextDecoder 正确处理 UTF-8；缺失时退化为字节直转（中文会乱码但事件结构可解析）
    if (typeof TextDecoder !== 'undefined') {
      try { return new TextDecoder('utf-8').decode(bytes); } catch (e) { /* 走退化路径 */ }
    }
    const parts = [];
    const step = 8192;
    for (let i = 0; i < bytes.length; i += step) {
      parts.push(String.fromCharCode.apply(null, bytes.subarray(i, i + step)));
    }
    return parts.join('');
  }
  return '';
}

/**
 * 探测 enableChunked 分块通道是否可用（页面加载时调用一次，幂等）。
 *
 * <p>用分块方式请求轻量 SSE 探测端点：onChunkReceived 能收到数据或 success 拿到 body
 * 说明分块通道可用（标记 {@link _chunkedUsable}），否则保持默认非分块模式。
 * 探测本身零业务副作用（后端 /api/agent/probe 不读会话、不调 LLM）。</p>
 */
function probeChunked() {
  if (_chunkProbed) return;
  _chunkProbed = true;
  const app = getApp();
  const baseUrl = app.globalData ? app.globalData.baseUrl : '';
  const token = wx.getStorageSync('token') || '';
  let receivedChunk = false;
  wx.request({
    method: 'GET',
    url: baseUrl + '/api/agent/probe',
    header: {
      'Accept': 'text/event-stream',
      'Authorization': token ? 'Bearer ' + token : ''
    },
    enableChunked: true,
    responseType: 'text',
    success: (res) => {
      // onChunkReceived 已送达数据则无需再看 success body
      if (receivedChunk) return;
      const body = toText(res.data);
      if (body && body.indexOf('data:') !== -1) _chunkedUsable = true;
    },
    fail: () => { /* 探测失败保持默认非分块（_chunkedUsable 仍为 false） */ },
    onChunkReceived: (res) => {
      const chunk = toText(res.data);
      if (chunk) {
        receivedChunk = true;
        _chunkedUsable = true;
      }
    }
  });
}

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
            forceReviewStatus(typeof resBody.data === 'string' ? resBody.data : AUTH_STATUS.PENDING);
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
            forceReviewStatus(typeof body.data === 'string' ? body.data : AUTH_STATUS.PENDING);
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

/**
 * 流式请求（SSE）— AI Agent「小邻」对话用。
 *
 * @param {string} url - API 路径，如 '/api/agent/chat'
 * @param {Object} data - POST JSON 数据
 * @param {Function} onChunk - 每收到 SSE 文本分块的回调（参数为累计原文 chunk）
 * @param {Function} onError - 网络/请求错误回调
 * @returns {Function} 中止函数（调用后取消请求，不再触发任何回调）
 */
const requestStream = (url, data, onChunk, onError) => {
  const app = getApp();
  const baseUrl = app.globalData ? app.globalData.baseUrl : '';
  const token = wx.getStorageSync('token') || '';
  let cancelled = false;
  let task = null;
  // 是否已通过 onChunkReceived 收到非空分块（未收到时 success 里兜底喂完整响应体）
  let receivedChunk = false;
  // 是否已降级为非分块请求重试过（分块请求网络层失败场景）
  let retried = false;

  // 模式：由探测结果决定——分块可用才用分块真流式；否则非分块直发（消息只发一次，绝无降级重试双发）。
  // 分块模式下每个分块 1 次 setData，非分块模式整段响应体一次到达、由解析器批量处理。
  const doRequest = (useChunked) => {
    task = wx.request({
      method: 'POST',
      url: baseUrl + url,
      header: {
        'content-type': 'application/json',
        'Accept': 'text/event-stream',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      data: JSON.stringify(data),
      enableChunked: useChunked,
      responseType: 'text',
      success: (res) => {
        if (cancelled) return;
        if (res.statusCode === 401) {
          forceRelogin();
        } else if (res.statusCode !== 200) {
          // 非 200 错误响应（@Valid 校验 400 / 意外 500）→ 直接报错，避免前端永久卡在发送中
          const msg = (res.data && res.data.message) || '请求失败';
          onError(new Error(msg));
        } else if (!receivedChunk) {
          // 非分块兜底：整个 SSE 响应体一次性到达，喂给解析器批量处理
          const body = toText(res.data);
          console.log('[requestStream] 兜底全量 bodyLen=', body ? body.length : 0, 'hasData=', body ? body.indexOf('data:') : -1);
          if (body && body.indexOf('data:') !== -1) {
            // 第二个参数 isFallback=true 告知解析器：这是非分块全量体（而非真机原生分块），
            // 解析器据此判断是否用打字机模拟流式，避免真机 TCP 粘包（原生分块多事件一次到达）被误判
            onChunk(body, true);
          } else {
            // 200 但无 SSE 事件 → 视为异常，避免卡到看门狗超时。
            // 注意：这里不做降级重试——分块请求已送达后端并处理过，重试会触发服务端重复处理
            // （同一消息二次处理会命中重复规则），由探测结果决定模式，默认非分块即可规避
            onError(new Error('响应格式异常'));
          }
        }
        // receivedChunk=true：事件已通过 onChunkReceived 分块送达，无需再喂
      },
      fail: (err) => {
        if (cancelled) return;
        // 已通过 onChunkReceived 收到过数据：连接关闭/拆流导致的 fail 不算错误——
        // 部分基础库流式响应结束后仍触发 fail，数据其实已完整送达（由 end 事件收尾）。
        // 若流中途真的断了（end 未到），静默看门狗会在 WATCHDOG_SILENCE_MS 后兜底判超时。
        if (receivedChunk) return;
        // enableChunked 在部分真机基础库不支持会立即 fail（且未收到任何分块）→
        // 降级为非分块普通请求重试一次（一次性拿全量响应体），避免"网络中断"误报
        if (useChunked && !retried) {
          console.log('[requestStream] 分块模式失败，降级为非分块重试:', err && err.errMsg);
          retried = true;
          doRequest(false);
          return;
        }
        console.error('[requestStream] 请求失败:', err && err.errMsg, err);
        onError(err);
      },
      onChunkReceived: (res) => {
        if (cancelled) return;
        const chunk = toText(res.data);
        if (chunk) {
          receivedChunk = true;
          // isFallback=false：原生分块流式，解析器直接消费、不做打字机模拟
          onChunk(chunk, false);
        }
      }
    });
  };

  // 探测确认分块可用才用分块真流式；否则（默认）非分块直发——消息只发一次，绝不双发
  if (_chunkedUsable) {
    doRequest(true);
  } else {
    doRequest(false);
  }

  return () => {
    cancelled = true;
    if (task && task.abort) task.abort();
  };
};

const api = {
  get: (url, data) => request('GET', url, data),
  post: (url, data) => request('POST', url, data),
  put: (url, data) => request('PUT', url, data),
  del: (url, data) => request('DELETE', url, data),
  upload: upload,
  requestStream: requestStream,
  probeChunked: probeChunked,
  forceRelogin: forceRelogin,
  forceReviewStatus: forceReviewStatus
};

module.exports = api;
