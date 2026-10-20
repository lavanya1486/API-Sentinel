import { useEffect, useState } from 'react';
import { ArrowLeft, CheckCircle2, CircleAlert, Clock3, RotateCcw } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { EmptyState, ErrorNotice, PageHeader, StatusPill } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { formatDate, formatDuration } from '../lib/format';
import type { IncidentDetail } from '../lib/types';

function EventIcon({ type }: { type: string }) {
  if (type === 'RESOLVED') return <CheckCircle2 size={13} />;
  if (type === 'RECOVERY_OBSERVED') return <RotateCcw size={13} />;
  return <CircleAlert size={13} />;
}

export function IncidentDetailPage() {
  const { id = '' } = useParams();
  const [data, setData] = useState<IncidentDetail | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let live = true;
    setLoading(true);
    api<IncidentDetail>(`/api/v1/incidents/${id}`).then((value) => { if (live) setData(value); })
      .catch((problem: unknown) => { if (live) setError(problem instanceof ApiError ? problem.message : 'Could not load incident details.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [id]);

  if (loading && !data) return <><PageHeader title="Incident" subtitle="Loading incident history…" /><div className="card" style={{ padding: 24 }}><div className="skeleton" style={{ height: 18, width: '38%', marginBottom: 15 }} /><div className="skeleton" style={{ height: 130 }} /></div></>;
  if (!data) return <><PageHeader title="Incident unavailable" /><ErrorNotice message={error || 'This incident could not be found.'} /><div style={{ marginTop: 14 }}><Link to="/incidents" className="button button-secondary"><ArrowLeft size={14} /> All incidents</Link></div></>;
  const incident = data.incident;
  return <>
    <div style={{ marginBottom: 14 }}><Link to="/incidents" style={{ color: '#667085', fontSize: 11, display: 'inline-flex', alignItems: 'center', gap: 6 }}><ArrowLeft size={13} /> All incidents</Link></div>
    <PageHeader kicker="Incident timeline" title={incident.title} subtitle={`${incident.endpointName} · Started ${formatDate(incident.startedAt)}`} actions={<StatusPill status={incident.status} />} />
    {error && <div style={{ marginBottom: 14 }}><ErrorNotice message={error} /></div>}
    <div className="detail-grid">
      <section className="card">
        <div className="card-heading"><div><h2 className="card-title">Event history</h2><p className="card-caption">Incident opening, detection and recovery events</p></div></div>
        {data.events.length ? <div className="card-body" style={{ paddingTop: 0 }}><div className="event-list">{data.events.map((event) => <div className="event-item" key={event.id}><span className="event-icon"><EventIcon type={event.eventType} /></span><span className="event-copy"><strong>{event.eventType.replace(/_/g, ' ').toLowerCase()}</strong><span>{event.detail}</span><span className="event-time">{formatDate(event.occurredAt)}</span></span></div>)}</div></div> : <EmptyState title="No events recorded" message="Incident event history is empty." />}
      </section>
      <section className="card detail-info"><h3>Incident details</h3><div className="info-row"><span>Status</span><StatusPill status={incident.status} /></div><div className="info-row"><span>Endpoint</span><Link to={`/endpoints/${incident.endpointId}`} style={{ color: '#5556dc', fontWeight: 600 }}>{incident.endpointName}</Link></div><div className="info-row"><span>Duration</span><span>{formatDuration(incident.durationSeconds)}</span></div><div className="info-row"><span>Started</span><span>{formatDate(incident.startedAt)}</span></div><div className="info-row"><span>Recovered</span><span>{incident.resolvedAt ? formatDate(incident.resolvedAt) : 'Awaiting recovery'}</span></div><div className="info-row"><span>Latest failure</span><span>{incident.failureDetail ?? '—'}</span></div><div className="alert-box alert-info" style={{ marginTop: 14, display: 'flex', gap: 8 }}><Clock3 size={14} /> Recovery requires the configured number of consecutive successful checks.</div></section>
    </div>
  </>;
}
