import { useCallback, useEffect, useState } from 'react';
import { Activity, ChevronLeft, ChevronRight, Pencil, Play, Plus, RefreshCw, Search, Trash2, Pause } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { ConfirmDialog, Button, EmptyState, ErrorNotice, PageHeader, StatusPill } from '../components/ui';
import { EndpointFormModal } from '../components/EndpointFormModal';
import { api, ApiError } from '../lib/api';
import { formatDate, formatLatency } from '../lib/format';
import type { ApiPage, Endpoint } from '../lib/types';

export function EndpointsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [data, setData] = useState<ApiPage<Endpoint> | null>(null);
  const [query, setQuery] = useState(searchParams.get('q') ?? '');
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('createdAt');
  const [direction, setDirection] = useState('desc');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Endpoint | undefined>(undefined);
  const [confirmDelete, setConfirmDelete] = useState<Endpoint | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const load = useCallback(async (currentQuery: string, currentPage: number) => {
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ page: String(currentPage), size: '20', sort, direction });
      if (currentQuery.trim()) params.set('q', currentQuery.trim());
      const result = await api<ApiPage<Endpoint>>(`/api/v1/endpoints?${params.toString()}`);
      setData(result);
      if (result.totalPages > 0 && currentPage >= result.totalPages) setPage(result.totalPages - 1);
    } catch (problem) {
      setError(problem instanceof ApiError ? problem.message : 'Could not load endpoints.');
    } finally { setLoading(false); }
  }, [sort, direction]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(query, page); }, 220);
    return () => window.clearTimeout(timer);
  }, [load, query, page]);

  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setEditing(undefined);
      setFormOpen(true);
      const next = new URLSearchParams(searchParams);
      next.delete('new');
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const openCreate = () => { setEditing(undefined); setFormOpen(true); };
  const openEdit = (endpoint: Endpoint) => { setEditing(endpoint); setFormOpen(true); };
  const onSaved = (saved: Endpoint) => {
    setFormOpen(false);
    setEditing(undefined);
    setNotice(`${saved.name} saved successfully.`);
    void load(query, page);
  };

  const runAction = async (endpoint: Endpoint, action: 'pause' | 'resume' | 'check-now') => {
    setBusyId(endpoint.id);
    setError('');
    setNotice('');
    try {
      await api<Endpoint | { scheduled: boolean }>(`/api/v1/endpoints/${endpoint.id}/${action}`, { method: 'POST' });
      setNotice(action === 'check-now' ? `A check for ${endpoint.name} has been queued.` : `${endpoint.name} ${action === 'pause' ? 'paused' : 'resumed'}.`);
      await load(query, page);
    } catch (problem) {
      setError(problem instanceof ApiError ? problem.message : 'Action could not be completed.');
    } finally { setBusyId(null); }
  };

  const removeEndpoint = async () => {
    if (!confirmDelete) return;
    setDeleteBusy(true);
    try {
      await api<void>(`/api/v1/endpoints/${confirmDelete.id}`, { method: 'DELETE' });
      setNotice(`${confirmDelete.name} was removed. Monitoring history is retained according to your retention policy.`);
      setConfirmDelete(null);
      await load(query, page);
    } catch (problem) {
      setError(problem instanceof ApiError ? problem.message : 'Could not remove this endpoint.');
    } finally { setDeleteBusy(false); }
  };

  return <>
    <PageHeader kicker="Inventory" title="Endpoints" subtitle="Search and manage your scheduled health checks."
      actions={<Button onClick={openCreate}><Plus size={14} /> Add endpoint</Button>} />
    {error && <div style={{ marginBottom: 13 }}><ErrorNotice message={error} onDismiss={() => setError('')} /></div>}
    {notice && <div className="alert-box alert-success" role="status" style={{ marginBottom: 13 }}>{notice}</div>}
    <div className="toolbar">
      <div className="toolbar-left"><label className="search-box"><Search size={14} /><input aria-label="Search endpoints" placeholder="Search name or host…" value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} /></label><span className="form-hint" style={{ margin: 0 }}>{data?.totalElements ?? 0} monitors</span></div>
      <div className="toolbar-right"><select className="range-select" aria-label="Sort endpoints" value={sort} onChange={(event) => setSort(event.target.value)}><option value="createdAt">Created date</option><option value="name">Name</option><option value="intervalSeconds">Interval</option></select><button className="button button-secondary button-sm" onClick={() => setDirection((current) => current === 'asc' ? 'desc' : 'asc')} aria-label={`Sort ${direction === 'asc' ? 'descending' : 'ascending'}`}>{direction === 'asc' ? 'Ascending' : 'Descending'}</button></div>
    </div>
    <section className="card table-card">
      {loading && !data ? <div style={{ padding: 24 }}><div className="skeleton" style={{ height: 15, width: '50%', marginBottom: 20 }} />{[1,2,3,4].map((item) => <div key={item} className="skeleton" style={{ height: 38, marginBottom: 10 }} />)}</div> : data?.content.length ? <>
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Endpoint</th><th>Method</th><th>Health</th><th>Latency</th><th>Interval</th><th>Last checked</th><th>Actions</th></tr></thead><tbody>
          {data.content.map((endpoint) => <tr key={endpoint.id}>
            <td><Link to={`/endpoints/${endpoint.id}`}><span className="endpoint-name">{endpoint.name}</span><span className="endpoint-url">{endpoint.targetDisplay}</span></Link></td>
            <td><span className="method-chip">{endpoint.method}</span></td><td><StatusPill status={endpoint.healthStatus} /></td>
            <td>{formatLatency(endpoint.latestResponseTimeMs)}</td><td>Every {endpoint.intervalSeconds}s</td><td>{formatDate(endpoint.lastCheckedAt)}</td>
            <td><div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
              <button className="icon-button" style={{ color: '#667085' }} title="Run check now" aria-label={`Run check for ${endpoint.name}`} disabled={busyId === endpoint.id || !endpoint.enabled} onClick={() => void runAction(endpoint, 'check-now')}><RefreshCw size={14} /></button>
              <button className="icon-button" style={{ color: '#667085' }} title={endpoint.enabled ? 'Pause monitor' : 'Resume monitor'} aria-label={`${endpoint.enabled ? 'Pause' : 'Resume'} ${endpoint.name}`} disabled={busyId === endpoint.id} onClick={() => void runAction(endpoint, endpoint.enabled ? 'pause' : 'resume')}>{endpoint.enabled ? <Pause size={14} /> : <Play size={14} />}</button>
              <button className="icon-button" style={{ color: '#667085' }} title="Edit monitor" aria-label={`Edit ${endpoint.name}`} onClick={() => openEdit(endpoint)}><Pencil size={14} /></button>
              <button className="icon-button" style={{ color: '#d92d20' }} title="Remove monitor" aria-label={`Remove ${endpoint.name}`} onClick={() => setConfirmDelete(endpoint)}><Trash2 size={14} /></button>
            </div></td>
          </tr>)}
        </tbody></table></div>
        <div className="table-footer"><span>Showing {data.totalElements ? page * data.size + 1 : 0}–{Math.min((page + 1) * data.size, data.totalElements)} of {data.totalElements}</span><div className="pagination"><Button size="sm" variant="secondary" disabled={page === 0 || loading} onClick={() => setPage((current) => Math.max(0, current - 1))}><ChevronLeft size={13} /> Previous</Button><Button size="sm" variant="secondary" disabled={!data.hasNext || loading} onClick={() => setPage((current) => current + 1)}>Next <ChevronRight size={13} /></Button></div></div>
      </> : <EmptyState icon={<Activity size={18} />} title={query ? 'No matching endpoints' : 'No endpoints yet'} message={query ? 'Try another name or hostname.' : 'Create your first monitor to start receiving real check results.'} action={!query ? <Button onClick={openCreate}><Plus size={14} /> Add endpoint</Button> : undefined} />}
    </section>
    {formOpen && <EndpointFormModal endpoint={editing} onClose={() => setFormOpen(false)} onSaved={onSaved} />}
    {confirmDelete && <ConfirmDialog title="Remove this monitor?" message={`“${confirmDelete.name}” will stop being checked and will disappear from your active list. Existing check and incident history is kept until the configured retention period.`} confirmLabel="Remove monitor" danger busy={deleteBusy} onCancel={() => setConfirmDelete(null)} onConfirm={() => void removeEndpoint()} />}
  </>;
}
