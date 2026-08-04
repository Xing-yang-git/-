/**
 * 测试数据种子脚本
 *
 * 通过后端 API 创建测试账号和测试数据，确保幂等性
 *
 * 用法:
 *   node fixtures/seed-data.js              # 创建所有种子数据（跳过已存在的）
 *   node fixtures/seed-data.js --reset       # 强制重建
 *   node fixtures/seed-data.js --verify      # 仅验证数据就绪
 */

const http = require('http');

const BASE = 'http://192.168.31.64:8080';
const ADMIN_AUTH = { username: 'admin', password: 'admin123' };

// ==================== 测试账号定义 ====================

const TEST_ACCOUNTS = [
  {
    id: 'test_user_a', phone: '13800000001', password: 'pass1234', name: '张三',
    building: 1, unit: 1, room: 101, userType: 'owner',
    authStatus: 'approved', // 需要在 B 端审批
  },
  {
    id: 'test_user_b', phone: '13800000002', password: 'pass1234', name: '李四',
    building: 2, unit: 1, room: 202, userType: 'owner',
    authStatus: 'approved',
  },
  {
    id: 'test_user_c', phone: '13800000003', password: 'pass1234', name: '王五',
    building: 3, unit: 1, room: 303, userType: 'tenant',
    authStatus: 'pending', // 审核中
  },
  {
    id: 'test_user_d', phone: '13800000004', password: 'pass1234', name: '赵六',
    building: 4, unit: 1, room: 404, userType: 'owner',
    authStatus: 'rejected', // 需注册后由 B 端拒绝
  },
  {
    id: 'test_user_e', phone: '13800000005', password: 'pass1234', name: '孙七',
    building: 5, unit: 1, room: 505, userType: 'owner',
    authStatus: 'banned', // 需注册后由 B 端封禁
  },
  {
    id: 'test_user_f', phone: '13800000006', password: 'pass1234', name: '周八',
    building: 6, unit: 1, room: 606, userType: 'owner',
    authStatus: 'registering', // 仅微信登录，未完成注册
  },
];

// ==================== API 辅助 ====================

function api(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE);
    const data = body ? JSON.stringify(body) : null;

    const headers = {
      'Content-Type': 'application/json',
    };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const req = http.request(url, {
      method,
      headers,
      timeout: 15000,
    }, (res) => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => {
        try {
          const json = JSON.parse(body);
          resolve({ status: res.statusCode, data: json });
        } catch {
          resolve({ status: res.statusCode, data: body });
        }
      });
    });

    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });

    if (data) req.write(data);
    req.end();
  });
}

// ==================== 主流程 ====================

async function main() {
  const args = process.argv.slice(2);
  const shouldReset = args.includes('--reset');
  const verifyOnly = args.includes('--verify');

  console.log('=== 测试数据种子脚本 ===');
  console.log(`后端: ${BASE}`);
  console.log(shouldReset ? '模式: 强制重建' : verifyOnly ? '模式: 仅验证' : '模式: 幂等创建');
  console.log('');

  try {
    // 1. 验证后端可访问
    console.log('1. 验证后端连接...');
    const tenants = await api('GET', '/api/common/tenants');
    if (tenants.status !== 200 || !tenants.data.data) {
      throw new Error('无法获取小区列表，请确保后端正在运行');
    }
    const tenantId = tenants.data.data[0]?.id;
    if (!tenantId) throw new Error('数据库中没有小区数据');
    console.log(`   小区 ID: ${tenantId}\n`);

    // 2. 管理员登录
    console.log('2. 管理员登录...');
    const adminLogin = await api('POST', '/api/auth/login', ADMIN_AUTH);
    const adminToken = adminLogin.data?.data?.token || adminLogin.data?.token;
    if (!adminToken) throw new Error('管理员登录失败: ' + JSON.stringify(adminLogin.data));
    console.log('   登录成功\n');

    if (verifyOnly) {
      await verifyAccounts(adminToken, tenantId);
      return;
    }

    // 3. 创建/验证测试账号
    console.log('3. 处理测试账号...');
    const tokens = {};
    for (const account of TEST_ACCOUNTS) {
      await createOrSkipAccount(account, tenantId, adminToken, tokens, shouldReset);
    }

    // 4. 为 test_user_a 发布测试物品
    console.log('\n4. 发布测试物品...');
    if (tokens.test_user_a) {
      await publishTestItems(tokens.test_user_a);
    }

    // 5. 总结
    console.log('\n=== 种子数据就绪 ===');
    console.log('可用账号:');
    for (const acc of TEST_ACCOUNTS) {
      const token = tokens[acc.id] ? '✅ token可用' : '⚠️ 无token';
      console.log(`  ${acc.id}: ${acc.phone} (${acc.authStatus}) ${token}`);
    }

  } catch (err) {
    console.error('❌ 种子数据创建失败:', err.message);
    process.exit(1);
  }
}

