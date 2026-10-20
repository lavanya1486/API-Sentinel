import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { useAuth } from './context/AuthContext';
import { EmptyState } from './components/ui';

const AuthPage = lazy(() => import('./pages/AuthPage').then((module) => ({ default: module.AuthPage })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const EndpointDetailPage = lazy(() => import('./pages/EndpointDetailPage').then((module) => ({ default: module.EndpointDetailPage })));
const EndpointsPage = lazy(() => import('./pages/EndpointsPage').then((module) => ({ default: module.EndpointsPage })));
const IncidentDetailPage = lazy(() => import('./pages/IncidentDetailPage').then((module) => ({ default: module.IncidentDetailPage })));
const IncidentsPage = lazy(() => import('./pages/IncidentsPage').then((module) => ({ default: module.IncidentsPage })));
const AlertsPage = lazy(() => import('./pages/AlertsPage').then((module) => ({ default: module.AlertsPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((module) => ({ default: module.SettingsPage })));

function ProtectedRoute() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="auth-panel"><div className="skeleton" style={{ width: 260, height: 30 }} /></div>;
  if (!user || !window.sessionStorage.getItem('sentinel.token')) return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

function NotFound() {
  return <div className="auth-panel"><EmptyState title="Page not found" message="That page is not part of this workspace." action={<a href="/" className="button button-primary">Go to overview</a>} /></div>;
}

function RouteFallback() {
  return <div className="card" style={{ padding: 24, minHeight: 170 }} aria-label="Loading page"><div className="skeleton" style={{ height: 16, width: '32%', marginBottom: 16 }} /><div className="skeleton" style={{ height: 110 }} /></div>;
}

export function App() {
  return <Suspense fallback={<div className="content"><RouteFallback /></div>}><Routes>
    <Route path="/login" element={<AuthPage mode="login" />} />
    <Route path="/register" element={<AuthPage mode="register" />} />
    <Route element={<ProtectedRoute />}>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="endpoints" element={<EndpointsPage />} />
        <Route path="endpoints/:id" element={<EndpointDetailPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="incidents/:id" element={<IncidentDetailPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>
    </Route>
    <Route path="*" element={<NotFound />} />
  </Routes></Suspense>;
}
