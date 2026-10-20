import { useEffect, useState } from 'react';
import { Activity, AlertTriangle, ArrowRight, CheckCircle2, Clock3, Plus, Radio, ShieldCheck } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Button, EmptyState, ErrorNotice, PageHeader, StatCard, StatusPill } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { formatChartTime, formatDate, formatLatency, formatPercent } from '../lib/format';
import type { ApiPage, Endpoint, Incident, Overview } from '../lib/types';

const ranges = ['24h', '7d', '30d'] as const;
type Range = typeof ranges[number];

export function DashboardPage() {
  const [range, setRange] = useState<Range>('24h');
  const [overview, setOverview] = useState<Overview | null>(null);
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    let live = true;
    setLoading(true);
    setError('');
    Promise.all([
      api<Overview>(`/api/v1/analytics/overview?range=${range}`),
      api<ApiPage<Endpoint>>('/api/v1/endpoints?page=0&size=6&sort=createdAt&direction=desc'),
      api<ApiPage<Incident>>('/api/v1/incidents?status=OPEN&page=0&size=5'),
    ]).then(([summary, monitorPage, incidentPage]) => {
      if (!live) return;
      setOverview(summary);
      setEndpoints(monitorPage.content);
      setIncidents(incidentPage.content);
    }).catch((problem: unknown) => {
      if (live) setError(problem instanceof ApiError ? problem.message : 'Could not load monitoring data.');
    }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [range]);

  return <>
    <PageHeader kicker="Monitor" title="Overview" subtitle="A real-time snapshot of the APIs you monitor."
      actions={<><select aria-label="Analytics time range" className="range-select" value={range} onChange={(event) => setRange(event.target.value as Range)}>{ranges.map((item) => <option key={item} value={item}>Last {item}</option>)}</select><Button onClick={() => navigate('/endpoints?new=1')}><Plus size={14} /> Add endpoint</Button></>} />
    {error && <div style={{ marginBottom: 16 }}><ErrorNotice message={error} /></div>}
    {loading && !overview ? <div className="grid stats-grid">{[0,1,2,3].map((index) => <div className="card stat-card" key={index}><div className="skeleton" style={{ height: 11, width: '50%' }} /><div className="skeleton" style={{ height: 25, width: '42%', marginTop: 20 }} /><div className="skeleton" style={{ height: 9, width: '64%', marginTop: 15 }} /></div>)}</div> : overview ? <>
      <div className="grid stats-grid">
        <StatCard label="Monitored endpoints" value={String(overview.totalEndpoints)} detail={`${overview.activeEndpoints} currently active`} icon={<Radio size={16} />} />
        <StatCard label={`Uptime · ${range}`} value={formatPercent(overview.uptimePercent)} detail={`${overview.successfulChecks.toLocaleString()} successful checks`} icon={<ShieldCheck size={16} />} tone="green" />
        <StatCard label="Average latency" value={formatLatency(overview.averageLatencyMs)} detail={`P95 ${formatLatency(overview.p95LatencyMs)} · P99 ${formatLatency(overview.p99LatencyMs)}`} icon={<Activity size={16} />} tone="orange" />
        <StatCard label="Open incidents" value={String(overview.openIncidents)} detail={`${overview.failedChecks.toLocaleString()} failed checks · ${formatPercent(overview.errorRatePercent)} error rate`} icon={<AlertTriangle size={16} />} tone={overview.openIncidents ? 'red' : 'green'} />
      </div>
      <div className="dashboard-grid">
        <section className="card">
          <div className="card-heading"><div><h2 className="card-title">Response time</h2><p className="card-caption">Average successful-check latency · {overview.totalChecks.toLocaleString()} checks</p></div><span className="health-legend"><span><i className="legend-dot" style={{ background: '#696bf0' }} />Latency</span></span></div>
          <div className="card-body" style={{ paddingTop: 0 }}>
            {overview.series.length ? <div className="chart-wrap"><ResponsiveContainer width="100%" height="100%"><AreaChart data={overview.series} margin={{ top: 12, right: 8, left: -16, bottom: 0 }}>
              <defs><linearGradient id="latencyFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#6567ef" stopOpacity={0.2} /><stop offset="100%" stopColor="#6567ef" stopOpacity={0.015} /></linearGradient></defs>
              <CartesianGrid stroke="#f0f2f6" vertical={false} />
              <XAxis dataKey="bucketAt" tickFormatter={(value: string) => formatChartTime(value, range)} tick={{ fill: '#98a2b3', fontSize: 10 }} axisLine={false} tickLine={false} minTickGap={28} />
              <YAxis tick={{ fill: '#98a2b3', fontSize: 10 }} axisLine={false} tickLine={false} tickFormatter={(value: number) => value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${Math.round(value)}ms`} />
              <Tooltip labelFormatter={(value) => formatDate(String(value))} formatter={(value) => [formatLatency(Number(value)), 'Avg latency']} contentStyle={{ border: 0, borderRadius: 9, boxShadow: '0 6px 24px rgba(16,24,40,.12)', fontSize: 11 }} />
              <Area type="monotone" dataKey="averageLatencyMs" stroke="#6567ef" strokeWidth={2.3} fill="url(#latencyFill)" activeDot={{ r: 4, strokeWidth: 0 }} />
            </AreaChart></ResponsiveContainer></div> : <div className="chart-empty"><div><Activity size={21} style={{ marginBottom: 8, color: '#a5a6ef' }} /><div>No check data in this time range yet.</div><div style={{ marginTop: 4 }}>Add an endpoint and the chart will populate from health checks.</div></div></div>}
          </div>
        </section>
        <section className="card">
          <div className="card-heading"><div><h2 className="card-title">Open incidents</h2><p className="card-caption">Failures that need attention</p></div><Link to="/incidents" className="button button-ghost button-sm">View all <ArrowRight size={13} /></Link></div>
          {incidents.length ? <div className="card-body" style={{ paddingTop: 0 }}><div className="event-list">{incidents.slice(0,4).map((incident) => <Link className="event-item" to={`/incidents/${incident.id}`} key={incident.id}>
            <span className="event-icon" style={{ color: '#d92d20', background: '#fff1f0' }}><AlertTriangle size={13} /></span><span className="event-copy"><strong>{incident.endpointName}</strong><span>{incident.failureDetail ?? incident.title}</span><span className="event-time">Started {formatDate(incident.startedAt)}</span></span>
          </Link>)}</div></div> : <EmptyState icon={<CheckCircle2 size={18} />} title="All clear" message="No open incidents. We'll surface new failures here automatically." />}
        </section>
      </div>
      <section className="card table-card" style={{ marginTop: 17 }}>
        <div className="card-heading"><div><h2 className="card-title">Your endpoints</h2><p className="card-caption">Latest health-check results</p></div><Link to="/endpoints" className="button button-ghost button-sm">Manage endpoints <ArrowRight size={13} /></Link></div>
        {endpoints.length ? <EndpointTable endpoints={endpoints} /> : <EmptyState icon={<Radio size={18} />} title="No endpoints monitored" message="Create your first monitor to start collecting uptime and latency data." action={<Button onClick={() => navigate('/endpoints?new=1')}><Plus size={14} /> Add endpoint</Button>} />}
      </section>
    </> : !error ? <EmptyState title="No dashboard data" message="The analytics API returned no summary." /> : null}
  </>;
}

export function EndpointTable({ endpoints }: { endpoints: Endpoint[] }) {
  return <div className="table-wrap"><table className="data-table"><thead><tr><th>Endpoint</th><th>Method</th><th>Health</th><th>Latency</th><th>Last checked</th></tr></thead><tbody>
    {endpoints.map((endpoint) => <tr key={endpoint.id}>
      <td><Link to={`/endpoints/${endpoint.id}`}><span className="endpoint-name">{endpoint.name}</span><span className="endpoint-url">{endpoint.targetDisplay}</span></Link></td>
      <td><span className="method-chip">{endpoint.method}</span></td>
      <td><StatusPill status={endpoint.healthStatus} /></td>
      <td>{formatLatency(endpoint.latestResponseTimeMs)}</td>
      <td><span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: '#98a2b3' }}><Clock3 size={12} />{formatDate(endpoint.lastCheckedAt)}</span></td>
    </tr>)}
  </tbody></table></div>;
}
