const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const database = 'hwsuanfa_schema_check_' + crypto.randomBytes(6).toString('hex');
if (!process.env.DB_PASSWORD) throw new Error('请显式设置 DB_PASSWORD');
const env = { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD };
const username = process.env.DB_USERNAME || 'root';
const script = path.join(root, '.tmp', database + '.sql');
let created = false;
function sql(statement, target) {
  const result = spawnSync('mysql.exe', ['--protocol=TCP', '-h', '127.0.0.1', '-u', username,
    '--default-character-set=utf8mb4', '-N', '-B', ...(target ? ['-D', target] : []), '-e', statement],
  { env, encoding: 'utf8' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr.trim() || 'MySQL schema validation failed');
  return result.stdout.trim();
}

try {
  if (!/^hwsuanfa_schema_check_[a-f0-9]{12}$/.test(database)) throw new Error('非法临时数据库名');
  if (sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + database + "'") !== '0') {
    throw new Error('临时库已存在，停止检查');
  }
  const schema = fs.readFileSync(path.join(root, 'backend/src/main/resources/db/schema.sql'), 'utf8');
  const seed = fs.readFileSync(path.join(root, 'backend/src/main/resources/db/data.sql'), 'utf8');
  fs.mkdirSync(path.dirname(script), { recursive: true });
  fs.writeFileSync(script, (schema + '\n' + seed).replaceAll('`hwsuanfa`', '`' + database + '`').replace(/USE hwsuanfa;/g, 'USE `' + database + '`;'));
  created = true;
  sql('SOURCE ' + script.replaceAll('\\', '/'));
  const tables = Number(sql('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()', database));
  const avatar = sql("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='avatar'", database);
  const personalLog = sql("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='contest_log' AND COLUMN_NAME='team_id'", database);
  if (tables !== 31 || avatar !== 'mediumtext' || personalLog !== 'YES') throw new Error('新库结构不完整');
  const users = Number(sql('SELECT COUNT(*) FROM user', database));
  const teams = Number(sql('SELECT COUNT(*) FROM team', database));
  const announcements = Number(sql('SELECT COUNT(*) FROM announcement', database));
  if (users || teams || announcements) throw new Error('初始化脚本不应包含账号或示例业务数据');
  console.log(JSON.stringify({ users, teams, announcements, tables, avatar, personalLogNullable: true, initialized: true }, null, 2));
} finally {
  if (created && /^hwsuanfa_schema_check_[a-f0-9]{12}$/.test(database)) sql('DROP DATABASE IF EXISTS `' + database + '`');
  if (fs.existsSync(script) && path.dirname(script) === path.join(root, '.tmp')) fs.unlinkSync(script);
}
