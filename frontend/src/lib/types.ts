export interface ApiPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface UserSummary {
  id: string;
  email: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface Endpoint {
  id: string;
  name: string;
  targetDisplay: string;
  urlHasPath: boolean;
  method: string;
  expectedStatusCodes: number[];
  timeoutMs: number;
  intervalSeconds: number;
  retryCount: number;
  retryBackoffMs: number;
  failureThreshold: number;
  recoveryThreshold: number;
  headerNames: string[];
  enabled: boolean;
  healthStatus: 'UP' | 'DOWN' | 'PAUSED' | 'UNKNOWN' | string;
  latestResponseTimeMs: number | null;
  latestStatusCode: number | null;
  lastFailureDetail: string | null;
  lastCheckedAt: string | null;
  consecutiveFailures: number;
  createdAt: string;
  updatedAt: string;
}

export interface HealthCheck {
  id: string;
  checkedAt: string;
  responseTimeMs: number;
  statusCode: number | null;
  successful: boolean;
  failureDetail: string | null;
  attemptCount: number;
}

export interface Overview {
  range: string;
  from: string;
  to: string;
  totalEndpoints: number;
  activeEndpoints: number;
  openIncidents: number;
  totalChecks: number;
  successfulChecks: number;
  failedChecks: number;
  uptimePercent: number;
  errorRatePercent: number;
  averageLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  series: Array<{
    bucketAt: string;
    averageLatencyMs: number;
    successfulChecks: number;
    failedChecks: number;
    totalChecks: number;
  }>;
}

export interface Incident {
  id: string;
  endpointId: string;
  endpointName: string;
  status: 'OPEN' | 'RESOLVED';
  title: string;
  failureDetail: string | null;
  startedAt: string;
  resolvedAt: string | null;
  durationSeconds: number;
}

export interface IncidentDetail {
  incident: Incident;
  events: Array<{ id: string; eventType: string; detail: string; occurredAt: string }>;
}

export interface AlertConfig {
  id: string | null;
  endpointId: string;
  endpointName: string;
  enabled: boolean;
  webhookConfigured: boolean;
  eventTypes: string[];
  updatedAt: string | null;
}

export interface Notification {
  id: string;
  endpointId: string;
  endpointName: string;
  eventType: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'RETRYING' | 'SENT' | 'FAILED' | string;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  lastAttemptAt: string | null;
  nextAttemptAt: string | null;
  deliveredAt: string | null;
}

export interface Account {
  id: string;
  email: string;
  createdAt: string;
}
