/** 站内支持的语言清单（粘贴板 / 代码转图片共用） */
export interface LangDef {
  code: string;
  label: string;
  /** 文件名后缀 → 自动识别用 */
  ext: string[];
}

export const LANGUAGES: LangDef[] = [
  { code: 'plaintext', label: '纯文本', ext: ['txt'] },
  { code: 'cpp', label: 'C++', ext: ['cpp', 'cc', 'cxx', 'h', 'hpp'] },
  { code: 'c', label: 'C', ext: ['c', 'h'] },
  { code: 'java', label: 'Java', ext: ['java'] },
  { code: 'python', label: 'Python', ext: ['py', 'pyw'] },
  { code: 'rust', label: 'Rust', ext: ['rs'] },
  { code: 'go', label: 'Go', ext: ['go'] },
  { code: 'javascript', label: 'JavaScript', ext: ['js', 'mjs'] },
  { code: 'typescript', label: 'TypeScript', ext: ['ts', 'tsx'] },
  { code: 'markdown', label: 'Markdown', ext: ['md', 'markdown'] },
  { code: 'json', label: 'JSON', ext: ['json'] },
  { code: 'yaml', label: 'YAML', ext: ['yaml', 'yml'] },
  { code: 'html', label: 'HTML', ext: ['html', 'htm'] },
  { code: 'css', label: 'CSS', ext: ['css'] },
  { code: 'sql', label: 'SQL', ext: ['sql'] },
  { code: 'bash', label: 'Shell', ext: ['sh', 'bash'] },
  { code: 'csharp', label: 'C#', ext: ['cs'] },
  { code: 'kotlin', label: 'Kotlin', ext: ['kt'] },
  { code: 'php', label: 'PHP', ext: ['php'] },
  { code: 'ruby', label: 'Ruby', ext: ['rb'] },
];

/** 按文件名后缀自动识别语言；识别不出返回 null */
export function detectLanguage(title?: string | null): string | null {
  if (!title) return null;
  const m = title.trim().toLowerCase().match(/\.([a-z0-9]+)$/);
  if (!m) return null;
  const ext = m[1];
  return LANGUAGES.find((l) => l.ext.includes(ext))?.code ?? null;
}

export function languageLabel(code?: string | null): string {
  if (!code) return '纯文本';
  return LANGUAGES.find((l) => l.code === code)?.label ?? code;
}
