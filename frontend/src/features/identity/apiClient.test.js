import { describe, expect, it, vi } from 'vitest';
import { createAuthClient } from './apiClient.js';

const profile = { id: 'fixture', fullName: 'Test Account', email: 'test@example.test', roles: ['CUSTOMER'] };
const reply = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => data });
const token = value => reply({ accessToken: value, tokenType: 'Bearer', expiresIn: 600 });
function serialLocks() {
  let pending = Promise.resolve();
  return { request: vi.fn((name, work) => { const run = pending.then(work); pending = run.catch(() => {}); return run; }) };
}
function server(handler = () => reply({})) {
  return vi.fn(async (url, options) => {
    if (url === '/api/v1/auth/csrf') return reply({ token: 'csrf-fixture', headerName: 'X-XSRF-TOKEN' });
    return handler(url, options);
  });
}
const calls = (fetch, url) => fetch.mock.calls.filter(call => call[0] === url);
function deferred() { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; }

describe('in-memory authentication client', () => {
  it('bootstraps once with refresh then me and does not use browser token storage', async () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    const fetch = server(url => url.endsWith('refresh') ? token('access-one') : reply(profile));
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() });
    await Promise.all([client.bootstrap(), client.bootstrap()]);
    expect(calls(fetch, '/api/v1/auth/refresh')).toHaveLength(1);
    expect(calls(fetch, '/api/v1/me')[0][1].headers.Authorization).toBe('Bearer access-one');
    expect(client.getSnapshot()).toMatchObject({ status: 'authenticated', user: profile });
    expect(storage).not.toHaveBeenCalled();
    expect(JSON.stringify(client.getSnapshot())).not.toContain('access-one');
  });
  it('shares one refresh across concurrent 401s and retries each request only once', async () => {
    let issued = 0;
    const fetch = server((url, options) => {
      if (url.endsWith('refresh')) return token(`access-${++issued}`);
      if (url.endsWith('/me')) return reply(profile);
      return options.headers.Authorization === 'Bearer access-1' ? reply({}, 401) : reply({ success: true });
    });
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() }); await client.bootstrap();
    const responses = await Promise.all([client.request('/api/one'), client.request('/api/two')]);
    expect(responses).toEqual([{ success: true }, { success: true }]);
    expect(calls(fetch, '/api/v1/auth/refresh')).toHaveLength(2); // bootstrap plus one renewal
    expect(calls(fetch, '/api/one')).toHaveLength(2);
    expect(calls(fetch, '/api/two')).toHaveLength(2);
  });
  it('does not refresh on a 403 or send access tokens to auth endpoints or external URLs', async () => {
    const fetch = server(url => url.endsWith('refresh') ? token('access') : url.endsWith('/me') ? reply(profile) : reply({ message: 'Forbidden' }, 403));
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() }); await client.bootstrap();
    await expect(client.request('/api/private')).rejects.toMatchObject({ status: 403 });
    await expect(client.request('https://elsewhere.test/api')).rejects.toThrow();
    await expect(client.request('/api/v1/auth/refresh')).rejects.toThrow();
    expect(calls(fetch, '/api/v1/auth/refresh')).toHaveLength(1);
    expect(client.getSnapshot().status).toBe('authenticated');
  });
  it('stops after a failed retry and clears an unusable session', async () => {
    const fetch = server(url => url.endsWith('refresh') ? token('access') : url.endsWith('/me') ? reply(profile) : reply({}, 401));
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() }); await client.bootstrap();
    await expect(client.request('/api/private')).rejects.toMatchObject({ status: 401 });
    expect(calls(fetch, '/api/v1/auth/refresh')).toHaveLength(2);
    expect(calls(fetch, '/api/private')).toHaveLength(2);
    expect(client.getSnapshot().status).toBe('anonymous');
  });
  it('never retries an ambiguous refresh network failure', async () => {
    const fetch = server(() => { throw new TypeError('network'); });
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() }); await client.bootstrap();
    expect(calls(fetch, '/api/v1/auth/refresh')).toHaveLength(1);
    expect(client.getSnapshot().status).toBe('anonymous');
    await expect(client.request('/api/private')).rejects.toMatchObject({ status: 401 });
    expect(calls(fetch, '/api/v1/auth/refresh')).toHaveLength(1);
  });
  it('serializes cookie rotation across independent tabs sharing Web Locks', async () => {
    const locks = serialLocks(); const gate = deferred(); const started = deferred(); let rotating = 0; let peak = 0;
    const fetch = server(async url => {
      if (url.endsWith('refresh')) { rotating++; peak = Math.max(peak, rotating); started.resolve(); await gate.promise; rotating--; return token('access'); }
      return reply(profile);
    });
    const a = createAuthClient({ fetchImpl: fetch, locks }); const b = createAuthClient({ fetchImpl: fetch, locks });
    const both = Promise.all([a.bootstrap(), b.bootstrap()]); await started.promise;
    expect(calls(fetch, '/api/v1/auth/refresh')).toHaveLength(1);
    gate.resolve(); await both; expect(peak).toBe(1);
    expect(a.getSnapshot().status).toBe('authenticated'); expect(b.getSnapshot().status).toBe('authenticated');
  });
  it('logout waits for an in-flight rotation and cannot be undone by its late result', async () => {
    let rotations = 0; const gate = deferred(); const started = deferred();
    const fetch = server(async url => {
      if (url.endsWith('refresh')) { if (++rotations > 1) { started.resolve(); await gate.promise; } return token(`access-${rotations}`); }
      if (url.endsWith('logout')) return reply(null, 204);
      if (url.endsWith('/me')) return reply(profile);
      return reply({}, 401);
    });
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() }); await client.bootstrap();
    const pending = client.request('/api/private').catch(error => error); await started.promise;
    const logout = client.logout(); expect(client.getSnapshot().status).toBe('anonymous');
    gate.resolve(); await Promise.all([pending, logout]);
    expect(client.getSnapshot().status).toBe('anonymous');
    expect(calls(fetch, '/api/v1/auth/logout')).toHaveLength(1);
  });
  it('broadcasts only invalidation and clears another tab on logout', async () => {
    const channel = { postMessage: vi.fn(), onmessage: null };
    const fetch = server(url => url.endsWith('login') ? token('private-access') : reply(profile));
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks(), channel });
    await client.login('test@example.test', ' exact password ');
    expect(channel.postMessage).toHaveBeenCalledWith({ type: 'identity-changed' });
    expect(JSON.parse(calls(fetch, '/api/v1/auth/login')[0][1].body)).toEqual({ email: 'test@example.test', password: ' exact password ' });
    channel.onmessage({ data: { type: 'identity-changed' } });
    expect(client.getSnapshot().status).toBe('anonymous');
  });
  it('fails closed on renewal without cross-tab locking support', async () => {
    const fetch = server(); const client = createAuthClient({ fetchImpl: fetch, locks: null });
    await client.bootstrap(); expect(fetch).not.toHaveBeenCalled(); expect(client.getSnapshot().status).toBe('anonymous');
  });
  it('includes CSRF protection on authenticated mutations', async () => {
    const fetch = server(url => url.endsWith('refresh') ? token('access') : url.endsWith('/me') ? reply(profile) : reply({ success: true }));
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() }); await client.bootstrap();
    await client.request('/api/resource', { method: 'PATCH', body: '{}' });
    expect(calls(fetch, '/api/resource')[0][1].headers).toMatchObject({ Authorization: 'Bearer access', 'X-XSRF-TOKEN': 'csrf-fixture' });
  });
  it('retains a retryable sign-out error if server revocation cannot be confirmed', async () => {
    const fetch = server(url => url.endsWith('login') ? token('access') : url.endsWith('/me') ? reply(profile) : reply({}, 500));
    const client = createAuthClient({ fetchImpl: fetch, locks: serialLocks() }); await client.login('test@example.test', 'password');
    await expect(client.logout()).rejects.toMatchObject({ status: 500 });
    expect(client.getSnapshot()).toMatchObject({ status: 'anonymous', user: null });
    expect(client.getSnapshot().error).toContain('retry sign-out');
  });
});
