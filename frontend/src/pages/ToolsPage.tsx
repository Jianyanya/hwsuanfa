import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Trash2 } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Pill } from '@/components/ui/Badge';
import { Button, Notice } from '@/components/ui/Button';
import {
  createLink, createPaste, deleteLink, deletePaste,
  fetchLinks, fetchMyPastes, updateLink,
} from '@/api/tools';
import { LANGUAGES, detectLanguage, languageLabel } from '@/config/languages';
import { useAuth } from '@/stores/auth';

/* ==================== 工具页 ==================== */

type Tab = 'paste' | 'codeimg' | 'links';

export default function ToolsPage() {
  const [tab, setTab] = useState<Tab>('paste');

  return (
    <>
      <PageHeader title="工具" description="训练常用小工具" />
      <div className="mb-4 flex flex-wrap gap-2">
        <Pill active={tab === 'paste'} onClick={() => setTab('paste')}>📋 粘贴板</Pill>
        <Pill active={tab === 'codeimg'} onClick={() => setTab('codeimg')}>🖼️ 代码转图片</Pill>
        <Pill active={tab === 'links'} onClick={() => setTab('links')}>🔗 友情链接</Pill>
      </div>
      {tab === 'paste' && <PasteBoard />}
      {tab === 'codeimg' && <CodeToImage />}
      {tab === 'links' && <FriendlyLinks />}
    </>
  );
}

/* ==================== ① 粘贴板 ==================== */

function PasteBoard() {
  const qc = useQueryClient();
  const [contentType, setContentType] = useState<'TEXT' | 'CODE'>('TEXT');
  const [title, setTitle] = useState('');
  const [language, setLanguage] = useState('cpp');
  const [content, setContent] = useState('');
  const [expireDays, setExpireDays] = useState<number | ''>('');
  const [created, setCreated] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      createPaste({
        content,
        title: title || undefined,
        contentType,
        language: contentType === 'CODE'
          ? (language === 'auto' ? (detectLanguage(title) ?? 'plaintext') : language)
          : undefined,
        expireDays: expireDays === '' ? undefined : Number(expireDays),
      }),
    onSuccess: (data) => {
      setCreated(data.code);
      setContent('');
      setTitle('');
      void qc.invalidateQueries({ queryKey: ['my-pastes'] });
    },
    onError: (e: Error) => setError(e.message),
  });

  const shareUrl = created ? `${location.origin}/paste/${created}` : null;

  return (
    <div className="space-y-4">
      <Card>
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <Pill active={contentType === 'TEXT'} onClick={() => setContentType('TEXT')}>文本</Pill>
          <Pill active={contentType === 'CODE'} onClick={() => setContentType('CODE')}>代码</Pill>
          {contentType === 'CODE' && (
            <select
              className="rounded-[var(--radius)] border bg-transparent px-2 py-1 text-xs outline-none"
              style={{ borderColor: 'var(--input)', color: 'var(--foreground)' }}
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
            >
              <option value="auto">自动识别（按文件名后缀）</option>
              {LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>{l.label}</option>
              ))}
            </select>
          )}
        </div>

        <input
          className="mb-2 w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
          style={{ borderColor: 'var(--input)' }}
          placeholder="标题（选填）"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={128}
        />
        <textarea
          className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-2 font-mono text-sm outline-none"
          style={{ borderColor: 'var(--input)', minHeight: 180 }}
          placeholder="粘贴文本或代码…"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={65536}
        />
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
          <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
            过期：
            <select
              className="rounded-[var(--radius)] border bg-transparent px-2 py-1 text-xs outline-none"
              style={{ borderColor: 'var(--input)', color: 'var(--foreground)' }}
              value={expireDays}
              onChange={(e) => setExpireDays(e.target.value === '' ? '' : Number(e.target.value))}
            >
              <option value="">永久</option>
              <option value={1}>1 天</option>
              <option value={7}>7 天</option>
              <option value={30}>30 天</option>
            </select>
          </label>
          <Button
            size="sm"
            variant="primary"
            disabled={!content.trim() || create.isPending}
            onClick={() => {
              setError(null);
              create.mutate();
            }}
          >
            {create.isPending ? '生成中…' : '生成分享链接'}
          </Button>
        </div>

        {error && <div className="mt-3"><Notice tone="danger">{error}</Notice></div>}
        {shareUrl && (
          <div className="mt-3">
            <Notice>
              <div className="mb-1">分享链接已生成（任何人可访问）：</div>
              <div className="flex items-center gap-2">
                <code className="truncate font-mono text-[11px]">{shareUrl}</code>
                <Button
                  size="sm"
                  onClick={() => navigator.clipboard.writeText(shareUrl)}
                >
                  <Copy className="h-3 w-3" />
                  复制
                </Button>
              </div>
            </Notice>
          </div>
        )}
      </Card>

      <MyPastesCard />
    </div>
  );
}

