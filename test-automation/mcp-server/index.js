#!/usr/bin/env node
/**
 * 微信小程序自动化测试 MCP Server
 */
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { getClient } from './automator-client.js';
import * as lowLevel from './tools/low-level.js';
import * as composite from './tools/composite.js';

const TOOLS = [
  { name: 'mini_launch', description: '启动微信开发者工具 auto 模式并连接模拟器', inputSchema: { type: 'object', properties: {}, required: [] } },
  { name: 'mini_close', description: '关闭与模拟器的连接', inputSchema: { type: 'object', properties: {}, required: [] } },
  { name: 'mini_current_page', description: '获取当前页面路径和数据', inputSchema: { type: 'object', properties: {}, required: [] } },
  { name: 'mini_page_stack', description: '获取小程序页面栈', inputSchema: { type: 'object', properties: {}, required: [] } },
  {
    name: 'mini_navigate', description: '导航到指定页面',
    inputSchema: {
      type: 'object',
      properties: {
        url: { type: 'string', description: '目标页面路径' },
        method: { type: 'string', enum: ['navigateTo', 'redirectTo', 'switchTab', 'reLaunch', 'navigateBack'], default: 'navigateTo' },
      },
      required: ['url'],
    },
  },
  {
    name: 'mini_tap', description: '点击 WXML 元素',
    inputSchema: {
      type: 'object',
      properties: {
        selector: { type: 'string', description: 'CSS 选择器' },
        pagePath: { type: 'string', description: '可选，当前页面路径校验' },
      },
      required: ['selector'],
    },
  },
  {
    name: 'mini_input', description: '向输入框输入文本',
    inputSchema: {
      type: 'object',
      properties: {
        selector: { type: 'string' },
        value: { type: 'string' },
        pagePath: { type: 'string' },
      },
      required: ['selector', 'value'],
    },
  },
  { name: 'mini_text', description: '获取元素文本内容', inputSchema: { type: 'object', properties: { selector: { type: 'string' } }, required: ['selector'] } },
  { name: 'mini_data', description: '获取页面 data', inputSchema: { type: 'object', properties: { path: { type: 'string' } }, required: [] } },
  { name: 'mini_set_data', description: '设置页面 data', inputSchema: { type: 'object', properties: { data: { type: 'string' } }, required: ['data'] } },
  { name: 'mini_screenshot', description: '截取模拟器画面', inputSchema: { type: 'object', properties: { filePath: { type: 'string' } }, required: [] } },
  { name: 'mini_evaluate', description: '在小程序上下文中执行 JS', inputSchema: { type: 'object', properties: { code: { type: 'string' } }, required: ['code'] } },
  { name: 'mini_wait', description: '等待条件（毫秒数或选择器）', inputSchema: { type: 'object', properties: { condition: { type: 'string' } }, required: ['condition'] } },
  { name: 'mini_scroll', description: '滚动页面', inputSchema: { type: 'object', properties: { scrollTop: { type: 'string' } }, required: ['scrollTop'] } },
  { name: 'mini_system_info', description: '获取设备系统信息', inputSchema: { type: 'object', properties: {}, required: [] } },
  { name: 'mini_call_wx', description: '调用微信 API', inputSchema: { type: 'object', properties: { method: { type: 'string' }, args: { type: 'string' } }, required: ['method'] } },
  { name: 'mini_mock_wx', description: 'Mock 微信 API', inputSchema: { type: 'object', properties: { method: { type: 'string' }, result: { type: 'string' } }, required: ['method', 'result'] } },
  {
    name: 'mp_switch_to', description: '切换登录账号（完整流程：清存储→重启→登录→验证）',
    inputSchema: { type: 'object', properties: { accountId: { type: 'string' } }, required: ['accountId'] },
  },
  { name: 'mp_login', description: '从登录页完成登录', inputSchema: { type: 'object', properties: { phone: { type: 'string' }, password: { type: 'string' }, tenantIndex: { type: 'number' } }, required: [] } },
  {
    name: 'mp_register_step', description: '执行注册流程的一步',
    inputSchema: { type: 'object', properties: { action: { type: 'string', enum: ['select_community', 'fill_room', 'fill_account', 'upload_docs'] }, fields: { type: 'object' } }, required: ['action'] },
  },
  {
    name: 'mp_publish', description: '完成发布流程',
    inputSchema: { type: 'object', properties: { postType: { type: 'string', enum: ['LEND', 'WANTED', 'HELP'] }, fields: { type: 'object' } }, required: ['postType'] },
  },
  {
    name: 'mp_account_b_action', description: '虚拟账号 B 操作',
    inputSchema: { type: 'object', properties: { actionType: { type: 'string', enum: ['api_call', 'login_and_get_token', 'apply_borrow'] }, fields: { type: 'object' } }, required: ['actionType'] },
  },
  {
    name: 'mp_assert_batch', description: '批量断言验证',
    inputSchema: {
      type: 'object',
      properties: {
        assertions: {
          type: 'array',
          items: { type: 'object', properties: { type: { type: 'string', enum: ['page', 'data', 'text', 'visible', 'toast'] }, selector: { type: 'string' }, path: { type: 'string' }, expected: { type: 'string' }, equals: {}, contains: { type: 'string' } }, required: ['type'] },
        },
      },
      required: ['assertions'],
    },
  },
];

