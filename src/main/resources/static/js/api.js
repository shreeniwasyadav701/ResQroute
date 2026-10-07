/* Thin REST client: token in localStorage, JSON everywhere, 401 -> logout. */
const API = (() => {
  const TOKEN_KEY = 'resqroute_token';
  const USER_KEY = 'resqroute_user';

  const token = () => localStorage.getItem(TOKEN_KEY);
  const user = () => {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  };

  function setSession(t, u) {
    localStorage.setItem(TOKEN_KEY, t);
    localStorage.setItem(USER_KEY, JSON.stringify(u));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  async function request(path, options = {}) {
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    if (token()) headers.Authorization = 'Bearer ' + token();

    const res = await fetch(path, Object.assign({}, options, { headers }));
    let body = null;
    try { body = await res.json(); } catch (e) { /* non json */ }

    if (res.status === 401 && !path.startsWith('/api/auth/login')) {
      clearSession();
      window.dispatchEvent(new Event('resq:unauthorized'));
      throw new Error((body && body.message) || 'Authentication required');
    }
    if (!res.ok) {
      throw new Error((body && body.message) || ('Request failed: ' + res.status));
    }
    return body;
  }

  const get = (path) => request(path);
  const post = (path, data) => request(path, { method: 'POST', body: JSON.stringify(data || {}) });
  const patch = (path) => request(path, { method: 'PATCH' });

  return { get, post, patch, token, user, setSession, clearSession };
})();
