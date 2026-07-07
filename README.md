# 社区互助闲置平台

> 邻里互助闲置物品平台 — 微信小程序 C端 + Vue 3 PC 管理后台

## 技术栈

| 层 | 技术 |
|---|---|
| C端 | 微信小程序原生 (WXML + WXSS + JS) |
| B端 | Vue 3 + Element Plus + ECharts + Pinia |
| 后端 | Spring Boot 3.2 + JPA + PostgreSQL |
| 实时 | WebSocket (聊天 + 看板推送) |
| 认证 | JWT (C端 wx.login / B端 账号密码) |

## 项目结构

```
community-platform/
├── server/                    # Spring Boot 后端 (85 文件)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/platform/
│       │   ├── config/        # CORS, Security, WebSocket
│       │   ├── security/      # JWT Token + Filter
│       │   ├── model/entity/  # 15 JPA 实体
│       │   ├── model/dto/     # 25 DTO
│       │   ├── repository/    # 15 Repository
│       │   ├── service/       # 9 Service
│       │   ├── controller/    # 9 Controller
│       │   ├── websocket/     # Chat + Dashboard Handler
│       │   └── common/        # Result + Exception
│       └── resources/
│           ├── application.yml
│           └── db/
│               ├── schema.sql # 15 张表 DDL
│               └── seed.sql   # 翠湖花园种子数据
│
├── miniprogram/               # C端微信小程序 (84 文件)
│   ├── app.js / app.json / app.wxss
│   ├── utils/                 # api.js, ws.js, auth.js
│   ├── components/            # nav-bar, star-rating, empty-state, image-uploader
│   └── pages/                 # 15 个页面
│       ├── login/ register/ review-status/
│       ├── home/ search/
│       ├── idle-detail/ help-detail/
│       ├── publish-idle/ publish-help/
│       ├── chat/ messages/
│       ├── return-detail/ rating/
│       └── my-posts/ profile/
│
├── admin/                     # B端 Vue 3 后台 (20 文件)
│   └── src/
│       ├── views/             # 8 页面 + 登录
│       ├── components/        # AppSidebar, StatCard
│       ├── stores/            # Pinia auth store
│       ├── router/            # Vue Router + auth guard
│       ├── utils/             # api.js (axios), ws.js
│       └── styles/            # b-end.css
│
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
./mvnw spring-boot:run
# 启动在 http://localhost:8080
# schema.sql + seed.sql 自动执行建表和种子数据
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

## 测试账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 超级管理员 | admin | admin123 |
| 普通管理员 | admin2 | admin123 |

C端用户通过"微信一键登录"自动注册（开发模式下 code 即 openid）。

## API 接口概览

| 模块 | 路径 | 说明 |
|---|---|---|
| 公共 | GET /api/common/* | 小区/楼栋/单元/房号查询 |
| 认证 | POST /api/auth/* | 微信登录/管理登录/注册/审核状态 |
| 闲置 | /api/idle/** | 发布/列表/详情/搜索/下架 |
| 借入 | /api/borrow/** | 申请/审批/归还确认 |
| 技能 | /api/help/** | 发布/列表/申请/审批 |
| 评分 | POST/GET /api/rating/** | 提交评分/查看评分 |
| 聊天 | /api/chat/** | 会话列表/消息/已读 |
| 通知 | /api/notification/** | 列表/未读数/全部已读 |
| 管理 | /api/admin/** | 看板/审核/内容管理/代发/记录/导出/日志 |
| WebSocket | /ws/chat | 聊天实时消息 |
| WebSocket | /ws/dashboard | B端看板实时推送 |
