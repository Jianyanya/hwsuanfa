const sensitiveKey = /password|passwd|token|secret|authorization|credential|invitecode|密码|口令/i;

function redactText(value: string) {
  return value
    .replace(/\beyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, '[已隐藏令牌]')
    .replace(/\bBearer\s+[A-Za-z0-9._~-]+/gi, 'Bearer [已隐藏]')
    .replace(/((?:password|passwd|access[_-]?token|refresh[_-]?token|token|secret|authorization|credential|密码|口令)\s*[:=]\s*)("[^"]*"|'[^']*'|[^\s,;]+)/gi, '$1[已隐藏]');
}

function redactValue(value: unknown): unknown {
  if (typeof value === 'string') return redactText(value);
  if (Array.isArray(value)) return value.map(redactValue);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, sensitiveKey.test(key.replace(/[ _-]/g, '')) ? '[已隐藏]' : redactValue(item)]));
  return value;
}

export function formatAuditDetail(detail: string | null) {
  if (!detail?.trim()) return '—';
  try { return JSON.stringify(redactValue(JSON.parse(detail)), null, 2); }
  catch { return redactText(detail); }
}
