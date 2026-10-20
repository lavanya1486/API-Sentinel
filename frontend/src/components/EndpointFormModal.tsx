import { useState, type FormEvent } from 'react';
import { api, ApiError } from '../lib/api';
import type { Endpoint } from '../lib/types';
import { Button, ErrorNotice, Modal } from './ui';

interface EndpointFormModalProps {
  endpoint?: Endpoint;
  onClose: () => void;
  onSaved: (endpoint: Endpoint) => void;
}

type Values = {
  name: string; url: string; method: string; statuses: string; timeoutMs: string; intervalSeconds: string;
  retryCount: string; retryBackoffMs: string; failureThreshold: string; recoveryThreshold: string;
};

const empty: Values = {
  name: '', url: '', method: 'GET', statuses: '200', timeoutMs: '5000', intervalSeconds: '60',
  retryCount: '1', retryBackoffMs: '500', failureThreshold: '3', recoveryThreshold: '2',
};

export function EndpointFormModal({ endpoint, onClose, onSaved }: EndpointFormModalProps) {
  const [values, setValues] = useState<Values>(() => endpoint ? {
    name: endpoint.name,
    url: '',
    method: endpoint.method,
    statuses: endpoint.expectedStatusCodes.join(','),
    timeoutMs: String(endpoint.timeoutMs),
    intervalSeconds: String(endpoint.intervalSeconds),
    retryCount: String(endpoint.retryCount),
    retryBackoffMs: String(endpoint.retryBackoffMs),
    failureThreshold: String(endpoint.failureThreshold),
    recoveryThreshold: String(endpoint.recoveryThreshold),
  } : empty);
  const [headersText, setHeadersText] = useState('');
  const [clearHeaders, setClearHeaders] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const change = (key: keyof Values, value: string) => setValues((current) => ({ ...current, [key]: value }));

  const parseHeaders = (): Record<string, string> => {
    const result: Record<string, string> = {};
    const lines = headersText.split('\n').map((line) => line.trim()).filter(Boolean);
    for (const line of lines) {
      const split = line.indexOf(':');
      if (split < 1) throw new Error('Headers must use one Key: value per line.');
      const key = line.slice(0, split).trim();
      const value = line.slice(split + 1).trim();
      if (!key || /[\r\n]/.test(value)) throw new Error('A request header is invalid.');
      if (Object.keys(result).some((existing) => existing.toLowerCase() === key.toLowerCase())) throw new Error(`Duplicate header: ${key}`);
      result[key] = value;
    }
    if (Object.keys(result).length > 20) throw new Error('Use at most 20 headers.');
    return result;
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    if (!values.name.trim()) { setError('Give this monitor a name.'); return; }
    if (!endpoint && !values.url.trim()) { setError('Enter an endpoint URL.'); return; }
    const statuses = values.statuses.split(',').map((part) => Number(part.trim())).filter(Number.isFinite);
    if (!statuses.length || statuses.some((code) => code < 100 || code > 599)) { setError('Expected status codes must be between 100 and 599.'); return; }
    if (new Set(statuses).size !== statuses.length) { setError('Expected status codes cannot repeat.'); return; }
    let parsedHeaders: Record<string, string>;
    try { parsedHeaders = parseHeaders(); } catch (problem) { setError(problem instanceof Error ? problem.message : 'Invalid headers.'); return; }

    const body: Record<string, unknown> = {
      name: values.name.trim(),
      method: values.method,
      expectedStatusCodes: statuses,
      timeoutMs: Number(values.timeoutMs),
      intervalSeconds: Number(values.intervalSeconds),
      retryCount: Number(values.retryCount),
      retryBackoffMs: Number(values.retryBackoffMs),
      failureThreshold: Number(values.failureThreshold),
      recoveryThreshold: Number(values.recoveryThreshold),
    };
    if (values.url.trim()) body.url = values.url.trim();
    if (!endpoint || headersText.trim() || clearHeaders) body.headers = parsedHeaders;

    setSaving(true);
    try {
      const saved = await api<Endpoint>(endpoint ? `/api/v1/endpoints/${endpoint.id}` : '/api/v1/endpoints', {
        method: endpoint ? 'PUT' : 'POST',
        body: JSON.stringify(body),
      });
      onSaved(saved);
    } catch (problem) {
      setError(problem instanceof ApiError ? problem.message : 'Could not save this monitor.');
    } finally {
      setSaving(false);
    }
  };

  const field = (key: keyof Values, label: string, type = 'text', min?: number, max?: number) => (
    <div className="form-field" key={key}>
      <label className="form-label" htmlFor={`endpoint-${key}`}>{label}</label>
      <input id={`endpoint-${key}`} className="form-control" type={type} min={min} max={max} value={values[key]} onChange={(event) => change(key, event.target.value)} />
    </div>
  );

  return <Modal title={endpoint ? 'Edit monitor' : 'Add an endpoint'} description="Configure a secure, scheduled HTTP health check." onClose={onClose}
    footer={<><Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button><Button onClick={() => document.getElementById('endpoint-form-submit')?.click()} disabled={saving}>{saving ? 'Saving…' : endpoint ? 'Save changes' : 'Create monitor'}</Button></>}>
    <form onSubmit={submit} id="endpoint-form">
      {error && <div style={{ marginBottom: 14 }}><ErrorNotice message={error} /></div>}
      <div className="form-grid">
        <div className="form-field full"><label className="form-label" htmlFor="endpoint-name">Monitor name</label><input id="endpoint-name" className="form-control" required maxLength={120} placeholder="Payments health" value={values.name} onChange={(event) => change('name', event.target.value)} /></div>
        <div className="form-field full"><label className="form-label" htmlFor="endpoint-url">Target URL</label><input id="endpoint-url" className="form-control" type="url" maxLength={2048} required={!endpoint} placeholder={endpoint ? 'Leave blank to keep the current target' : 'https://api.example.com/health'} value={values.url} onChange={(event) => change('url', event.target.value)} /><div className="form-hint">Only public HTTP(S) destinations are accepted. Credentials in URLs are never returned by the API; use encrypted headers instead. {endpoint?.targetDisplay && <span>Current target: {endpoint.targetDisplay}</span>}</div></div>
        <div className="form-field"><label className="form-label" htmlFor="endpoint-method">HTTP method</label><select id="endpoint-method" className="form-control" value={values.method} onChange={(event) => change('method', event.target.value)}>{['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'].map((method) => <option key={method}>{method}</option>)}</select></div>
        <div className="form-field"><label className="form-label" htmlFor="endpoint-statuses">Expected status codes</label><input id="endpoint-statuses" className="form-control" value={values.statuses} onChange={(event) => change('statuses', event.target.value)} placeholder="200,204" /><div className="form-hint">Comma-separated, for example 200, 204.</div></div>
        {field('intervalSeconds', 'Check interval (seconds)', 'number', 10, 86400)}
        {field('timeoutMs', 'Timeout (ms)', 'number', 500, 30000)}
        {field('retryCount', 'Retries (max 3)', 'number', 0, 3)}
        {field('retryBackoffMs', 'Retry backoff (ms)', 'number', 100, 10000)}
        {field('failureThreshold', 'Failures to open incident', 'number', 1, 20)}
        {field('recoveryThreshold', 'Successes to resolve incident', 'number', 1, 20)}
        <div className="form-field full"><label className="form-label" htmlFor="endpoint-headers">Request headers</label><textarea id="endpoint-headers" className="form-control" value={headersText} onChange={(event) => setHeadersText(event.target.value)} placeholder="Authorization: Bearer …\nX-Environment: production" /><div className="form-hint">Header values are encrypted at rest and never returned. {endpoint?.headerNames.length ? `Saved headers: ${endpoint.headerNames.join(', ')}. Leave blank to keep them.` : 'Use one Key: value per line.'}</div></div>
        {endpoint?.headerNames.length ? <label className="form-field full" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: '#667085' }}><input type="checkbox" checked={clearHeaders} onChange={(event) => setClearHeaders(event.target.checked)} />Remove saved request headers</label> : null}
      </div>
      <button type="submit" id="endpoint-form-submit" style={{ display: 'none' }} aria-hidden="true" />
    </form>
  </Modal>;
}
