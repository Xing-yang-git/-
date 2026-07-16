---
name: annotation-guarantee
description: Use when reviewing or enforcing code comment coverage (20%-30% target) and comment quality standards. Checks that public APIs, complex logic, business rules, and non-obvious decisions are properly documented. Use when the user says "注释覆盖率", "annotation", "comment coverage", "检查注释", "补注释", or when performing code quality review that includes documentation standards.
---

# Annotation Guarantee

## Overview

Systematic methodology for ensuring code comments achieve **20%-30% coverage** with **high-quality, actionable documentation**. Comments must explain *why*, not *what* — the code itself should be readable enough to show *what* it does.

This skill is **language-agnostic** — it defines the methodology. The calling agent or project context provides the specific file paths, language conventions, and review scope.

## Core Principles

1. **20%-30% is the sweet spot.** Below 20% = under-documented (maintenance risk). Above 30% = likely over-commented (noise risk, code may need simplification). The range is a guideline, not a hard gate — exceptional clarity in either direction is fine with reasoned justification.
2. **Comments age faster than code.** A wrong comment is worse than no comment. Every comment must be true *now*, not what the code did last month.
3. **Explain intent, not syntax.** `// Set x to 1` adds zero value. `// Default threshold from UX study (see CONFIG-42)` adds value.
4. **Public surface first.** Public APIs, exported functions, and shared types carry the highest documentation burden. Private internals self-document through naming and structure.
5. **No performance comments.** Don't comment on language semantics or obvious behavior. The reader knows the language.
6. **Comment language must match the project convention.** If the project declares a comment language (in its CLAUDE.md, contributing guide, or calling agent's context), every substantive comment must use it. Technical terms, identifiers, and third-party/vendored code are exempt. Flag comments written in a different language as a quality finding — mixed-language comment bases decay fastest, because nobody feels ownership of the "other" language's comments. The skill does not prescribe which language; the project does.

## Coverage Measurement

### What Counts

| Counts | Does NOT Count |
|--------|----------------|
| `//` single-line comments explaining intent | Blank lines |
| `/* */` block comments (substantive) | Import / package declaration lines |
| `/** */` JSDoc / Javadoc on public APIs | `{}` / `()` / `;` only lines |
| `<!-- -->` WXML section markers | Code that is commented-out (dead code) |
| `@param`, `@return`, `@throws` tags | License headers / copyright boilerplate |
| Meaningful `#` / `--` / `/**` comments in config/scripts | Auto-generated code markers |

### Formula

```
coverage = (comment_lines / total_non_blank_lines) × 100%

comment_lines = count of lines whose primary purpose is documentation
total_non_blank_lines = all lines - blank lines
```

### Tiers by File Type

| File Type | Target | Reasoning |
|-----------|--------|-----------|
| Core business logic (Service, domain) | 25-30% | Complex rules need documentation |
| Public API (Controller, exported functions) | 25-30% | External contract — must be documented |
| Internal utilities / helpers | 20-25% | Documented at function level |
| DTOs / Entities / Models | 15-20% | Self-documenting if well-named; comment only constraints and non-obvious fields |
| Config files (YML, JSON, XML) | 10-15% | Structure is self-evident; comment environment-specific values |
| Tests | 10-15% | Test names should tell the story; comment only non-obvious setup or assertions |
| CSS / WXSS / SCSS | 5-10% | Section markers and utility explanations only |
| WXML / HTML templates | 5-10% | Section markers (`<!-- 头部导航 -->`) and conditional logic explanation |

## What to Comment

### Required (must comment)

- **Public APIs**: Every `public` method/function/class — Javadoc/JSDoc with `@param`, `@return`, `@throws`
- **Business rules**: Non-obvious domain logic (`// Refund window closes 7 days after delivery, per policy v2.1`)
- **Magic numbers**: Constants that aren't extracted to named variables (`// 0.97 = iOS default tap-scale per HIG`)
- **Workarounds**: Temporary fixes or platform-specific hacks (`// WXSS does not support backdrop-filter; fallback to opacity`)
- **Non-obvious decisions**: Design trade-offs, rejected alternatives (`// Used recursion instead of iteration because the tree depth is guaranteed ≤3`)
- **Security-sensitive code**: Auth checks, input validation, cryptographic operations

### Optional (comment when it adds clarity)

- **Complex algorithms**: Summary of approach before the implementation
- **Regular expressions**: What the pattern matches, with examples
- **Edge case handling**: Why this specific branch exists
- **Section markers**: Structural comments in long files (`// ─── User Management ───`)

### Forbidden (never comment)

- **Obvious syntax**: `// Increment i` above `i++`
- **Redundant restatement**: `// Call the login function` above `login();`
- **Commented-out code**: Dead code should be deleted, not commented. If it's genuinely useful reference, add a dated note explaining *when* to restore it.
- **Git blame in comments**: That's what `git blame` is for. No `// Added by Zhang on 2024-03-15`.
- **Emoji-only comments**: A single emoji conveys zero information. Use words.
- **Changelog in file headers**: That's what commit history is for.

## Language-Specific Formats

### Java (Javadoc)

```java
/**
 * Processes a refund for the given order.
 *
 * <p>This method validates the refund window (7 days post-delivery),
 * calculates the refund amount including prorated shipping, and
 * initiates the payment gateway reversal.
 *
 * @param orderId  the order to refund, must be in DELIVERED status
 * @param reason   refund reason code from {@link RefundReason}
 * @return the created refund with PENDING status
 * @throws OrderNotFoundException   if orderId does not exist
 * @throws RefundWindowExpiredException  if outside the 7-day window
 */
public Refund processRefund(UUID orderId, RefundReason reason) { ... }
```

**Key rules:**
- Every `public` class and method gets Javadoc
- `@param` describes constraints, not just "the order ID" (bad) — "must be in DELIVERED status" (good)
- `@throws` lists ALL checked exceptions the caller might receive
- `@return` describes the returned object's state, not just its type

### JavaScript / TypeScript (JSDoc)

```typescript
/**
 * Validates that the user has permission to access the target resource.
 * Checks both direct ownership and admin role delegation.
 *
 * @param userId - The requesting user's ID
 * @param resourceId - The target resource ID
 * @param resourceType - Type from {@link ResourceType} enum
 * @returns true if access is granted
 * @throws {AuthorizationError} If the user is not authenticated
 */
async function checkAccess(
  userId: string,
  resourceId: string,
  resourceType: ResourceType
): Promise<boolean> { ... }
```

**Key rules:**
- Exported functions get full JSDoc
- Internal functions: at minimum a one-line `//` describing intent
- TypeScript interfaces: comment only non-obvious fields; the type system documents the rest
- Miniprogram `.js` files: use JSDoc for shared utility functions; page lifecycle methods (onLoad, onShow) get a one-line summary

### Vue SFC

```vue
<!--
  AuditView.vue — 内容审核管理页面

  功能：审核闲置物品、互助请求、借用申请的发布内容。
  状态流转：pending → approved / rejected（支持批量操作）。
  权限：仅 admin / moderator 角色可访问。
-->
<script setup lang="ts">
// ...
</script>

<template>
  <!-- 筛选工具栏 -->
  <div class="filter-row">...</div>

  <!-- 审核表格 -->
  <el-table>...</el-table>

  <!-- 审核弹窗（通过/拒绝/预览） -->
  <el-dialog>...</el-dialog>
</template>
```

**Key rules:**
- File-level comment at the top of `<script setup>` or `<template>`: purpose, key features, permission requirements
- Template section markers for major regions: `<!-- 筛选工具栏 -->`, `<!-- 表格主体 -->`, `<!-- 分页 -->`
- Script: JSDoc on exported functions, one-liners for complex reactive logic

### Miniprogram (WXML + WXSS + JS)

**WXML:**
```xml
<!-- 首页头部 — 搜索栏 + 位置选择器 -->
<view class="header">
  <search-bar />
  <location-picker />
</view>

<!-- 闲置物品瀑布流 -->
<view class="idle-grid">...</view>
```

**WXSS:**
```css
/* ─── 首页头部 ─── */
.header { ... }

/* 搜索栏阴影 — 比全局 shadow-md 更重以匹配原型 */
.header .search { box-shadow: 0 2px 8px rgba(0,0,0,0.12); }
```

**JS:**
```javascript
/**
 * 闲置物品列表页
 * 
 * 支持：下拉刷新、滚动分页、分类筛选、关键词搜索
 * 数据缓存：5分钟内不重复请求
 */
Page({
  // ...
})
```

### SQL (schema / seed)

```sql
-- ============================================================
-- 用户表：存储微信小程序用户和PC管理端用户
-- 字段 tenant_type: 'resident' | 'property' | 'admin'
-- 软删除：deleted_at 非空表示已删除
-- ============================================================
CREATE TABLE users ( ... );

-- 初始管理员账号: admin@platform.com / 密码哈希见 AuthService
INSERT INTO users (...) VALUES (...);
```

### YML / Properties Config

```yaml
# ─── JWT 配置 ───
# secret: 生产环境必须通过环境变量 JWT_SECRET 覆盖此默认值
# expiration: 7天 (604800000ms)，开发阶段设置较长方便调试
jwt:
  secret: ${JWT_SECRET:changeme-in-production}
  expiration: 604800000
```

## Measurement Procedure

1. **Count comment lines**: grep for `//`, `/*`, `*`, `<!--`, `#`, `--` patterns (filtering out blank, bracket-only, import lines)
2. **Count total non-blank lines**: `wc -l` minus blank lines
3. **Calculate**: `comment_lines / total_non_blank × 100`
4. **Categorize by file type** and compare to tier targets above
5. **Assess quality** — not just quantity. Spot-check 5-10 comments for substance:
   - Does each comment explain *why*?
   - Is any comment outdated (doesn't match current code)?
   - Are there sections that *should* be commented but aren't?
   - Is the comment written in the project's declared comment language (if one is declared)?

## Review Checklist

After completing annotation review, report:

```
| File | Total Lines | Comment Lines | Coverage | Tier Target | Status |
|------|-------------|---------------|----------|-------------|--------|
| AuthService.java | 245 | 62 | 25.3% | 25-30% | ✓ |
| AuthController.java | 53 | 3 | 5.7% | 25-30% | ✗ LOW |
| HomeView.vue | 320 | 28 | 8.8% | 20-25% | ✗ LOW |
| schema.sql | 180 | 18 | 10.0% | 10-15% | ✓ |
```

## Common Mistakes

| Mistake | Why it happens | Fix |
|---------|---------------|-----|
| Commenting what code does | Developer learned "always comment" without learning *what* to comment | Delete the comment if the code is clear; if the code isn't clear, refactor it |
| Comment = code's twin | Comment was written once, code evolved, comment stayed frozen | Verify every comment matches current behavior |
| Javadoc on private methods | Overzealous IDE template filling | Private methods get a `//` line if complex; no full Javadoc needed |
| No comment on regex | Developer thinks the regex is "obvious" | Add a comment with what it matches and an example |
| Block comments as version control | "Old implementation below, new one above" | Delete old code; use git history |
| Emoji as documentation | One emoji can't convey intent | Use words; emoji can supplement but not replace |
| Commenting the language | `// Loop through array` above `for (x of arr)` | The `for` keyword already says it's a loop |
| Zero comments in the whole file | Wrote it alone, assumed it's clear | At minimum: file-level purpose comment + section markers |

## The Bottom Line

**Comment coverage is a proxy for code clarity, not a goal in itself.**

A file at 35% coverage that explains every `if` statement is worse than a file at 18% with precise, high-value comments on the 3 genuinely tricky parts. The 20-30% range guides attention — files outside it deserve a closer look, not automatic correction.
