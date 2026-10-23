const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const source = fs.readFileSync(path.join(__dirname, 'hwsuanfa-luogu-sync.user.js'), 'utf8');
const context = vm.createContext({ document: { body: null }, window: { addEventListener() {} } });
vm.runInContext(source.replace(/\}\)\(\);\s*$/, `
  globalThis.metadataParser = { normalizeTags, collectTagNames, readLuoguProblemMeta, parseRecordPage, formatDate };
})();`), context);
const parser = context.metadataParser;

function page(problem, extra = {}, format = 'lentille') {
  const value = { currentData: { problem }, ...extra };
  if (format === 'fe') {
    return 'window._feInjection = JSON.parse(decodeURIComponent("' + encodeURIComponent(JSON.stringify(value)) + '"))';
  }
  return '<script id="lentille-context">' + JSON.stringify(value) + '</script>';
}

function plain(value) {
  return JSON.parse(JSON.stringify(value));
}

test('数字标签编号转换成名称并去重', () => {
  const names = parser.collectTagNames([{ id: 1, name: '模拟' }, { id: 2, name: '贪心' }]);
  const result = parser.readLuoguProblemMeta(page({ pid: 'P1001', title: 'A+B Problem', difficulty: 1, tags: [1, 2, 1] }), 'P1001', names);
  assert.equal(result.tags, '模拟,贪心');
  assert.equal(result.difficulty, '入门');
});

test('支持编号字典和嵌套标签列表', () => {
  const names = parser.collectTagNames({ '1': '模拟', groups: [{ tags: [{ id: 2, name: 'DP' }] }] });
  assert.equal(names.get('1'), '模拟');
  assert.equal(names.get('2'), 'DP');
});

test('支持页面内的标签表及具名标签', () => {
  const result = parser.readLuoguProblemMeta(page({ pid: 'P1002', difficulty: 3, tags: [1, { id: 2, name: '动态规划' }, '贪心'] },
    { tags: [{ id: 1, name: '数学' }] }), 'P1002');
  assert.equal(result.tags, '数学,动态规划,贪心');
  assert.equal(result.difficulty, '普及/提高−');
});

test('兼容旧版编码页面数据', () => {
  const result = parser.readLuoguProblemMeta(page({ pid: 'P1001', title: '中文题名', difficulty: 5, tags: ['图论'] }, {}, 'fe'), 'P1001');
  assert.equal(result.title, '中文题名');
  assert.equal(result.difficulty, '提高+/省选−');
  assert.equal(result.tags, '图论');
});

test('缺少难度不会伪造暂无评定', () => {
  const result = parser.readLuoguProblemMeta(page({ pid: 'P1001', tags: ['模拟'] }), 'P1001');
  assert.equal(result.difficulty, '');
});

test('验证页和错误题号明确失败', () => {
  assert.throws(() => parser.readLuoguProblemMeta('<html>验证页面</html>', 'P1001'));
  assert.throws(() => parser.readLuoguProblemMeta(page({ pid: 'P1002', tags: ['数学'] }), 'P1001'));
});

test('无法解析的标签编号不会静默上传空标签或部分标签', () => {
  assert.throws(() => parser.readLuoguProblemMeta(page({ pid: 'P1001', tags: [999] }), 'P1001'));
  assert.throws(() => parser.readLuoguProblemMeta(page({ pid: 'P1001', tags: ['模拟', 999] }), 'P1001'));
});

test('上传标签在字段限制内且保留完整标签', () => {
  const tags = Array.from({ length: 40 }, (_, index) => '算法标签测试名称' + index);
  const result = plain(parser.readLuoguProblemMeta(page({ pid: 'P1001', tags }), 'P1001'));
  assert(result.tags.length <= 255);
  assert(result.tags.split(',').every((tag) => tags.includes(tag)));
});

test('记录结构缺失不能误报零条同步成功', () => {
  assert.throws(() => parser.parseRecordPage(page({ pid: 'P1001' }), '123', 1));
  const empty = page(null, { currentData: { records: { count: 0, perPage: 20, result: [] } } });
  assert.equal(parser.parseRecordPage(empty, '123', 1).count, 0);
});

test('洛谷时间戳固定转换为上海时间', () => {
  assert.equal(parser.formatDate(new Date('2026-10-02T16:30:00Z')), '2026-10-03 00:30:00');
});
