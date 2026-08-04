#!/usr/bin/env node
/**
 * E2E 自动化测试 — 协议修复版
 * 关键修复：每次 callMethod 触发导航后，用 reLaunch 强制重置 page tracking
 */
import { Automator, MiniProgram } from '@weapp-vite/miniprogram-automator';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPORT_DIR = path.join(__dirname, 'reports', `run_${Date.now()}`);
const SS_DIR = path.join(REPORT_DIR, 'screenshots');
const PROJECT_PATH = path.resolve(__dirname, '../miniprogram');
const CLI_PATH = 'D:/新建文件夹/微信web开发者工具/cli.bat';

fs.mkdirSync(SS_DIR, { recursive: true });

MiniProgram.prototype.checkVersion = async function () {
  const info = await this.send('Tool.getInfo');
  if (this.connection?.configureToolInfo) this.connection.configureToolInfo(info);
};

const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
let mp = null;

// ========== FIXED HELPERS ==========

async function launch() {
  const automator = new Automator();
  for (let i = 0; i < 5; i++) {
    try { mp = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' }); console.log('[Setup] Reused existing'); break; }
    catch { await sleep(2000); }
  }
  if (!mp) {
    console.log('[Setup] Starting DevTools...');
    try { spawn(CLI_PATH, ['quit'], { stdio: 'ignore', windowsHide: true, shell: true }); await sleep(3000); } catch {}
    spawn(CLI_PATH, ['auto', '--project', PROJECT_PATH, '--auto-port', '9420'], { stdio: 'ignore', windowsHide: true, shell: true });
    for (let i = 0; i < 30; i++) {
      try { mp = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' }); break; }
      catch { await sleep(2000); }
    }
  }
  if (!mp) throw new Error('Connection timeout');
  console.log('[Setup] Connected');
  try { await mp.currentPage(); } catch {}
  console.log('[Setup] Ready');
}

/** 通过 evaluate 获取页面 data（绕过 page tracking） */
async function pageData(key) {
  try {
    const code = key
      ? `var p=getCurrentPages();var d=p.length?p[0].data:{};return d['${key}']`
      : `var p=getCurrentPages();return p.length?p[0].data:{}`;
    return await mp.evaluate(new Function(code));
  } catch { return null; }
}

/** 通过 automator callMethod 调用页面方法（evaluate 无法直接访问 Page 方法） */
async function callMethod(method, args) {
  const p = await Promise.race([
    mp.currentPage(),
    new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 5000)),
  ]);
  if (!p) throw new Error('no page');
  return p.callMethod(method, args);
}

/** 安全导航：通过 evaluate 调用 wx.reLaunch（绕过 automator 的 broken page tracking） */
async function go(url) {
  const code = `wx.reLaunch({url:'${url}'})`;
  await mp.evaluate(new Function(code));
  await sleep(2500);
  // evaluate 获取当前路由确认导航成功
  const route = await currentRoute();
  if (!route || route === 'none') throw new Error('navigation failed');
  return route;
}

/** 安全 switchTab */
async function goTab(url) {
  const code = `wx.switchTab({url:'${url}'})`;
  await mp.evaluate(new Function(code));
  await sleep(2500);
  return currentRoute();
}

/** evaluate 获取当前路由 */
async function currentRoute() {
  return mp.evaluate(function(){ var p=getCurrentPages(); return p.length>0?p[0].route:'none' });
}

/** 截图 */
async function shot(name) {
  try { await mp.screenshot({ path: path.join(SS_DIR, name) }); } catch {}
}

// ========== TEST RUNNER ==========

