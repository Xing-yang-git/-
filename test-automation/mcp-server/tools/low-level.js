/**
 * 底层 MCP 工具 — 包装 automator-client 为可调用的工具函数
 */
import { getClient } from '../automator-client.js';
import config from '../config.js';
import path from 'node:path';

function formatResult(result) {
  return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] };
}

export async function miniCurrentPage() {
  const client = getClient();
  const result = await client.currentPage();
  return formatResult(result);
}

export async function miniPageStack() {
  const client = getClient();
  const result = await client.pageStack();
  return formatResult(result);
}

export async function miniLaunch() {
  const client = getClient();
  const result = await client.launch();
  return formatResult(result);
}

export async function miniClose() {
  const client = getClient();
  await client.close();
  return formatResult({ closed: true });
}

export async function miniNavigate(url, method = 'navigateTo') {
  const client = getClient();
  const result = await client.navigateTo(url, method);
  return formatResult(result);
}

export async function miniTap(selector, pagePath) {
  const client = getClient();
  if (pagePath) {
    const current = await client.currentPage();
    if (current.path !== pagePath) {
      return formatResult({ error: 'wrong_page', expected: pagePath, actual: current.path });
    }
  }
  const result = await client.tap(selector);
  await client.sleep(300);
  return formatResult(result);
}

export async function miniInput(selector, value, pagePath) {
  const client = getClient();
  if (pagePath) {
    const current = await client.currentPage();
    if (current.path !== pagePath) {
      return formatResult({ error: 'wrong_page', expected: pagePath, actual: current.path });
    }
  }
  const result = await client.input(selector, String(value));
  return formatResult(result);
}

export async function miniText(selector) {
  const result = await getClient().getText(selector);
  return formatResult(result);
}

export async function miniData(dataPath) {
  const result = await getClient().getPageData(dataPath || undefined);
  return formatResult(result);
}

export async function miniSetData(data) {
  let parsedData = data;
  if (typeof data === 'string') {
    try { parsedData = JSON.parse(data); } catch { /* keep as string */ }
  }
  const result = await getClient().setPageData(parsedData);
  return formatResult(result);
}

export async function miniScreenshot(filePath) {
  const result = await getClient().screenshot(
    filePath || path.join(config.screenshotDir, `shot_${Date.now()}.png`)
  );
  return formatResult(result);
}

export async function miniEvaluate(code) {
  const result = await getClient().evaluate(code);
  return formatResult(result);
}

export async function miniWait(condition) {
  const result = await getClient().waitFor(condition);
  return formatResult(result);
}

export async function miniScroll(scrollTop) {
  const result = await getClient().scrollTo(scrollTop);
  return formatResult(result);
}

export async function miniSystemInfo() {
  const result = await getClient().systemInfo();
  return formatResult(result);
}

export async function miniCallWx(method, args) {
  let parsedArgs = args;
  if (typeof args === 'string') {
    try { parsedArgs = JSON.parse(args); } catch { parsedArgs = [args]; }
  }
  if (!Array.isArray(parsedArgs)) parsedArgs = [parsedArgs];
  const result = await getClient().callWxMethod(method, ...parsedArgs);
  return formatResult(result);
}

export async function miniMockWx(method, result) {
  let parsedResult = result;
  if (typeof result === 'string') {
    try { parsedResult = JSON.parse(result); } catch {}
  }
  const res = await getClient().mockWxMethod(method, parsedResult);
  return formatResult(res);
}
