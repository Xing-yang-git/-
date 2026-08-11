/**
 * 轻量 Markdown 子集解析器 — 把「小邻」回复解析为块结构，供 WXML 逐块排版渲染（无气泡全宽正式排版）。
 *
 * <p>支持语法（与 system.md 中约定给模型的回复排版一致）：
 * <ul>
 *   <li>{@code ## } / {@code ### } 小标题；</li>
 *   <li>{@code **加粗**} 行内加粗（一段内可多处）；</li>
 *   <li>{@code - } / {@code * } 无序列表项、{@code 1. } 有序列表项（连续项自动编号）；</li>
 *   <li>空行分段；无标记的普通行按段落处理。</li>
 * </ul>
 * 不支持表格/代码块/链接等复杂语法（小邻回复不需要）。</p>
 *
 * <p>输出块结构：[{type:'h2'|'h3'|'p'|'li', ordered?:boolean, num?:number, parts:[{text, bold}]}]。
 * 非 Markdown 的纯文本（如拦截文案）也按段落解析，保证统一渲染。</p>
 */

/** 行内加粗标记（非贪婪匹配中间内容） */
const BOLD_PATTERN = /\*\*(.+?)\*\*/g;

/**
 * 解析 Markdown 文本为块数组。
 *
 * @param {string} text 待解析文本（可为 null/空）
 * @return {Array<{type: string, ordered?: boolean, num?: number, parts: Array<{text: string, bold: boolean}>}>} 块数组
 */
function parseMarkdown(text) {
  if (!text) return [];
  const blocks = [];
  const state = { orderedCounter: 0, hadBlank: false };   // 跨行解析状态：列表编号、空行分隔
  for (const raw of String(text).replace(/\r\n/g, '\n').split('\n')) {
    const line = raw.trim();
    if (!line) { state.hadBlank = true; continue; }   // 空行分段
    parseLineInto(blocks, line, state);
  }
  return blocks;
}

/**
 * 解析单行 Markdown 并写入 blocks（主循环薄分派，状态由 state 跨行维护）。
 * 行分类优先级：标题 → 无序列表 → 有序列表 → 列表项补充描述 → 普通段落。
 *
 * @param blocks 已解析的块数组（就地追加）
 * @param line   当前行（已 trim）
 * @param state  {orderedCounter, hadBlank} 跨行状态
 */
