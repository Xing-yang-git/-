/**
 * 复合 MCP 工具 — 封装多步骤操作为单次调用
 */
import { getClient } from '../automator-client.js';
import selectors from '../selectors.js';
import config from '../config.js';

function formatSuccess(data) {
  return { content: [{ type: 'text', text: JSON.stringify({ success: true, ...data }, null, 2) }] };
}

function formatError(message) {
  return { content: [{ type: 'text', text: JSON.stringify({ success: false, error: message }, null, 2) }] };
}

function formatStepError(steps, message) {
  return { content: [{ type: 'text', text: JSON.stringify({ success: false, error: message, stepsCompleted: steps }, null, 2) }] };
}

/**
 * mp_switch_to — 切换登录账号
 */
export async function mpSwitchTo(accountId) {
  const client = getClient();
  const account = config.accounts[accountId];
  if (!account) return formatError(`未知账号: ${accountId}`);

  const steps = [];
  try {
    steps.push('清除存储');
    await client.evaluate("try { wx.clearStorageSync(); } catch(e) {} return 'ok'");
    await client.sleep(500);

    steps.push('跳转登录页');
    await client.navigateTo('/pages/login/login', 'reLaunch');
    await client.sleep(1500);

    const current = await client.currentPage();
    if (!current.path || !current.path.includes('login')) {
      return formatError(`未在登录页，当前: ${current.path}`);
    }

    steps.push('选择小区');
    const pickerEl = await client.findElement(selectors.login.communityPicker);
    if (pickerEl) {
      await pickerEl.tap();
      await client.sleep(500);
      // 通过 evaluate 调用 onTenantChange
      await client.evaluate(`
        var pages = getCurrentPages();
        var page = pages[pages.length - 1];
        if (page && page.onTenantChange) {
          page.onTenantChange({ detail: { value: 0 } });
        }
        return 'picker_handled';
      `);
    }
    await client.sleep(300);

    steps.push('输入手机号');
    const phoneInput = await client.findElement(selectors.login.phoneInput);
    if (phoneInput) await phoneInput.input(account.phone);
    await client.sleep(200);

    steps.push('输入密码');
    const pwdInput = await client.findElement(selectors.login.passwordInput);
    if (pwdInput) await pwdInput.input(account.password);
    await client.sleep(200);

    steps.push('勾选协议');
    try {
      const checkbox = await client.findElement('checkbox');
      if (checkbox) await checkbox.tap();
    } catch {}
    await client.sleep(200);

    steps.push('点击登录');
    const loginBtn = await client.findElement(selectors.login.loginButton);
    if (loginBtn) await loginBtn.tap();
    await client.sleep(2500);

    const afterLogin = await client.currentPage();
    return formatSuccess({ accountId, phone: account.phone, currentPage: afterLogin.path, steps });
  } catch (err) {
    return formatStepError(steps, err.message);
  }
}

/**
 * mp_login — 从登录页完成登录
 */
export async function mpLogin(fields = {}) {
  const client = getClient();
  const phone = fields.phone || config.accounts.test_user_a.phone;
  const password = fields.password || config.accounts.test_user_a.password;
  const steps = [];

  try {
    steps.push('选择小区');
    await client.evaluate(`
      var pages = getCurrentPages();
      var page = pages[pages.length - 1];
      if (page && page.onTenantChange) {
        page.onTenantChange({ detail: { value: ${fields.tenantIndex || 0} } });
      }
      return 'picker_handled';
    `);
    await client.sleep(300);

    steps.push('输入手机号');
    const phoneInput = await client.findElement(selectors.login.phoneInput);
    if (phoneInput) await phoneInput.input(phone);
    await client.sleep(200);

    steps.push('输入密码');
    const pwdInput = await client.findElement(selectors.login.passwordInput);
    if (pwdInput) await pwdInput.input(password);
    await client.sleep(200);

    steps.push('勾选协议+登录');
    try { const cb = await client.findElement('checkbox'); if (cb) await cb.tap(); } catch {}
    await client.sleep(200);
    const loginBtn = await client.findElement(selectors.login.loginButton);
    if (loginBtn) await loginBtn.tap();
    await client.sleep(2500);

    const result = await client.currentPage();
    return formatSuccess({ loggedIn: true, phone, currentPage: result.path, steps });
  } catch (err) {
    return formatStepError(steps, err.message);
  }
}

/**
 * mp_register_step — 执行注册流程的一步
 */
