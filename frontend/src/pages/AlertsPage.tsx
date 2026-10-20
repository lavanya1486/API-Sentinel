import { useEffect, useState } from 'react';
import { BellRing, ExternalLink, History, Send, Shield, Webhook } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button, EmptyState, ErrorNotice, PageHeader, StatusPill } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { formatDate } from '../lib/format';
import type { AlertConfig, ApiPage, Endpoint, Notification } from '../lib/types';

const allEvents = ['INCIDENT_OPENED', 'INCIDENT_RESOLVED'];

export function AlertsPage() {
  const [searchParams] = useSearchParams();
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const [configs, setConfigs] = useState<AlertConfig[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [selectedId, setSelectedId] = useState(searchParams.get('endpointId') ?? '');
  const [config, setConfig] = useState<AlertConfig | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [events, setEvents] = useState<string[]>(allEvents);
  const [webhookUrl, setWebhookUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let live = true;
    Promise.all([
      api<ApiPage<Endpoint>>('/api/v1/endpoints?page=0&size=100'),
      api<AlertConfig[]>('/api/v1/alerts'),
      api<ApiPage<Notification>>('/api/v1/notifications?page=0&size=50'),
    ]).then(([endpointPage, alertRows, notificationPage]) => {
      if (!live) return;
      setEndpoints(endpointPage.content);
      setConfigs(alertRows);
      setNotifications(notificationPage.content);
      const wanted = searchParams.get('endpointId');
      setSelectedId((current) => (wanted && endpointPage.content.some((item) => item.id === wanted) ? wanted : current || endpointPage.content[0]?.id || ''));
    }).catch((problem: unknown) => { if (live) setError(problem instanceof ApiError ? problem.message : 'Could not load alert settings.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [searchParams]);

  useEffect(() => {
    if (!selectedId) { setConfig(null); return; }
    let live = true;
    setError(''); setWebhookUrl('');
    api<AlertConfig>(`/api/v1/endpoints/${selectedId}/alert`).then((value) => {
      if (!live) return;
      setConfig(value); setEnabled(value.enabled); setEvents(value.eventTypes);
    }).catch((problem: unknown) => { if (live) setError(problem instanceof ApiError ? problem.message : 'Could not load this endpoint’s alert.'); });
    return () => { live = false; };
  }, [selectedId]);

  const toggleEvent = (event: string) => setEvents((current) => current.includes(event) ? current.filter((item) => item !== event) : [...current, event]);

  const save = async () => {
    if (!selectedId) return;
    setSaving(true); setError(''); setNotice('');
    try {
      const body: { enabled: boolean; eventTypes: string[]; webhookUrl?: string } = { enabled, eventTypes: events };
      if (webhookUrl.trim()) body.webhookUrl = webhookUrl.trim();
      const updated = await api<AlertConfig>(`/api/v1/endpoints/${selectedId}/alert`, { method: 'PUT', body: JSON.stringify(body) });
      setConfig(updated); setWebhookUrl('');
      setConfigs((current) => [...current.filter((item) => item.endpointId !== updated.endpointId), updated]);
      setNotice('Alert configuration saved. Webhook URLs are encrypted and never returned.');
    } catch (problem) { setError(problem instanceof ApiError ? problem.message : 'Could not save alert settings.'); }
    finally { setSaving(false); }
  };

  const endpointName = endpoints.find((item) => item.id === selectedId)?.name;
  const configuredCount = configs.filter((item) => item.enabled && item.webhookConfigured).length;

  return <>
    <PageHeader kicker="Notifications" title="Alerts & webhooks" subtitle="Deliver incident events securely and review every delivery attempt." actions={<span className="status-pill status-unknown"><Webhook size={12} />{configuredCount} enabled</span>} />
    {error && <div style={{ marginBottom: 14 }}><ErrorNotice message={error} onDismiss={() => setError('')} /></div>}
    {notice && <div className="alert-box alert-success" role="status" style={{ marginBottom: 14 }}>{notice}</div>}
    {loading ? <div className="card" style={{ padding: 22 }}><div className="skeleton" style={{ height: 16, width: '40%', marginBottom: 18 }} /><div className="skeleton" style={{ height: 150 }} /></div> : !endpoints.length ? <section className="card"><EmptyState icon={<BellRing size={18} />} title="Add an endpoint first" message="Alert configurations belong to monitored endpoints. Create a monitor before setting up a webhook." action={<Link to="/endpoints" className="button button-primary">Go to endpoints <ExternalLink size={13} /></Link>} /></section> : <>
      <div className="detail-grid">
        <section className="card">
          <div className="card-heading"><div><h2 className="card-title">Webhook destination</h2><p className="card-caption">One HTTPS webhook per monitored endpoint</p></div><Shield size={17} color="#696bf0" /></div>
          <div className="card-body" style={{ paddingTop: 8 }}>
            <div className="form-field" style={{ marginBottom: 17 }}><label className="form-label" htmlFor="alert-endpoint">Endpoint</label><select id="alert-endpoint" className="form-control" value={selectedId} onChange={(event) => setSelectedId(event.target.value)}>{endpoints.map((endpoint) => <option key={endpoint.id} value={endpoint.id}>{endpoint.name} · {endpoint.targetDisplay}</option>)}</select></div>
            {selectedId && <>
              <div className="alert-row"><div><div className="form-label" style={{ marginBottom: 4 }}>Incident webhook</div><div className="form-hint" style={{ marginTop: 0 }}>Queue notifications when incidents open or recover.</div></div><button type="button" className={`toggle ${enabled ? 'on' : ''}`} aria-label="Enable incident webhook" aria-pressed={enabled} onClick={() => setEnabled((value) => !value)} /></div>
              <div className="form-field" style={{ marginTop: 17 }}><label className="form-label" htmlFor="webhook-url">HTTPS webhook URL</label><input id="webhook-url" type="url" className="form-control" value={webhookUrl} onChange={(event) => setWebhookUrl(event.target.value)} placeholder={config?.webhookConfigured ? 'Saved securely · enter a new URL to rotate it' : 'https://hooks.example.com/your-endpoint'} /><div className="form-hint">HTTPS only · private, localhost, metadata, and redirect destinations are blocked. URL is encrypted at rest and hidden from API responses.</div></div>
              {config?.webhookConfigured && <div className="alert-box alert-info" style={{ marginTop: 10 }}>A webhook URL is saved for <strong>{endpointName}</strong>. Leave the field blank to keep it; enter a new URL to rotate it.</div>}
              <div style={{ marginTop: 19 }}><div className="form-label">Send on</div>{allEvents.map((event) => <label key={event} className="alert-row" style={{ cursor: 'pointer' }}><span><strong style={{ display: 'block', color: '#344054', fontSize: 11 }}>{event === 'INCIDENT_OPENED' ? 'Incident opens' : 'Incident recovers'}</strong><span className="form-hint">{event === 'INCIDENT_OPENED' ? 'Consecutive failures cross the configured threshold.' : 'Consecutive successes meet the recovery threshold.'}</span></span><input type="checkbox" checked={events.includes(event)} onChange={() => toggleEvent(event)} /></label>)}</div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 18 }}><Button onClick={() => void save()} disabled={saving}>{saving ? 'Saving…' : 'Save alert settings'}</Button></div>
            </>}
          </div>
        </section>
        <section className="card detail-info"><h3>Configured alerts</h3>
          {configs.length ? configs.map((item) => <div className="alert-row" key={item.endpointId}><div style={{ minWidth: 0 }}><Link to={`/endpoints/${item.endpointId}`} className="endpoint-name">{item.endpointName}</Link><div className="form-hint">{item.eventTypes.map((event) => event.replace(/_/g, ' ').toLowerCase()).join(' · ') || 'No event types'}</div></div><StatusPill status={item.enabled && item.webhookConfigured ? 'UP' : 'PAUSED'} label={item.enabled && item.webhookConfigured ? 'Enabled' : 'Disabled'} /></div>) : <div className="form-hint">No alert configurations saved yet.</div>}
          <div className="alert-box alert-info" style={{ marginTop: 15, display: 'flex', gap: 8 }}><Shield size={14} /> Webhook requests use bounded timeouts, HTTPS, SSRF checks, disabled redirects, delivery history, and exponential retry.</div>
        </section>
      </div>
      <section className="card table-card" style={{ marginTop: 17 }}><div className="card-heading"><div><h2 className="card-title">Delivery history</h2><p className="card-caption">Persisted webhook attempts · most recent first</p></div><History size={16} color="#98a2b3" /></div>
        {notifications.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Endpoint</th><th>Event</th><th>Status</th><th>Attempts</th><th>Created</th><th>Last attempt</th><th>Details</th></tr></thead><tbody>{notifications.map((item) => <tr key={item.id}><td><Link to={`/endpoints/${item.endpointId}`} className="endpoint-name">{item.endpointName}</Link></td><td>{item.eventType.replace(/_/g, ' ').toLowerCase()}</td><td><StatusPill status={item.status} /></td><td>{item.attempts}</td><td>{formatDate(item.createdAt)}</td><td>{formatDate(item.lastAttemptAt)}</td><td>{item.lastError ?? (item.deliveredAt ? <span style={{ color: '#07864d' }}>Delivered {formatDate(item.deliveredAt)}</span> : 'Waiting to send')}</td></tr>)}</tbody></table></div> : <EmptyState icon={<Send size={17} />} title="No delivery attempts" message="When an incident opens or recovers, configured webhooks will be queued and logged here." />}
      </section>
    </>}
  </>;
}
