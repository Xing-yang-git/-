import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default {
  // 小程序项目路径
  projectPath: path.resolve(__dirname, '../../miniprogram'),

  // 微信开发者工具 CLI 路径
  cliPath: 'D:\\新建文件夹\\微信web开发者工具\\cli.bat',

  // 自动化服务端口
  devToolsPort: 9420,

  // 后端 API 地址
  backendBaseUrl: 'http://192.168.31.64:8080',

  // 测试账号
  accounts: {
    test_user_a: { phone: '13800000001', password: 'pass1234', name: '张三', tenantName: '测试小区A' },
    test_user_b: { phone: '13800000002', password: 'pass1234', name: '李四', tenantName: '测试小区A' },
    test_user_c: { phone: '13800000003', password: 'pass1234', name: '王五', tenantName: '测试小区A' },
    test_user_d: { phone: '13800000004', password: 'pass1234', name: '赵六', tenantName: '测试小区A' },
    test_user_e: { phone: '13800000005', password: 'pass1234', name: '孙七', tenantName: '测试小区A' },
    test_user_f: { phone: '13800000006', password: 'pass1234', name: '周八', tenantName: '测试小区A' },
  },

  // 超时配置（毫秒）
  defaultTimeout: 10000,
  navigationTimeout: 15000,

  // 报告路径
  reportDir: path.resolve(__dirname, '../reports'),
  screenshotDir: path.resolve(__dirname, '../reports/screenshots'),
};
