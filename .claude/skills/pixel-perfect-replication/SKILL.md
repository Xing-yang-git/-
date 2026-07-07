---
name: pixel-perfect-replication
description: Use when aligning a miniprogram page (WXML+WXSS) or Vue SFC (PC admin) to match its design prototype HTML+CSS pixel-perfectly — including layout, spacing, colors, fonts, icons, shadows, borders, animations, and every visual detail. Use when the user says "align", "match the prototype", "pixel-perfect", "对齐原型", "按原型改", "一比一复刻", or when a page looks different from its reference prototype.
---

# Pixel-Perfect Replication

## Overview

Systematic process for replicating a design prototype (HTML+CSS+JS) to a target implementation — WeChat miniprogram (WXML+WXSS) or Vue SFC — with 1:1 visual fidelity. Every layout dimension, color value, font property, shadow, border-radius, spacing, icon, and animation must match exactly.

This skill is **platform-agnostic** — it defines the methodology. The calling agent or project context provides the specific prototype paths, page mappings, and design token conversion tables.

## ⚠️ Critical Anti-Pattern (MUST READ)

**The #1 cause of multi-round rework: writing code BEFORE reading the prototype.**

This failure follows a predictable pattern:
1. Jump straight to writing WXSS from memory/guesswork → px not converted, spacing wrong
2. Fix issues reactively as the user reports them → each round catches only what was reported
3. Never did a full Capture→Compare cycle → systemic mismatches survive multiple rounds

**To avoid this:** You MUST complete Phase 1 (Capture) and Phase 2 (Compare) BEFORE writing a single line of CSS. If you find yourself writing styles without having the prototype open side by side, STOP. Go back to Phase 1.

---

## Core Principles

1. **The prototype is the single source of truth.** If the prototype and implementation differ, the prototype wins — always.
2. **Every CSS property matters.** Padding, margin, line-height, letter-spacing, border-radius, shadow, font-size, font-weight, color, background, transform, transition — nothing is too small to check.
3. **Compare rendered output, not code structure.** Different platforms (HTML vs WXML, CSS vs WXSS, CSS vs Vue scoped styles) use different syntax; focus on the visual result, not 1:1 code translation.
4. **Check all states.** Default, hover/active, focused, disabled, empty, loading, error — every interactive state must match.
5. **Walk the DOM tree, not sections.** Compare element by element in tree order. Never say "the card area looks about right" — compare each individual element's computed styles against the prototype.

## Unit Conversion: px → rpx (Miniprogram Only)

Prototype uses `px` at 375px viewport width. Miniprogram uses `rpx` at 750rpx.

```
rpx = prototype_px × 2

Examples:
  16px → 32rpx    20px → 40rpx    24px → 48rpx
  10px → 20rpx     8px → 16rpx    44px → 88rpx
  17px → 34rpx    15px → 30rpx    13px → 26rpx
```

**Vue/web targets use `px` directly — no conversion needed.**

## Replication Workflow

### Phase 1: Capture — Read Both Sides

**⚠️ This phase is MANDATORY. Never skip to writing code.**

#### 1.0 Locate the Prototype

The prototype may NOT be inside the project directory. Search broadly:

1. Check `**/prototype/**`, `**/c-end/**`, `**/b-end/**` inside the project
2. Check sibling/parent directories for folders named `原型`, `prototype`, `design`
3. Check `d:/notegenWordFile/prototype/` for standalone prototype directories
4. Use Glob with broad patterns (`**/*.html`) if the above fail
5. If still not found, ask the user: "Where is the design prototype for this page?"

#### 1.1 Read All Source Files

Read all source files in parallel:

**Prototype side (applies to both C端 and B端):**
- The page HTML file (contains inline `<style>` with page-specific CSS)
- The global CSS file referenced by the prototype (design tokens — resolve all `var(--ios-*)` values to their actual values)
- Any page-specific JS (interactions/animations)

**Miniprogram target:**
- The page WXML file (structure)
- The page WXSS file (page-specific styles)
- The page JSON file (navigation bar title, pull-to-refresh, etc.)
- The page JS file (logic and interactions)
- The global `app.wxss` (design tokens — look for CSS variables in the `page {}` block)

**Vue SFC target:**
- The `.vue` file (`<template>` + `<script setup>` + `<style scoped>`)
- Any child components referenced by the view
- Global CSS imports (design tokens)

#### 1.2 Extract the Prototype DOM Tree

