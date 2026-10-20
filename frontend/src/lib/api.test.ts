import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, AUTH_EXPIRED_EVENT } from './api';

describe('API client', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('attaches the session bearer token and decodes JSON responses', async () => {
    window.sessionStorage.setItem('sentinel.token', 'signed-token');
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ healthy: true }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(api<{ healthy: boolean }>('/api/v1/account')).resolves.toEqual({ healthy: true });
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer signed-token');
    expect(new Headers(request.headers).get('Accept')).toBe('application/json');
  });

  it('surfaces safe API errors and signals an expired authenticated session', async () => {
    const expired = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, expired);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Access denied', code: 'UNAUTHORIZED' }), { status: 401 })));

    await expect(api('/api/v1/endpoints')).rejects.toMatchObject({ name: 'ApiError', status: 401, message: 'Access denied' });
    expect(expired).toHaveBeenCalledOnce();
    window.removeEventListener(AUTH_EXPIRED_EVENT, expired);
  });

  it('returns an actionable network error without exposing fetch internals', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('socket private details')));
    await expect(api('/api/v1/analytics/overview')).rejects.toBeInstanceOf(ApiError);
    await expect(api('/api/v1/analytics/overview')).rejects.toThrow('Cannot reach API Sentinel');
  });
});
