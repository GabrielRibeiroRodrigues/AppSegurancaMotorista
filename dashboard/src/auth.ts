// Operator authentication for the Central de Operações.
// Alerts are now restricted to operators (is_staff), so the dashboard logs in
// with JWT, attaches the access token to every request, and refreshes it on 401.

export const API_URL = import.meta.env.VITE_SERVER_URL ?? 'http://localhost:8000';

const ACCESS_KEY = 'copiloto_access';
const REFRESH_KEY = 'copiloto_refresh';

export class AuthError extends Error {}

export function getAccessToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_KEY);
  } catch {
    return null;
  }
}

function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

function storeTokens(access: string, refresh?: string): void {
  try {
    localStorage.setItem(ACCESS_KEY, access);
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
  } catch {
    /* storage blocked — session lives only in memory for this tab */
  }
}

function clearTokens(): void {
  try {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  } catch {
    /* ignore */
  }
}

export function isLoggedIn(): boolean {
  return getAccessToken() !== null;
}

export interface OperatorInfo {
  username: string;
  email: string;
  is_operator: boolean;
}

/** Logs an operator in. Rejects (and clears) if the account is not an operator. */
export async function login(username: string, password: string): Promise<OperatorInfo> {
  const res = await fetch(`${API_URL}/api/auth/login/`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (res.status === 429) throw new AuthError('Muitas tentativas. Aguarde um minuto.');
  if (!res.ok) throw new AuthError('Usuário ou senha inválidos.');
  const data = await res.json();
  storeTokens(data.access, data.refresh);

  const me = await fetchMe();
  if (!me || !me.is_operator) {
    await logout();
    throw new AuthError('Esta conta não tem acesso de operador à Central.');
  }
  return me;
}

export async function fetchMe(): Promise<OperatorInfo | null> {
  try {
    const res = await authFetch('/api/auth/me/');
    if (!res.ok) return null;
    return (await res.json()) as OperatorInfo;
  } catch {
    return null;
  }
}

export async function logout(): Promise<void> {
  const refresh = getRefreshToken();
  const access = getAccessToken();
  if (refresh && access) {
    try {
      await fetch(`${API_URL}/api/auth/logout/`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${access}` },
        body: JSON.stringify({ refresh }),
      });
    } catch {
      /* best effort */
    }
  }
  clearTokens();
}

async function refreshAccess(): Promise<boolean> {
  const refresh = getRefreshToken();
  if (!refresh) return false;
  try {
    const res = await fetch(`${API_URL}/api/auth/refresh/`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh }),
    });
    if (!res.ok) return false;
    const data = await res.json();
    // Rotation is on, so the server may return a fresh refresh token too.
    storeTokens(data.access, data.refresh);
    return true;
  } catch {
    return false;
  }
}

/**
 * fetch() with the bearer token attached. On a 401 it refreshes once and retries;
 * if the refresh fails it clears the session and throws AuthError (→ login screen).
 */
export async function authFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const run = (token: string | null) =>
    fetch(`${API_URL}${path}`, {
      ...init,
      headers: {
        ...(init.headers ?? {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    });

  let res = await run(getAccessToken());
  if (res.status === 401) {
    if (await refreshAccess()) {
      res = await run(getAccessToken());
    }
    if (res.status === 401) {
      clearTokens();
      throw new AuthError('Sessão expirada. Entre novamente.');
    }
  }
  return res;
}