async function createOrSkipAccount(account, tenantId, adminToken, tokens, reset) {
  const { id, phone, password, name, building, unit, room, userType, authStatus } = account;

  // 查询是否已存在
  if (!reset) {
    const check = await api('POST', '/api/auth/phone-login', {
      phone, password, tenantId,
    });
    if (check.status === 200 && check.data?.data?.token) {
      const token = check.data.data.token || check.data.token;
      tokens[id] = token;
      console.log(`  ${id} (${phone}): 已存在，跳过`);
      return;
    }
  }

  // 注册新用户
  console.log(`  ${id} (${phone}): 注册中...`);
  const register = await api('POST', '/api/auth/register', {
    tenantId,
    building, unit, room,
    name, phone, password, userType,
    docImages: ['https://via.placeholder.com/300x200.png?text=ID+Doc'],
  });

  if (register.status === 200) {
    const token = register.data?.data?.token || register.data?.token;
    if (token) tokens[id] = token;

    // 如果需要审批
    if (authStatus === 'approved' || authStatus === 'rejected' || authStatus === 'pending') {
      console.log(`    处理审核状态: ${authStatus}`);
      await handleAudit(phone, tenantId, authStatus, adminToken);
    }

    // 重新登录获取最新 token
    const relogin = await api('POST', '/api/auth/phone-login', {
      phone, password, tenantId,
    });
    if (relogin.data?.data?.token || relogin.data?.token) {
      tokens[id] = relogin.data.data?.token || relogin.data?.token;
    }
  } else {
    console.log(`    注册返回: ${register.status} ${JSON.stringify(register.data).substring(0, 100)}`);
  }
}

async function handleAudit(phone, tenantId, targetStatus, adminToken) {
  // 搜索用户 ID
  const search = await api('GET',
    `/api/admin/residents/search?keyword=${phone}&page=0&size=10`,
    null, adminToken
  );
  const users = search.data?.data?.content || search.data?.data || [];
  const user = Array.isArray(users) ? users.find(u => u.phone === phone) : null;
  if (!user) {
    console.log(`    未找到用户 ${phone}，跳过审核`);
    return;
  }

  const userId = user.id || user.userId;
  const currentStatus = user.authStatus;

  if (currentStatus === targetStatus) {
    console.log(`    状态已为 ${targetStatus}，跳过`);
    return;
  }

  // 审批通过
  if (targetStatus === 'approved') {
    await api('PUT', `/api/admin/audits/${userId}`,
      { approved: true, reason: '自动化测试审批' },
      adminToken
    );
    console.log(`    ✅ 已审批通过`);
  } else if (targetStatus === 'rejected') {
    // 先通过再拒绝（模拟审核流）
    await api('PUT', `/api/admin/audits/${userId}`,
      { approved: true, reason: '自动化测试审批' },
      adminToken
    );
    await api('PUT', `/api/admin/audits/${userId}`,
      { approved: false, reason: '自动化测试拒绝' },
      adminToken
    );
    console.log(`    ✅ 已设为拒绝状态`);
  }
  // pending 保持原样
}

async function publishTestItems(token) {
  const items = [
    {
      postType: 'LEND',
      title: '博世冲击钻套装 GBH 2-20',
      description: '九成新，带原装箱子，所有附件齐全。适合家庭装修使用。',
      category: '工具',
      price: 1500,
      maxDuration: 3,
      durationUnit: 'day',
      pickupMethod: 'self_pickup',
      condition: 'like-new',
    },
    {
      postType: 'LEND',
      title: '戴森吸尘器 V15 Detect',
      description: '用了半年，功能完好，配件齐全。',
      category: '家居',
      price: 3000,
      maxDuration: 2,
      durationUnit: 'day',
      pickupMethod: 'both',
      condition: 'normal',
    },
    {
      postType: 'HELP',
      title: '帮忙搬家',
      description: '从3栋搬到5栋，大件家具有沙发和床，需要2-3人帮忙。',
      category: '搬运',
      isUrgent: false,
    },
  ];

  for (const item of items) {
    try {
      let res;
      if (item.postType === 'HELP') {
        res = await api('POST', '/api/help-requests', item, token);
      } else {
        // Upload a placeholder image first? Skip for now
        const { images, ...rest } = item;
        rest.images = ['https://via.placeholder.com/400x300.png?text=Item'];
        res = await api('POST', '/api/idle-items', rest, token);
      }
      console.log(`  ${item.title}: ${res.status === 200 ? '✅' : '⚠️'} ${res.status}`);
    } catch (e) {
      console.log(`  ${item.title}: ❌ ${e.message}`);
    }
  }
}

async function verifyAccounts(adminToken, tenantId) {
  console.log('验证测试账号...');
  for (const acc of TEST_ACCOUNTS) {
    try {
      const res = await api('POST', '/api/auth/phone-login', {
        phone: acc.phone, password: acc.password, tenantId,
      });
      const ok = res.status === 200 && (res.data?.data?.token || res.data?.token);
      console.log(`  ${acc.id} (${acc.phone}): ${ok ? '✅ 可用' : '❌ 不可用'} [${acc.authStatus}]`);
    } catch {
      console.log(`  ${acc.id} (${acc.phone}): ❌ 连接失败`);
    }
  }
}

main();
