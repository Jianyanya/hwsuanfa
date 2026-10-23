const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');

const filename = path.resolve(__dirname, '../src/lib/date.ts');
const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const loaded = new Module(filename, module);
loaded._compile(source, filename);
const { shanghaiDateKey, calendarToday, localDateKey, safeRedirect } = loaded.exports;

test('统计日期使用上海午夜而非 UTC 午夜', () => {
  assert.equal(shanghaiDateKey(new Date('2026-10-02T15:59:59Z')), '2026-10-02');
  assert.equal(shanghaiDateKey(new Date('2026-10-02T16:00:00Z')), '2026-10-03');
  assert.equal(shanghaiDateKey(new Date('2026-12-31T16:00:00Z')), '2027-01-01');
});

test('浏览器在其他时区也使用上海日历日期', () => {
  const previous = process.env.TZ;
  try {
    process.env.TZ = 'America/Los_Angeles';
    assert.equal(localDateKey(calendarToday()), shanghaiDateKey());
  } finally {
    previous === undefined ? delete process.env.TZ : process.env.TZ = previous;
  }
});

test('登录返回地址限定本站路由', () => {
  global.window = { location: { origin: 'http://localhost:5173' } };
  assert.equal(safeRedirect('/blog/12#comments'), '/blog/12#comments');
  assert.equal(safeRedirect('/blog/mine?tab=favorites'), '/blog/mine?tab=favorites');
  for (const value of ['https://example.com', '//example.com', '/\\example.com', 'javascript:alert(1)', null]) {
    assert.equal(safeRedirect(value), '/');
  }
});
