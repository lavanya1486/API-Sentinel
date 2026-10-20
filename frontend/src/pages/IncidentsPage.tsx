import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, ChevronLeft, ChevronRight, Clock3, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button, EmptyState, ErrorNotice, PageHeader, StatusPill } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { formatDate, formatDuration } from '../lib/format';
import type { ApiPage, Incident } from '../lib/types';

export function IncidentsPage() {
  const [status, setStatus] = useState('all');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ApiPage<Incident> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setData(await api<ApiPage<Incident>>(`/api/v1/incidents?status=${status}&page=${page}&size=20`)); }
    catch (problem) { setError(problem instanceof ApiError ? problem.message : 'Could not load incidents.'); }
    finally { setLoading(false); }
  }, [status, page]);
  useEffect(() => { void load(); }, [load]);

  return <>
    <PageHeader kicker="Reliability" title="Incidents" subtitle="Automatic incident lifecycle and recovery history." actions={<select aria-label="Filter incident status" className="range-select" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}><option value="all">All incidents</option><option value="OPEN">Open</option><option value="RESOLVED">Resolved</option></select>} />
    {error && <div style={{ marginBottom: 14 }}><ErrorNotice message={error} /></div>}
    <section className="card table-card">
      {loading && !data ? <div style={{ padding: 20 }}>{[1,2,3,4].map((index) => <div key={index} className="skeleton" style={{ height: 38, marginBottom: 10 }} />)}</div> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Incident</th><th>Status</th><th>Started</th><th>Duration</th><th>Last failure</th></tr></thead><tbody>{data.content.map((incident) => <tr key={incident.id}><td><Link to={`/incidents/${incident.id}`}><span className="endpoint-name">{incident.endpointName}</span><span className="endpoint-url">{incident.title}</span></Link></td><td><StatusPill status={incident.status} /></td><td><span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}><Clock3 size={12} color="#98a2b3" />{formatDate(incident.startedAt)}</span></td><td>{formatDuration(incident.durationSeconds)}</td><td>{incident.failureDetail ?? '—'}</td></tr>)}</tbody></table></div><div className="table-footer"><span>{data.totalElements} incident{data.totalElements === 1 ? '' : 's'} · page {page + 1}</span><div className="pagination"><Button size="sm" variant="secondary" disabled={!page || loading} onClick={() => setPage((current) => Math.max(0, current - 1))}><ChevronLeft size={13} /> Previous</Button><Button size="sm" variant="secondary" disabled={!data.hasNext || loading} onClick={() => setPage((current) => current + 1)}>Next <ChevronRight size={13} /></Button></div></div></> : <EmptyState icon={status === 'OPEN' ? <ShieldCheck size={18} /> : <AlertTriangle size={18} />} title={status === 'OPEN' ? 'No open incidents' : 'No incidents found'} message={status === 'OPEN' ? 'All monitored endpoints are currently clear.' : 'Incidents will appear after the configured consecutive-failure threshold is reached.'} />}
    </section>
  </>;
}