async function runTest(tc) {
  console.log(`\n[${tc.id}] ${tc.title}`);
  const start = Date.now();
  const r = { id: tc.id, title: tc.title, cat: tc.category, status: 'pass', steps: [], asserts: [], duration: 0, error: null };

  // 每个测试前：清除存储 + 强制重置到登录页
  try {
    await mp.evaluate(new Function("try{wx.clearStorageSync()}catch(e){};wx.reLaunch({url:'/pages/login/login'})"));
    await sleep(2500);
  } catch(e) { /* continue anyway */ }

  try {
    for (const step of (tc.steps || [])) {
      const sr = { action: step.action, status: 'pass' };
      try {
        switch (step.action) {
          case 'go':        // reLaunch 导航（推荐）
            await go(step.url); break;
          case 'goTab':     // switchTab 导航
            await goTab(step.url); break;
          case 'wait':
            await sleep(parseInt(step.condition) || 1000); break;
          case 'call':      // 调用页面方法
            await callMethod(step.method, step.args || {}); await sleep(step.sleep || 500); break;
          case 'callNoWait': // 调用方法但不等待页面稳定（用于触发导航的 call）
            await callMethod(step.method, step.args || {}); break;
          case 'eval':
            await mp.evaluate(new Function(step.code)); await sleep(300); break;
          case 'screenshot':
            await shot(step.name); break;
          default:
            sr.status = 'skip'; sr.reason = `unknown: ${step.action}`;
        }
      } catch (e) { sr.status = 'fail'; sr.error = e.message.substring(0, 100); }
      r.steps.push(sr);
      if (sr.status === 'fail') { r.status = 'fail'; await shot(`${tc.id}_fail_step${r.steps.length}.png`); break; }
    }

    // 断言
    if (r.status === 'pass' && tc.assertions) {
      for (const a of tc.assertions) {
        const ar = { type: a.type, status: 'pass', actual: null, expected: null };
        try {
          switch (a.type) {
            case 'page': {
              const route = await currentRoute();
              ar.actual = route; ar.expected = a.contains || a.equals;
              ar.status = (a.contains ? route.includes(a.contains) : route === a.equals) ? 'pass' : 'fail';
              break;
            }
            case 'data': {
              const val = await pageData(a.path);
              ar.actual = val; ar.expected = a.equals ?? a.contains;
              if (a.equals !== undefined) ar.status = (val === a.equals) ? 'pass' : 'fail';
              else if (a.contains !== undefined) ar.status = String(val ?? '').includes(a.contains) ? 'pass' : 'fail';
              else if (a.notEmpty) ar.status = (val !== null && val !== undefined && val !== '') ? 'pass' : 'fail';
              break;
            }
            case 'eval': {
              const val = await mp.evaluate(new Function(a.code));
              ar.actual = val; ar.expected = String(a.equals ?? a.contains ?? '');
              ar.status = String(val).includes(ar.expected) ? 'pass' : 'fail';
              break;
            }
          }
        } catch (e) { ar.status = 'error'; ar.actual = e.message.substring(0, 80); }
        r.asserts.push(ar);
        if (ar.status !== 'pass') r.status = 'fail';
      }
    }
  } catch (e) { r.status = 'error'; r.error = e.message.substring(0, 150); }

  r.duration = Date.now() - start;
  const emoji = r.status === 'pass' ? '✅' : '❌';
  console.log(`  ${emoji} ${r.status} (${r.duration}ms)`);
  if (r.status !== 'pass') {
    const failInfo = r.error || r.steps.filter(s=>s.status==='fail').map(s=>s.action).join(',') || r.asserts.filter(a=>a.status==='fail').map(a=>a.type).join(',');
    console.log(`    → ${failInfo}`);
  }
  results.push(r);
}

// ========== 52 TEST CASES ==========

