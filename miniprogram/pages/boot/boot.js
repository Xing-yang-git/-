const api = require('../../utils/api');
const { AUTH_STATUS, STORAGE_KEY } = require('../../utils/constants');

/**
 * Boot 启动页 — 冷启动会话恢复与路由分发。
 *
 * 作为 app.json 首个页面，在渲染任何登录 UI 之前完成会话恢复：
 * 同步读本地 token + 缓存审核状态，按状态直达目标页，消除已登录用户
 * "先看到登录页再自动跳首页"的闪现与等待：
 * - 无 token → 登录页；
 * - approved → 首页 tab；
 * - registering → 注册页；
 * - pending / rejected / banned → 审核状态页（页内"换账号登录"会清 token 回登录页，无死循环）。
 * 老会话（无缓存 authStatus）在本页异步拉一次 /api/auth/status 再路由，期间展示启动画面而非登录表单。
 */
Page({
  onLoad() {
    const token = wx.getStorageSync(STORAGE_KEY.TOKEN);
    if (!token) {
      this._go('reLaunch', '/pages/login/login');
      return;
    }
    const userInfo = wx.getStorageSync(STORAGE_KEY.USER_INFO) || {};
    if (userInfo.authStatus) {
      this.routeByStatus(userInfo.authStatus);
      return;
    }
    // 老会话无缓存审核状态：拉一次真实状态再路由
    api.get('/api/auth/status')
      .then((d) => this.routeByStatus(d.authStatus || AUTH_STATUS.APPROVED))
      .catch(() => {
        // 401 已由 api 层 forceRelogin 清 token 并 reLaunch 登录页，此处不重复路由，避免导航冲突
        if (!wx.getStorageSync(STORAGE_KEY.TOKEN)) return;
        // 网络异常乐观进首页；token 确已失效则由业务页 401 → forceRelogin 兜底重登
        this.routeByStatus(AUTH_STATUS.APPROVED);
      });
  },

  /**
   * 按审核状态分发到目标页（与登录页原路由逻辑对齐）。
   * 所有跳转经 _go 带失败重试，防止启动帧导航竞争导致停在启动画面。
   */
  routeByStatus(status) {
    if (status === AUTH_STATUS.REGISTERING) {
      this._go('redirectTo', '/pages/register/register?from=needRegister');
    } else if (status === AUTH_STATUS.APPROVED) {
      this._go('switchTab', '/pages/home/home');
    } else {
      // pending / rejected / banned → 审核状态页
      this._go('reLaunch', '/pages/review-status/review-status?state=' + status);
    }
  },

  /**
   * 导航安全网：启动帧内导航偶发失败时延迟重试一次，仍失败则兜底登录页
   * （登录页永远可达），避免用户停留在启动画面。
   *
   * @param {string} type 导航 API 名（switchTab / reLaunch / redirectTo）
   * @param {string} url  目标页地址
   */
  _go(type, url) {
    wx[type]({
      url,
      fail: () => {
        setTimeout(() => {
          wx[type]({
            url,
            fail: () => wx.reLaunch({ url: '/pages/login/login' })
          });
        }, 300);
      }
    });
  }
});
