const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'hwsuanfa-release-'));
function git(args) {
  const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || 'Git check failed');
  return result.stdout;
}

try {
  const hasRepository = fs.existsSync(path.join(root, '.git'));
  if (!hasRepository) git(['init', '--bare', '--quiet', temporary]);
  const prefix = hasRepository ? [] : ['--git-dir=' + temporary, '--work-tree=' + root];
  const files = [...new Set(git([...prefix, 'ls-files', '-z', '--cached', '--others', '--exclude-standard']).split('\0').filter(Boolean))];
  const forbidden = /(^|\/)(\.local-private|\.tmp|\.logs|\.workbuddy|\.vscode|\.idea|\.agents|\.codex|\.aws|node_modules|target|dist|design|uploads|backups|exports|shots)(\/|$)|(^|\/)(\.env($|\.)|application-local\.)|\.(rdb|aof|log|dump|bak|db|sqlite\w*|pem|key|p12|pfx|jks|zip|7z|gz|tsbuildinfo|csv|xlsx|xls|har)$/i;
  const issues = [];
  const contentIssues = (name, text, version) => {
    if (name === 'tools/check-release.cjs') return;
    const patterns = [
      /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
      /\b(?:ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b/,
      /eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}/,
    ];
    if (patterns.some(p => p.test(text))) issues.push(`${name}: possible credential (${version})`);
    if (name === 'backend/src/main/resources/application.yml') {
      if (/^\s*(?:password|secret|member-code|admin-code):\s*(?!\$\{)\S/m.test(text)) issues.push(`${name}: hardcoded credential (${version})`);
    }
    if (name === 'backend/src/main/resources/db/data.sql' && /(?:INSERT\s+(?:IGNORE\s+)?INTO|UPDATE)\s+`?(?:user|team|announcement)`?\b/i.test(text)) issues.push(`${name}: business seed data (${version})`);
    if (name.startsWith('frontend/src/') && /admin123|首次部署默认账号/.test(text)) issues.push(`${name}: default account hint (${version})`);
  };
  for (const name of files) {
    if (forbidden.test(name) && name !== '.env.example') issues.push(`${name}: private or generated file`);
    if (/\.sql$/i.test(name) && !/^backend\/src\/main\/resources\/db\/(schema|data|migration-[\w-]+)\.sql$/.test(name)) issues.push(`${name}: unexpected SQL export`);
    const file = path.join(root, name);
    if (fs.existsSync(file)) {
      if (fs.lstatSync(file).isSymbolicLink()) { issues.push(`${name}: review symlink`); continue; }
      if (fs.statSync(file).size > 2 * 1024 * 1024) issues.push(`${name}: review large file`);
      else contentIssues(name, fs.readFileSync(file, 'utf8'), 'working tree');
    }
  }
  if (hasRepository) {
    const tracked = git(['ls-files', '-z']).split('\0').filter(Boolean);
    for (const name of tracked) contentIssues(name, git(['show', ':' + name]), 'index');
  }
  if (issues.length) {
    console.error(issues.join('\n'));
    process.exitCode = 1;
  } else {
    console.log(`Release scan passed: ${files.length} files; ${hasRepository ? 'working tree and index' : 'prospective files using .gitignore'}.`);
    console.log('No database or Redis data was read or changed. Review staged filenames before pushing.');
  }
} finally {
  fs.rmSync(temporary, { recursive: true, force: true });
}
