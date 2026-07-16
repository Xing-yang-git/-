# CLAUDE.md — 项目约定与协作机制

> 本文件承载**项目约定层**：不可从代码直接推导的机制、规范与决策原因。
> 结构性事实（模块、页面、实体、接口清单）以 [README.md](README.md) 为唯一来源，不在此重复。
> 个人偏好与对 Claude 的工作反馈存放在用户主目录记忆中，不入仓库。

## 1. 项目速览

社区互助闲置平台 monorepo，三个独立构建、独立部署的平台共享同一业务域：

| 平台 | 目录 | 技术栈 |
|---|---|---|
| C端（微信小程序） | `miniprogram/` | 原生 WXML + WXSS + JavaScript |
| B端（PC 管理端） | `admin/` | Vue 3 + Vite + Element Plus + Pinia + ECharts（JavaScript） |
| 后端 | `server/` | Spring Boot 3.2 + JPA + PostgreSQL + JWT + WebSocket |

**为什么是 monorepo + 独立构建**：三个平台共享领域逻辑，但用户、构建工具、发布节奏都不同。monorepo 保持协调，独立构建保证各自可独立部署。C端与 B端调用同一个 `server/` REST API。

目录结构、页面/实体/接口清单等详见 [README.md](README.md)。

## 2. 原型参照（UI 改动铁律）

设计原型位于：`D:\notegenWordFile\prototype\社区互助闲置平台原型-C端B端`

- `c-end/` → C端原型（HTML+CSS+JS），全局 token 在 `c-end/css/ios-ui.css`，带 `--ios-` 前缀
- `b-end/` → B端原型（HTML+CSS+JS），全局 token 在 `b-end/css/b-end.css`，无前缀

**任何 UI 改动前必须先查原型，不得自创设计。** 若原型没有对应设计且现有代码模式推导不出正确答案，必须询问用户，不许凭空发明（历史教训：曾未查原型自创 tabBar 第 4 项，导致返工）。

Token 转换约定：C端实现时去掉 `--ios-` 前缀；B端直接使用无前缀名。

## 3. Skill-Agent 架构

严格分层：

| 层 | 内容 | 位置 |
|---|---|---|
| **Skill** | 纯方法论，零硬编码路径，跨项目可复用 | `.claude/skills/<name>/SKILL.md`（项目级）或用户主目录（用户级） |
| **Subagent** | 项目上下文：文件路径、token 映射、scope 规则、平台清单 | `.claude/agents/<name>.md` |

**判定标准**：一个 skill 必须能不加修改地复制到其他项目立即使用。允许示例代码和泛化平台名；禁止绝对路径、项目名、具体目录结构。

**调用模式**：子代理开工前必须先通过 Skill 工具加载其技能，技能是方法论，子代理是导航者。

项目级技能：`pixel-perfect-replication`、`annotation-guarantee`、`security-audit`、`git-commit-standard`、`test-guarantee`、`database-schema-alignment`
用户级技能：`requesting-code-review`、`receiving-code-review`

## 4. 子代理清单

| 子代理 | 触发方式 | 触发关键词 / 条件 | 技能 | 职责 |
|---|---|---|---|---|
| `prototype-alignment` | 主动 | "对齐"、"align"、"按原型改" | pixel-perfect-replication | C端/B端页面与原型像素级对齐（Capture → Compare → Fix → Verify） |
| `quality-review` | 主动 | "审查"、"review"、"安全检查" | security-audit、annotation-guarantee、test-guarantee 等 | 三端五维审查（安全/质量/注释/测试），产出审查通行证 |
| `database-operator` | **自动** | Entity 字段变更、schema.sql 修改，或 "对齐数据库" | database-schema-alignment | JPA Entity 与 PostgreSQL 实际 schema 对齐，生成幂等 DDL（psql 优先，SchemaMigration.java 备选） |
| `git-save` | 被动 | "保存版本"、"提交"、"commit"、"打个点" | git-commit-standard | 审查门禁执行 + Conventional Commit + **文档同步（README/CLAUDE.md）+ 个人记忆同步** |
| `test-generator` | 被动 | "写测试"、"生成测试"、"补测试" | test-guarantee | 生成 AAA 模式单元测试并运行修复至全绿 |

**为什么 database-operator 自动触发**：schema 与 Entity 不一致会立即导致运行时错误，不像提交那样可由用户择机决定。
**为什么 git-save 被动**：用户明确要求它不得主动建议提交，只响应显式保存指令。
**安全审查统一归 quality-review**：其他子代理不得重复做安全扫描。

## 5. 审查门禁机制

**任何代码提交前必须通过质量审查。** 契约：