const server = new Server({ name: 'miniprogram-e2e-mcp', version: '1.0.0' }, { capabilities: { tools: {} } });

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: TOOLS.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })),
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;
  try {
    let result;
    switch (name) {
      case 'mini_launch': result = await lowLevel.miniLaunch(); break;
      case 'mini_close': result = await lowLevel.miniClose(); break;
      case 'mini_current_page': result = await lowLevel.miniCurrentPage(); break;
      case 'mini_page_stack': result = await lowLevel.miniPageStack(); break;
      case 'mini_navigate': result = await lowLevel.miniNavigate(args.url, args.method); break;
      case 'mini_tap': result = await lowLevel.miniTap(args.selector, args.pagePath); break;
      case 'mini_input': result = await lowLevel.miniInput(args.selector, args.value, args.pagePath); break;
      case 'mini_text': result = await lowLevel.miniText(args.selector); break;
      case 'mini_data': result = await lowLevel.miniData(args.path); break;
      case 'mini_set_data': result = await lowLevel.miniSetData(args.data); break;
      case 'mini_screenshot': result = await lowLevel.miniScreenshot(args.filePath); break;
      case 'mini_evaluate': result = await lowLevel.miniEvaluate(args.code); break;
      case 'mini_wait': result = await lowLevel.miniWait(args.condition); break;
      case 'mini_scroll': result = await lowLevel.miniScroll(args.scrollTop); break;
      case 'mini_system_info': result = await lowLevel.miniSystemInfo(); break;
      case 'mini_call_wx': result = await lowLevel.miniCallWx(args.method, args.args); break;
      case 'mini_mock_wx': result = await lowLevel.miniMockWx(args.method, args.result); break;
      case 'mp_switch_to': result = await composite.mpSwitchTo(args.accountId); break;
      case 'mp_login': result = await composite.mpLogin(args); break;
      case 'mp_register_step': result = await composite.mpRegisterStep(args.action, args.fields || {}); break;
      case 'mp_publish': result = await composite.mpPublish(args.postType, args.fields || {}); break;
      case 'mp_account_b_action': result = await composite.mpAccountBAction(args.actionType, args.fields || {}); break;
      case 'mp_assert_batch': result = await composite.mpAssertBatch(args.assertions); break;
      default: result = { content: [{ type: 'text', text: JSON.stringify({ error: `未知工具: ${name}` }) }] };
    }
    return result;
  } catch (err) {
    console.error(`[mcp] 工具 ${name} 异常:`, err);
    return { content: [{ type: 'text', text: JSON.stringify({ error: `服务器内部错误: ${err.message}` }) }], isError: true };
  }
});

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('[mcp] 微信小程序自动化测试 MCP Server 已启动');
}

main().catch(err => { console.error('[mcp] 启动失败:', err); process.exit(1); });

process.on('SIGINT', async () => { await getClient().close(); process.exit(0); });
process.on('SIGTERM', async () => { await getClient().close(); process.exit(0); });
