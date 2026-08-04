/**
 * automator-client — 封装 miniprogram-automator 的连接生命周期管理
 */

import { Automator, MiniProgram } from '@weapp-vite/miniprogram-automator';
import path from 'node:path';
import fs from 'node:fs';
import { spawn } from 'node:child_process';
import config from './config.js';

// 修复：新版微信开发者工具返回 { version: "2.01.xxx" } 而非 { SDKVersion: "x.x.x" }
// 覆盖 checkVersion 以兼容两种格式，同时保留 configureToolInfo 调用以启用协议回退
const origCheckVersion = MiniProgram.prototype.checkVersion;
MiniProgram.prototype.checkVersion = async function () {
  try {
    const toolInfo = await this.send('Tool.getInfo');
    // 关键：调用 configureToolInfo 以检测版本并启用 App-service Page 协议回退
    if (this.connection && this.connection.configureToolInfo) {
      this.connection.configureToolInfo(toolInfo);
    }
    const sdkVersion = toolInfo.SDKVersion || toolInfo.version || '2.7.3';
    console.error('[automator] 工具版本:', sdkVersion);
    return;
  } catch (e) {
    console.error('[automator] 版本检查失败（已忽略）:', e.message);
  }
};

const automator = new Automator();

class AutomatorClient {
  constructor() {
    this.miniProgram = null;
    this.connected = false;
  }

  async launch() {
    if (this.connected && this.miniProgram) {
      return { connected: true, message: '已连接，无需重复启动' };
    }

    try {
      console.error('[automator] 正在启动微信开发者工具...');
      console.error(`[automator] 项目路径: ${config.projectPath}`);

      const port = config.devToolsPort;

      // 先尝试关闭已有的 IDE
      try {
        const quitProc = spawn(config.cliPath, ['quit'], {
          stdio: 'ignore', windowsHide: true,
        });
        await new Promise(r => { quitProc.on('close', r); setTimeout(r, 3000); });
      } catch { /* 忽略 */ }

      await this.sleep(2000);

      // 启动 auto 模式
      console.error(`[automator] 启动 auto 模式，端口: ${port}`);
      spawn(config.cliPath, [
        'auto', '--project', config.projectPath, '--auto-port', String(port),
      ], { stdio: 'ignore', windowsHide: true });

      // 轮询连接
      const maxAttempts = 30;
      let connected = false;
      for (let i = 0; i < maxAttempts; i++) {
        try {
          console.error(`[automator] 尝试连接 WS (${i + 1}/${maxAttempts})...`);
          this.miniProgram = await automator.connect({
            wsEndpoint: `ws://127.0.0.1:${port}`,
          });
          connected = true;
          break;
        } catch (e) {
          if (i < maxAttempts - 1) await this.sleep(2000);
        }
      }

      if (!connected) {
        throw new Error(`无法连接到 DevTools WebSocket (ws://127.0.0.1:${port})`);
      }

      this.connected = true;

      // 预热：触发 Page 协议回退（首次调用较慢，后续调用瞬间完成）
      console.error('[automator] 预热中（触发协议回退）...');
      const warmStart = Date.now();
      const currentPage = await this.miniProgram.currentPage();
      console.error(`[automator] 预热完成 (${Date.now() - warmStart}ms)，当前页面: ${currentPage ? currentPage.path : '未知'}`);

      return { connected: true, currentPage: currentPage ? currentPage.path : null };
    } catch (err) {
      console.error('[automator] 启动失败:', err.message);
      throw err;
    }
  }

  async close() {
    try { if (this.miniProgram) await this.miniProgram.close(); } catch {}
    this.miniProgram = null;
    this.connected = false;
  }

  ensureConnected() {
    if (!this.connected || !this.miniProgram) {
      throw new Error('未连接到模拟器，请先调用 mini_launch');
    }
  }

  // ==================== 页面导航 ====================

  async currentPage() {
    this.ensureConnected();
    const page = await this.miniProgram.currentPage();
    return {
      path: page ? page.path : null,
      query: page ? page.query : null,
      data: page ? await this.safeGetData(page) : null,
    };
  }

  async pageStack() {
    this.ensureConnected();
    const stack = await this.miniProgram.pageStack();
    return stack.map(p => ({ path: p.path, query: p.query }));
  }

  async navigateTo(url, method = 'navigateTo') {
    this.ensureConnected();
    const methods = {
      navigateTo: () => this.miniProgram.navigateTo(url),
      redirectTo: () => this.miniProgram.redirectTo(url),
      switchTab: () => this.miniProgram.switchTab(url),
      reLaunch: () => this.miniProgram.reLaunch(url),
      navigateBack: () => this.miniProgram.navigateBack(),
    };
    const fn = methods[method];
    if (!fn) throw new Error(`不支持的导航方式: ${method}`);
    try {
      const page = await fn();
      await this.sleep(500);
      return { path: page ? page.path : null, data: page ? await this.safeGetData(page) : null };
    } catch (err) {
      return { error: `导航失败: ${err.message}` };
    }
  }