Build a structured tree from the prototype HTML. For each element in the visual hierarchy, record:
- Tag name and class list
- CSS properties from inline styles
- CSS properties from the global CSS file (resolving all `var()` references)
- Text content

Format:
```
.prototype-page                  (display: flex; align-items: center; justify-content: center)
├── .status-bar                  (height: 28px; background: rgba(255,255,255,0.92))
├── .nav-bar                     (height: 44px; background: rgba(255,255,255,0.82))
│   └── .nav-title               (font-size: 17px; font-weight: 600; "账户状态")
├── .page-content                (flex: 1; display: flex; align-items: center; justify-content: center)
│   └── .state-panel             (display: none/block — no padding, no background)
│       ├── .status-card         (padding: 48px 32px; text-align: center)
│       │   ├── .status-icon     (width: 72px; height: 72px; border-radius: 50%; margin-bottom: 20px)
│       │   │   └── svg          (36×36; stroke="currentColor" → resolves to #1d1d1f)
│       │   ├── .status-title    (font-size: 22px; font-weight: 700; margin-bottom: 8px)
│       │   ├── .status-desc     (font-size: 17px; color: #6e6e73; line-height: 1.6; margin-bottom: 20px)
│       │   ├── .btn             (height: 44px; padding: 0 20px; border-radius: 8px; font-size: 17px)
│       │   └── .callout         (if present: padding: 14px 16px; margin: 0 0 16px; font-size: 13px)
│       └── p.footer-tip         (font-size: 12px; color: #86868b; no margin)
```

This tree is your comparison reference. Every element in the tree must have a corresponding element in the target with matching styles.

### Phase 2: Compare — Element by Element (Tree Walk)

**⚠️ This phase produces the comparison table. Do NOT write any code until this table is complete.**

Go through the DOM tree from Phase 1.2 element by element. For each element in the prototype tree, compare its target counterpart across all dimensions.

#### 2.0 Comparison Table (MANDATORY — complete before writing any code)

Build this table for EVERY element in the DOM tree:

```
| Element | Property | Prototype | Target (before) | Match? | Fix needed |
|---------|----------|-----------|-----------------|--------|------------|
| .nav-title | text | "账户状态" | "审核状态" | ❌ | Change to "账户状态" |
| .status-card | padding | 48px 32px | (none) | ❌ | Add 96rpx 64rpx |
| .status-card | text-align | center | (none) | ❌ | Add text-align: center |
| ... | ... | ... | ... | ... | ... |
```

For each element, check at minimum:
- Tag type (div→view, span→text, img→image, etc.)
- Class name (strip `--ios-` prefix for miniprogram CSS variables)
- padding, margin (all four sides)
- font-size, font-weight, font-family
- color, background
- border, border-radius
- width, height, min-height
- display, position, flex properties
- text content (especially nav titles, button labels, footer text)
- line-height, letter-spacing

#### 2.1 Layout & Spacing

#### 2.1 Layout & Spacing
- Page-level: flex direction, alignment, justify-content, padding, margin
- Component-level: each element's padding, margin, gap
- Container widths, heights, min-heights
- Positioning: relative/absolute/fixed, top/left/right/bottom

#### 2.2 Colors (most common mismatch)
- Text color (primary, secondary, tertiary)
- Background color (page, card, input, button)
- Accent/tint colors (blue, red, orange, green)
- Border/separator colors
- Shadow colors and opacities
- Check: does the target use hardcoded hex or CSS variables? Hardcoded values → replace with CSS variables.

#### 2.3 Typography
- Font family (system font stack order matters)
- Font size at each level (title, subtitle, body, caption)
- Font weight (prototypes often use specific weights like 450, 550, 650 — not just 400/600/700)
- Line height
- Letter spacing (especially negative values like `-0.01em` or `-0.02em` on titles)
- Text alignment

#### 2.4 Borders & Radii
- Border width, style, color
- Border-radius for each element (sm: 6px, default: 10px, lg: 14px, xl: 20px, pill: 9999px)
- Check individual corner radii (border-bottom-left-radius, etc.)

#### 2.5 Shadows
- box-shadow: offset-x, offset-y, blur, spread, color, opacity
- text-shadow (if any)
- Drop shadows on images/icons

