const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');

const filename = path.resolve(__dirname, '../src/lib/submissions.ts');
const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const loaded = new Module(filename, module);
loaded._compile(source, filename);
const { countAcceptedProblems, countSubmissionDays } = loaded.exports;
const row = (id, overrides = {}) => ({ id, platform: 'LUOGU', problemNo: 'P1000', result: 'AC', submittedAt: '2026-10-03 10:00:00', ...overrides });

test('累计通过使用本人提交并按题目去重，不依赖公开排行榜', () => {
  const submissions = [
    row(1, { problemId: 101 }), row(2, { problemId: 101 }),
    row(3, { problemId: 102, problemNo: 'P1001' }),
    row(4, { problemId: 103, problemNo: 'P1002', result: 'WA' }),
  ];
  assert.equal(countAcceptedProblems(submissions), 2);
});

test('缺少题目 ID 时使用平台题号，混合旧新记录也不重复计数', () => {
  const submissions = [
    row(1, { problemId: 101 }), row(2, { problemNo: ' p1000 ', platform: 'luogu' }),
    row(3, { problemNo: 'P1001' }), row(4, { problemNo: 'P1001' }),
    row(5, { platform: 'CODEFORCES', problemNo: 'P1001' }),
  ];
  assert.equal(countAcceptedProblems(submissions), 3);
});

test('今日完成按当天 AC 的题目去重', () => {
  const submissions = [
    row(1, { problemId: 101, submittedAt: '2026-10-02 23:00:00' }),
    row(2, { problemId: 101 }), row(3, { problemId: 101 }),
    row(4, { problemId: 102, problemNo: 'P1001', result: 'TLE' }),
    row(5, { problemId: 103, problemNo: 'P1002' }),
  ];
  assert.equal(countAcceptedProblems(submissions, '2026-10-03'), 2);
  assert.equal(countAcceptedProblems(submissions, '2026-10-02'), 1);
});

test('提交热力图包含全部真实提交，不过滤结果或对题目去重', () => {
  const submissions = [row(1), row(2), row(3, { result: 'WA' }), row(4, { result: 'TLE' }), row(5, { submittedAt: '2026-10-02 12:00:00' })];
  const days = countSubmissionDays(submissions);
  assert.equal(days.get('2026-10-03'), 4);
  assert.equal(days.get('2026-10-02'), 1);
});
