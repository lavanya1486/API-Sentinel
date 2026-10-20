# API Sentinel

API Sentinel is a full-stack API availability and latency monitoring platform. It includes a Java 21 / Spring Boot REST API, PostgreSQL persistence with Flyway migrations, and a React + TypeScript + Vite interface styled with Tailwind CSS and visualized with Recharts.

## Features

- **Accounts and isolation:** email/password registration and sign-in, BCrypt password hashes, short-lived signed JWT access tokens, password-change token revocation, and owner-scoped reads/writes for endpoints, incidents, alerts, and delivery history.
- **Endpoint inventory:** create, edit, search, sort, paginate, pause/resume, archive, and manually check monitors. Methods, expected HTTP statuses, timeouts, cadence, retry policy, and consecutive failure/recovery thresholds are configurable.
- **Scheduled checks:** bounded worker pool, database-backed check leases, retry/backoff, response-size cap, status/latency history, and automatic retention cleanup.
- **SSRF-aware outbound requests:** only HTTP(S) monitor targets are accepted; DNS answers are checked for public routability and reused by the HTTP client's resolver; private, loopback, link-local, multicast, common special-use/reserved ranges, and known metadata destinations are rejected; redirects are not followed. HTTPS is required for webhooks.
- **Analytics:** selectable 1-hour, 24-hour, 7-day, 30-day, and 90-day windows with uptime/error rate, average/P95/P99 latency, and time-bucketed chart data.
- **Incidents:** configurable consecutive-failure opening and consecutive-success recovery thresholds, one active incident per endpoint, and an event timeline.
- **Webhook alerts:** encrypted HTTPS webhook destinations, event selection for incident open/recovery, an asynchronous persisted delivery outbox, bounded retries/backoff, delivery history, and secret-free event payloads.
- **Secret handling:** monitor URLs, request headers, and webhook URLs are encrypted at rest with AES-256-GCM. API responses show only a sanitized target host and header names, never stored credentials or the full target URL.
- **Operations/security:** CORS allowlist, JSON error envelopes, per-IP in-memory rate limiting, health endpoint, security headers, Nginx SPA/API proxy, Docker Compose, OpenAPI/Swagger UI, and GitHub Actions CI workflow.

## Stack

| Area | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.16, Spring Security, Spring Data JPA, Springdoc OpenAPI |
| Database | PostgreSQL 16+ and Flyway SQL migrations |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS 4, React Router, Recharts |
| Tests | JUnit 5, Mockito, MockWebServer, Testcontainers; Vitest, Testing Library, jsdom |
| Delivery | Multi-stage Dockerfiles, Docker Compose, unprivileged Nginx container, GitHub Actions |

## Quick start with Docker Compose

Requirements: Docker Engine with the Compose v2 plugin and OpenSSL.

1. Copy the development template and replace all three development secrets:

   ```sh
   cp .env.example .env
   openssl rand -hex 32       # POSTGRES_PASSWORD
   openssl rand -base64 48    # JWT_SECRET (at least 32 UTF-8 bytes)
   openssl rand -base64 32    # APP_ENCRYPTION_KEY (must decode to exactly 32 bytes)
   ```

   Put the generated values in `.env`. Do not commit or reuse `.env`, the example values, or secrets across environments. Keep the encryption key backed up: changing it without a deliberate data-key migration makes previously encrypted endpoint and webhook settings undecryptable.

2. Build and start the stack:

   ```sh
   docker compose -f compose.yml up --build -d
   docker compose -f compose.yml ps
   ```

   The database is initialized by Flyway on backend startup. Open:

   - Web app: <http://localhost:3000>
   - OpenAPI/Swagger UI: <http://localhost:3000/swagger-ui.html>
   - OpenAPI JSON: <http://localhost:3000/v3/api-docs>
   - Health check: <http://localhost:3000/actuator/health>

   Register an account in the UI to begin. The API and database are **not** published as host ports; browser traffic reaches the backend through Nginx.