#### 2.6 Icons & Images
- Icon type: SVG inline, emoji, image, icon font
- **Miniprogram rule:** NEVER use inline `<svg>` tags or text emoji (🔍/✕/✓/⏳/❌/🚫/etc.) for UI icons. Inline SVG is not supported by WXML; text emoji renders inconsistently across devices. ALWAYS create a standalone `.svg` file under `miniprogram/images/` and reference it via `<image src="/images/xxx.svg" mode="aspectFit">`. See the login page's `login-icon.svg` as the reference pattern.
- **⚠️ currentColor tracing (CRITICAL):** Prototype SVGs often use `stroke="currentColor"` or `fill="currentColor"`. Since `<image>` tags cannot inherit CSS `color`, you MUST resolve `currentColor` by tracing the CSS inheritance chain: start from the SVG's parent element, walk up the tree checking for a `color` property, and use the first one found. If no ancestor sets `color`, use the `body`/`page` text color (typically `#1d1d1f` or `var(--text)`). Embed the resolved color directly in the standalone SVG file. DO NOT guess based on the icon's background circle color — the icon color is determined by `currentColor` inheritance, not by the background.
- Icon size (width × height) — set explicit `width`/`height` in WXSS
- Icon color — embed the correct `stroke` or `fill` color directly in the SVG file (since `<image>` cannot recolor SVGs)
- Icon placement relative to text
- Image dimensions, object-fit, border-radius

#### 2.7 Animations & Transitions
- `transition`: property, duration, timing function
- `animation`: name, duration, timing function, fill mode
- `@keyframes`: exact transform/opacity values
- `:active` states: scale, opacity changes
- `transform` values (especially scale factors like 0.97, 0.98)

#### 2.8 Interactive States
- Default appearance
- `:active` / tap feedback (miniprogram: use `hover-class` attribute)
- `hover` (desktop/web only)
- `disabled` appearance
- `focus` ring (web only)
- Loading/spinner state
- Empty state
- Error state

### Phase 3: Fix — Generate Precise Diffs

For each mismatch found, produce an exact edit using the Edit tool:

1. **Map design tokens.** Look up the prototype's CSS variable name in the project's token mapping table (provided by the calling agent). Use the target's corresponding variable. If no mapping exists, use the prototype's literal value.
2. **Convert px to rpx** for miniprogram WXSS (multiply by 2). Do NOT convert for Vue/web targets.
3. **Preserve prototype exact values.** Don't round, don't "improve", don't adjust for "consistency" unless the prototype itself is inconsistent.
4. **Apply one fix per edit.** Each mismatch gets its own Edit call so changes are traceable.

### Phase 4: Verify — Checklist

After all edits:
- [ ] Run a grep for hardcoded color values that should be CSS variables
- [ ] Verify all `px` values in miniprogram WXSS are correctly doubled from prototype
- [ ] Check that no design token variable names are mismatched
- [ ] Re-read the edited file to confirm all changes applied correctly

## Platform-Specific Rules

### Miniprogram (WXML + WXSS)

**What miniprogram CAN'T do (don't try to replicate):**
- `backdrop-filter: blur()` — WXSS does not support this. Accept the opacity-only fallback (`rgba(255,255,255,0.82)`).
- `::before` / `::after` pseudo-elements — only supported on `<view>` and `<text>` with severe limitations; test cautiously.
- CSS `@keyframes` in component WXSS — define them in `app.wxss` and reference by name.
- `position: sticky` with complex interactions — test carefully on real devices.
- `gap` in flexbox — supported in recent base libraries but verify on older WeChat versions.
- SVG inline in WXML — not supported. Use `<image src="/images/xxx.svg" mode="aspectFit">`. Do NOT use text emoji (🔍/✕/✓) as icon replacements — they render inconsistently across iOS/Android devices. Always create standalone SVG icon files.
- `calc()` mixing `vh`/`vw` with `rpx` — avoid this combination as it can cause silent failures. Use pure `rpx` or pure `vh` instead.

**WXML text constraints:**
- WXML does NOT support escape sequences. `\n` is treated as literal backslash-n characters, not a newline. For line breaks in text, use separate `<view>` or `<text>` elements.
- Inline styles in WXML are supported but should be used sparingly. Prefer CSS classes in WXSS.
- `<text>` elements inside `<view>` do NOT inherit all CSS properties consistently — prefer applying text styles directly to the `<text>` element.

**wx:if chain completeness:**
- When using `wx:if`/`wx:elif` chains to show different states (pending/rejected/banned, or loaded/empty/error), ALWAYS include a `wx:else` fallback. Without it, unexpected data values render nothing → blank page.
- This also applies to data-driven rendering: if the prototype JS has a default fallback (`getParam('state') || 'pending'`), replicate the same default in the miniprogram's data initialization AND in the wx:if chain.

