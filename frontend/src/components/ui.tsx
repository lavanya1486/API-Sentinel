import { AlertCircle, Check, LoaderCircle, X } from 'lucide-react';
import type { ButtonHTMLAttributes, PropsWithChildren, ReactNode } from 'react';

export function Button({
  children, variant = 'primary', size, className = '', ...props
}: PropsWithChildren<ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' | 'ghost'; size?: 'sm'; className?: string }>) {
  return <button className={`button button-${variant}${size ? ' button-sm' : ''} ${className}`} {...props}>{children}</button>;
}

export function PageHeader({ kicker, title, subtitle, actions }: { kicker?: string; title: string; subtitle?: string; actions?: ReactNode }) {
  return <div className="page-header"><div>{kicker && <div className="page-kicker">{kicker}</div>}<h1 className="page-title">{title}</h1>{subtitle && <p className="page-subtitle">{subtitle}</p>}</div>{actions && <div className="header-actions">{actions}</div>}</div>;
}

export function ErrorNotice({ message, onDismiss }: { message: string; onDismiss?: () => void }) {
  return <div className="alert-box alert-error" role="alert" style={{ display: 'flex', alignItems: 'center', gap: 8 }}><AlertCircle size={15} /><span style={{ flex: 1 }}>{message}</span>{onDismiss && <button className="icon-button" style={{ color: 'inherit' }} onClick={onDismiss} aria-label="Dismiss error"><X size={14} /></button>}</div>;
}

export function SuccessNotice({ children }: PropsWithChildren) {
  return <div className="alert-box alert-success" role="status" style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Check size={15} />{children}</div>;
}

export function LoadingBlock({ rows = 3 }: { rows?: number }) {
  return <div className="card" aria-label="Loading" style={{ padding: 19 }}><LoaderCircle size={17} className="animate-spin" style={{ color: '#7778ee', marginBottom: 15 }} />{Array.from({ length: rows }, (_, i) => <div key={i} className="skeleton" style={{ height: 13, width: `${86 - i * 12}%`, marginBottom: 12 }} />)}</div>;
}

export function EmptyState({ icon, title, message, action }: { icon?: ReactNode; title: string; message: string; action?: ReactNode }) {
  return <div className="empty-state"><div className="empty-icon">{icon ?? <AlertCircle size={18} />}</div><h3>{title}</h3><p>{message}</p>{action}</div>;
}

export function StatusPill({ status, label }: { status: string; label?: string }) {
  const normalized = status.toLowerCase().replace(/[^a-z_]/g, '');
  return <span className={`status-pill status-${normalized}`}>{label ?? status.replace(/_/g, ' ')}</span>;
}

export function Modal({ title, description, onClose, children, footer, labelledBy }: PropsWithChildren<{
  title: string; description?: string; onClose: () => void; footer?: ReactNode; labelledBy?: string;
}>) {
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="modal" role="dialog" aria-modal="true" aria-labelledby={labelledBy ?? 'modal-title'}>
      <div className="modal-header"><div><h2 id={labelledBy ?? 'modal-title'}>{title}</h2>{description && <p>{description}</p>}</div><button className="icon-button" onClick={onClose} aria-label="Close dialog"><X size={16} /></button></div>
      <div className="modal-body">{children}</div>{footer && <div className="modal-footer">{footer}</div>}
    </section>
  </div>;
}

export function ConfirmDialog({ title, message, confirmLabel = 'Confirm', danger = false, busy = false, onCancel, onConfirm }: {
  title: string; message: string; confirmLabel?: string; danger?: boolean; busy?: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  return <Modal title={title} description="Please confirm this action." onClose={onCancel} footer={<><Button variant="secondary" onClick={onCancel} disabled={busy}>Cancel</Button><Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} disabled={busy}>{busy ? 'Working…' : confirmLabel}</Button></>}>
    <p style={{ margin: 0, color: '#667085', fontSize: 12, lineHeight: 1.6 }}>{message}</p>
  </Modal>;
}

export function StatCard({ label, value, detail, icon, tone }: { label: string; value: string; detail: string; icon: ReactNode; tone?: 'green' | 'orange' | 'red' }) {
  return <article className="card stat-card"><div className="stat-top"><span className="stat-label">{label}</span><span className={`stat-icon ${tone ?? ''}`}>{icon}</span></div><div className="stat-value">{value}</div><div className="stat-foot">{detail}</div></article>;
}

export function InlineSpinner({ label = 'Loading' }: { label?: string }) {
  return <span style={{ display: 'inline-flex', gap: 7, alignItems: 'center' }}><LoaderCircle size={14} className="animate-spin" />{label}</span>;
}
