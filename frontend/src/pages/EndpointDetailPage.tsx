import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, BellRing, CheckCircle2, ChevronLeft, ChevronRight, Pencil, Play, Pause, RefreshCw, Trash2 } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ConfirmDialog, Button, EmptyState, ErrorNotice, PageHeader, StatusPill } from '../components/ui';
import { EndpointFormModal } from '../components/EndpointFormModal';
import { api, ApiError } from '../lib/api';
import { formatChartTime, formatDate, formatLatency, formatPercent } from '../lib/format';
import type { ApiPage, Endpoint, HealthCheck, Overview } from '../lib/types';

export function EndpointDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [endpoint, setEndpoint] = useState<Endpoint | null>(null);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [checks, setChecks] = useState<ApiPage<HealthCheck> | null>(null);
  const [range, setRange] = useState<'24h' | '7d' | '30d'>('24h');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [actionBusy, setActionBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [removeOpen, setRemoveOpen] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const [endpointResult, analytics, history] = await Promise.all([
        api<Endpoint>(`/api/v1/endpoints/${id}`),
        api<Overview>(`/api/v1/analytics/overview?range=${range}&endpointId=${id}`),
        api<ApiPage<HealthCheck>>(`/api/v1/endpoints/${id}/checks?range=${range}&page=${page}&size=15`),
      ]);
      setEndpoint(endpointResult);
      setOverview(analytics);
      setChecks(history);
    } catch (problem) {
      setError(problem instanceof ApiError ? problem.message : 'Could not load endpoint details.');
    } finally { setLoading(false); }
  }, [id, range, page]);

  useEffect(() => { void load(); }, [load]);

  const action = async (name: 'pause' | 'resume' | 'check-now') => {
    if (!endpoint) return;
    setActionBusy(true); setError(''); setNotice('');
    try {
      await api(`/api/v1/endpoints/${id}/${name}`, { method: 'POST' });
      setNotice(name === 'check-now' ? 'A health check has been queued.' : `Monitor ${name === 'pause' ? 'paused' : 'resumed'}.`);
      await load();
    } catch (problem) { setError(problem instanceof ApiError ? problem.message : 'Action failed.'); }
    finally { setActionBusy(false); }
  };

  const remove = async () => {
    setActionBusy(true);
    try { await api<void>(`/api/v1/endpoints/${id}`, { method: 'DELETE' }); navigate('/endpoints'); }
    catch (problem) { setError(problem instanceof ApiError ? problem.message : 'Could not remove this monitor.'); setRemoveOpen(false); }
    finally { setActionBusy(false); }
  };

  if (loading && !endpoint) return <div><PageHeader title="Endpoint" subtitle="Loading monitor configuration…" /><div className="card" style={{ padding: 24 }}><div className="skeleton" style={{ height: 20, width: '30%', marginBottom: 20 }} /><div className="skeleton" style={{ height: 220 }} /></div></div>;
  if (!endpoint) return <><PageHeader title="Endpoint unavailable" /><ErrorNotice message={error || 'This endpoint could not be found.'} /><div style={{ marginTop: 15 }}><Link to="/endpoints" className="button button-secondary"><ArrowLeft size={14} /> Back to endpoints</Link></div></>;

  return <>
    <div style={{ marginBottom: 15 }}><Link to="/endpoints" style={{ color: '#667085', fontSize: 11, display: 'inline-flex', alignItems: 'center', gap: 6 }}><ArrowLeft size={13} /> All endpoints</Link></div>
    <PageHeader kicker={`${endpoint.method} monitor`} title={endpoint.name} subtitle={endpoint.targetDisplay}
      actions={<><StatusPill status={endpoint.healthStatus} /><Button variant="secondary" onClick={() => void action('check-now')} disabled={actionBusy || !endpoint.enabled}><RefreshCw size={13} /> Check now</Button><Button variant="secondary" onClick={() => void action(endpoint.enabled ? 'pause' : 'resume')} disabled={actionBusy}>{endpoint.enabled ? <><Pause size={13} /> Pause</> : <><Play size={13} /> Resume</>}</Button><Button variant="secondary" onClick={() => navigate(`/alerts?endpointId=${id}`)}><BellRing size={13} /> Alerts</Button><Button variant="secondary" onClick={() => setEditOpen(true)}><Pencil size={13} /> Edit</Button><Button variant="danger" onClick={() => setRemoveOpen(true)} aria-label="Remove endpoint"><Trash2 size={13} /></Button></>} />
    {error && <div style={{ marginBottom: 13 }}><ErrorNotice message={error} onDismiss={() => setError('')} /></div>}
    {notice && <div className="alert-box alert-success" role="status" style={{ marginBottom: 13 }}>{notice}</div>}
    <div className="detail-grid">
      <section className="card">
        <div className="card-heading"><div><h2 className="card-title">Performance</h2><p className="card-caption">Health-check analytics from real persisted results</p></div><select aria-label="Performance time range" className="range-select" value={range} onChange={(event) => { setRange(event.target.value as '24h' | '7d' | '30d'); setPage(0); }}>{['24h','7d','30d'].map((item) => <option key={item} value={item}>Last {item}</option>)}</select></div>
        {overview ? <>
          {overview.series.length ? <div className="chart-wrap" style={{ height: 220, padding: '0 14px 8px' }}><ResponsiveContainer width="100%" height="100%"><AreaChart data={overview.series} margin={{ top: 12, right: 8, left: -16, bottom: 0 }}>
            <defs><linearGradient id="detailFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#6567ef" stopOpacity={0.2} /><stop offset="100%" stopColor="#6567ef" stopOpacity={0.015} /></linearGradient></defs><CartesianGrid stroke="#f0f2f6" vertical={false} /><XAxis dataKey="bucketAt" tickFormatter={(value: string) => formatChartTime(value, range)} tick={{ fill: '#98a2b3', fontSize: 9 }} axisLine={false} tickLine={false} minTickGap={24} /><YAxis tick={{ fill: '#98a2b3', fontSize: 9 }} axisLine={false} tickLine={false} tickFormatter={(value: number) => value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${Math.round(value)}ms`} /><Tooltip labelFormatter={(value) => formatDate(String(value))} formatter={(value) => [formatLatency(Number(value)), 'Avg latency']} contentStyle={{ border: 0, borderRadius: 9, boxShadow: '0 6px 24px rgba(16,24,40,.12)', fontSize: 11 }} /><Area type="monotone" dataKey="averageLatencyMs" stroke="#6567ef" strokeWidth={2.2} fill="url(#detailFill)" /></AreaChart></ResponsiveContainer></div> : <div className="chart-empty" style={{ height: 220 }}><div>No successful check data in this range yet.</div></div>}
          <div className="metric-strip"><div className="metric"><div className="metric-label">Uptime</div><div className="metric-value">{formatPercent(overview.uptimePercent)}</div></div><div className="metric"><div className="metric-label">Average latency</div><div className="metric-value">{formatLatency(overview.averageLatencyMs)}</div></div><div className="metric"><div className="metric-label">P95 / P99</div><div className="metric-value" style={{ fontSize: 14 }}>{formatLatency(overview.p95LatencyMs)} / {formatLatency(overview.p99LatencyMs)}</div></div></div>
        </> : <div className="chart-empty">No analytics available.</div>}
      </section>
      <section className="card detail-info"><h3>Monitor configuration</h3>
        <div className="info-row"><span>Target</span><span>{endpoint.targetDisplay}</span></div><div className="info-row"><span>Method</span><span>{endpoint.method}</span></div><div className="info-row"><span>Expected status</span><span>{endpoint.expectedStatusCodes.join(', ')}</span></div><div className="info-row"><span>Interval</span><span>{endpoint.intervalSeconds}s</span></div><div className="info-row"><span>Timeout</span><span>{endpoint.timeoutMs} ms</span></div><div className="info-row"><span>Retries</span><span>{endpoint.retryCount} · {endpoint.retryBackoffMs} ms base backoff</span></div><div className="info-row"><span>Incident thresholds</span><span>{endpoint.failureThreshold} failures / {endpoint.recoveryThreshold} recoveries</span></div><div className="info-row"><span>Saved header names</span><span>{endpoint.headerNames.length ? endpoint.headerNames.join(', ') : 'None'}</span></div><div className="info-row"><span>Created</span><span>{formatDate(endpoint.createdAt, false)}</span></div>
        {endpoint.lastFailureDetail && <div className="alert-box alert-error" style={{ marginTop: 14 }}>Latest failure: {endpoint.lastFailureDetail}</div>}
      </section>
    </div>
    <section className="card table-card" style={{ marginTop: 16 }}><div className="card-heading"><div><h2 className="card-title">Recent checks</h2><p className="card-caption">Stored health-check history · {checks?.totalElements ?? 0} results in range</p></div><span className="health-legend"><span><i className="legend-dot" style={{ background: '#0e9f6e' }} />Success</span><span><i className="legend-dot" style={{ background: '#f04438' }} />Failure</span></span></div>
      {checks?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Result</th><th>Time</th><th>Latency</th><th>Status</th><th>Attempts</th><th>Details</th></tr></thead><tbody>{checks.content.map((check) => <tr key={check.id}><td><StatusPill status={check.successful ? 'UP' : 'DOWN'} label={check.successful ? 'Success' : 'Failure'} /></td><td>{formatDate(check.checkedAt)}</td><td>{formatLatency(check.responseTimeMs)}</td><td>{check.statusCode ?? '—'}</td><td>{check.attemptCount}</td><td>{check.failureDetail ?? <span style={{ color: '#98a2b3' }}>—</span>}</td></tr>)}</tbody></table></div><div className="table-footer"><span>Page {page + 1} · {checks.totalElements} checks</span><div className="pagination"><Button size="sm" variant="secondary" disabled={!page} onClick={() => setPage((current) => Math.max(0, current - 1))}><ChevronLeft size={13} /> Previous</Button><Button size="sm" variant="secondary" disabled={!checks.hasNext} onClick={() => setPage((current) => current + 1)}>Next <ChevronRight size={13} /></Button></div></div></> : <EmptyState icon={<CheckCircle2 size={18} />} title="No checks recorded" message="Checks run automatically on the configured schedule. You can also start one manually." />}
    </section>
    {editOpen && <EndpointFormModal endpoint={endpoint} onClose={() => setEditOpen(false)} onSaved={() => { setEditOpen(false); void load(); }} />}
    {removeOpen && <ConfirmDialog title="Remove this monitor?" message="This endpoint will be paused and archived. Existing monitoring history remains until the retention policy expires." confirmLabel="Remove monitor" danger busy={actionBusy} onCancel={() => setRemoveOpen(false)} onConfirm={() => void remove()} />}
  </>;
}