**WXML equivalents for HTML elements:**
```
<div>       → <view>
<span>      → <text>
<img>       → <image>
<input>     → <input>
<button>    → <button>   (has default ::after border, must reset)
<a href>    → <navigator url>
<p>         → <view> or <text>
<ul>/<li>   → <view wx:for>
```

**Button reset in WXSS (REQUIRED for every `<button>` element):**
```css
button::after { border: none; }
```

### Vue SFC (Web/PC)

- Use `<style scoped>` for page-specific styles
- Import or reference global CSS variables — they should be available in all components
- UI framework components (e.g., Element Plus) may need `:deep()` overrides for their internals
- `v-html` for dynamic content; `v-for` for lists
- `<Transition>` component for enter/leave animations matching prototype keyframes
- Font sizes and spacing use `px` directly (no rpx conversion)
- Focus ring: replicate the prototype's exact focus style (often a colored box-shadow ring)

## Common Mistakes

| Mistake | Why it happens | Fix |
|---------|---------------|-----|
| Hardcoding colors instead of CSS variables | Page styles were written before design tokens existed | Replace hardcoded values with `var(--*)` |
| Wrong blue value | Different design systems use different blues | Match prototype's exact hex value |
| Wrong button border-radius | Default styles vs prototype's design | Match prototype exactly |
| Missing `letter-spacing` | Easy to overlook | Check every heading, title, and button |
| Wrong shadow opacity | Hard to spot visually but changes the feel | Match rgba values exactly |
| Icon mismatch | Prototype uses one icon system, target uses another | Match appearance as closely as platform allows; document the difference |
| `backdrop-filter` in WXSS | Not supported in WeChat miniprogram | Use `rgba(255,255,255,0.82)` background without blur |
| Wrong font weight | Prototype uses non-standard weights (450, 550, 650) | Match exactly; `font-weight: 500` ≠ `font-weight: 600` ≠ `font-weight: 550` |
| Missing `:active` state | Only default state was checked | Check and replicate all interactive states |
| px not converted to rpx | Used prototype px values directly in WXSS | Multiply by 2: 16px → 32rpx |
| Forgetting `button::after` reset | Miniprogram `<button>` has a built-in `::after` border | Always add `button::after { border: none; }` |
| Rounding prototype values | Thinking "close enough" is fine | Preserve exact values; 0.98 ≠ 0.95 for scale transforms |
| Using text emoji as UI icons | Quick shortcuts like 🔍 ✕ ✓ instead of proper icon files | Create SVG files under `images/`, reference via `<image>`. Text emoji render differently on iOS vs Android. |
| Using inline SVG tags in WXML | Prototype uses SVG markup directly | Miniprogram WXML doesn't support `<svg>`. Export as standalone `.svg` file, use `<image>` tag. |
| **Writing styles without reading prototype** | Jumping to code before Capture→Compare | Must read prototype HTML+CSS first, build DOM tree, compare every element before writing |
| **Skipping currentColor resolution** | Prototype SVG uses `stroke="currentColor"` | Trace CSS inheritance chain to find actual `color` value, embed in standalone SVG |
| **WXML `\n` in text nodes** | Assuming WXML supports escape sequences like HTML | Use `<view>` or `<text>` tags for line breaks; never use `\n` |
| **Missing `wx:else` fallback** | Only handling expected states | Always include `wx:else` in `wx:if`/`wx:elif` chains to prevent blank pages |
| **Comparing sections, not elements** | "Card area looks right" vs checking each child | Walk the DOM tree node by node; compare every element's computed styles |
| **Wrong element gets the padding** | Prototype puts padding on `.card`, implementation puts it on `.panel` | Use the DOM tree to identify the EXACT element that has each property |
| **`calc(100vh - 88rpx)` mixed units** | Trying to offset nav height | Avoid mixing `vh`/`vw` with `rpx`; use `100vh` alone or use `box-sizing: border-box` |

## Report Format

After completing alignment, report in a table:

```
| Element | Dimension | Prototype | Before | After | Status |
|---------|-----------|-----------|--------|-------|--------|
| .btn    | width     | 240px     | 260px  | 240px | ✓      |
| .btn    | radius    | 25px      | 12px   | 50rpx | ✓      |
```

Mark intentional deviations due to platform limitations with `(平台限制)`.
