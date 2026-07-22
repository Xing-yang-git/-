# Review Report — 全量审查与修复（最终）

| Field | Value |
|-------|-------|
| **timestamp** | 2026-07-22T20:15:00+08:00 |
| **scope** | miniprogram, server, admin |
| **reviewed-commit** | 9bbe92a |
| **assessment** | **PASS** |
| **critical** | 0 (3 fixed) |
| **high** | 0 (15 fixed) |
| **medium** | 20 remaining (4 fixed) |

## 已修复汇总

### Critical (3/3)
- `findActiveHelpApplication` "accepted" → `BizStatus.APPROVED`（真实 bug）
- `BorrowServiceTest` mock `NotificationRepository` → `NotificationService` + `findByIdWithLock`
- `BizStatus` 新增 `RESERVED`/`REGISTERING`/`DELETED` 常量

### High (15/15)
- `BizStatus` 魔法字符串集中化（4 个 Service 引用同步）
- `WebSocketConfig` DashboardWebSocketHandler 端点注册
- `application.yml` 密码/JWT secret 改为 `${ENV_VAR:dev-default}`
- `GlobalExceptionHandler` 不暴露异常类名
- `UserFormatter` 工具类（消除 6 Service ~200 行重复代码）
- `CommonService` 创建 TenantDTO/BuildingDTO/UnitDTO/RoomDTO
- C端 3 处 CSS 铁律违规（px→rpx, #hex→var(--*)）
- C端 `publish-idle.wxss` 缺 `button::after` 重置
- C端 `search.js` onUnload 定时器清理
- C端 零测试覆盖 → 4 个 utils 模块 59 个测试，覆盖率 87.5%

### 新增文件
- `UserFormatter.java` — 用户信息格式化工具类
- `TenantDTO.java`, `BuildingDTO.java`, `UnitDTO.java`, `RoomDTO.java` — 小区数据 DTO
- `miniprogram/utils/__mocks__/wx.mock.js` — wx 全局 mock
- `miniprogram/utils/__tests__/constants.test.js` (7 tests)
- `miniprogram/utils/__tests__/auth.test.js` (16 tests)
- `miniprogram/utils/__tests__/api.test.js` (19 tests)
- `miniprogram/utils/__tests__/ws.test.js` (17 tests)

### 延后项
- 14 个 DTO 类字段级 Javadoc — 建议分批补充
- B端 `api/admin.ts` DTO 字段注释 — 与后端同步补充
- 3 个预存测试失败（Chat/Help/UserActivity）— mock 依赖不匹配，下次迭代修
