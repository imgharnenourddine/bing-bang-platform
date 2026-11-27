import { test } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { fileURLToPath, URL } from 'node:url';
import { Buffer } from 'node:buffer';

async function load(entry, mockApi = false) {
  const result = await build({ entryPoints: [fileURLToPath(new URL(entry, import.meta.url))], bundle: true, write: false, platform: 'node', format: 'esm', plugins: mockApi ? [{
    name: 'mock-api', setup(builder) {
      builder.onResolve({ filter: /services\/api$/ }, () => ({ path: 'api', namespace: 'mock' }));
      builder.onLoad({ filter: /.*/, namespace: 'mock' }, () => ({ contents: `
        export class ApiError extends Error { constructor(status) { super(); this.status=status; } }
        globalThis.__MockApiError = ApiError;
        export const api={ get: (...args)=>globalThis.__apiGet(...args), post: (...args)=>globalThis.__apiPost(...args) };
      `, loader: 'js' }));
    },
  }] : [] });
  return import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
}

test('required names, whitespace, Unicode and maximum length', async () => {
  const { validateName } = await load('../src/features/participant/validation.ts');
  assert.ok(validateName('   ', 'First name'));
  assert.ok(validateName('', 'Last name'));
  assert.equal(validateName('  أسماء  ', 'First name'), undefined);
  assert.equal(validateName("Jean-Pierre O’Connor", 'Last name'), undefined);
  assert.ok(validateName('a'.repeat(101), 'First name'));
});

test('session recognition, registration responses and failures', async () => {
  const module = await load('../src/features/participant/registrationApi.ts', true);
  const identity = { id: 'id', participantNumber: 42, firstName: 'أحمد', lastName: 'Alami', role: 'PARTICIPANT' };
  globalThis.__apiGet = async path => { assert.equal(path, '/auth/me'); return { data: identity }; };
  assert.deepEqual(await module.fetchIdentity(), identity);
  const admin = { id: 'admin-id', username: 'admin', role: 'ADMIN' };
  globalThis.__apiGet = async () => ({ data: admin });
  assert.deepEqual(await module.fetchIdentity(), admin);
  globalThis.__apiGet = async () => { throw new globalThis.__MockApiError(401); };
  assert.equal(await module.fetchIdentity(), null);
  globalThis.__apiPost = async (path, data) => { assert.equal(path, '/participants/register'); assert.deepEqual(data, { firstName: 'أحمد', lastName: 'Alami' }); return { status: 201, data: identity }; };
  assert.deepEqual(await module.registerParticipant('أحمد', 'Alami'), { identity, created: true });
  globalThis.__apiPost = async () => ({ status: 200, data: identity });
  assert.deepEqual(await module.registerParticipant('أحمد', 'Alami'), { identity, created: false });
  globalThis.__apiPost = async () => { throw new Error('network'); };
  await assert.rejects(module.registerParticipant('أحمد', 'Alami'), /network/);
  globalThis.__apiGet = async () => { throw new Error('network'); };
  await assert.rejects(module.fetchIdentity(), /network/);
});

test('submission gate blocks a second simultaneous registration and allows retry', async () => {
  const { createSubmissionGate } = await load('../src/features/participant/registrationApi.ts', true);
  const gate = createSubmissionGate();
  assert.equal(gate.start(), true);
  assert.equal(gate.start(), false);
  gate.finish();
  assert.equal(gate.start(), true);
});