export async function mpRegisterStep(action, fields = {}) {
  const client = getClient();
  const steps = [];

  try {
    switch (action) {
      case 'select_community': {
        steps.push('搜索小区');
        if (fields.keyword) {
          const searchInput = await client.findElement(selectors.register.searchInput);
          if (searchInput) await searchInput.input(fields.keyword);
          await client.sleep(500);
        }
        const rows = await client.findElements(selectors.register.tenantListRows);
        if (rows && rows.length > 0) {
          await rows[0].tap();
          steps.push('已选择第一个小区');
        }
        break;
      }
      case 'fill_room': {
        steps.push('填写房间信息');
        if (fields.building) {
          const el = await client.findElement(selectors.register.buildingInput);
          if (el) await el.input(String(fields.building));
          await client.sleep(200);
        }
        if (fields.unit) {
          const el = await client.findElement(selectors.register.unitInput);
          if (el) await el.input(String(fields.unit));
          await client.sleep(200);
        }
        if (fields.room) {
          const el = await client.findElement(selectors.register.roomInput);
          if (el) await el.input(String(fields.room));
          await client.sleep(200);
        }
        if (fields.residentType) {
          const items = await client.findElements(selectors.register.residentTypeSegment);
          if (items) {
            for (const item of items) {
              const text = await item.text();
              if ((fields.residentType === 'owner' && text.includes('业主')) ||
                  (fields.residentType === 'tenant' && text.includes('租客'))) {
                await item.tap();
                break;
              }
            }
          }
        }
        break;
      }
      case 'fill_account': {
        steps.push('填写账号信息');
        if (fields.phone) {
          const el = await client.findElement(selectors.register.stepPhoneInput);
          if (el) await el.input(String(fields.phone));
          await client.sleep(200);
        }
        if (fields.password) {
          const el = await client.findElement(selectors.register.stepPasswordInput);
          if (el) await el.input(String(fields.password));
          await client.sleep(200);
        }
        if (fields.passwordConfirm) {
          const el = await client.findElement(selectors.register.stepPasswordConfirmInput);
          if (el) await el.input(String(fields.passwordConfirm));
          await client.sleep(200);
        }
        break;
      }
      case 'upload_docs': {
        steps.push('填写姓名和证件');
        if (fields.realName) {
          const el = await client.findElement(selectors.register.realNameInput);
          if (el) await el.input(String(fields.realName));
          await client.sleep(200);
        }
        if (fields.uploadImages !== false) {
          await client.mockWxMethod('chooseMedia', { tempFiles: [{ tempFilePath: '/mock/test-image.png' }] });
          await client.sleep(200);
          const addBtn = await client.findElement(selectors.register.imageUploadAdd);
          if (addBtn) { await addBtn.tap(); steps.push('已触发上传'); }
        }
        break;
      }
    }

    await client.sleep(300);
    const current = await client.currentPage();
    return formatSuccess({ action, steps, currentPage: current.path });
  } catch (err) {
    return formatStepError(steps, err.message);
  }
}

/**
 * mp_publish — 完整发布流程
 */
export async function mpPublish(postType, fields = {}) {
  const client = getClient();
  const steps = [];

  try {
    steps.push('导航到发布页');
    await client.navigateTo(`/pages/publish-idle/publish-idle?type=${postType}`, 'navigateTo');
    await client.sleep(1500);

    if (fields.title) {
      steps.push('填写标题');
      const input = await client.findElement("input[placeholder*='标题'], input[placeholder*='名称']");
      if (input) await input.input(fields.title);
      await client.sleep(200);
    }

    if (fields.category) {
      steps.push(`选择分类: ${fields.category}`);
      const pills = await client.findElements(selectors.publish.category.pills);
      if (pills) {
        for (const pill of pills) {
          const text = await pill.text();
          if (text === fields.category) { await pill.tap(); break; }
        }
      }
      await client.sleep(200);
    }

    if (fields.price && postType === 'LEND') {
      steps.push(`填写价格: ${fields.price}`);
      const input = await client.findElement('input[type=digit]');
      if (input) await input.input(String(fields.price));
      await client.sleep(200);
    }

    if (fields.description) {
      steps.push('填写描述');
      const textarea = await client.findElement('.textarea');
      if (textarea) await textarea.input(fields.description);
      await client.sleep(200);
    }

    steps.push('点击提交');
    const submitBtn = await client.findElement('.btn-primary.btn-block');
    if (submitBtn) { await submitBtn.tap(); steps.push('已点击提交'); }
    else return formatStepError(steps, '未找到提交按钮');

    await client.sleep(2500);
    const result = await client.currentPage();
    return formatSuccess({ postType, published: true, currentPage: result.path, steps });
  } catch (err) {
    return formatStepError(steps, err.message);
  }
}

