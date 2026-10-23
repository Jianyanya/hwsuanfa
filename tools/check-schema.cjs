const fs = require('node:fs');
const path = require('node:path');

const project = path.resolve(__dirname, '..');
const entityDirectory = path.join(project, 'backend/src/main/java/com/hwsuanfa/algo/entity');
const sql = fs.readFileSync(path.join(project, 'backend/src/main/resources/db/schema.sql'), 'utf8');
const tables = new Map([...sql.matchAll(/CREATE TABLE(?: IF NOT EXISTS)?\s+`?(\w+)`?\s*\(([\s\S]*?)\) ENGINE/gi)]
  .map((match) => [match[1], new Set([...match[2].matchAll(/^\s*`?(\w+)`?\s+(?:BIGINT|INT|TINYINT|VARCHAR|TEXT|MEDIUMTEXT|DATETIME|DATE|JSON|DECIMAL)\b/gim)]
    .map((column) => column[1]))]));
const issues = [];
let entityCount = 0;
for (const filename of fs.readdirSync(entityDirectory)) {
  if (!filename.endsWith('.java')) continue;
  const source = fs.readFileSync(path.join(entityDirectory, filename), 'utf8');
  const table = source.match(/@TableName\("`?(\w+)`?"\)/)?.[1];
  if (!table) continue;
  entityCount++;
  if (!tables.has(table)) {
    issues.push({ table, missingTable: true });
    continue;
  }
  for (const field of source.matchAll(/(?:@TableField\([^)]*\)\s*)?private\s+[\w<>.]+\s+(\w+)\s*;/g)) {
    if (field[0].includes('exist = false')) continue;
    const column = field[1].replace(/[A-Z]/g, (letter) => '_' + letter.toLowerCase());
    if (!tables.get(table).has(column)) issues.push({ table, missingColumn: column });
  }
}
console.log(JSON.stringify({ entityCount, tableCount: tables.size, issues }, null, 2));
if (issues.length) process.exitCode = 1;
