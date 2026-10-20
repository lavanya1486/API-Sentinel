import { useState } from 'react';
import { Activity, AlertTriangle, BellRing, LayoutDashboard, LogOut, Menu, Server, Settings, X } from 'lucide-react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const navItems = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/endpoints', label: 'Endpoints', icon: Server },
  { to: '/incidents', label: 'Incidents', icon: AlertTriangle },
  { to: '/alerts', label: 'Alerts & webhooks', icon: BellRing },
  { to: '/settings', label: 'Account settings', icon: Settings },
];

function displayTitle(pathname: string): string {
  if (pathname.startsWith('/endpoints/')) return 'Endpoint detail';
  if (pathname.startsWith('/incidents/')) return 'Incident detail';
  return navItems.find((item) => item.end ? pathname === item.to : pathname.startsWith(item.to))?.label ?? 'API Sentinel';
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const initials = user?.email?.slice(0, 1).toUpperCase() ?? 'S';

  const handleLogout = () => { logout(); navigate('/login', { replace: true }); };
  return <div className="app-shell">
    <div className={`mobile-overlay ${mobileOpen ? 'show' : ''}`} onClick={() => setMobileOpen(false)} aria-hidden="true" />
    <aside className={`sidebar ${mobileOpen ? 'open' : ''}`} aria-label="Main navigation">
      <NavLink to="/" className="brand" onClick={() => setMobileOpen(false)}><span className="brand-mark"><Activity size={17} /></span><span>API Sentinel<small>Monitor · Analyze · Act</small></span></NavLink>
      <div className="sidebar-label">Workspace</div><nav className="nav-list">{navItems.slice(0,4).map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={() => setMobileOpen(false)} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}><Icon size={16} strokeWidth={1.8} />{label}</NavLink>)}</nav>
      <div className="sidebar-label">Settings</div><nav className="nav-list">{navItems.slice(4).map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} onClick={() => setMobileOpen(false)} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}><Icon size={16} strokeWidth={1.8} />{label}</NavLink>)}</nav>
      <div className="sidebar-spacer" />
      <div className="sidebar-footer"><span className="avatar">{initials}</span><div className="sidebar-user"><strong>{user?.email}</strong><span>Workspace owner</span></div><button className="icon-button" aria-label="Sign out" title="Sign out" onClick={handleLogout}><LogOut size={14} /></button></div>
    </aside>
    <div className="workspace">
      <header className="topbar"><div className="topbar-left"><button className="icon-button mobile-menu" aria-label={mobileOpen ? 'Close menu' : 'Open menu'} onClick={() => setMobileOpen((value) => !value)}>{mobileOpen ? <X size={18} /> : <Menu size={18} />}</button><span className="topbar-mark"><Activity size={15} /></span><span className="topbar-title">{displayTitle(location.pathname)}</span></div><div className="topbar-right"><span className="topbar-user">{user?.email}</span><span className="avatar" style={{ width: 29, height: 29, background: '#eeeefe', color: '#5556dc' }}>{initials}</span></div></header>
      <main className="content"><Outlet /></main>
    </div>
  </div>;
}
