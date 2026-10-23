// ==UserScript==
// @name         校队算法训练平台 · 洛谷同步
// @namespace    https://hwsuanfa.local
// @version      0.3.3
// @description  在当前洛谷登录态下，把 AC 记录同步到校队算法训练平台（固定站点，面板可收起）
// @match        https://www.luogu.com.cn/*
// @match        https://luogu.com.cn/*
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_deleteValue
// @grant        GM_xmlhttpRequest
// @connect      *
// ==/UserScript==

(function () {
  'use strict';

  // ===== 固定站点：只此一站，地址写死，无需配置 =====
  const SITE_BASE = 'http://localhost:5173';
  const KEY_TOKEN = 'hwsuanfa.luogu.access-token';
  const KEY_COLLAPSED = 'hwsuanfa.luogu.collapsed';
  const PAGE_DELAY = 900;
  const UPLOAD_BATCH = 500;

  let panel;
  let busy = false;

  function asObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
  }

  function pageContext(html) {
    const encoded = html.match(/window\._feInjection\s*=\s*JSON\.parse\(decodeURIComponent\("((?:\\.|[^"\\])*)"\)\)/s);
    if (encoded) {
      try {
        return asObject(JSON.parse(decodeURIComponent(encoded[1])));
      } catch (_) {
        throw new Error('洛谷页面数据解析失败');
      }
    }
    const lentille = html.match(/<script\b[^>]*\bid=["']lentille-context["'][^>]*>([\s\S]*?)<\/script\s*>/i);
    if (lentille) {
      try {
        return asObject(JSON.parse(lentille[1].trim()));
      } catch (_) {
        throw new Error('洛谷页面数据解析失败');
      }
    }
    throw new Error('找不到洛谷页面数据，请刷新页面或更新脚本');
  }

  function currentUser(context) {
    const data = asObject(context.data);
    return asObject(context.currentUser) || asObject(context.user) ||
      asObject(data && (data.currentUser || data.user));
  }

  function currentUid() {
    try {
      const context = pageContext(document.documentElement.outerHTML);
      const user = currentUser(context);
      const uid = user && (user.uid || user.id);
      if (/^[1-9]\d*$/.test(String(uid || ''))) return String(uid);
    } catch (_) {}
    const cookie = document.cookie.split(';').map((x) => x.trim());
    const value = cookie.find((x) => x.startsWith('_uid='));
    return value && /^[1-9]\d*$/.test(value.slice(5)) ? value.slice(5) : null;
  }

  async function fetchRecordPage(uid, page) {
    const url = `${location.origin}/record/list?user=${encodeURIComponent(uid)}&page=${page}`;
    const response = await fetch(url, { credentials: 'include', redirect: 'follow' });
    if (!response.ok) throw new Error(`洛谷记录页请求失败：HTTP ${response.status}`);
    return { html: await response.text(), url: response.url || url };
  }

  function parseRecordPage(html, uid, page) {
    const context = pageContext(html);
    const data = asObject(context.data);
    const current = currentUser(context);
    const pageUid = current && (current.uid || current.id);
    if (pageUid && String(pageUid) !== String(uid)) throw new Error('洛谷登录账号与当前页面账号不一致');
    const currentData = asObject(context.currentData) || data;
    const container = asObject(currentData && currentData.records);
    if (!container || container.count == null || (!Array.isArray(container.result) && !Array.isArray(container.records))) {
      throw new Error(`洛谷第 ${page} 页数据格式发生变化，请检查登录或页面验证状态`);
    }
    const rows = Array.isArray(container && container.result)
      ? container.result
      : Array.isArray(container && container.records) ? container.records : [];
    const count = Number(container && container.count);
    const pageSize = Number(container.perPage);
    const perPage = Number.isInteger(pageSize) && pageSize > 0 ? pageSize : rows.length || 20;
    if (!Number.isInteger(count) || count < 0) {
      throw new Error(`洛谷第 ${page} 页数据格式发生变化`);
    }
    const records = rows.filter((row) => {
      const status = Number(row && row.status);
      return status === 12;
    }).map((row) => {
      const problem = asObject(row.problem);
      const id = Number(row.id);
      const submitTime = Number(row.submitTime);
      const problemNo = problem && String(problem.pid || '').trim().toUpperCase();
      if (!Number.isSafeInteger(id) || id <= 0 || !Number.isSafeInteger(submitTime) || submitTime <= 0 || !problemNo) return null;
      return {
        problemNo,
        submittedAt: formatDate(new Date(submitTime * 1000)),
        language: row.language == null ? null : String(row.language),
        url: `https://www.luogu.com.cn/record/${id}`
      };
    }).filter(Boolean);
    return { count, perPage, records };
  }

  function formatDate(date) {
    const values = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
    }).formatToParts(date).map((part) => [part.type, part.value]));
    return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`;
  }

  function normalizeTags(value, tagNames = new Map()) {
    const values = Array.isArray(value)
      ? value.map((item) => {
        if (typeof item === 'string' || typeof item === 'number') return item;
        return item && (item.name || item.label || item.title || item.tag || item.id || item.tid);
      })
      : typeof value === 'string' ? value.split(/[,，]/) : [];
    return [...new Set(values.map((item) => {
      const tag = String(item ?? '').trim();
      return /^\d+$/.test(tag) ? tagNames.get(tag) || '' : tag;
    }).filter(Boolean))];
  }

  function collectTagNames(value, names = new Map(), depth = 0) {
    if (!value || typeof value !== 'object' || depth > 10) return names;
    for (const [key, item] of Object.entries(value)) {
      if (typeof item === 'string' && /^\d+$/.test(key) && item.trim()) names.set(key, item.trim());
      if (!item || typeof item !== 'object') continue;
      const id = item.id ?? item.tid ?? (/^\d+$/.test(key) && !Array.isArray(value) ? key : null);
      const name = item.name || item.label || item.title;
      if (/^\d+$/.test(String(id)) && typeof name === 'string' && name.trim()) names.set(String(id), name.trim());
      collectTagNames(item, names, depth + 1);
    }
    return names;
  }

  async function fetchLuoguTagNames() {
    try {
      const response = await fetch(`${location.origin}/_lfe/tags`, { credentials: 'include' });
      if (!response.ok) return new Map();
      const value = await response.json();
      return collectTagNames(value.tags || value.data?.tags || value.data || value);
    } catch (_) {
      return new Map();
    }
  }

  function findProblemNode(value, problemNo, seen = new Set(), depth = 0) {
    if (!value || typeof value !== 'object' || depth > 8 || seen.has(value)) return null;
    seen.add(value);
    if (!Array.isArray(value)) {
      const pid = value.pid || value.problemNo;
      if (pid && String(pid).trim().toUpperCase() === problemNo) return value;
      for (const key of ['problem', 'problemData', 'currentProblem']) {
        const found = findProblemNode(value[key], problemNo, seen, depth + 1);
        if (found) return found;
      }
    }
    const entries = Array.isArray(value) ? value : Object.values(value);
    for (const item of entries.slice(0, 300)) {
      const found = findProblemNode(item, problemNo, seen, depth + 1);
      if (found) return found;
    }
    return null;
  }

  function readLuoguProblemMeta(html, problemNo, tagNames = new Map()) {
    const context = pageContext(html);
    const node = findProblemNode(context, problemNo);
    if (!node) throw new Error(`题目 ${problemNo} 的页面数据无法识别，请检查洛谷登录或验证状态`);
    const names = new Map(tagNames);
    for (const value of [context.tags, context.tagMap, context.currentData?.tags, context.data?.tags]) {
      collectTagNames(value, names);
    }
    const rawTags = node.tags || node.tag || [];
    const tags = normalizeTags(rawTags, names);
    const difficultyValue = node.difficulty;
    const difficultyNames = ['暂无评定', '入门', '普及−', '普及/提高−', '普及+/提高', '提高+/省选−', '省选/NOI−', 'NOI/NOI+/CTSC'];
    const difficulty = difficultyValue != null && difficultyValue !== '' && Number.isInteger(Number(difficultyValue)) && Number(difficultyValue) > 0 && Number(difficultyValue) < difficultyNames.length
      ? difficultyNames[Number(difficultyValue)]
      : typeof difficultyValue === 'string' && !/^\d+$/.test(difficultyValue) ? difficultyValue.trim() : '';
    if (tags.length === 0) {
      try {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        tags.push(...normalizeTags([...doc.querySelectorAll('.problem-tags .tag, #problem-tags .tag, a[href*="tag="]')]
          .map((element) => element.textContent.trim()), names));
      } catch (_) {}
    }
    const title = node && (node.title || node.name);
    const unresolved = Array.isArray(rawTags) && rawTags.some((item) => {
      const id = typeof item === 'object' && item ? item.id ?? item.tid : item;
      return /^\d+$/.test(String(id)) && !names.has(String(id))
        && !(item && typeof item === 'object' && (item.name || item.label || item.title));
    });
    if (unresolved) throw new Error(`题目 ${problemNo} 的标签编号无法解析，请稍后重试`);
    const fittedTags = [];
    for (const tag of [...new Set(tags)]) {
      if ([...fittedTags, tag].join(',').length > 255) break;
      fittedTags.push(tag);
    }
    return {
      problemNo,
      title: typeof title === 'string' ? title.trim().slice(0, 200) : '',
      difficulty,
      tags: fittedTags.join(','),
      url: `https://www.luogu.com.cn/problem/${encodeURIComponent(problemNo)}`
    };
  }

  async function fetchLuoguProblemMeta(problemNo, tagNames) {
    const url = `${location.origin}/problem/${encodeURIComponent(problemNo)}`;
    const response = await fetch(url, { credentials: 'include', redirect: 'follow' });
    if (!response.ok) throw new Error(`题目 ${problemNo} 请求失败：HTTP ${response.status}`);
    return readLuoguProblemMeta(await response.text(), problemNo, tagNames);
  }

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  function apiRequest(method, path, body, timeoutMs) {
    const base = SITE_BASE.replace(/\/$/, '');
    const token = String(GM_getValue(KEY_TOKEN, '')).trim();
    if (!token) return Promise.reject(new Error('请先设置校队平台访问令牌'));
    return new Promise((resolve, reject) => {
      GM_xmlhttpRequest({
        method,
        url: `${base}${path}`,
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
        },
        data: body === undefined ? undefined : JSON.stringify(body),
        timeout: timeoutMs || 30000,
        onload: (response) => {
          let payload;
          try { payload = JSON.parse(response.responseText); } catch (_) { payload = null; }
          if (response.status < 200 || response.status >= 300) {
            if (response.status === 401) GM_deleteValue(KEY_TOKEN);
            reject(new Error(payload && payload.message ? payload.message : `校队平台请求失败：HTTP ${response.status}`));
            return;
          }
          if (!payload || payload.code !== 0) {
            reject(new Error(payload && payload.message ? payload.message : '校队平台返回数据无效'));
            return;
          }
          resolve(payload.data);
        },
        onerror: () => reject(new Error('无法连接校队算法训练平台')),
        ontimeout: () => reject(new Error('校队平台请求超时'))
      });
    });
  }

  async function fetchAllAcRecords() {
    const uid = currentUid();
    if (!uid) throw new Error('请先登录洛谷');
    setStatus(`已识别洛谷 UID ${uid}，开始读取提交记录…`);
    const first = await fetchRecordPage(uid, 1);
    const firstPage = parseRecordPage(first.html, uid, 1);
    const totalPages = Math.max(1, Math.ceil(firstPage.count / firstPage.perPage));
    const all = [...firstPage.records];
    for (let page = 2; page <= totalPages; page += 1) {
      setStatus(`正在读取第 ${page}/${totalPages} 页…`);
      await sleep(PAGE_DELAY);
      const current = await fetchRecordPage(uid, page);
      all.push(...parseRecordPage(current.html, uid, page).records);
    }
    const unique = new Map();
    for (const record of all) {
      const key = `${record.problemNo}|${record.submittedAt}`;
      if (!unique.has(key)) unique.set(key, record);
    }
    return [...unique.values()].sort((a, b) => a.submittedAt.localeCompare(b.submittedAt));
  }

  async function sync() {
    if (busy) return;
    busy = true;
    setStatus('正在检查登录态…');
    try {
      const me = await apiRequest('GET', '/api/auth/me');
      const profile = await apiRequest('GET', '/api/profile/me');
      if (!profile.luoguHandle) {
        throw new Error(`校队账号 ${me.nickname || me.username} 尚未绑定洛谷用户名，请先在个人中心保存。`);
      }
      const records = await fetchAllAcRecords();
      let inserted = 0;
      let firstAcGranted = 0;
      for (let i = 0; i < records.length; i += UPLOAD_BATCH) {
        const result = await apiRequest('POST', '/api/submissions/import/LUOGU', {
          records: records.slice(i, i + UPLOAD_BATCH)
        });
        inserted += Number(result.inserted || 0);
        firstAcGranted += Number(result.firstAcGranted || 0);
        setStatus(`已上传 ${Math.min(i + UPLOAD_BATCH, records.length)}/${records.length} 条 AC…`);
      }
      setStatus(`同步完成：读取 ${records.length} 条，新增 ${inserted} 条，首次完成积分 ${firstAcGranted} 条。`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '同步失败');
    } finally {
      busy = false;
    }
  }

  async function backfillMetadata() {
    if (busy) return;
    busy = true;
    setStatus('正在请求平台后台回填题目标签…');
    try {
      // 单批 60 题 × 700ms 限流 + 网络耗时，需要 1~2 分钟
      const result = await apiRequest('POST', '/api/admin/problems/backfill-metadata?limit=60', null, 300000);
      setStatus(`回填完成：更新 ${Number(result.updated || 0)} 题，跳过 ${Number(result.skipped || 0)} 题，失败 ${Number(result.failed || 0)} 题，剩余 ${Number(result.remaining || 0)} 题。` +
        (Number(result.failed || 0) > 0 ? ' 失败题目可稍后重试。' : ' 返回本站后刷新首页即可查看。'));
    } catch (error) {
      setStatus(error instanceof Error ? `${error.message}（请确认当前账号是站主）` : '标签回填失败');
    } finally {
      busy = false;
    }
  }

  function applyCollapsed(nextState) {
    if (!panel) return;
    const collapsed = nextState === undefined
      ? panel.dataset.collapsed === 'true'
      : Boolean(nextState);
    const collapseButton = panel.querySelector('[data-collapse]');
    const collapsedBall = panel.querySelector('[data-collapsed-ball]');
    panel.dataset.collapsed = String(collapsed);
    if (collapseButton) {
      collapseButton.textContent = collapsed ? '+' : '—';
      collapseButton.title = collapsed ? '展开' : '收起';
      collapseButton.setAttribute('aria-label', collapsed ? '展开洛谷同步' : '收起洛谷同步');
    }
    if (collapsedBall) {
      collapsedBall.setAttribute('aria-label', '展开洛谷同步');
    }
  }

  function setStatus(message) {
    if (panel) panel.querySelector('[data-status]').textContent = message;
  }

  function setToken() {
    const token = prompt('粘贴校队平台 Access Token（个人中心可查，到期后需重新设置，不会发送到洛谷）：',
      String(GM_getValue(KEY_TOKEN, '')));
    if (token && token.trim()) {
      GM_setValue(KEY_TOKEN, token.trim());
      setStatus('令牌已保存 ✓');
    }
  }

  function mount() {
    panel = document.createElement('div');
    panel.setAttribute('data-hwsuanfa-panel', '');
    panel.dataset.collapsed = String(GM_getValue(KEY_COLLAPSED, 'false')) === 'true';
    panel.innerHTML = `
      <style>
        [data-hwsuanfa-panel] {
          position: fixed; right: 18px; bottom: 18px; z-index: 2147483647;
          width: 300px; padding: 0; color: #e8eaed;
          background: linear-gradient(160deg, #1c1f2e 0%, #232741 100%);
          border: 1px solid rgba(124,140,248,.35);
          border-radius: 14px;
          box-shadow: 0 8px 32px rgba(0,0,0,.45), 0 0 0 1px rgba(255,255,255,.04) inset;
          font: 13px/1.5 system-ui, sans-serif;
          overflow: hidden;
        }
        [data-hwsuanfa-panel][data-collapsed="true"] { width: 46px; border-radius: 50%; }
        [data-hwsuanfa-panel] [data-head] {
          display: flex; align-items: center; justify-content: space-between;
          padding: 10px 12px;
          background: linear-gradient(90deg, rgba(124,140,248,.22), rgba(34,211,238,.12));
          border-bottom: 1px solid rgba(255,255,255,.08);
        }
        [data-hwsuanfa-panel] [data-head] strong { margin: 0; font-size: 13px; letter-spacing: .02em; }
        [data-hwsuanfa-panel] [data-head] button {
          border: none; background: rgba(255,255,255,.08); color: #e8eaed;
          width: 22px; height: 22px; border-radius: 6px; cursor: pointer;
          display: flex; align-items: center; justify-content: center; font-size: 13px;
        }
        [data-hwsuanfa-panel] [data-head] button:hover { background: rgba(255,255,255,.18); }
        [data-hwsuanfa-panel] [data-body] { padding: 12px; }
        [data-hwsuanfa-panel] p { margin: 0 0 10px; color: #9aa3b5; word-break: break-word; }
        [data-hwsuanfa-panel] button { padding: 6px 10px; border: 1px solid rgba(255,255,255,.14); border-radius: 7px; background: rgba(255,255,255,.06); color: #e8eaed; cursor: pointer; transition: all .15s; }
        [data-hwsuanfa-panel] button:hover { background: rgba(255,255,255,.14); }
        [data-hwsuanfa-panel] button[data-sync] {
          border: none; color: #fff; font-weight: 500;
          background: linear-gradient(135deg, #6366f1, #3b82f6);
          box-shadow: 0 2px 10px rgba(99,102,241,.4);
        }
        [data-hwsuanfa-panel] button[data-sync]:hover { filter: brightness(1.15); }
        [data-hwsuanfa-panel] [data-collapsed-ball] { display: none; }
        [data-hwsuanfa-panel][data-collapsed="true"] [data-head],
        [data-hwsuanfa-panel][data-collapsed="true"] [data-body] { display: none; }
        [data-hwsuanfa-panel][data-collapsed="true"] [data-collapsed-ball] {
          display: flex; align-items: center; justify-content: center;
          width: 38px; height: 38px; margin: 0; border: none; border-radius: 50%;
          background: linear-gradient(135deg, #6366f1, #3b82f6);
          color: #fff; font-size: 17px; cursor: pointer;
          box-shadow: 0 4px 14px rgba(99,102,241,.5);
          transition: transform .15s;
        }
        [data-hwsuanfa-panel][data-collapsed="true"] [data-collapsed-ball]:hover { transform: scale(1.12); }
      </style>
      <div data-head>
        <strong>🎯 洛谷同步</strong>
        <button type="button" data-collapse title="收起">—</button>
      </div>
      <div data-body>
        <p data-status>未同步。首次使用请先设置平台令牌。</p>
        <button type="button" data-token>设置令牌</button>
        <button type="button" data-sync>同步 AC 记录</button>
        <button type="button" data-metadata>回填题目标签</button>
      </div>
      <button type="button" data-collapsed-ball title="展开洛谷同步">📋</button>`;
    document.body.appendChild(panel);
    panel.querySelector('[data-token]').addEventListener('click', setToken);
    panel.querySelector('[data-sync]').addEventListener('click', sync);
    panel.querySelector('[data-metadata]').addEventListener('click', backfillMetadata);
    panel.querySelector('[data-collapse]').addEventListener('click', () => {
      GM_setValue(KEY_COLLAPSED, 'true');
      applyCollapsed(true);
    });
    panel.querySelector('[data-collapsed-ball]').addEventListener('click', () => {
      GM_setValue(KEY_COLLAPSED, 'false');
      applyCollapsed(false);
    });
    applyCollapsed();
  }

  if (document.body) mount();
  else window.addEventListener('DOMContentLoaded', mount, { once: true });
})();
