import type { ReactNode } from 'react';

export function BlogMarkdown({ content }: { content: string }) {
  const lines = content.replace(/\r\n?/g, '\n').split('\n');
  const blocks: ReactNode[] = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    const blockKey = index;
    const fence = line.match(/^\s*```(.*)$/);
    if (fence) {
      const code: string[] = [];
      index++;
      while (index < lines.length && !/^\s*```/.test(lines[index])) code.push(lines[index++]);
      if (index < lines.length) index++;
      blocks.push(<div key={blockKey} className="my-4 overflow-hidden rounded-[var(--radius)] border border-[var(--border)] bg-muted">{fence[1].trim() && <div className="border-b border-[var(--border)] px-3 py-1.5 text-[11px] text-muted-foreground">{fence[1].trim()}</div>}<pre className="overflow-x-auto p-3 text-[13px] leading-6"><code>{code.join('\n')}</code></pre></div>);
      continue;
    }
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    if (heading) {
      const headingContent = inline(heading[2]);
      const className = 'mb-2 mt-5 font-semibold leading-snug';
      blocks.push(heading[1].length === 1 ? <h2 key={blockKey} className={`${className} text-2xl`}>{headingContent}</h2> : heading[1].length === 2 ? <h3 key={blockKey} className={`${className} text-xl`}>{headingContent}</h3> : <h4 key={blockKey} className={`${className} text-base`}>{headingContent}</h4>);
      index++;
      continue;
    }
    if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) { blocks.push(<hr key={blockKey} className="my-5 border-[var(--border)]" />); index++; continue; }
    if (/^\s*>/.test(line)) {
      const quote: string[] = [];
      while (index < lines.length && /^\s*>/.test(lines[index])) quote.push(lines[index++].replace(/^\s*>\s?/, ''));
      blocks.push(<blockquote key={blockKey} className="my-3 border-l-4 border-[var(--border)] pl-4 text-muted-foreground">{quote.map((text, position) => <div key={position}>{inline(text)}</div>)}</blockquote>);
      continue;
    }
    const unordered = line.match(/^\s*[-*+]\s+(.*)$/);
    const ordered = line.match(/^\s*(\d+)\.\s+(.*)$/);
    if (unordered || ordered) {
      const items: ReactNode[] = [];
      const pattern = ordered ? /^\s*\d+\.\s+(.*)$/ : /^\s*[-*+]\s+(.*)$/;
      while (index < lines.length) {
        const item = lines[index].match(pattern);
        if (!item) break;
        items.push(<li key={index}>{inline(item[1])}</li>);
        index++;
      }
      blocks.push(ordered ? <ol key={blockKey} start={Number(ordered[1])} className="my-3 list-decimal space-y-1 pl-6">{items}</ol> : <ul key={blockKey} className="my-3 list-disc space-y-1 pl-6">{items}</ul>);
      continue;
    }
    if (!line.trim()) { index++; continue; }
    const paragraph: string[] = [line];
    index++;
    while (index < lines.length && lines[index].trim() && !/^\s*(?:```|#{1,6}\s|>|[-*+]\s|\d+\.\s|(?:-{3,}|\*{3,}|_{3,})\s*$)/.test(lines[index])) paragraph.push(lines[index++]);
    blocks.push(<p key={blockKey} className="my-3 whitespace-pre-wrap">{paragraph.map((text, position) => <span key={position}>{position > 0 && <br />}{inline(text)}</span>)}</p>);
  }
  return <div className="min-w-0 break-words text-sm leading-7">{blocks}</div>;
}

function inline(text: string, depth = 0): ReactNode {
  if (depth > 4) return text;
  const pattern = /(`[^`\n]+`|\*\*[^*\n]+\*\*|\*[^*\n]+\*|\[[^\]\n]+\]\([^\s)]+\))/g;
  const nodes: ReactNode[] = [];
  let offset = 0;
  for (const match of text.matchAll(pattern)) {
    const position = match.index;
    if (position > offset) nodes.push(text.slice(offset, position));
    const token = match[0];
    if (token.startsWith('`')) nodes.push(<code key={position} className="rounded bg-muted px-1.5 py-0.5 font-mono text-[13px]">{token.slice(1, -1)}</code>);
    else if (token.startsWith('**')) nodes.push(<strong key={position}>{inline(token.slice(2, -2), depth + 1)}</strong>);
    else if (token.startsWith('*')) nodes.push(<em key={position}>{inline(token.slice(1, -1), depth + 1)}</em>);
    else {
      const link = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)!;
      try {
        const url = new URL(link[2]);
        nodes.push(url.protocol === 'http:' || url.protocol === 'https:' ? <a key={position} href={url.href} target="_blank" rel="noopener noreferrer" className="text-primary underline underline-offset-2">{inline(link[1], depth + 1)}</a> : token);
      } catch { nodes.push(token); }
    }
    offset = position + token.length;
  }
  if (offset < text.length) nodes.push(text.slice(offset));
  return nodes;
}
