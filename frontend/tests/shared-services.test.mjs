import { test } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { fileURLToPath, URL } from 'node:url';
import { Buffer } from 'node:buffer';
import { setImmediate } from 'node:timers';

async function bundle(entry, mocks) {
  const result = await build({
    entryPoints: [fileURLToPath(new URL(entry, import.meta.url))], bundle: true, write: false, platform: 'node', format: 'esm',
    plugins: [{ name: 'mocks', setup(builder) {
      builder.onResolve({ filter: /.*/ }, args => mocks[args.path] ? { path: args.path, namespace: 'mock' } : undefined);
      builder.onLoad({ filter: /.*/, namespace: 'mock' }, args => ({ contents: mocks[args.path], loader: 'js' }));
    } }],
  });
  return import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
}

test('API fetches a fresh CSRF header for every mutation and does not retry', async () => {
  globalThis.__csrfCount = 0;
  const axiosMock = `
    export class AxiosHeaders { static from(h) { return { ...h, set(k,v) { this[k]=v; } }; } }
    const axios = {
      create() { return {
        interceptors: { request: { use(fn) { this.handler=fn; } }, response: { use(ok,fail) { this.failure=fail; } } },
        async get() { return { data: { headerName:'X-CSRF-TOKEN', token:String(++globalThis.__csrfCount), parameterName:'_csrf' } }; }
      }; },
      isAxiosError(e) { return !!e.isAxiosError; }
    }; export default axios;`;
  const { api, ApiError } = await bundle('../src/services/api.ts', { axios: axiosMock });
  const handler = api.interceptors.request.handler;
  const first = await handler({ method: 'post', headers: {} });
  const second = await handler({ method: 'patch', headers: {} });
  await handler({ method: 'get', headers: {} });
  assert.equal(first.headers['X-CSRF-TOKEN'], '1');
  assert.equal(second.headers['X-CSRF-TOKEN'], '2');
  assert.equal(globalThis.__csrfCount, 2);
  await assert.rejects(api.interceptors.response.failure({ isAxiosError: true, response: { status: 403, data: { detail: 'Forbidden' } } }), error => error instanceof ApiError && error.status === 403);
});

test('realtime shares a connection, restores subscriptions and cleans them up', async () => {
  globalThis.__clients = [];
  globalThis.window = { location: { protocol: 'http:', host: 'localhost:5173' } };
  const timers = [];
  const oldSetTimeout = globalThis.setTimeout;
  const oldClearTimeout = globalThis.clearTimeout;
  globalThis.setTimeout = callback => { timers.push(callback); return timers.length; };
  globalThis.clearTimeout = () => {};
  const stompMock = `export class Client {
    constructor(config) { this.config=config; this.connected=false; this.calls=[]; globalThis.__clients.push(this); }
    activate() {} async deactivate() { this.connected=false; }
    subscribe(destination,callback) { const sub={destination,callback,closed:false,unsubscribe(){this.closed=true}}; this.calls.push(sub); return sub; }
  }`;
  try {
    const { realtime } = await bundle('../src/services/realtime.ts', {
      '@stomp/stompjs': stompMock,
      './api': `export async function getCsrfToken(){return {headerName:'X-CSRF-TOKEN',token:'fresh'}}`,
    });
    let connected = 0;
    const remove = realtime.subscribe('/topic/quiz', () => {});
    realtime.onConnected(() => connected++);
    realtime.start(); realtime.start();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(globalThis.__clients.length, 1);
    const first = globalThis.__clients[0];
    assert.equal(first.config.connectHeaders['X-CSRF-TOKEN'], 'fresh');
    first.connected = true; first.onConnect();
    assert.equal(first.calls.length, 1);
    first.onWebSocketClose();
    assert.equal(timers.length, 1);
    timers.shift()();
    await new Promise(resolve => setImmediate(resolve));
    const second = globalThis.__clients[1];
    second.connected = true; second.onConnect();
    assert.equal(second.calls.length, 1);
    assert.equal(connected, 2);
    remove();
    assert.equal(second.calls[0].closed, true);
    await realtime.stop();
  } finally {
    globalThis.setTimeout = oldSetTimeout;
    globalThis.clearTimeout = oldClearTimeout;
    delete globalThis.window;
  }
});