3. Stop the services with `docker compose -f compose.yml down`. Persistent database files remain in the `postgres-data` volume. **Destructive:** `docker compose -f compose.yml down -v` also deletes that database volume.

To run the frontend on another host port, set `FRONTEND_PORT` in `.env` (default `3000`). For a production host behind a TLS-terminating reverse proxy, a binding such as `FRONTEND_PORT=127.0.0.1:3000` keeps the HTTP container port off the public interface. Terminate TLS at a trusted ingress, have that ingress overwrite forwarded-proto/client-IP headers, configure HSTS there, use backups/monitoring, and restrict outbound network access as defense in depth. The Compose stack itself does not provision certificates or a public deployment.

## Local development (without Docker)

Requirements: Java 21, PostgreSQL 16+, Node.js 20.19+ (or 22.13+), npm, and OpenSSL. The Maven wrapper downloads its pinned Maven distribution on first use.

1. Create a local database and role (or supply different datasource settings):

   ```sh
   createuser --pwprompt apisentinel
   createdb --owner=apisentinel apisentinel
   ```

2. In the backend terminal, set environment variables and start the API. Flyway creates the schema automatically.

   ```sh
   cd backend
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/apisentinel
   export SPRING_DATASOURCE_USERNAME=apisentinel
   export SPRING_DATASOURCE_PASSWORD='your-local-postgres-password'
   export JWT_SECRET="$(openssl rand -base64 48)"
   export APP_ENCRYPTION_KEY="$(openssl rand -base64 32)"
   export CORS_ALLOWED_ORIGINS=http://localhost:5173
   ./mvnw spring-boot:run
   ```

3. In another terminal, install the locked frontend dependencies and run Vite:

   ```sh
   cd frontend
   npm ci
   VITE_API_PROXY=http://localhost:8080 npm run dev
   ```

   Open <http://localhost:5173>. The Vite development proxy forwards `/api`, `/actuator`, and `/v3/api-docs` to the backend. The Spring Boot defaults in `application.yml` include local-only development credentials; set proper environment variables for every non-local environment.

## Configuration

The Compose variables are documented in `.env.example`. Important settings:

| Variable | Purpose | Default / requirement |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | PostgreSQL database and credentials | Compose defaults DB/user to `apisentinel`; set a strong password |
| `JWT_SECRET` | HMAC-SHA256 JWT signing key | At least 32 UTF-8 bytes; generate independently per environment |
| `APP_ENCRYPTION_KEY` | AES-256-GCM key for target URLs, headers, and webhooks | Base64 encoding of exactly 32 random bytes; preserve for decryption |
| `JWT_TTL_MINUTES` | Access-token lifetime | 15 minutes |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact browser origins | Local UI origins in the example; keep production allowlist narrow |
| `FRONTEND_PORT` | Published Nginx port mapping | `3000` |
| `MONITOR_SCAN_DELAY_MS` | Due-monitor scan interval | 5000 ms |
| `MONITOR_MAX_CONCURRENT_CHECKS` | Maximum monitor worker count | 8 |
| `HISTORY_RETENTION_DAYS` | Health-check history retention | 90 days (bounded by the application) |
| `DB_POOL_SIZE` | Backend connection-pool maximum | 12 |
| `VITE_API_PROXY` | Vite-only backend proxy target | `http://localhost:8080` |
| `TRUST_PROXY_HEADERS` | Trust the `X-Real-IP` header for rate-limit identity | `false` outside Compose; Compose enables it only behind its Nginx proxy |

Webhook dispatch scanning is configurable through `NOTIFICATION_SCAN_DELAY_MS` (default 5000 ms); retry count is capped at five attempts. Monitoring response bodies are read only up to a fixed 64 KiB cap and are not stored. Monitor cadence is 10 seconds–24 hours, timeout 500 ms–30 seconds, retries 0–3, and endpoint failure/recovery thresholds 1–20. Monitoring supports public HTTP or HTTPS URLs; webhook destinations must be public HTTPS URLs.

## API outline

