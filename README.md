# 社区互助闲置平台

> 邻里互助闲置物品平台 — 微信小程序 C端 + Vue 3 PC 管理后台

## 技术栈

| 层 | 技术 |
|---|---|
| C端 | 微信小程序原生 (WXML + WXSS + JS) |
| B端 | Vue 3 + Vite + Element Plus + ECharts + Pinia (JavaScript) |
| 后端 | Spring Boot 3.2 + JPA + PostgreSQL |
| 实时 | WebSocket 聊天中继（纯转发不落库，握手 JWT 鉴权） |
| 认证 | JWT（C端 手机号+密码 / B端 账号密码；后端另提供微信 code 登录接口） |

## 项目结构

```
community-platform/
├── server/                    # Spring Boot 后端
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/platform/
│       │   ├── config/        # CORS, Security, WebSocket, DataInitializer, SchemaMigration
│       │   ├── security/      # JwtTokenProvider, JwtAuthenticationFilter, JwtHandshakeInterceptor
│       │   ├── model/entity/  # 12 JPA 实体（Tenant, Building, Unit, Room, User,
│       │   │                  #   IdleItem, HelpRequest, HelpApplication,
│       │   │                  #   BorrowRequest, Notification, OperationLog, Rating）
│       │   ├── model/dto/     # DTO
│       │   ├── repository/    # 12 Repository
│       │   ├── service/       # 11 Service（含 WeChatService）
│       │   ├── controller/    # 10 Controller
│       │   ├── websocket/     # ChatWebSocketHandler, DashboardWebSocketHandler
│       │   └── common/        # Result + Exception
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/            # schema.sql（12 张表）+ seed-*.sql
│       └── test/java/com/platform/service/   # 8 个 Service 单元测试
│
├── miniprogram/               # C端微信小程序
│   ├── app.js / app.json / app.wxss
│   ├── utils/                 # api.js, auth.js, ws.js
│   ├── components/            # nav-bar, star-rating, empty-state, image-uploader
│   └── pages/                 # 14 个页面
│       ├── login/ register/ review-status/
│       ├── home/ search/
│       ├── idle-detail/ help-detail/
│       ├── publish-idle/      # 双模式表单：闲置发布 + 求助发布
│       ├── chat/ messages/
│       ├── return-detail/ rating/
│       └── my-posts/ profile/
│
├── admin/                     # B端 Vue 3 后台
│   └── src/
│       ├── views/             # 8 个视图（Dashboard, Audit, Content, Records,
│       │                      #   Export, Settings, Home, Login）
│       ├── components/        # AppSidebar, StatCard
│       ├── stores/            # Pinia：auth, community
│       ├── router/            # Vue Router + auth guard
│       ├── utils/             # api.js (axios), ws.js
│       └── styles/            # b-end.css
│
├── reviews/                   # 质量审查报告归档
├── CLAUDE.md                  # 项目约定与协作机制
└── README.md
```

## 本地启动

### 1. PostgreSQL

```bash
# 创建数据库
createdb community_platform

# 或使用 psql
psql -U postgres -c "CREATE DATABASE community_platform;"
```

### 2. 后端 (Spring Boot)

```bash
cd server
mvn spring-boot:run
# 启动在 http://localhost:8080
# schema.sql 自动建表；DataInitializer 播种管理员账号与小区/楼栋/单元/房号数据
```

运行单元测试：

```bash
cd server
mvn test    # service 层 8 个测试类
```

### 3. B端管理后台 (Vue 3)

```bash
cd admin
npm install
npm run dev
# 启动在 http://localhost:5173
# 登录账号: admin / admin123
```

### 4. C端微信小程序

1. 打开微信开发者工具
2. 导入项目 → 选择 `miniprogram/` 目录
3. 填入测试 AppID（或使用测试号）
4. 开发者工具中模拟器即见效果

> 真机调试时后端跑在本地局域网 IP（如 `192.168.31.64:8080`），改后端代码后必须重启服务才能生效。

## 测试账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 超级管理员 | admin | admin123 |

C端用户通过手机号 + 密码注册登录（`register` 页注册，`login` 页登录）；后端保留 `/api/auth/wx-login` 微信 code 登录接口。

## API 接口概览

| 模块 | 路径 | 说明 |
|---|---|---|
| 公共 | GET /api/common/* | 小区/楼栋/单元/房号查询 |
| 认证 | POST /api/auth/* | wx-login / login / phone-login / register / appeal，GET status |
| 闲置 | /api/idle-items/** | 发布/列表/详情/搜索/下架 |
| 借入 | /api/borrow-requests/** | 申请/审批/归还确认 |
| 技能求助 | /api/help-requests/** | 发布/列表/申请/审批 |
| 评分 | /api/ratings/** | 提交评分/查看评分 |
| 聊天 | /api/chats/** | 消息发送/历史/会话列表/撤回 |
| 通知 | /api/notifications/** | 列表/未读数/全部已读 |
| 用户活动 | GET /api/users/* | profile / posts / approvals / in-progress / completed |
| 管理 | /api/admin/** | 看板/审核/内容管理/代发/记录/导出/日志 |
| WebSocket | /ws/chat | 聊天实时消息（JwtHandshakeInterceptor 握手鉴权） |

> 注：`DashboardWebSocketHandler`（B端看板推送 `/ws/dashboard`）已有实现且 admin 前端有连接代码，但当前未在 `WebSocketConfig` 中注册。
