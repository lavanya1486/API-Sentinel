CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    token_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE monitored_endpoints (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    url_host VARCHAR(253) NOT NULL,
    url_ciphertext TEXT NOT NULL,
    headers_ciphertext TEXT,
    method VARCHAR(10) NOT NULL CHECK (method IN ('GET','HEAD','POST','PUT','PATCH','DELETE','OPTIONS')),
    expected_status_codes VARCHAR(200) NOT NULL,
    timeout_ms INTEGER NOT NULL CHECK (timeout_ms BETWEEN 500 AND 30000),
    interval_seconds INTEGER NOT NULL CHECK (interval_seconds BETWEEN 10 AND 86400),
    retry_count INTEGER NOT NULL CHECK (retry_count BETWEEN 0 AND 3),
    retry_backoff_ms INTEGER NOT NULL CHECK (retry_backoff_ms BETWEEN 100 AND 10000),
    failure_threshold INTEGER NOT NULL CHECK (failure_threshold BETWEEN 1 AND 20),
    recovery_threshold INTEGER NOT NULL CHECK (recovery_threshold BETWEEN 1 AND 20),
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    consecutive_successes INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_check_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    check_lease_until TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_endpoints_owner_created ON monitored_endpoints(owner_id, created_at DESC) WHERE archived_at IS NULL;
CREATE INDEX idx_endpoints_due ON monitored_endpoints(next_check_at) WHERE enabled = TRUE AND archived_at IS NULL;

CREATE TABLE health_checks (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL REFERENCES monitored_endpoints(id) ON DELETE CASCADE,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    response_time_ms BIGINT NOT NULL CHECK (response_time_ms >= 0),
    status_code INTEGER CHECK (status_code BETWEEN 100 AND 599),
    successful BOOLEAN NOT NULL,
    failure_detail VARCHAR(200),
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count BETWEEN 1 AND 4)
);
CREATE INDEX idx_health_checks_endpoint_time ON health_checks(endpoint_id, checked_at DESC);
CREATE INDEX idx_health_checks_time ON health_checks(checked_at);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL REFERENCES monitored_endpoints(id) ON DELETE CASCADE,
    status VARCHAR(12) NOT NULL CHECK (status IN ('OPEN','RESOLVED')),
    title VARCHAR(180) NOT NULL,
    failure_detail VARCHAR(200),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((status = 'OPEN' AND resolved_at IS NULL) OR (status = 'RESOLVED' AND resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_incident_open_per_endpoint ON incidents(endpoint_id) WHERE status = 'OPEN';
CREATE INDEX idx_incidents_status_started ON incidents(status, started_at DESC);
CREATE INDEX idx_incidents_endpoint_started ON incidents(endpoint_id, started_at DESC);

CREATE TABLE incident_events (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('OPENED','FAILURE_OBSERVED','RECOVERY_OBSERVED','RESOLVED')),
    detail VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_incident_events_incident_time ON incident_events(incident_id, occurred_at ASC);

CREATE TABLE alert_configurations (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL UNIQUE REFERENCES monitored_endpoints(id) ON DELETE CASCADE,
    webhook_url_ciphertext TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    event_types VARCHAR(100) NOT NULL DEFAULT 'INCIDENT_OPENED,INCIDENT_RESOLVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (enabled = FALSE OR webhook_url_ciphertext IS NOT NULL)
);

CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    alert_configuration_id UUID NOT NULL REFERENCES alert_configurations(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('INCIDENT_OPENED','INCIDENT_RESOLVED')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','RETRYING','SENT','FAILED')),
    payload_json TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    last_error VARCHAR(200),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_delivery_due ON notification_deliveries(status, next_attempt_at)
    WHERE status IN ('PENDING','RETRYING');
CREATE INDEX idx_notification_delivery_alert_created ON notification_deliveries(alert_configuration_id, created_at DESC);