All application routes other than registration, login, health, and OpenAPI documentation require a `Bearer` access token. Full schemas and response models are published in OpenAPI.

| Route | Purpose |
|---|---|
| `POST /api/v1/auth/register` / `POST /api/v1/auth/login` | Create account or receive a JWT |
| `GET /api/v1/account` / `POST /api/v1/account/password` | View account / change password (revokes existing tokens) |
| `/api/v1/endpoints` | List/search/sort, create, read, edit, pause/resume, archive monitors |
| `POST /api/v1/endpoints/{id}/check-now` | Queue an immediate monitor check |
| `GET /api/v1/endpoints/{id}/checks` | Paginated health-check history |
| `GET /api/v1/analytics/overview?range=24h` | Overview metrics and chart series (optional `endpointId`) |
| `/api/v1/incidents` and `/api/v1/incidents/{id}` | Filter incidents and inspect lifecycle events |
| `GET /api/v1/endpoints/{id}/alert` / `PUT /api/v1/endpoints/{id}/alert` | Read/update an endpoint's webhook configuration |
| `/api/v1/alerts` and `/api/v1/notifications` | List alert setups and persisted delivery results |
| `GET /actuator/health` | Health status; no component details exposed |

The browser stores the short-lived bearer token in `sessionStorage`; this is a single-browser-session convenience, not an HttpOnly-cookie design. Use HTTPS in production and protect the UI from XSS. Rate limits are in-memory and per backend instance (10 auth requests/minute and 240 other API requests/minute per derived IP); use a shared gateway/rate limiter if horizontally scaling. When proxy trust is enabled, ensure the trusted proxy overwrites `X-Real-IP` and do not expose the backend directly.

## Tests and verification

From the repository root:

```sh
cd backend && ./mvnw --batch-mode --no-transfer-progress clean verify
cd ../frontend && npm ci && npm run lint && npm test -- --run && npm run build && npm audit
```

The backend tests cover URL/DNS security rules, public DNS resolution, redirects and timeouts, retry/backoff, analytics calculations, AES-GCM secret protection, incident open/recovery transitions, and a Testcontainers API/ownership integration flow. The Testcontainers test runs when Docker is available and is configured to skip when Docker is absent. Frontend tests cover API error/auth behavior, formatting, and endpoint-form validation.

**Verification performed in this workspace (2026-10-20):**

- Backend: Maven tests passed — 14 tests, 0 failures, and 0 errors. The suite included a PostgreSQL 16 Testcontainers integration test when Docker was available, and Flyway V1 applied successfully.
- Backend compilation and packaging passed with the Java 21 target. Run the checks under an installed JDK 21 before release certification.
- Frontend: dependency installation, ESLint, Vitest (7 tests), TypeScript type checking, and the Vite production build passed. `npm audit` reported 0 vulnerabilities for the locked dependency tree at verification time.
- Docker Compose was built and started successfully with temporary audit-only secrets. PostgreSQL, backend, and frontend health checks passed. Registration through the published frontend returned HTTP 201.
- Browser E2E, a live external webhook receiver, and public deployment hardening are not included in this repository's automated verification.

## Repository layout

```text
.github/workflows/ci.yml                  Java/frontend CI
.env.example                              Development environment template
compose.yml                               PostgreSQL + API + Nginx UI stack
backend/
  Dockerfile
  mvnw, .mvn/wrapper/                     Pinned Maven bootstrap
  pom.xml
  src/main/java/dev/apisentinel/          API, security, services, persistence
  src/main/resources/db/migration/        Flyway schema
  src/test/java/dev/apisentinel/          Backend tests
frontend/
  Dockerfile, nginx.conf, *.conf, *.inc    SPA image, forwarded-proto map and security headers
  package.json, package-lock.json         Reproducible JS dependencies
  eslint.config.js                        ESLint flat configuration
  src/components/, src/pages/             React UI and routes
  src/lib/                                API client, types, formatting
  *.test.ts(x)                            Vitest and Testing Library tests
```