  // ==================== 元素操作 ====================

  async findElement(selector) {
    this.ensureConnected();
    const page = await this.miniProgram.currentPage();
    if (!page) throw new Error('无法获取当前页面');
    return await page.$(selector);
  }

  async findElements(selector) {
    this.ensureConnected();
    const page = await this.miniProgram.currentPage();
    if (!page) throw new Error('无法获取当前页面');
    return await page.$$(selector);
  }

  async tap(selector) {
    this.ensureConnected();
    try {
      const el = await this.findElement(selector);
      if (!el) return { error: 'element_not_found', selector };
      await el.tap();
      return { tapped: true, selector };
    } catch (err) {
      return { error: `点击失败: ${err.message}`, selector };
    }
  }

  async input(selector, value) {
    this.ensureConnected();
    try {
      const el = await this.findElement(selector);
      if (!el) return { error: 'element_not_found', selector };
      await el.input(String(value));
      return { input: value, selector };
    } catch (err) {
      return { error: `输入失败: ${err.message}`, selector };
    }
  }

  async getText(selector) {
    this.ensureConnected();
    try {
      const el = await this.findElement(selector);
      if (!el) return { error: 'element_not_found', selector };
      const text = await el.text();
      return { text, selector };
    } catch (err) {
      return { error: `获取文本失败: ${err.message}`, selector };
    }
  }

  // ==================== 页面数据 ====================

  async getPageData(dataPath) {
    this.ensureConnected();
    try {
      const page = await this.miniProgram.currentPage();
      if (!page) throw new Error('无法获取当前页面');
      const data = await page.data(dataPath || undefined);
      return { data };
    } catch (err) {
      return { error: `获取页面数据失败: ${err.message}` };
    }
  }

  async setPageData(data) {
    this.ensureConnected();
    try {
      const page = await this.miniProgram.currentPage();
      if (!page) throw new Error('无法获取当前页面');
      await page.setData(data);
      return { ok: true };
    } catch (err) {
      return { error: `设置页面数据失败: ${err.message}` };
    }
  }

  async evaluate(code) {
    this.ensureConnected();
    try {
      const result = await this.miniProgram.evaluate(code);
      return { result };
    } catch (err) {
      return { error: `执行脚本失败: ${err.message}` };
    }
  }

  async screenshot(filePath) {
    this.ensureConnected();
    try {
      const finalPath = filePath || path.join(config.screenshotDir, `screenshot_${Date.now()}.png`);
      const dir = path.dirname(finalPath);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      await this.miniProgram.screenshot({ path: finalPath });
      return { path: finalPath };
    } catch (err) {
      return { error: `截图失败: ${err.message}` };
    }
  }

  async waitFor(condition) {
    this.ensureConnected();
    try {
      if (/^\d+$/.test(String(condition))) {
        await this.sleep(parseInt(condition, 10));
        return { waited: parseInt(condition, 10) };
      }
      const page = await this.miniProgram.currentPage();
      if (page) {
        try { await page.waitFor(condition); return { found: condition }; } catch {}
      }
      await this.sleep(1000);
      return { waited: 1000 };
    } catch (err) {
      return { error: `等待失败: ${err.message}` };
    }
  }

  async scrollTo(scrollTop) {
    this.ensureConnected();
    try {
      await this.miniProgram.pageScrollTo(parseInt(scrollTop, 10));
      return { scrolled: scrollTop };
    } catch (err) {
      return { error: `滚动失败: ${err.message}` };
    }
  }

  async systemInfo() {
    this.ensureConnected();
    try { return await this.miniProgram.systemInfo(); }
    catch (err) { return { error: `获取系统信息失败: ${err.message}` }; }
  }

  async callWxMethod(method, ...args) {
    this.ensureConnected();
    try { const result = await this.miniProgram.callWxMethod(method, ...args); return { result }; }
    catch (err) { return { error: `调用 wx.${method} 失败: ${err.message}` }; }
  }

  async mockWxMethod(method, result) {
    this.ensureConnected();
    try { await this.miniProgram.mockWxMethod(method, result); return { mocked: method }; }
    catch (err) { return { error: `Mock wx.${method} 失败: ${err.message}` }; }
  }

  // ==================== 私有 ====================

  sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

  async safeGetData(page, dataPath) {
    try { return await page.data(dataPath || undefined); } catch { return null; }
  }
}

let clientInstance = null;

export function getClient() {
  if (!clientInstance) clientInstance = new AutomatorClient();
  return clientInstance;
}

export { AutomatorClient };