/**
 * mp_account_b_action — 虚拟账号 B 操作
 */
export async function mpAccountBAction(actionType, fields = {}) {
  const client = getClient();
  const accountB = config.accounts.test_user_b;

  try {
    const code = `
      (async function() {
        var baseUrl = '${config.backendBaseUrl}';
        ${actionType === 'login_and_get_token' ? `
          var res = await new Promise(function(resolve, reject) {
            wx.request({
              url: baseUrl + '/api/auth/phone-login',
              method: 'POST',
              data: { phone: '${accountB.phone}', password: '${accountB.password}' },
              success: resolve, fail: reject,
            });
          });
          var data = res.data.data || res.data;
          return { token: data.token, userId: data.userId };
        ` : actionType === 'apply_borrow' ? `
          var loginRes = await new Promise(function(resolve, reject) {
            wx.request({
              url: baseUrl + '/api/auth/phone-login',
              method: 'POST',
              data: { phone: '${accountB.phone}', password: '${accountB.password}' },
              success: resolve, fail: reject,
            });
          });
          var token = loginRes.data.data?.token || loginRes.data.token;
          var res = await new Promise(function(resolve, reject) {
            wx.request({
              url: baseUrl + '/api/borrow-requests',
              method: 'POST',
              data: { idleId: ${fields.idleId || 0}, durationType: '${fields.durationType || 'day'}', durationDays: ${fields.durationDays || 3}, note: '${fields.note || '自动化测试申请'}' },
              header: { 'Authorization': 'Bearer ' + token },
              success: resolve, fail: reject,
            });
          });
          return res.data;
        ` : `
          return { error: 'unknown_action' };
        `}
      })()
    `;
    const result = await client.evaluate(code);
    return formatSuccess(result);
  } catch (err) {
    return formatError(`账号 B 操作失败: ${err.message}`);
  }
}

/**
 * mp_assert_batch — 批量断言
 */
export async function mpAssertBatch(assertions) {
  const client = getClient();
  const results = [];
  const list = Array.isArray(assertions) ? assertions : [assertions];

  for (const assertion of list) {
    const result = { assertion, status: 'pending' };
    try {
      switch (assertion.type) {
        case 'page': {
          const current = await client.currentPage();
          result.status = (current.path === assertion.expected || (current.path && current.path.includes(assertion.expected))) ? 'pass' : 'fail';
          result.actual = current.path;
          result.expected = assertion.expected;
          break;
        }
        case 'data': {
          const res = await client.getPageData(assertion.path);
          const actualValue = assertion.path
            ? assertion.path.split('.').reduce((o, k) => (o && o[k] !== undefined ? o[k] : null), res.data)
            : res.data;
          if (assertion.equals !== undefined) {
            result.status = actualValue === assertion.equals ? 'pass' : 'fail';
          } else if (assertion.contains !== undefined) {
            result.status = String(actualValue).includes(assertion.contains) ? 'pass' : 'fail';
          }
          result.actual = actualValue;
          result.expected = assertion.equals || assertion.contains;
          break;
        }
        case 'text': {
          const el = await client.findElement(assertion.selector);
          if (!el) { result.status = 'fail'; result.actual = 'element_not_found'; break; }
          const text = await el.text();
          if (assertion.equals) result.status = text === assertion.equals ? 'pass' : 'fail';
          else if (assertion.contains) result.status = text.includes(assertion.contains) ? 'pass' : 'fail';
          result.actual = text;
          result.expected = assertion.equals || assertion.contains;
          break;
        }
        case 'visible': {
          const el = await client.findElement(assertion.selector);
          result.status = assertion.not ? (el ? 'fail' : 'pass') : (el ? 'pass' : 'fail');
          result.actual = el ? 'visible' : 'not_found';
          break;
        }
        default:
          result.status = 'skipped';
          result.reason = `未知断言类型: ${assertion.type}`;
      }
    } catch (err) {
      result.status = 'error';
      result.error = err.message;
    }
    results.push(result);
  }

  const summary = {
    total: results.length,
    passed: results.filter(r => r.status === 'pass').length,
    failed: results.filter(r => r.status === 'fail').length,
  };
  return formatSuccess({ summary, assertions: results });
}