```
quality-review ──写──→ .claude/review-reports/review-result.md ──读──→ git-save
                                                     └──提交成功后删除──┘
```

git-save 的三道门禁（详细流程见 [.claude/agents/git-save.md](.claude/agents/git-save.md)）：

1. **Gate 1 — Assessment**：`PASS` 放行；`PASS WITH WARNINGS` 需用户确认；`FAIL` 硬阻断
2. **Gate 2 — Commit hash**：`reviewed-commit` 必须等于当前 HEAD，否则报告过期
3. **Gate 3 — Scope 覆盖**：通行证 scope 必须覆盖本次提交涉及的全部平台

**白名单例外**（经用户确认可跳过审查）：`*.md`/`*.txt` 文档、安全配置（`.gitignore`、`tsconfig.*`、`vite.config.*`）、`.claude/**`、纯样式格式化。**不在白名单**：`application.yml`、`.env`、`schema.sql`、`pom.xml`、`package.json` 及一切逻辑文件。

**一次性通行证**：提交成功后 git-save 删除通行证文件，下次提交需重新审查——防止一份陈旧报告覆盖多次提交。

## 6. Git 规范

[Conventional Commits](https://www.conventionalcommits.org/)，**提交主题默认中文**，祈使句，≤ 25 个汉字。

| 改动目录 | scope |
|---|---|
| `miniprogram/**` | `miniprogram` |
| `admin/**` | `admin` |
| `server/**` | `server` |
| 多平台同一功能 | 逗号分隔，如 `admin,server` |
| `.claude/**` | `chore:`（无 scope 或 `chore(.claude):`） |
| 根目录 `*.md` | `docs:`（无 scope） |

**拆分 vs 合并**：默认按平台拆分提交。仅当跨平台改动服务同一功能、拆开会留下破碎中间态、或单独回滚会破坏功能时才合并。一个提交 = 一个完整、可独立审查、可独立回滚的改动。

**分支命名**：`<type>/<short-description>`，小写连字符，如 `feat/rating-system`、`fix/login-npe`。

## 7. 注释规范（2026-07-16 确立）

**所有代码注释一律使用中文**（含 Javadoc/JSDoc 描述、WXML/Vue 模板注释、CSS/WXSS 注释、SQL/YML 注释）：

- 句子主体用中文；JWT、WebSocket、token、Bean、Mock、DTO 等技术术语保留英文原词，不生硬直译
- Javadoc/JSDoc 的 `@param` / `@return` / `@throws` 标签结构保留，描述文字用中文
- 第三方代码（`node_modules/` 等）与被注释掉的代码不受此约束
- 覆盖率与质量标准遵循 `annotation-guarantee` 技能（20%-30%，解释 why 而非 what）

**检测**：quality-review 的注释维度会检查注释语言一致性，发现成段英文注释报 **High**（提交时进入 PASS WITH WARNINGS，需用户确认）。

## 8. 后端重启规则

改动 `server/`（Spring Boot）代码后，**由 Claude 自行编译并重启后端**，绝不让用户手动操作：

1. 编译改动
2. 停掉占用 8080 的旧 java 进程（Windows git bash：`taskkill //PID <pid> //F`）
3. 后台重启：`cd server && mvn -o spring-boot:run`
4. 确认端口就绪 / 启动日志无误后反馈用户

**原因**：后端跑在本地局域网 IP（如 `192.168.31.64:8080`），不重启则小程序打到旧进程，改动等于没生效。前端（小程序/Vue）改动不涉及此流程。

## 9. 记忆维护协议（三层事实来源）

| 层 | 位置 | 承载内容 | 更新时机 |
|---|---|---|---|
| **README.md** | 仓库根 | 结构性事实：模块、页面、实体、接口、启动方式 | git-save 提交前随 diff 同步（Step 2.5） |
| **CLAUDE.md** | 仓库根 | 项目约定与机制（本文件） | 约定/机制变更时随提交同步（Step 2.5） |
| **用户主目录 memory** | `~/.claude/projects/<本项目>/memory/` | 仅个人偏好与对 Claude 的工作反馈 | git-save 提交成功后自省同步（Step 5.5） |

**判定标准**：能从代码推导的（数量、清单、结构）→ README.md；不能从代码推导但属于项目的（约定、机制、决策原因）→ CLAUDE.md；只关于用户个人的（沟通语言、工作习惯、反馈）→ memory。

**执行者**：git-save 子代理在每次提交流程中负责前两层同步（结构变了改 README，约定变了改 CLAUDE.md，并随本次提交入库），提交后自省第三层。这保证文档不再与代码脱节。
