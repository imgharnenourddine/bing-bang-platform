import { test } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { fileURLToPath, URL } from 'node:url';
import { Buffer } from 'node:buffer';
const result = await build({ entryPoints: [fileURLToPath(new URL('../src/features/admin/adminApi.ts', import.meta.url))], bundle: true, write: false, platform: 'node', format: 'esm', plugins: [{
  name: 'mock-transport', setup(builder) {
    builder.onResolve({filter: /services\/api$/}, () => ({path: 'api', namespace: 'mock'}));
    builder.onResolve({filter: /participant\/registrationApi$/}, () => ({path: 'identity', namespace: 'mock'}));
    builder.onLoad({filter: /api/, namespace: 'mock'}, () => ({contents: 'export const api = { post: (...args) => globalThis.__adminPost(...args) };', loader:'js'}));
    builder.onLoad({filter: /identity/, namespace: 'mock'}, () => ({contents: 'export const fetchIdentity = () => globalThis.__adminIdentity();', loader:'js'}));
  },
}] });
const { loginAdministrator, logoutSession, validateCredentials, ParticipantSessionError, InvalidAdminSessionError } = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
const admin = {id:'admin-id', username:'admin', role:'ADMIN'};
test('required credentials reject empty and whitespace inputs', () => {
  assert.ok(validateCredentials(' ', '').username); assert.ok(validateCredentials('', ' ').password);
  assert.deepEqual(validateCredentials('admin', 'password'), {username:'', password:''});
});
test('login posts credentials once and confirms server session and role', async () => {
  let reads=0; let posts=0;
  globalThis.__adminIdentity = async () => ++reads === 1 ? null : admin;
  globalThis.__adminPost = async (path, body) => { posts++; assert.equal(path, '/auth/admin/login'); assert.deepEqual(body, {username:'admin',password:'password'}); return {data:admin}; };
  assert.deepEqual(await loginAdministrator('admin','password'),admin); assert.equal(posts,1); assert.equal(reads,2);
});
test('existing participant session prevents login and existing admin is restored', async () => {
  let posts=0; globalThis.__adminPost = async () => { posts++; };
  globalThis.__adminIdentity = async () => ({id:'p', role:'PARTICIPANT'});
  await assert.rejects(loginAdministrator('admin','password'), ParticipantSessionError); assert.equal(posts,0);
  globalThis.__adminIdentity = async () => admin;
  assert.deepEqual(await loginAdministrator('admin','password'),admin); assert.equal(posts,0);
});
test('rejects wrong role or missing verified session and surfaces failures without retry', async () => {
  let calls=0; globalThis.__adminIdentity = async () => null;
  globalThis.__adminPost = async () => { calls++; return {data:{...admin,role:'PARTICIPANT'}}; };
  await assert.rejects(loginAdministrator('a','b'), InvalidAdminSessionError); assert.equal(calls,1);
  globalThis.__adminPost = async () => ({data:admin});
  await assert.rejects(loginAdministrator('a','b'), InvalidAdminSessionError);
  globalThis.__adminPost = async () => { throw new Error('invalid credentials'); };
  await assert.rejects(loginAdministrator('a','b'), /invalid credentials/);
  globalThis.__adminIdentity = async () => { throw new Error('network'); };
  await assert.rejects(loginAdministrator('a','b'), /network/);
});
test('logout uses the shared API', async () => {
  globalThis.__adminPost = async path => { assert.equal(path,'/auth/logout'); return {}; };
  await logoutSession();
});
