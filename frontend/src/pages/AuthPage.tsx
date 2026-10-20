import { useState, type FormEvent } from 'react';
import { Activity, ArrowUpRight, Check, ShieldCheck } from 'lucide-react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Button, ErrorNotice } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setError('');
    if (mode === 'register' && password.length < 12) { setError('Use a password with at least 12 characters.'); return; }
    if (mode === 'register' && password !== confirm) { setError('The passwords do not match.'); return; }
    setBusy(true);
    try {
      if (mode === 'login') await login(email, password);
      else await register(email, password);
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      navigate(from || '/', { replace: true });
    } catch (problem) { setError(problem instanceof ApiError ? problem.message : 'Authentication failed. Try again.'); }
    finally { setBusy(false); }
  };

  const isRegister = mode === 'register';
  return <div className="auth-page">
    <section className="auth-visual"><div className="brand" style={{ padding: 0 }}><span className="brand-mark"><Activity size={17} /></span><span>API Sentinel<small>Observe · Respond · Improve</small></span></div>
      <div className="auth-visual-content"><div className="page-kicker" style={{ color: '#9d9eff' }}>RELIABILITY, AT A GLANCE</div><h1>Know when your APIs need you.</h1><p>Continuous checks, clear performance analytics, and incidents that resolve themselves when your services recover.</p><div className="auth-points"><span><Check size={15} /> Secure, isolated endpoint monitoring</span><span><Check size={15} /> Real response-time and uptime analytics</span><span><Check size={15} /> Incident alerts with delivery history</span></div></div>
      <div className="auth-footer">API Sentinel · Developer-first monitoring</div>
    </section>
    <section className="auth-panel"><div className="auth-form">
      <div className="page-kicker">{isRegister ? 'GET STARTED' : 'WELCOME BACK'}</div><h2>{isRegister ? 'Create your account' : 'Sign in to Sentinel'}</h2><p>{isRegister ? 'Set up your private monitoring workspace.' : 'See what is happening across your APIs.'}</p>
      {error && <div style={{ marginBottom: 14 }}><ErrorNotice message={error} /></div>}
      <form onSubmit={submit}>
        <div className="form-field"><label className="form-label" htmlFor="auth-email">Email address</label><input className="form-control" id="auth-email" type="email" autoComplete="email" required maxLength={320} placeholder="you@company.com" value={email} onChange={(event) => setEmail(event.target.value)} /></div>
        <div className="form-field"><label className="form-label" htmlFor="auth-password">Password</label><input className="form-control" id="auth-password" type="password" autoComplete={isRegister ? 'new-password' : 'current-password'} required minLength={isRegister ? 12 : undefined} maxLength={72} placeholder={isRegister ? 'At least 12 characters' : 'Enter your password'} value={password} onChange={(event) => setPassword(event.target.value)} /></div>
        {isRegister && <div className="form-field"><label className="form-label" htmlFor="auth-confirm">Confirm password</label><input className="form-control" id="auth-confirm" type="password" autoComplete="new-password" required minLength={12} maxLength={72} placeholder="Re-enter your password" value={confirm} onChange={(event) => setConfirm(event.target.value)} /></div>}
        <Button type="submit" className="auth-submit" disabled={busy}>{busy ? 'Please wait…' : <>{isRegister ? 'Create account' : 'Sign in'} <ArrowUpRight size={14} /></>}</Button>
      </form>
      <div className="auth-switch">{isRegister ? 'Already have an account?' : 'New to API Sentinel?'}<Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Sign in' : 'Create account'}</Link></div>
      <div className="alert-box alert-info" style={{ marginTop: 25, display: 'flex', gap: 9, alignItems: 'flex-start' }}><ShieldCheck size={14} /><span>Passwords are hashed with BCrypt. Endpoint credentials are encrypted before storage.</span></div>
    </div></section>
  </div>;
}
