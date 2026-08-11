const { parseMarkdown, stripMarkdown } = require('../markdown');

describe('markdown 轻量解析器', () => {
  test('空/null 输入返回空数组', () => {
    expect(parseMarkdown('')).toEqual([]);
    expect(parseMarkdown(null)).toEqual([]);
    expect(parseMarkdown('   ')).toEqual([]);
  });

  test('标题：## 与 ### 解析为 h2/h3', () => {
    const blocks = parseMarkdown('## 小区介绍\n\n### 设施清单');
    expect(blocks).toHaveLength(2);
    expect(blocks[0].type).toBe('h2');
    expect(blocks[0].parts).toEqual([{ text: '小区介绍', bold: false }]);
    expect(blocks[1].type).toBe('h3');
  });

  test('行内加粗：**文字** 拆为 bold 片段', () => {
    const blocks = parseMarkdown('请记住**物业电话**是 12345');
    expect(blocks[0].type).toBe('p');
    expect(blocks[0].parts).toEqual([
      { text: '请记住', bold: false },
      { text: '物业电话', bold: true },
      { text: '是 12345', bold: false },
    ]);
  });

  test('无序列表：- 与 * 与 • 均解析为 li(ordered=false)', () => {
    const blocks = parseMarkdown('- 工具\n* 家居\n• 书籍');
    expect(blocks).toHaveLength(3);
    for (const b of blocks) {
      expect(b.type).toBe('li');
      expect(b.ordered).toBe(false);
    }
  });

  test('列表项「**标题**：正文」拆为 title/descParts（标题与正文分行）；非标题结构保持 parts', () => {
    const blocks = parseMarkdown(
      '- **地址**：翠湖花园18号楼一层（西门入口北侧）\n' +
      '- 普通列表项\n' +
      '- 平台没有统一规定，**由物主自行约定**'
    );
    // 标题+正文分行：标题去加粗存 title，正文去冒号前缀存 descParts
    expect(blocks[0]).toMatchObject({ type: 'li', ordered: false, title: '地址' });
    expect(blocks[0].descParts).toEqual([{ text: '翠湖花园18号楼一层（西门入口北侧）', bold: false }]);
    // 无加粗标题的普通项保持 parts
    expect(blocks[1].title).toBeUndefined();
    expect(blocks[1].parts).toEqual([{ text: '普通列表项', bold: false }]);
    // 加粗在中间（非开头标题）不分行
    expect(blocks[2].title).toBeUndefined();
    expect(blocks[2].parts).toEqual([
      { text: '平台没有统一规定，', bold: false },
      { text: '由物主自行约定', bold: true },
    ]);
  });

  test('有序列表「**标题**：正文」同样分行并保留编号', () => {
    const blocks = parseMarkdown('1. **客服电话**：400-168-6688');
    expect(blocks[0]).toMatchObject({ type: 'li', ordered: true, num: 1, title: '客服电话' });
    expect(blocks[0].descParts).toEqual([{ text: '400-168-6688', bold: false }]);
  });

  test('有序列表：连续项自动编号，换段后重置', () => {
    const blocks = parseMarkdown('1. 第一步\n2. 第二步\n\n普通段\n1. 重新开始');
    expect(blocks[0]).toMatchObject({ type: 'li', ordered: true, num: 1 });
    expect(blocks[1]).toMatchObject({ type: 'li', ordered: true, num: 2 });
    expect(blocks[2].type).toBe('p');
    expect(blocks[3]).toMatchObject({ type: 'li', ordered: true, num: 1 });
  });

  test('列表项后无空行的普通行合并为补充描述，编号保持连续', () => {
    // 模型常见「1. 标题\n说明」非规范 Markdown：说明行并入列表项，避免打断有序编号
    const blocks = parseMarkdown('1. 第一步\n第一步说明\n2. 第二步\n第二步说明');
    expect(blocks).toHaveLength(2);
    expect(blocks[0]).toMatchObject({ type: 'li', ordered: true, num: 1 });
    expect(blocks[0].descParts).toEqual([
      { text: '第一步', bold: false },
      { text: '\n第一步说明', bold: false },
    ]);
    expect(blocks[1]).toMatchObject({ type: 'li', ordered: true, num: 2 });
    expect(blocks[1].descParts).toEqual([
      { text: '第二步', bold: false },
      { text: '\n第二步说明', bold: false },
    ]);
  });

  test('空行分隔的普通行不并入列表项（独立段落）', () => {
    const blocks = parseMarkdown('1. 第一步\n\n独立说明');
    expect(blocks).toHaveLength(2);
    expect(blocks[0].type).toBe('li');
    expect(blocks[1].type).toBe('p');
  });

  test('普通段落与空行分段', () => {
    const blocks = parseMarkdown('第一段\n\n第二段');
    expect(blocks).toHaveLength(2);
    expect(blocks[0].type).toBe('p');
    expect(blocks[1].type).toBe('p');
  });

  test('未闭合的加粗标记按普通文本保留', () => {
    const blocks = parseMarkdown('这是一个**没闭合的加粗');
    expect(blocks[0].parts).toEqual([{ text: '这是一个**没闭合的加粗', bold: false }]);
  });

  test('纯文本（无任何标记）按段落解析', () => {
    const blocks = parseMarkdown('你好，有什么可以帮你？');
    expect(blocks).toHaveLength(1);
    expect(blocks[0].type).toBe('p');
  });
});

describe('stripMarkdown 去标记', () => {
  test('去掉标题/列表/加粗标记', () => {
    const text = '## 标题\n**加粗**重点\n- 列表项\n1. 有序项';
    expect(stripMarkdown(text)).toBe('标题\n加粗重点\n列表项\n有序项');
  });

  test('null/空返回空串', () => {
    expect(stripMarkdown(null)).toBe('');
    expect(stripMarkdown('')).toBe('');
  });
});