function parseLineInto(blocks, line, state) {
  // 标题（## / ###）
  const h = line.match(/^(#{2,3})\s+(.*)$/);
  if (h) {
    state.orderedCounter = 0;
    blocks.push({ type: h[1].length === 2 ? 'h2' : 'h3', parts: parseInline(h[2]) });
    state.hadBlank = false;
    return;
  }
  // 无序列表项（- 或 * 或 •）
  const ul = line.match(/^[-*•]\s+(.*)$/);
  if (ul) {
    state.orderedCounter = 0;
    blocks.push(buildLi(false, parseInline(ul[1])));
    state.hadBlank = false;
    return;
  }
  // 有序列表项（1. / 1、 / 1)），连续项自动编号
  const ol = line.match(/^\d+[.、)]\s+(.*)$/);
  if (ol) {
    const prev = blocks[blocks.length - 1];
    state.orderedCounter = prev?.type === 'li' && prev?.ordered ? state.orderedCounter + 1 : 1;
    blocks.push(buildLi(true, parseInline(ol[1]), state.orderedCounter));
    state.hadBlank = false;
    return;
  }
  // 普通行：无空行紧跟列表项 → 合并为该列表项补充描述（覆盖模型「1. 标题\n说明」非规范 Markdown，
  // 合并后编号保持连续、说明缩进在列表项下）；空行分隔的普通行 → 独立段落（新语义段）
  const prev = blocks[blocks.length - 1];
  if (prev?.type === 'li' && !state.hadBlank) {
    appendLiDesc(prev, line);
    state.hadBlank = false;
    return;
  }
  // 普通段落
  state.orderedCounter = 0;
  blocks.push({ type: 'p', parts: parseInline(line) });
  state.hadBlank = false;
}

/**
 * 把紧跟列表项（无空行分隔）的普通行合并为该列表项的补充描述，按 \n 换行显示。
 * 模型输出「1. 标题\n说明」时说明行会被解析为独立段落、打断有序编号——合并后编号连续、排版为标题下缩进说明。
 *
 * @param li   上一个列表项块（就地追加）
 * @param line 待合并的普通行
 */
function appendLiDesc(li, line) {
  if (li.descParts) {
    // 已有 descParts（**标题**：正文 或已合并过）：追加一段说明
    li.descParts.push({ text: '\n' + line, bold: false });
  } else {
    // 无 descParts：把原 parts 作为首段正文，后续行追加（渲染走 descParts 分行结构）
    li.descParts = [...(li.parts || [])];
    delete li.parts;
    li.descParts.push({ text: '\n' + line, bold: false });
  }
}

/**
 * 构建列表项块：识别「**标题**：正文」结构时输出 title/descParts（供渲染层标题与正文分行），
 * 否则输出普通 parts（保持单行）。
 *
 * @param {boolean} ordered 是否有序列表
 * @param {Array<{text: string, bold: boolean}>} parts 行内解析片段
 * @param {number} [num] 有序编号（仅在 ordered 时传入）
 * @return {Object} 列表项块
 */
function buildLi(ordered, parts, num) {
  const split = splitLiTitle(parts);
  const base = { type: 'li', ordered, ...(num !== undefined ? { num } : {}) };
  if (split) {
    return { ...base, title: split.title, descParts: split.descParts };
  }
  return { ...base, parts };
}

/**
 * 识别列表项内的「**标题**：正文」结构：首片段加粗且其后首非加粗片段以冒号开头，
 * 拆为标题字符串与正文片段数组（正文去掉冒号前缀）。
 *
 * <p>如 {@code - **地址**：翠湖花园18号楼一层} 拆为 title=「地址」、descParts=「翠湖花园…」，
 * 渲染层据此标题独立一行、正文缩进下一行，避免长正文在单行 flex 中换行混乱。</p>
 *
 * @param {Array<{text: string, bold: boolean}>} parts 行内解析片段
 * @return {{title: string, descParts: Array}|null} 拆分结果；非该结构返回 null
 */
function splitLiTitle(parts) {
  if (parts.length < 2 || !parts[0].bold) {
    return null;
  }
  const second = parts[1].text;
  if (typeof second !== 'string' || !/^[：:]\s*/.test(second)) {
    return null;
  }
  const descParts = [{ text: second.replace(/^[：:]\s*/, ''), bold: false }];
  for (let i = 2; i < parts.length; i++) {
    descParts.push(parts[i]);
  }
  return { title: parts[0].text, descParts };
}

/**
 * 解析行内 **加粗**，拆成有序片段数组。
 *
 * @param {string} text 行内文本
 * @return {Array<{text: string, bold: boolean}>} 片段数组（未闭合的 ** 按普通文本保留）
 */
function parseInline(text) {
  const parts = [];
  let last = 0;
  let m;
  BOLD_PATTERN.lastIndex = 0;
  while ((m = BOLD_PATTERN.exec(text)) !== null) {
    if (m.index > last) {
      parts.push({ text: text.slice(last, m.index), bold: false });
    }
    parts.push({ text: m[1], bold: true });
    last = m.index + m[0].length;
  }
  if (last < text.length) {
    parts.push({ text: text.slice(last), bold: false });
  }
  if (parts.length === 0) {
    parts.push({ text, bold: false });
  }
  return parts;
}

/**
 * 把 Markdown 文本还原为纯文本（去掉标题/加粗/列表标记），供复制等场景使用。
 *
 * @param {string} text 原始 Markdown 文本
 * @return {string} 去标记后的纯文本
 */
function stripMarkdown(text) {
  if (!text) return '';
  return String(text)
    .replace(/^(#{2,3})\s+/gm, '')
    .replace(/^[-*•]\s+/gm, '')
    .replace(/^\d+[.、)]\s+/gm, '')
    .replace(/\*\*(.+?)\*\*/g, '$1');
}

module.exports = { parseMarkdown, stripMarkdown };
