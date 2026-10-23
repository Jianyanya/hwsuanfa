const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');
const axios = require('axios');

function loadHttp(adapter) {
  const values = new Map([
    ['hwsuanfa.access_token', 'old-access'],
    ['hwsuanfa.refresh_token', 'old-refresh'],
  ]);
  global.localStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  };
  const previousAdapter = axios.defaults.adapter;
  axios.defaults.adapter = adapter;
  const filename = path.resolve(__dirname, '../src/lib/http.ts');
  const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
  }).outputText;
  const loaded = new Module(filename, module);
  loaded.filename = filename;
  loaded.paths = Module._nodeModulePaths(path.dirname(filename));
  loaded._compile(source, filename);
  axios.defaults.adapter = previousAdapter;
  return loaded.exports;
}

function success(config, data) {
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, message: '成功', data } };
}

function failure(config, status, message = '登录已过期') {
  throw new axios.AxiosError(message, 'ERR_BAD_RESPONSE', config, undefined, {
    config, status, statusText: 'Failure', headers: {}, data: { code: status, message, data: null },
  });
}

test('并发过期请求只刷新一次并重试原请求', async () => {
  let refreshCount = 0;
  let protectedCount = 0;
  const http = loadHttp(async (config) => {
    if (config.url === '/auth/refresh') {
      refreshCount++;
      assert.equal(JSON.parse(config.data).refreshToken, 'old-refresh');
      await new Promise((resolve) => setImmediate(resolve));
      return success(config, { accessToken: 'new-access', refreshToken: 'new-refresh', user: { id: 1 } });
    }
    protectedCount++;
    if (config.headers.get('Authorization') === 'Bearer old-access') failure(config, 401);
    assert.equal(config.headers.get('Authorization'), 'Bearer new-access');
    return success(config, config.url);
  });
  assert.deepEqual(await Promise.all([http.get('/one'), http.get('/two')]), ['/one', '/two']);
  assert.equal(refreshCount, 1);
  assert.equal(protectedCount, 4);
  assert.equal(localStorage.getItem(http.REFRESH_KEY), 'new-refresh');
});

test('登录失败和限流保留原会话并显示服务器消息', async () => {
  const http = loadHttp(async (config) => failure(config, config.url === '/auth/login' ? 429 : 401, '尝试次数过多，请稍后重试'));
  let expired = 0;
  http.setSessionHandlers(() => expired++, () => {});
  await assert.rejects(http.post('/auth/login', {}), (error) => error.status === 429 && error.message === '尝试次数过多，请稍后重试');
  await assert.rejects(http.post('/auth/register', {}), (error) => error.status === 401);
  assert.equal(localStorage.getItem(http.TOKEN_KEY), 'old-access');
  assert.equal(localStorage.getItem(http.REFRESH_KEY), 'old-refresh');
  assert.equal(expired, 0);
});

test('刷新网络故障保留双令牌供稍后重试', async () => {
  const http = loadHttp(async (config) => {
    if (config.url === '/auth/refresh') throw new axios.AxiosError('Network failure', 'ERR_NETWORK', config);
    failure(config, 401);
  });
  let expired = 0;
  http.setSessionHandlers(() => expired++, () => {});
  await assert.rejects(http.get('/protected'), /网络异常/);
  assert.equal(localStorage.getItem(http.TOKEN_KEY), 'old-access');
  assert.equal(localStorage.getItem(http.REFRESH_KEY), 'old-refresh');
  assert.equal(expired, 0);
});

test('刷新令牌失效清除会话，禁止无限重试', async () => {
  let attempts = 0;
  const http = loadHttp(async (config) => { attempts++; failure(config, 401); });
  let expired = 0;
  http.setSessionHandlers(() => expired++, () => {});
  await assert.rejects(http.get('/protected'), (error) => error.status === 401);
  assert.equal(attempts, 2);
  assert.equal(expired, 1);
  assert.equal(localStorage.getItem(http.TOKEN_KEY), null);
  assert.equal(localStorage.getItem(http.REFRESH_KEY), null);
});

test('切换账号期间旧刷新响应不能覆盖新账号令牌', async () => {
  let release;
  let started;
  const waiting = new Promise((resolve) => { started = resolve; });
  const http = loadHttp(async (config) => {
    if (config.url !== '/auth/refresh') failure(config, 401);
    started();
    await new Promise((resolve) => { release = resolve; });
    return success(config, { accessToken: 'obsolete-access', refreshToken: 'obsolete-refresh', user: { id: 1 } });
  });
  const original = http.get('/protected');
  await waiting;
  http.markSessionChange();
  localStorage.setItem(http.TOKEN_KEY, 'other-access');
  localStorage.setItem(http.REFRESH_KEY, 'other-refresh');
  const rejected = assert.rejects(original, /登录状态已切换/);
  release();
  await rejected;
  assert.equal(localStorage.getItem(http.TOKEN_KEY), 'other-access');
  assert.equal(localStorage.getItem(http.REFRESH_KEY), 'other-refresh');
});

test('HTTP 200 的业务失败不会解包为成功', async () => {
  const http = loadHttp(async (config) => ({ ...success(config, null), data: { code: 4001, message: '业务失败', data: null } }));
  await assert.rejects(http.get('/business'), (error) => error.code === 4001 && error.message === '业务失败');
});
