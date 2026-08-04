/**
 * C端 WXSS 代码规范批量修复脚本
 *
 * 修复两项：
 *   1. px → rpx（值 ×2，排除 token 定义区的 px 和 hairline border）
 *   2. 硬编码颜色 → var(--*)（基于已有设计 token 映射）
 *
 * 用法：node scripts/fix-wxss-standards.js [--dry-run]
 *   --dry-run  仅预览改动，不写入文件
 */

const fs = require('fs');
const path = require('path');

const DRY_RUN = process.argv.includes('--dry-run');

// ============================================================
// 颜色 → CSS 变量映射表
// ============================================================
const COLOR_MAP = new Map([
  // 白/黑/灰 → surface/text/separator 体系
  ['#FFFFFF', 'var(--surface)'],
  ['#ffffff', 'var(--surface)'],
  ['#FFF',    'var(--surface)'],
  ['#fff',    'var(--surface)'],
  ['#FFF;',   'var(--surface)'],
  ['#fff;',   'var(--surface)'],

  ['#1D1D1F', 'var(--text)'],
  ['#1d1d1f', 'var(--text)'],

  ['#6E6E73', 'var(--text-secondary)'],
  ['#6e6e73', 'var(--text-secondary)'],

  ['#86868B', 'var(--text-tertiary)'],
  ['#86868b', 'var(--text-tertiary)'],
  ['#8E8E93', 'var(--text-tertiary)'],
  ['#8e8e93', 'var(--text-tertiary)'],

  ['#D2D2D7', 'var(--separator)'],
  ['#d2d2d7', 'var(--separator)'],

  ['#E8E8ED', 'var(--separator-opaque)'],
  ['#e8e8ed', 'var(--separator-opaque)'],
  ['#E5E5EA', 'var(--separator-opaque)'],
  ['#e5e5ea', 'var(--separator-opaque)'],

  // 品牌色
  ['#0071E3', 'var(--blue)'],
  ['#0071e3', 'var(--blue)'],
  ['#0077ED', 'var(--blue-hover)'],
  ['#0077ed', 'var(--blue-hover)'],

  ['#FF3B30', 'var(--red)'],
  ['#ff3b30', 'var(--red)'],

  ['#FF9500', 'var(--orange)'],
  ['#ff9500', 'var(--orange)'],

  ['#34C759', 'var(--green)'],
  ['#34c759', 'var(--green)'],

  ['#5AC8FA', 'var(--teal)'],
  ['#5ac8fa', 'var(--teal)'],

  ['#5856D6', 'var(--indigo)'],
  ['#5856d6', 'var(--indigo)'],

  // 背景/填充
  ['#F2F2F7', 'var(--fill-tertiary)'],
  ['#f2f2f7', 'var(--fill-tertiary)'],
  ['#F5F5F7', 'var(--bg)'],
  ['#f5f5f7', 'var(--bg)'],

  // 需要新增 token 的颜色（脚本会自动在 app.wxss 的 page {} 块中添加）
  ['#1C1C1E', 'var(--dark-surface)'],
  ['#1c1c1e', 'var(--dark-surface)'],
  ['#AEAEB2', 'var(--text-quaternary-solid)'],
  ['#aeaeb2', 'var(--text-quaternary-solid)'],
  ['#C0392B', 'var(--red)'],
  ['#c0392b', 'var(--red)'],

  // 状态背景色（token 由脚本自动添加到 app.wxss）
  ['#E8F8E8', 'var(--green-bg)'],
  ['#e8f8e8', 'var(--green-bg)'],
  ['#FFF8E8', 'var(--orange-bg)'],
  ['#fff8e8', 'var(--orange-bg)'],
  ['#FFE8E8', 'var(--red-bg)'],
  ['#ffe8e8', 'var(--red-bg)'],
]);

// ============================================================
// 需要新增到 app.wxss 的 token（如果映射中的目标 var 尚不存在）
// ============================================================
const NEW_TOKENS = [
  '  --dark-surface: #1C1C1E;           /* 深色表面（图片轮播背景） */',
  '  --text-quaternary-solid: #AEAEB2;  /* 第四级文字色（实色版） */',
  '  --green-bg: #E8F8E8;              /* 绿色浅底（状态标签） */',
  '  --orange-bg: #FFF8E8;             /* 橙色浅底（状态标签） */',
  '  --red-bg: #FFE8E8;                /* 红色浅底（状态标签） */',
];

