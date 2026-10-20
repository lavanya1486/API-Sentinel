import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EndpointFormModal } from './EndpointFormModal';


describe('EndpointFormModal', () => {
  it('validates required endpoint details before contacting the API', async () => {
    render(<EndpointFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Monitor name'), { target: { value: 'Payments health' } });
    const form = document.querySelector('#endpoint-form');
    expect(form).not.toBeNull();
    await fireEvent.submit(form!);
    expect(screen.getByRole('alert')).toHaveTextContent('Enter an endpoint URL.');
  });

  it('keeps saved header values out of an edit form', () => {
    const endpoint = {
      id: 'endpoint-1', name: 'Private monitor', targetDisplay: 'https://api.example.net/…', urlHasPath: true,
      method: 'GET', expectedStatusCodes: [200], timeoutMs: 5000, intervalSeconds: 60, retryCount: 1,
      retryBackoffMs: 500, failureThreshold: 3, recoveryThreshold: 2, headerNames: ['Authorization'],
      enabled: true, healthStatus: 'UP', latestResponseTimeMs: 25, latestStatusCode: 200,
      lastFailureDetail: null, lastCheckedAt: null, consecutiveFailures: 0,
      createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
    };
    render(<EndpointFormModal endpoint={endpoint} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByText(/Saved headers: Authorization/)).toBeInTheDocument();
    expect(screen.getByLabelText('Request headers')).toHaveValue('');
    expect(screen.getByText(/Leave blank to keep them/)).toBeInTheDocument();
  });
});
