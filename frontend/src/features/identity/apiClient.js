export class ApiError extends Error {
  constructor(message, status = 0, code = '', fieldErrors = []) {
    super(message); this.status = status; this.code = code; this.fieldErrors = fieldErrors;
  }
}

export async function jsonRequest(path, options = {}, fetchImpl = globalThis.fetch) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetchImpl(path, { ...options, credentials: 'same-origin', signal: controller.signal });
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) throw new ApiError(data?.message || 'Unable to complete the request.', response.status, data?.code, data?.fieldErrors);
    return data;
  } catch (error) {
    if (error.name === 'AbortError') throw new ApiError('The request timed out. Please check before trying again.');
    if (error instanceof TypeError) throw new ApiError('Unable to connect. Check your connection and try again.');
    throw error;
  } finally { clearTimeout(timeout); }
}

export async function authPost(path, body, fetchImpl = globalThis.fetch) {
  const csrf = await jsonRequest('/api/v1/auth/csrf', {}, fetchImpl);
  if (!csrf?.headerName || !csrf?.token) throw new ApiError('Unable to prepare authentication. Please try again.');
  return jsonRequest(path, {
    method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  }, fetchImpl);
}

// Each instance owns one in-memory identity. Neither token is put in web storage or URLs.
export function createAuthClient({ fetchImpl = (...args) => globalThis.fetch(...args),
  locks = globalThis.navigator?.locks, channel = null } = {}) {
  let accessToken = null;
  let generation = 0;
  let refreshPromise = null;
  let bootstrapPromise = null;
  let snapshot = { status: 'loading', user: null, error: '' };
  const listeners = new Set();
  const publish = next => { snapshot = next; listeners.forEach(listener => listener()); };
  const clear = (message = '') => {
    generation++; accessToken = null;
    publish({ status: 'anonymous', user: null, error: message });
  };
  const expired = () => new ApiError('Your session has ended. Please sign in again.', 401, 'SESSION_EXPIRED');
  const locked = work => locks?.request ? locks.request('swiftdrop-auth-cookie', work) : work();
  const storeToken = data => {
    if (typeof data?.accessToken !== 'string' || data.tokenType !== 'Bearer' || !(data.expiresIn > 0)) {
      throw new ApiError('Unable to establish your session. Please sign in again.');
    }
    accessToken = data.accessToken;
  };
  const announceChange = () => channel?.postMessage({ type: 'identity-changed' });
  if (channel) channel.onmessage = event => {
    if (event.data?.type === 'identity-changed') clear();
  };

  function refresh() {
    if (refreshPromise) return refreshPromise;
    const started = generation;
    refreshPromise = (async () => {
      try {
        // Without cross-tab locking, fail closed instead of risking a consumed-token replay.
        if (!locks?.request) throw new ApiError('Session renewal requires a browser with Web Locks support. Please sign in again.');
        await locked(async () => {
          if (started !== generation) throw expired();
          const data = await authPost('/api/v1/auth/refresh', undefined, fetchImpl);
          if (started !== generation) throw expired();
          storeToken(data);
        });
      } catch (error) {
        if (started === generation) {
          clear(error.status === 401 ? '' : 'Your session could not be restored. Please sign in again.');
          // No retry: a network failure may have happened after the server consumed the token.
        }
        throw error;
      }
    })().finally(() => { refreshPromise = null; });
    return refreshPromise;
  }

  async function request(path, options = {}) {
    if (!path.startsWith('/api/') || path.startsWith('/api/v1/auth/')) throw new Error('Use the explicit authentication operations for auth endpoints.');
    const started = generation;
    const usedToken = accessToken;
    if (!usedToken) throw expired();
    const send = async () => {
      const headers = { ...options.headers, Authorization: `Bearer ${accessToken}` };
      if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
        const csrf = await jsonRequest('/api/v1/auth/csrf', {}, fetchImpl);
        if (!csrf?.headerName || !csrf?.token) throw new ApiError('Unable to prepare the request.');
        headers[csrf.headerName] = csrf.token;
      }
      return jsonRequest(path, { ...options, headers }, fetchImpl);
    };
    try {
      const result = await send();
      if (started !== generation) throw expired();
      return result;
    } catch (error) {
      if (error.status !== 401 || started !== generation) throw error; // 403 never triggers refresh.
      if (accessToken === usedToken) await refresh(); // Another request may already have renewed it.
      if (started !== generation || !accessToken) throw expired();
      try {
        const result = await send(); // Exactly one retry; never recurse.
        if (started !== generation) throw expired();
        return result;
      } catch (retryError) { if (retryError.status === 401 && started === generation) clear(); throw retryError; }
    }
  }

  async function loadProfile(started) {
    // This request intentionally cannot initiate another refresh after bootstrap/login.
    const user = await jsonRequest('/api/v1/me', { headers: { Authorization: `Bearer ${accessToken}` } }, fetchImpl);
    if (started !== generation) throw expired();
    publish({ status: 'authenticated', user, error: '' });
    return user;
  }

  function bootstrap() {
    if (bootstrapPromise) return bootstrapPromise; // React StrictMode also shares this operation.
    bootstrapPromise = (async () => {
      const started = generation;
      try { await refresh(); await loadProfile(started); }
      catch (error) { if (started === generation) clear(error.status === 401 ? '' : 'Please sign in to continue.'); }
    })();
    return bootstrapPromise;
  }

  async function login(email, password) {
    clear(); const started = generation;
    try {
      await locked(async () => {
        if (started !== generation) throw expired();
        const data = await authPost('/api/v1/auth/login', { email, password }, fetchImpl);
        if (started !== generation) throw expired();
        storeToken(data);
        announceChange(); // Other tabs must not display a previous user's identity.
      });
      return await loadProfile(started);
    } catch (error) { if (started === generation) clear(); throw error; }
  }

  async function logout() {
    clear(); announceChange(); const started = generation;
    try {
      // Queue behind any in-flight refresh so logout revokes the newest cookie's session.
      await locked(() => authPost('/api/v1/auth/logout', undefined, fetchImpl));
    } catch (error) {
      if (started === generation) publish({ status: 'anonymous', user: null, error: 'Sign-out could not be confirmed. Check your connection and retry sign-out.' });
      throw error;
    }
  }
  async function updateProfile(changes) {
    const profile = await request('/api/v1/me', {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(changes),
    });
    if (snapshot.status === 'authenticated') publish({ ...snapshot, user: profile });
    return profile;
  }
  async function reloadProfile() {
    const user = await request('/api/v1/me');
    if (snapshot.status === 'authenticated') publish({ ...snapshot, user });
    return user;
  }
  return { subscribe: listener => { listeners.add(listener); return () => listeners.delete(listener); },
    getSnapshot: () => snapshot, bootstrap, login, logout, request, updateProfile, reloadProfile };
}

const authChannel = typeof window !== 'undefined' && window.BroadcastChannel ? new window.BroadcastChannel('swiftdrop-identity') : null;
export const authClient = createAuthClient({ channel: authChannel });
