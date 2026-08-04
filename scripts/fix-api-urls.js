/**
 * 后端 URL 单数→复数：批量替换 miniprogram 中的 API URL 引用。
 * 用法：node scripts/fix-api-urls.js [--dry-run]
 */
const fs = require('fs');
const path = require('path');

const DRY_RUN = process.argv.includes('--dry-run');

// 替换规则（顺序重要：精确匹配先于前缀匹配）
const RULES = [
  // POST 无尾斜杠的精确端点
  ["'/api/help'",  "'/api/help-requests'"],
  ["'/api/idle'",  "'/api/idle-items'"],
  ["'/api/borrow'","'/api/borrow-requests'"],
  ["'/api/rating'","'/api/ratings'"],
  // 带尾斜杠的前缀（最常用）
  ["/api/chat/",        "/api/chats/"],
  ["/api/help/",        "/api/help-requests/"],
  ["/api/user/",        "/api/users/"],
  ["/api/notification/", "/api/notifications/"],
  ["/api/idle/",        "/api/idle-items/"],
  ["/api/borrow/",      "/api/borrow-requests/"],
];

function processFile(filePath) {
  const original = fs.readFileSync(filePath, 'utf-8');
  let modified = original;
  let changes = 0;

  for (const [from, to] of RULES) {
    const before = modified;
    // 全局替换字符串（非正则）
    modified = modified.split(from).join(to);
    if (before !== modified) {
      const count = before.split(from).length - 1;
      changes += count;
    }
  }

  if (changes === 0) return { filePath, changes: 0 };

  if (!DRY_RUN) {
    fs.writeFileSync(filePath, modified, 'utf-8');
  }
  return { filePath, changes };
}

function collectJsFiles(dir) {
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== 'node_modules') {
      files.push(...collectJsFiles(full));
    } else if (entry.isFile() && entry.name.endsWith('.js')) {
      files.push(full);
    }
  }
  return files;
}

const miniprogramDir = path.resolve(__dirname, '..', 'miniprogram');
const jsFiles = collectJsFiles(miniprogramDir);

let totalChanges = 0;
for (const file of jsFiles) {
  const { changes } = processFile(file);
  if (changes > 0) {
    console.log(`${path.relative(miniprogramDir, file)}: ${changes} 处`);
    totalChanges += changes;
  }
}

console.log(`\n总计: ${totalChanges} 处替换`);
if (DRY_RUN) console.log('(dry-run 模式，未写入)');
