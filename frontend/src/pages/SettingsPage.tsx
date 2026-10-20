import { useEffect, useState, type FormEvent } from 'react';
import { KeyRound, LogOut, ShieldCheck, UserRound } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button, ErrorNotice, PageHeader, SuccessNotice } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { api, ApiError } from '../lib/api';
import { formatDate } from '../lib/format';
import type { Account } from '../lib/types';

export function SettingsPage() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [account, setAccount] = useState<Account | null>(null);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    let live = true;
    api<Account>('/api/v1/account').then((value) => { if (live) setAccount(value); })
      .catch((problem: unknown) => { if (live) setError(problem instanceof ApiError ? problem.message : 'Could not load account settings.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, []);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setError(''); setSuccess('');
    if (newPassword.length < 12) { setError('Use at least 12 characters for your new password.'); return; }
    if (newPassword !== confirmPassword) { setError('The new password and confirmation do not match.'); return; }
    setSaving(true);
    try {
      await api<void>('/api/v1/account/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) });
      setSuccess('Password changed. All previous access tokens have been revoked; sign in again with your new password.');
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
      window.setTimeout(() => { logout(); navigate('/login', { replace: true }); }, 1200);
    } catch (problem) { setError(problem instanceof ApiError ? problem.message : 'Could not update password.'); }
    finally { setSaving(false); }
  };

  return <>
    <PageHeader kicker="Preferences" title="Account settings" subtitle="Manage your profile and account security." />
    {error && <div style={{ marginBottom: 14 }}><ErrorNotice message={error} /></div>}
    {success && <div style={{ marginBottom: 14 }}><SuccessNotice>{success}</SuccessNotice></div>}
    <div className="settings-grid">
      <nav className="card settings-menu" aria-label="Settings sections"><a className="settings-menu-item active" href="#profile"><UserRound size={14} /> Profile</a><a className="settings-menu-item" href="#security"><KeyRound size={14} /> Security</a></nav>
      <div className="grid" style={{ gap: 15 }}>
        <section className="card settings-section" id="profile"><h2>Profile</h2><p>Your account identity and registration details.</p>
          {loading ? <div className="skeleton" style={{ height: 40 }} /> : account && <><div className="info-row"><span>Email address</span><span>{account.email}</span></div><div className="info-row"><span>Member since</span><span>{formatDate(account.createdAt, false)}</span></div><div className="info-row"><span>Account ID</span><span style={{ fontFamily: 'ui-monospace,monospace', fontSize: 9 }}>{account.id}</span></div></>}
        </section>
        <section className="card settings-section" id="security"><h2>Security</h2><p>Change your password. Existing access tokens will be revoked immediately.</p>
          <form onSubmit={submit} style={{ maxWidth: 440 }}><div className="form-field" style={{ marginBottom: 14 }}><label className="form-label" htmlFor="current-password">Current password</label><input className="form-control" id="current-password" type="password" autoComplete="current-password" required value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></div><div className="form-field" style={{ marginBottom: 14 }}><label className="form-label" htmlFor="new-password">New password</label><input className="form-control" id="new-password" type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /><div className="form-hint">At least 12 characters (maximum 72 UTF-8 bytes for BCrypt).</div></div><div className="form-field" style={{ marginBottom: 16 }}><label className="form-label" htmlFor="confirm-password">Confirm new password</label><input className="form-control" id="confirm-password" type="password" autoComplete="new-password" required value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /></div><Button type="submit" disabled={saving}>{saving ? 'Updating…' : <><ShieldCheck size={14} /> Update password</>}</Button></form>
        </section>
        <section className="card settings-section"><h2>Session</h2><p>API Sentinel uses short-lived, signed bearer tokens in this browser session.</p><Button variant="secondary" onClick={() => { logout(); navigate('/login', { replace: true }); }}><LogOut size={14} /> Sign out</Button></section>
      </div>
    </div>
  </>;
}
