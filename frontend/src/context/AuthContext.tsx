/* eslint react-refresh/only-export-components: off */
import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react';
import { api, AUTH_EXPIRED_EVENT } from '../lib/api';
import type { AuthResponse, UserSummary } from '../lib/types';

interface AuthContextValue {
  user: UserSummary | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function storedUser(): UserSummary | null {
  try {
    const value = window.sessionStorage.getItem('sentinel.user');
    return value ? (JSON.parse(value) as UserSummary) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<UserSummary | null>(() => storedUser());
  const [loading] = useState(false);

  const logout = () => {
    window.sessionStorage.removeItem('sentinel.token');
    window.sessionStorage.removeItem('sentinel.user');
    setUser(null);
  };

  useEffect(() => {
    window.addEventListener(AUTH_EXPIRED_EVENT, logout);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, logout);
  }, []);

  const authenticate = async (path: string, email: string, password: string) => {
    const result = await api<AuthResponse>(path, {
      method: 'POST',
      body: JSON.stringify({ email: email.trim(), password }),
    });
    window.sessionStorage.setItem('sentinel.token', result.token);
    window.sessionStorage.setItem('sentinel.user', JSON.stringify(result.user));
    setUser(result.user);
  };

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    login: (email, password) => authenticate('/api/v1/auth/login', email, password),
    register: (email, password) => authenticate('/api/v1/auth/register', email, password),
    logout,
  }), [user, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