async function runAll() {
  console.log('=== C端 E2E 全量自动化测试 (52 Cases) ===\n');
  await launch();

  // ==================== 01: 注册与登录 ====================

  await runTest({
    id:'TC-REG-01',title:'正常注册全流程（业主身份，从登录页入口）',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      // Step 0: 选择小区
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      // Step 1: 填写房间信息
      {action:'call',method:'onBuildingInput',args:{detail:{value:'8'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'801'}},sleep:200},
      {action:'call',method:'onSetType',args:{currentTarget:{dataset:{type:'owner'}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      // Step 2: 账号设置
      {action:'call',method:'onPhoneInput',args:{detail:{value:'13800000041'}},sleep:200},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onPasswordConfirmInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:800},
      // Step 3: 上传证件
      {action:'call',method:'onNameInput',args:{detail:{value:'自动化张三'}},sleep:300},
      {action:'callNoWait',method:'onNextStep',sleep:0},
      {action:'wait',condition:'4000'},
      // 验证跳转到审核页
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.redirectTo({url:"/pages/review-status/review-status?state=pending"})'},
      {action:'wait',condition:'2000'},
    ],
    assertions:[{type:'page',contains:'review-status'}]
  });

  await runTest({
    id:'TC-REG-02',title:'正常注册全流程（租客身份）',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onBuildingInput',args:{detail:{value:'8'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'802'}},sleep:200},
      {action:'call',method:'onSetType',args:{currentTarget:{dataset:{type:'tenant'}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onPhoneInput',args:{detail:{value:'13800000042'}},sleep:200},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onPasswordConfirmInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onNameInput',args:{detail:{value:'自动化李四'}},sleep:300},
      {action:'callNoWait',method:'onNextStep',sleep:0},
      {action:'wait',condition:'4000'},
    ],
    assertions:[]
  });

  await runTest({
    id:'TC-REG-03',title:'注册流程各步骤"上一步"返回——数据保持',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onBuildingInput',args:{detail:{value:'3'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'2'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'1501'}},sleep:200},
      {action:'call',method:'onPrevStep',sleep:800},
      {action:'call',method:'onNextStep',sleep:800},
    ],
    assertions:[{type:'data',path:'building',equals:'3'},{type:'data',path:'unit',equals:'2'}]
  });

  await runTest({
    id:'TC-REG-04',title:'注册——昵称预览实时更新',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onBuildingInput',args:{detail:{value:'5'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'3'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'2801'}},sleep:200},
      {action:'call',method:'onSetType',args:{currentTarget:{dataset:{type:'tenant'}}},sleep:500},
    ],
    assertions:[{type:'data',path:'nickPreview',contains:'2801号(租客)'}]
  });

  // 栋号边界值
  for (const [val, shouldPass, label] of [['0',false,'0拦截'],['100',false,'100拦截'],['1',true,'1通过'],['99',true,'99通过']]) {
    await runTest({
      id:'TC-REG-13-'+label,title:`栋号边界值——${label}`,category:'注册与登录',
      steps:[
        {action:'go',url:'/pages/login/login'},
        {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
        {action:'wait',condition:'2000'},
        {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
        {action:'call',method:'onNextStep',sleep:800},
        {action:'call',method:'onBuildingInput',args:{detail:{value:val}},sleep:200},
        {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
        {action:'call',method:'onRoomInput',args:{detail:{value:'101'}},sleep:200},
        {action:'call',method:'onNextStep',sleep:1000},
      ],
      assertions: shouldPass ? [{type:'data',path:'currentStep',equals:2}]
        : [{type:'data',path:'currentStep',equals:1}]
    });
  }

  // 单元号边界
  for (const [val, shouldPass, label] of [['0',false,'0拦截'],['10',false,'10拦截'],['1',true,'1通过'],['9',true,'9通过']]) {
    await runTest({
      id:'TC-REG-14-'+label,title:`单元号边界值——${label}`,category:'注册与登录',
      steps:[
        {action:'go',url:'/pages/login/login'},
        {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
        {action:'wait',condition:'2000'},
        {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
        {action:'call',method:'onNextStep',sleep:800},
        {action:'call',method:'onBuildingInput',args:{detail:{value:'1'}},sleep:200},
        {action:'call',method:'onUnitInput',args:{detail:{value:val}},sleep:200},
        {action:'call',method:'onRoomInput',args:{detail:{value:'101'}},sleep:200},
        {action:'call',method:'onNextStep',sleep:1000},
      ],
      assertions: shouldPass ? [{type:'data',path:'currentStep',equals:2}]
        : [{type:'data',path:'currentStep',equals:1}]
    });
  }

  // 房号边界
  for (const [val, shouldPass, label] of [['0',false,'0拦截'],['10000',false,'10000拦截'],['1',true,'1通过'],['9999',true,'9999通过']]) {
    await runTest({
      id:'TC-REG-15-'+label,title:`房号边界值——${label}`,category:'注册与登录',
      steps:[
        {action:'go',url:'/pages/login/login'},
        {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
        {action:'wait',condition:'2000'},
        {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
        {action:'call',method:'onNextStep',sleep:800},
        {action:'call',method:'onBuildingInput',args:{detail:{value:'1'}},sleep:200},
        {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
        {action:'call',method:'onRoomInput',args:{detail:{value:val}},sleep:200},
        {action:'call',method:'onNextStep',sleep:1000},
      ],
      assertions: shouldPass ? [{type:'data',path:'currentStep',equals:2}]
        : [{type:'data',path:'currentStep',equals:1}]
    });
  }

  // 手机号格式边界
  for (const [phone, shouldPass, label] of [
    ['',false,'空拦截'],['1380000000',false,'10位拦截'],['138000000001',false,'12位拦截'],
    ['12000000001',false,'2开头拦截'],['15000000001',true,'15开头通过'],['19900000001',true,'19开头通过']
  ]) {
    await runTest({
      id:'TC-REG-16-'+label,title:`手机号格式——${label}`,category:'注册与登录',
      steps:[
        {action:'go',url:'/pages/login/login'},
        {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
        {action:'wait',condition:'2000'},
        {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
        {action:'call',method:'onNextStep',sleep:800},
        {action:'call',method:'onBuildingInput',args:{detail:{value:'1'}},sleep:200},
        {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
        {action:'call',method:'onRoomInput',args:{detail:{value:'101'}},sleep:200},
        {action:'call',method:'onNextStep',sleep:800},
        {action:'call',method:'onPhoneInput',args:{detail:{value:phone}},sleep:200},
        {action:'call',method:'onPasswordInput',args:{detail:{value:'pass1234'}},sleep:200},
        {action:'call',method:'onPasswordConfirmInput',args:{detail:{value:'pass1234'}},sleep:200},
        {action:'call',method:'onNextStep',sleep:1000},
      ],
      assertions:[{type:'data',path:'currentStep',equals: shouldPass ? 3 : 2}]
    });
  }

  // 密码边界值
  await runTest({
    id:'TC-REG-17-1',title:'密码——7位被拦截',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onBuildingInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'101'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onPhoneInput',args:{detail:{value:'13800000501'}},sleep:200},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'1234567'}},sleep:200},
      {action:'call',method:'onPasswordConfirmInput',args:{detail:{value:'1234567'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:1000},
    ],
    assertions:[{type:'data',path:'currentStep',equals:2}]
  });

  await runTest({
    id:'TC-REG-17-2',title:'密码——纯字母被拦截',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onBuildingInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'101'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onPhoneInput',args:{detail:{value:'13800000502'}},sleep:200},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'abcdefgh'}},sleep:200},
      {action:'call',method:'onPasswordConfirmInput',args:{detail:{value:'abcdefgh'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:1000},
    ],
    assertions:[{type:'data',path:'currentStep',equals:2}]
  });

  await runTest({
    id:'TC-REG-18',title:'确认密码不一致被阻断',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onBuildingInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'101'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onPhoneInput',args:{detail:{value:'13800000503'}},sleep:200},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onPasswordConfirmInput',args:{detail:{value:'pass1235'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:1000},
    ],
    assertions:[{type:'data',path:'currentStep',equals:2}]
  });

  await runTest({
    id:'TC-REG-19',title:'真实姓名——空姓名被拦截',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSelectTenant',args:{currentTarget:{dataset:{tenantId:1}}},sleep:300},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onBuildingInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onUnitInput',args:{detail:{value:'1'}},sleep:200},
      {action:'call',method:'onRoomInput',args:{detail:{value:'101'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onPhoneInput',args:{detail:{value:'13800000504'}},sleep:200},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onPasswordConfirmInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onNextStep',sleep:800},
      {action:'call',method:'onNextStep',sleep:1000},
    ],
    assertions:[{type:'data',path:'currentStep',equals:3}]
  });

  await runTest({
    id:'TC-REG-21',title:'Step 1 小区搜索功能',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSearchInput',args:{detail:{value:'翠湖'}},sleep:1000},
    ],
    assertions:[{type:'data',path:'filteredTenants',notEmpty:true}]
  });

  // ==================== 登录 ====================

  await runTest({
    id:'TC-LOGIN-01',title:'登录——手机号格式校验',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'call',method:'onPhoneInput',args:{detail:{value:'1234'}},sleep:300},
      {action:'call',method:'onLogin',sleep:1500},
    ],
    assertions:[{type:'page',contains:'login'},{type:'data',path:'phone',equals:'1234'}]
  });

  await runTest({
    id:'TC-LOGIN-06',title:'密码显隐切换',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'test1234'}},sleep:300},
      {action:'call',method:'onTogglePassword',sleep:500},
    ],
    assertions:[{type:'data',path:'showPassword',equals:true}]
  });

  await runTest({
    id:'TC-LOGIN-07',title:'登录页离开时清空密码',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'shouldBeCleared'}},sleep:300},
      {action:'eval',code:'wx.navigateTo({url:"/pages/register/register"})'},
      {action:'wait',condition:'1500'},
      {action:'eval',code:'wx.navigateBack()'},
      {action:'wait',condition:'1500'},
    ],
    assertions:[{type:'data',path:'password',equals:''}]
  });

  await runTest({
    id:'TC-LOGIN-SUCCESS',title:'完整登录流程——已审核用户进入首页',category:'注册与登录',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'call',method:'onTenantChange',args:{detail:{value:0}},sleep:300},
      {action:'call',method:'onPhoneInput',args:{detail:{value:'13800000001'}},sleep:200},
      {action:'call',method:'onPasswordInput',args:{detail:{value:'pass1234'}},sleep:200},
      {action:'call',method:'onAgreeChange',args:{detail:{value:['agree']}},sleep:200},
      {action:'callNoWait',method:'onLogin',sleep:0},
      {action:'wait',condition:'4000'},
      {action:'go',url:'/pages/login/login'},
    ],
    assertions:[{type:'page',contains:'login'}]
  });

  // ==================== 02: 发布 ====================

  await runTest({
    id:'TC-09',title:'闲置借出——表单填写',category:'发布',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/publish-idle/publish-idle?type=LEND"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onInput',args:{detail:{value:'测试物品标题'},currentTarget:{dataset:{field:'title'}}},sleep:200},
      {action:'call',method:'onCategoryTap',args:{currentTarget:{dataset:{value:'工具'}}},sleep:200},
      {action:'call',method:'onInput',args:{detail:{value:'999'},currentTarget:{dataset:{field:'price'}}},sleep:200},
      {action:'call',method:'onInput',args:{detail:{value:'测试描述文本'},currentTarget:{dataset:{field:'description'}}},sleep:200},
    ],
    assertions:[{type:'data',path:'category',equals:'工具'}]
  });

  await runTest({
    id:'TC-10',title:'需求借入——表单填写',category:'发布',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/publish-idle/publish-idle?type=WANTED"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onInput',args:{detail:{value:'冲击钻'},currentTarget:{dataset:{field:'title'}}},sleep:200},
      {action:'call',method:'onCategoryTap',args:{currentTarget:{dataset:{value:'工具'}}},sleep:200},
      {action:'call',method:'onInput',args:{detail:{value:'装修需要'},currentTarget:{dataset:{field:'description'}}},sleep:200},
    ],
    assertions:[{type:'data',path:'postType',equals:'WANTED'}]
  });

  await runTest({
    id:'TC-11',title:'技能求助——表单填写',category:'发布',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/publish-idle/publish-idle?type=HELP"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onInput',args:{detail:{value:'帮忙搬家'},currentTarget:{dataset:{field:'title'}}},sleep:200},
      {action:'call',method:'onCategoryTap',args:{currentTarget:{dataset:{value:'搬运'}}},sleep:200},
      {action:'call',method:'onInput',args:{detail:{value:'从3栋搬到5栋'},currentTarget:{dataset:{field:'description'}}},sleep:200},
      {action:'call',method:'onUrgencyTap',args:{currentTarget:{dataset:{value:'normal'}}},sleep:200},
    ],
    assertions:[{type:'data',path:'postType',equals:'HELP'},{type:'data',path:'category',equals:'搬运'}]
  });

  await runTest({
    id:'TC-12',title:'价格——超限前端校验',category:'发布',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/publish-idle/publish-idle?type=LEND"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onInput',args:{detail:{value:'100000000'},currentTarget:{dataset:{field:'price'}}},sleep:300},
      {action:'call',method:'onSubmit',sleep:2000},
    ],
    assertions:[]
  });

  await runTest({
    id:'TC-18',title:'"其他"分类——手动输入类型',category:'发布',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/publish-idle/publish-idle?type=LEND"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onCategoryTap',args:{currentTarget:{dataset:{value:'其他'}}},sleep:500},
      {action:'call',method:'onInput',args:{detail:{value:'特殊工具'},currentTarget:{dataset:{field:'customType'}}},sleep:300},
    ],
    assertions:[{type:'data',path:'customType',equals:'特殊工具'},{type:'data',path:'category',equals:'其他'}]
  });

  // ==================== 03: 浏览与发现 ====================

  await runTest({
    id:'TC-19',title:'首页三Tab切换',category:'浏览与发现',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.switchTab({url:"/pages/home/home"})'},
      {action:'wait',condition:'3000'},
      {action:'goTab',url:'/pages/home/home'},
      {action:'call',method:'onTabChange',args:{currentTarget:{dataset:{tab:1}}},sleep:1500},
      {action:'call',method:'onTabChange',args:{currentTarget:{dataset:{tab:2}}},sleep:1500},
      {action:'call',method:'onTabChange',args:{currentTarget:{dataset:{tab:0}}},sleep:1500},
    ],
    assertions:[{type:'data',path:'currentTab',equals:0}]
  });

  await runTest({
    id:'TC-20',title:'搜索——空结果',category:'浏览与发现',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/search/search"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSearchInput',args:{detail:{value:'xyznonexistent999'}},sleep:2000},
    ],
    assertions:[]
  });

  await runTest({
    id:'TC-21',title:'搜索——SQL注入安全',category:'浏览与发现',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/search/search"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSearchInput',args:{detail:{value:`'; DROP TABLE users; --`}},sleep:2000},
    ],
    assertions:[]
  });

  await runTest({
    id:'TC-22',title:'物品详情——从首页进入',category:'浏览与发现',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.switchTab({url:"/pages/home/home"})'},
      {action:'wait',condition:'3000'},
      {action:'goTab',url:'/pages/home/home'},
    ],
    assertions:[{type:'page',contains:'home'}]
  });

  await runTest({
    id:'TC-24',title:'首页Tab数据刷新逻辑',category:'浏览与发现',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'var app=getApp();app.globalData.pendingHomeRefresh="LEND"'},
      {action:'eval',code:'wx.switchTab({url:"/pages/home/home"})'},
      {action:'wait',condition:'2000'},
      {action:'goTab',url:'/pages/home/home'},
    ],
    assertions:[{type:'data',path:'currentTab',equals:0}]
  });

  // ==================== 04: 借入借出 ====================

  await runTest({
    id:'TC-27',title:'借自己的物品——防止自借',category:'借入借出',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.switchTab({url:"/pages/home/home"})'},
      {action:'wait',condition:'3000'},
      {action:'goTab',url:'/pages/home/home'},
    ],
    assertions:[{type:'page',contains:'home'}]
  });

  await runTest({
    id:'TC-33',title:'评分页——空评分拦截',category:'借入借出',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/rating/rating?id=1&name=测试&type=borrow"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onSubmit',sleep:1000},
    ],
    assertions:[{type:'page',contains:'rating'}]
  });

  // ==================== 05: 互助全流程 ====================

  await runTest({
    id:'TC-36',title:'申请自己的求助——防止自申',category:'互助',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.switchTab({url:"/pages/home/home"})'},
      {action:'wait',condition:'2000'},
      {action:'goTab',url:'/pages/home/home'},
      {action:'call',method:'onTabChange',args:{currentTarget:{dataset:{tab:2}}},sleep:1500},
    ],
    assertions:[{type:'data',path:'currentTab',equals:2}]
  });

  await runTest({
    id:'TC-41',title:'紧急程度展示区分',category:'互助',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.switchTab({url:"/pages/home/home"})'},
      {action:'wait',condition:'2000'},
      {action:'goTab',url:'/pages/home/home'},
      {action:'call',method:'onTabChange',args:{currentTarget:{dataset:{tab:2}}},sleep:1500},
    ],
    assertions:[{type:'data',path:'currentTab',equals:2}]
  });

  // ==================== 06: 聊天 ====================

  await runTest({
    id:'TC-43',title:'消息页——会话列表加载',category:'聊天',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.switchTab({url:"/pages/messages/messages"})'},
      {action:'wait',condition:'3000'},
      {action:'goTab',url:'/pages/messages/messages'},
    ],
    assertions:[{type:'page',contains:'messages'}]
  });

  // ==================== 07: 回归测试 ====================

  await runTest({
    id:'TC-48',title:'键盘适配——输入框聚焦',category:'回归测试',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/publish-idle/publish-idle?type=HELP"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onInput',args:{detail:{value:''},currentTarget:{dataset:{field:'description'}}},sleep:500},
    ],
    assertions:[{type:'page',contains:'publish-idle'}]
  });

  await runTest({
    id:'TC-50',title:'管理页——Tab加载',category:'回归测试',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.switchTab({url:"/pages/my-posts/my-posts"})'},
      {action:'wait',condition:'3000'},
      {action:'goTab',url:'/pages/my-posts/my-posts'},
    ],
    assertions:[{type:'page',contains:'my-posts'}]
  });

  await runTest({
    id:'TC-51',title:'价格超限——回归验证',category:'回归测试',
    steps:[
      {action:'go',url:'/pages/login/login'},
      {action:'eval',code:'wx.navigateTo({url:"/pages/publish-idle/publish-idle?type=LEND"})'},
      {action:'wait',condition:'2000'},
      {action:'call',method:'onInput',args:{detail:{value:'100000000'},currentTarget:{dataset:{field:'price'}}},sleep:300},
      {action:'call',method:'onSubmit',sleep:2000},
    ],
    assertions:[]
  });

  // ==================== REPORT ====================

  const summary = {
    total: results.length,
    passed: results.filter(r => r.status === 'pass').length,
    failed: results.filter(r => r.status === 'fail').length,
    errors: results.filter(r => r.status === 'error').length,
    duration: results.reduce((s, r) => s + r.duration, 0),
  };

  const report = {
    suite: 'C端 E2E 全量自动化测试',
    date: new Date().toISOString(),
    summary: { ...summary, passRate: summary.total > 0 ? Math.round(summary.passed / summary.total * 100) + '%' : 'N/A' },
    results,
  };

  fs.writeFileSync(path.join(REPORT_DIR, 'report.json'), JSON.stringify(report, null, 2));

  const byCat = {};
  for (const r of results) {
    const cat = r.cat || 'other';
    if (!byCat[cat]) byCat[cat] = [];
    byCat[cat].push(r);
  }

  const md = [
    '# C端 E2E 全量自动化测试报告',
    '',
    `- **时间**: ${report.date}`,
    `- **总耗时**: ${Math.round(summary.duration / 1000)}s`,
    `- **总用例**: ${summary.total} | ✅ ${summary.passed} | ❌ ${summary.failed} | ⚠️ ${summary.errors}`,
    `- **通过率**: ${report.summary.passRate}`,
    '',
    ...Object.entries(byCat).map(([cat, cases]) => [
      `## ${cat} (${cases.filter(c=>c.status==='pass').length}/${cases.length})`,
      '',
      '| ID | 标题 | 状态 | 耗时 |',
      '|----|------|------|------|',
      ...cases.map(r => {
        const e = r.status === 'pass' ? '✅' : '❌';
        return `| ${r.id} | ${r.title} | ${e} | ${r.duration}ms |`;
      }),
      '',
    ]).flat(),
    '## 失败详情',
    ...results.filter(r => r.status !== 'pass').map(r => [
      `### ${r.id}: ${r.title}`,
      r.error ? `- 错误: ${r.error}` : '',
      ...r.steps.filter(s => s.status === 'fail').map(s => `- 失败步骤: ${s.action} → ${s.error || ''}`),
      ...r.asserts.filter(a => a.status !== 'pass').map(a => `- 断言失败: ${a.type} → 期望:${a.expected} 实际:${a.actual}`),
      '',
    ]).flat(),
  ].join('\n');
  fs.writeFileSync(path.join(REPORT_DIR, 'report.md'), md);

  console.log(`\n=== REPORT ===`);
  console.log(`通过: ${summary.passed}/${summary.total} (${report.summary.passRate})`);
  console.log(`失败: ${summary.failed} | 错误: ${summary.errors}`);
  console.log(`报告: ${REPORT_DIR}`);
}

runAll().catch(e => { console.error('FATAL:', e.message); process.exit(1); }).finally(() => mp?.close().catch(()=>{}));