// ============================================================
// px → rpx：不需要转换的例外列表
// ============================================================
const PX_SKIP_PATTERNS = [
  /^[\s]*--/,                  // CSS 变量定义行（--xxx: 12px）
  /0\.5px/,                    // hairline
  /\/\*.*px/,                  // 注释中的 px
  /calc\(/,                    // calc() 表达式
];

function shouldSkipPx(line, pxValue) {
  for (const pat of PX_SKIP_PATTERNS) {
    if (pat.test(line)) return true;
  }
  // border 1px 保留为 hairline
  if (pxValue === 1 && /\bborder\b/.test(line)) return true;
  // 0px 保留
  if (pxValue === 0) return true;
  return false;
}

// ============================================================
// 处理单个文件
// ============================================================
function processFile(filePath) {
  const original = fs.readFileSync(filePath, 'utf-8');
  const lines = original.split('\n');
  const changes = [];
  const newLines = lines.map((line, idx) => {
    let modified = line;
    const lineNum = idx + 1;

    // --- Step 1: px → rpx ---
    modified = modified.replace(/\b(\d+)px\b/g, (match, numStr) => {
      const pxValue = parseInt(numStr, 10);
      if (shouldSkipPx(line, pxValue)) return match;
      const rpxValue = pxValue * 2;
      changes.push({ line: lineNum, type: 'px→rpx', from: match, to: `${rpxValue}rpx` });
      return `${rpxValue}rpx`;
    });

    // --- Step 2: 颜色 → var(--*) ---
    // 跳过 CSS 变量定义行（--xxx: value），防止 token 值被替换成自身引用
    if (!/^\s*--/.test(line)) {
      COLOR_MAP.forEach((varName, hex) => {
        if (modified.includes(hex)) {
          // 精确匹配：hex 作为独立 token 出现（前后是非字母数字字符）
          const escaped = hex.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
          const regex = new RegExp(`(?<![a-zA-Z0-9#-])${escaped}(?![a-zA-Z0-9])`, 'g');
          const newModified = modified.replace(regex, varName);
          if (newModified !== modified) {
            changes.push({ line: lineNum, type: 'color', from: hex, to: varName });
            modified = newModified;
          }
        }
      });
    }

    return modified;
  });

  const result = newLines.join('\n');

  return { original, result, changes, filePath };
}

// ============================================================
// 确保 app.wxss 包含所需的新 token
// ============================================================
function ensureTokensInAppWxss(appWxssPath) {
  let content = fs.readFileSync(appWxssPath, 'utf-8');

  const missingTokens = NEW_TOKENS.filter(tokenDef => {
    const varName = tokenDef.match(/--[\w-]+/)[0];
    return !content.includes(varName + ':');
  });

  if (missingTokens.length === 0) return;

  // 在 --indigo 行之后插入新 token
  const insertAfter = content.indexOf('--indigo:');
  const endOfLine = content.indexOf('\n', insertAfter);
  const insertPos = endOfLine + 1;

  const newTokenBlock = missingTokens.join('\n') + '\n';
  content = content.slice(0, insertPos) + newTokenBlock + content.slice(insertPos);

  if (!DRY_RUN) {
    fs.writeFileSync(appWxssPath, content, 'utf-8');
    console.log(`  [token] app.wxss +${missingTokens.length} 个新 token`);
  } else {
    console.log(`  [token] app.wxss 将添加 ${missingTokens.length} 个新 token (dry-run)`);
  }
}

// ============================================================
// 主流程
// ============================================================
function main() {
  const miniprogramDir = path.resolve(__dirname, '..', 'miniprogram');
  const appWxssPath = path.join(miniprogramDir, 'app.wxss');

  // 收集所有 WXSS 文件（排除 node_modules）
  function collectWxssFiles(dir) {
    const files = [];
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory() && entry.name !== 'node_modules') {
        files.push(...collectWxssFiles(fullPath));
      } else if (entry.isFile() && entry.name.endsWith('.wxss')) {
        files.push(fullPath);
      }
    }
    return files;
  }

  const wxssFiles = collectWxssFiles(miniprogramDir);
  console.log(`找到 ${wxssFiles.length} 个 WXSS 文件\n`);

  // 先确保 app.wxss 有所需token（在处理颜色映射之前）
  ensureTokensInAppWxss(appWxssPath);

  // 处理 app.wxss（特殊处理：跳过 token 定义区）
  console.log('=== app.wxss (全局样式，跳过 token 定义区) ===');
  const appResult = processFile(appWxssPath);
  printChanges(appResult);

  // 处理其余文件
  let totalChanges = appResult.changes.length;
  const otherFiles = wxssFiles.filter(f => f !== appWxssPath);

  for (const file of otherFiles) {
    const relPath = path.relative(miniprogramDir, file);
    console.log(`\n=== ${relPath} ===`);
    const result = processFile(file);
    printChanges(result);
    totalChanges += result.changes.length;
  }

  console.log(`\n========================================`);
  console.log(`总计: ${totalChanges} 处改动`);
  if (DRY_RUN) {
    console.log('(dry-run 模式，未写入任何文件。去掉 --dry-run 参数执行实际修复。)');
  } else {
    console.log('所有文件已写入。');
  }
}

function printChanges(result) {
  const { changes, filePath, original, result: modified } = result;

  if (changes.length === 0) {
    console.log('  (无改动)');
    return;
  }

  const pxChanges = changes.filter(c => c.type === 'px→rpx');
  const colorChanges = changes.filter(c => c.type === 'color');

  if (pxChanges.length > 0) {
    console.log(`  px→rpx: ${pxChanges.length} 处`);
    // 只展示前 5 个样本
    pxChanges.slice(0, 5).forEach(c => {
      console.log(`    L${c.line}: ${c.from} → ${c.to}`);
    });
    if (pxChanges.length > 5) console.log(`    ... 还有 ${pxChanges.length - 5} 处`);
  }

  if (colorChanges.length > 0) {
    console.log(`  颜色→var: ${colorChanges.length} 处`);
    colorChanges.slice(0, 5).forEach(c => {
      console.log(`    L${c.line}: ${c.from} → ${c.to}`);
    });
    if (colorChanges.length > 5) console.log(`    ... 还有 ${colorChanges.length - 5} 处`);
  }

  // 写入文件
  if (!DRY_RUN) {
    fs.writeFileSync(filePath, modified, 'utf-8');
  }
}

main();