function MyPastesCard() {
  const qc = useQueryClient();
  const query = useQuery({ queryKey: ['my-pastes'], queryFn: fetchMyPastes });
  const list = query.data ?? [];

  const del = useMutation({
    mutationFn: (code: string) => deletePaste(code),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['my-pastes'] }),
  });

  return (
    <Card>
      <div className="mb-2 text-sm font-medium">
        我的粘贴
        <span className="ml-2 text-xs text-muted-foreground">{list.length} 条</span>
      </div>
      {query.isLoading && <div className="py-4 text-xs text-muted-foreground">加载中…</div>}
      {query.isError && <Notice tone="danger">{query.error.message}</Notice>}
      {del.isError && <Notice tone="danger">{del.error.message}</Notice>}
      {!query.isLoading && !query.isError && list.length === 0 && (
        <div className="py-4 text-center text-xs text-muted-foreground">还没有创建过粘贴</div>
      )}
      <div className="space-y-1.5">
        {list.map((p) => (
          <div
            key={p.id}
            className="flex items-center justify-between gap-2 rounded-[var(--radius)] px-2 py-1.5 text-xs"
            style={{ backgroundColor: 'color-mix(in oklab, var(--muted) 35%, transparent)' }}
          >
            <span className="min-w-0 flex-1 truncate">
              <a href={`/paste/${p.code}`} target="_blank" rel="noreferrer" className="font-mono hover:underline">
                /paste/{p.code}
              </a>
              <span className="ml-2">{p.title ?? p.content.slice(0, 30)}</span>
              {p.expiresAt && (
                <span className="ml-2 text-[10px] text-muted-foreground">
                  至 {p.expiresAt.slice(0, 10)} 过期
                </span>
              )}
            </span>
            <span className="flex-none flex items-center gap-2">
              <span className="text-[11px] text-muted-foreground">{p.viewCount ?? 0} 次浏览</span>
              <button
                type="button"
                aria-label="删除粘贴"
                className="text-muted-foreground hover:text-[var(--destructive)]"
                disabled={del.isPending}
                onClick={() => { if (confirm('确定删除这条粘贴？')) del.mutate(p.code); }}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}

/* ==================== ② 代码转图片（Carbon 风格） ==================== */

/** 主题配色 */
const C2I_THEMES: Record<string, { bg: string; text: string; keyword: string; string: string; comment: string; number: string; title: string }> = {
  'Dark Modern': { bg: '#1e1e2e', text: '#cdd6f4', keyword: '#c792ea', string: '#a5e075', comment: '#636d83', number: '#f78c6c', title: '#9399b2' },
  'One Dark':    { bg: '#282c34', text: '#abb2bf', keyword: '#c678dd', string: '#98c379', comment: '#5c6370', number: '#d19a66', title: '#9da5b4' },
  'Dracula':     { bg: '#282a36', text: '#f8f8f2', keyword: '#ff79c6', string: '#f1fa8c', comment: '#6272a4', number: '#bd93f9', title: '#6272a4' },
  'GitHub Light':{ bg: '#ffffff', text: '#24292e', keyword: '#d73a49', string: '#032f62', comment: '#6a737d', number: '#005cc5', title: '#6a737d' },
};

const C2I_BG_COLORS = ['#dbe3ef', '#ffffff', '#1e1e2e', '#4c8bf5', '#a78bfa', '#34d399', '#ef5350', '#f5a524', '#c084fc', '#22d3ee'];

const C2I_KEYWORDS = [
  'int', 'long', 'double', 'float', 'char', 'bool', 'void', 'auto', 'const', 'struct', 'class',
  'public', 'private', 'return', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break',
  'continue', 'using', 'namespace', 'include', 'define', 'template', 'new', 'delete', 'true',
  'false', 'nullptr', 'import', 'from', 'def', 'print', 'in', 'not', 'and', 'or', 'self', 'let',
  'function', 'var', 'export', 'static', 'final', 'try', 'catch', 'throw',
];

/** 单行语法高亮：返回 [文本, 类型] 序列（canvas 逐段着色） */
function tokenizeLine(line: string): [string, string][] {
  const tokens: [string, string][] = [];
  const re = /(\/\/.*$|\/\*.*|\*.*$|#.*$)|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|\b(\d+\.?\d*)\b|\b([A-Za-z_][A-Za-z0-9_]*)\b|([^A-Za-z0-9_]+|\s+|.)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(line)) !== null) {
    if (m[1]) tokens.push([m[1], 'comment']);
    else if (m[2]) tokens.push([m[2], 'string']);
    else if (m[3]) tokens.push([m[3], 'number']);
    else if (m[4]) tokens.push([m[4], C2I_KEYWORDS.includes(m[4]) ? 'keyword' : 'text']);
    else if (m[5]) tokens.push([m[5], 'text']);
  }
  return tokens.length ? tokens : [['', 'text']];
}

/** markdown 行式着色：标题/列表/围栏/粗体/行内代码 */
function mdTokens(line: string): [string, string][] {
  const heading = line.match(/^(#{1,6})(\s+)(.*)$/);
  if (heading) return [[heading[1] + ' ', 'heading'], [heading[3], 'heading']];
  const fence = line.match(/^\s*(```|~~~)/);
  if (fence) return [[line, 'fence']];
  const list = line.match(/^(\s*)([-*+]|\d+\.)(\s+)(.*)$/);
  if (list) return [[list[1] + list[2] + list[3], 'listmark'], [list[4], 'text']];
  if (/^\s*>/.test(line)) return [[line, 'fence']];

  // 行内：**粗体** / `行内代码`
  const tokens: [string, string][] = [];
  const re = /(\*\*[^*]+\*\*)|(`[^`]+`)|([^*`]+|[\s\S]+)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(line)) !== null) {
    if (m[1]) tokens.push([m[1], 'bold']);
    else if (m[2]) tokens.push([m[2], 'inlinecode']);
    else tokens.push([m[3], 'text']);
  }
  return tokens.length ? tokens : [[line, 'text']];
}

/** 按语言分发：markdown 行式，其余关键词高亮 */
function tokenizeFor(lang: string, line: string): [string, string][] {
  return lang === 'markdown' ? mdTokens(line) : tokenizeLine(line);
}

function CodeToImage() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [code, setCode] = useState(
    '#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n    int n;\n    cin >> n;\n    cout << "Hello, 校队!\\n";\n    return 0;\n}',
  );
  const [title, setTitle] = useState('main.cpp');
  const [langMode, setLangMode] = useState<'auto' | string>('auto');
  const lang = langMode === 'auto' ? (detectLanguage(title) ?? 'plaintext') : langMode;
  const [themeName, setThemeName] = useState('Dark Modern');
  const [bgColor, setBgColor] = useState('#dbe3ef');
  const [padV, setPadV] = useState(56);
  const [padH, setPadH] = useState(56);
  const [fontSize, setFontSize] = useState(14);
  const [scale, setScale] = useState(2);
  const [windowButtons, setWindowButtons] = useState(true);
  const [shadow, setShadow] = useState(true);
  const [lineNumbers, setLineNumbers] = useState(true);

  const theme = C2I_THEMES[themeName];
  const lineH = Math.round(fontSize * 1.6);
  const lines = code.split('\n');

  // 实时预览：任何外观/代码变化都重绘
  useEffect(() => {
    render();
  });

  function render() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d')!;
    ctx.setTransform(1, 0, 0, 1, 0, 0);

    const gutter = lineNumbers ? fontSize * 3 : padH;
    const contentW = Math.max(
      ...lines.map((line) => {
        ctx.font = `${fontSize}px Consolas, monospace`;
        return ctx.measureText(line).width;
      }),
    );
    const width = gutter + contentW + padH;
    const height = padV * 2 + (windowButtons ? 36 : 0) + lines.length * lineH;

    canvas.width = width * scale;
    canvas.height = height * scale;
    ctx.scale(scale, scale);

    // 背景色
    ctx.fillStyle = bgColor;
    ctx.fillRect(0, 0, width, height);

    // 阴影 + 代码窗
    const winY = 0;
    const winH = height;
    ctx.save();
    if (shadow) {
      ctx.shadowColor = 'rgba(0,0,0,0.35)';
      ctx.shadowBlur = 30;
      ctx.shadowOffsetY = 10;
    }
    ctx.fillStyle = theme.bg;
    // 窗口内嵌在背景色里（四周留内边距）
    ctx.fillRect(0, winY, width, winH);
    ctx.restore();

    // 窗口按钮
    let contentTop = winY + padV / 2;
    if (windowButtons) {
      const colors = ['#ff5f57', '#febc2e', '#28c840'];
      colors.forEach((c, i) => {
        ctx.fillStyle = c;
        ctx.beginPath();
        ctx.arc(padH / 2 + 10 + i * 20, winY + 24, 6, 0, 7);
        ctx.fill();
      });
      contentTop = winY + 44;
    } else {
      contentTop = winY + padV / 2;
    }

    // 逐行渲染：markdown 走行式样式，其余走语法高亮
    lines.forEach((line, i) => {
      const y = contentTop + i * lineH + fontSize;
      let x = lineNumbers ? gutter : padH / 2;
      if (lineNumbers) {
        ctx.fillStyle = theme.comment;
        ctx.textAlign = 'right';
        ctx.fillText(String(i + 1), gutter - fontSize, y);
        ctx.textAlign = 'left';
      }
      const toks = tokenizeFor(lang, line);
      for (const [text, type] of toks) {
        const bold = type === 'heading' || type === 'bold';
        ctx.font = `${bold ? 'bold ' : ''}${type === 'heading' ? fontSize + 2 : fontSize}px Consolas, monospace`;
        ctx.fillStyle =
          type === 'comment' || type === 'fence' ? theme.comment
          : type === 'string' || type === 'inlinecode' ? theme.string
          : type === 'number' ? theme.number
          : type === 'keyword' || type === 'heading' || type === 'listmark' ? theme.keyword
          : type === 'bold' ? theme.text
          : theme.text;
        ctx.fillText(text, x, y);
        x += ctx.measureText(text).width;
      }
    });
  }

  function download() {
    render();
    const canvas = canvasRef.current;
    if (!canvas) return;
    const a = document.createElement('a');
    a.download = `${title || 'code'}.png`;
    a.href = canvas.toDataURL('image/png');
    a.click();
  }

  async function copyImage() {
    render();
    const canvas = canvasRef.current;
    if (!canvas) return;
    try {
      const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, 'image/png'));
      if (blob) await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
    } catch {
      // 剪贴板不支持图片时退化为下载
      download();
    }
  }

  const sliderRow = (label: string, value: string, node: React.ReactNode) => (
    <div className="mb-3">
      <div className="mb-1 text-xs text-muted-foreground">{label} {value}</div>
      {node}
    </div>
  );

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_260px]">
      <div className="space-y-4">
        <Card>
          <div className="text-sm font-medium">代码</div>
          <div className="mt-1 text-xs text-muted-foreground">
            粘贴或编辑代码后，下方会实时预览。当前识别：{languageLabel(lang)}
          </div>
          <input
            className="mt-2 w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={64}
            placeholder="文件名（显示在窗口标题栏，也用于语言自动识别）"
          />
          <textarea
            className="mt-3 w-full rounded-[var(--radius)] border bg-transparent px-3 py-2 font-mono text-[13px] outline-none"
            style={{
              borderColor: 'var(--input)',
              minHeight: 220,
              backgroundColor: 'var(--card)',
              color: 'var(--foreground)',
            }}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            spellCheck={false}
          />
        </Card>

        <Card>
          <div className="mb-2 text-sm font-medium">🖼️ 预览</div>
          <div className="flex justify-center overflow-x-auto rounded-[var(--radius)] py-4"
               style={{ backgroundColor: 'color-mix(in oklab, var(--muted) 30%, transparent)' }}>
            <canvas ref={canvasRef} className="max-w-full" />
          </div>
        </Card>
      </div>

      {/* 外观面板 */}
      <div className="space-y-4">
        <Card>
          <div className="mb-2 text-sm font-medium">外观</div>
          <div className="text-xs text-muted-foreground">调整主题、字体和导出参数。</div>

          <div className="mt-4 mb-3">
            <div className="mb-1 text-xs text-muted-foreground">
              语言（自动识别：{languageLabel(lang)}）
            </div>
            <select
              className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1.5 text-sm outline-none"
              style={{ borderColor: 'var(--input)', color: 'var(--foreground)', backgroundColor: 'var(--card)' }}
              value={langMode}
              onChange={(e) => setLangMode(e.target.value)}
            >
              <option value="auto">自动识别</option>
              {LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>{l.label}</option>
              ))}
            </select>
          </div>

          <div className="mb-3">
            <div className="mb-1 text-xs text-muted-foreground">主题</div>
            <select
              className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1.5 text-sm outline-none"
              style={{ borderColor: 'var(--input)', color: 'var(--foreground)', backgroundColor: 'var(--card)' }}
              value={themeName}
              onChange={(e) => setThemeName(e.target.value)}
            >
              {Object.keys(C2I_THEMES).map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          <div className="mb-3">
            <div className="mb-1 text-xs text-muted-foreground">背景色</div>
            <div className="flex flex-wrap gap-1.5">
              {C2I_BG_COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  aria-label={`背景色 ${c}`}
                  className="h-7 w-7 rounded-md"
                  style={{
                    backgroundColor: c,
                    outline: bgColor === c ? '2px solid var(--primary)' : '1px solid var(--border)',
                  }}
                  onClick={() => setBgColor(c)}
                />
              ))}
            </div>
          </div>

          {sliderRow('内边距 垂直', `${padV}`, (
            <input type="range" min={16} max={120} value={padV}
                   onChange={(e) => setPadV(Number(e.target.value))} className="w-full accent-[var(--primary)]" />
          ))}
          {sliderRow('内边距 水平', `${padH}`, (
            <input type="range" min={16} max={120} value={padH}
                   onChange={(e) => setPadH(Number(e.target.value))} className="w-full accent-[var(--primary)]" />
          ))}
          {sliderRow('字号', `${fontSize}px`, (
            <input type="range" min={10} max={24} value={fontSize}
                   onChange={(e) => setFontSize(Number(e.target.value))} className="w-full accent-[var(--primary)]" />
          ))}

          <div className="mb-3">
            <div className="mb-1 text-xs text-muted-foreground">导出倍率</div>
            <select
              className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1.5 text-sm outline-none"
              style={{ borderColor: 'var(--input)', color: 'var(--foreground)', backgroundColor: 'var(--card)' }}
              value={scale}
              onChange={(e) => setScale(Number(e.target.value))}
            >
              {[1, 2, 3].map((s) => (
                <option key={s} value={s}>{s}x</option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label className="flex items-center justify-between text-xs">
              窗口按钮
              <input type="checkbox" checked={windowButtons} onChange={(e) => setWindowButtons(e.target.checked)} />
            </label>
            <label className="flex items-center justify-between text-xs">
              阴影
              <input type="checkbox" checked={shadow} onChange={(e) => setShadow(e.target.checked)} />
            </label>
            <label className="flex items-center justify-between text-xs">
              行号
              <input type="checkbox" checked={lineNumbers} onChange={(e) => setLineNumbers(e.target.checked)} />
            </label>
          </div>

          <div className="mt-4 space-y-2">
            <Button size="sm" variant="primary" className="w-full" onClick={copyImage}>
              📋 复制图片
            </Button>
            <Button size="sm" className="w-full" onClick={download}>
              ⬇️ 下载 PNG
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}

/* ==================== ③ 友情链接 ==================== */

interface LinkRow {
  id: number;
  name: string;
  url: string;
  logo: string | null;
  description: string | null;
  sortOrder: number;
}

function FriendlyLinks() {
  const me = useAuth((s) => s.user);
  const qc = useQueryClient();
  const query = useQuery({ queryKey: ['friendly-links'], queryFn: fetchLinks });
  const list = (query.data ?? []) as LinkRow[];
  const [editing, setEditing] = useState<number | 'new' | null>(null);

  const isOwner = me?.role === 'SITE_OWNER';

  const del = useMutation({
    mutationFn: (id: number) => deleteLink(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['friendly-links'] }),
  });

  return (
    <div className="space-y-4">
      <Card>
        <div className="mb-3 flex items-center justify-between">
          <div className="text-sm font-medium">🔗 友情链接</div>
          {isOwner && (
            <Button size="sm" variant="primary" onClick={() => setEditing('new')}>
              新增友链
            </Button>
          )}
        </div>
        {list.length === 0 && (
          <div className="py-6 text-center text-xs text-muted-foreground">
            还没有友链{isOwner ? '，点右上「新增友链」添加' : ''}
          </div>
        )}
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((l) => (
            <div
              key={l.id}
              className="rounded-[var(--radius)] border p-3"
              style={{ borderColor: 'var(--border)' }}
            >
              <div className="flex items-center justify-between">
                <a href={l.url} target="_blank" rel="noreferrer" className="text-sm font-medium hover:underline">
                  {l.name}
                </a>
                {isOwner && (
                  <span className="flex gap-2">
                    <button
                      type="button"
                      className="text-[11px] text-muted-foreground hover:text-foreground"
                      onClick={() => setEditing(l.id)}
                    >
                      编辑
                    </button>
                    <button
                      type="button"
                      aria-label="删除友链"
                      className="text-muted-foreground hover:text-[var(--destructive)]"
                      onClick={() => del.mutate(l.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </span>
                )}
              </div>
              {l.description && (
                <div className="mt-1 text-[11px] text-muted-foreground">{l.description}</div>
              )}
              <div className="mt-1 truncate text-[10px] text-muted-foreground">{l.url}</div>
            </div>
          ))}
        </div>
      </Card>

      {editing !== null && (
        <LinkForm
          initial={editing === 'new' ? null : list.find((l) => l.id === editing) ?? null}
          onDone={() => {
            setEditing(null);
            void qc.invalidateQueries({ queryKey: ['friendly-links'] });
          }}
          onCancel={() => setEditing(null)}
        />
      )}
    </div>
  );
}

function LinkForm({
  initial, onDone, onCancel,
}: {
  initial: LinkRow | null;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [url, setUrl] = useState(initial?.url ?? 'https://');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [sortOrder, setSortOrder] = useState(initial?.sortOrder ?? 0);
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      initial
        ? updateLink(initial.id, { name, url, description, sortOrder })
        : createLink({ name, url, description, sortOrder }),
    onSuccess: onDone,
    onError: (e: Error) => setError(e.message),
  });

  return (
    <Card>
      <div className="mb-3 text-sm font-medium">{initial ? '编辑友链' : '新增友链'}</div>
      <div className="grid gap-2 sm:grid-cols-2">
        <input
          className="rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
          style={{ borderColor: 'var(--input)' }}
          placeholder="名称"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={64}
        />
        <input
          className="rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
          style={{ borderColor: 'var(--input)' }}
          placeholder="https://..."
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          maxLength={500}
        />
      </div>
      <input
        className="mt-2 w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
        style={{ borderColor: 'var(--input)' }}
        placeholder="描述（选填）"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        maxLength={200}
      />
      <div className="mt-2 text-xs text-muted-foreground">排序（小的在前）</div>
      <input
        className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
        style={{ borderColor: 'var(--input)' }}
        type="number"
        value={sortOrder}
        onChange={(e) => setSortOrder(Number(e.target.value))}
      />
      {error && <div className="mt-2"><Notice tone="danger">{error}</Notice></div>}
      <div className="mt-3 flex gap-2">
        <Button
          size="sm"
          variant="primary"
          disabled={!name.trim() || !url.trim() || save.isPending}
          onClick={() => {
            setError(null);
            save.mutate();
          }}
        >
          {save.isPending ? '保存中…' : '保存'}
        </Button>
        <Button size="sm" onClick={onCancel}>取消</Button>
      </div>
    </Card>
  );
}
