# Nusantara · Digital Retail Banking

A complete **portfolio banking simulation** built with Java 21, Spring Boot 3.5 and React/TypeScript. It demonstrates secure internet banking, atomic double-entry transfers, scheduled reservations, a fictional DuitNow-style network, PDF statements and reliable Kafka notifications.

**Fictional customers, accounts and MYR funds only. No real financial transactions or affiliation with a bank, DuitNow, PayNet or Bank Negara Malaysia.** This is personal project experience, not regulated banking-domain employment or a production banking system.

## Start locally

Prerequisites: Docker Engine + Compose. For development/testing: JDK 21 **including javac**, Maven 3.9+, Node 22.12+ or Node 24, curl and OpenSSL. Allow at least 10 GB free disk and 8 GB RAM. Docker's vfs driver can require substantially more disk.

```sh
sh scripts/init-env.sh
docker compose up --build -d
```

Wait for the banking service to become healthy and Keycloak realm import to finish, then open **http://localhost:5173**. The initial dashboard is an explicitly labelled **read-only preview**. Choose **Sign in to banking** for real simulation database accounts.

Fictional users `amira`, `daniel` and `reviewer` use the random `DEMO_PASSWORD` generated in your **local, ignored `.env`**. Read it locally; never commit it. `reviewer` has ADMIN/SUPPORT; Amira starts VERIFIED; Daniel starts with PENDING simulated KYC. Keycloak administration is http://localhost:8180 (`platform-admin`, separate generated `KEYCLOAK_ADMIN_PASSWORD`). Passwords, recovery, authenticator enrollment and sessions belong to Keycloak. Configure a mock SMTP server to demonstrate email recovery; never use real customer addresses.

### Development without application containers

```sh
sh scripts/init-env.sh
docker compose up -d postgres redis kafka keycloak
sh scripts/run-local.sh
cd frontend
npm ci
npm run dev
```

The script builds all services, starts migration owner first, waits for readiness and records PIDs/logs in ignored `.runtime/`. Stop with `sh scripts/stop-local.sh` from the repository root. Do not rebuild executable JARs while they are running.

If Kafka's container image exceeds a constrained environment's disk, use a **real embedded KRaft development broker**, not a mock: start only PostgreSQL/Redis/Keycloak with Compose, run `sh scripts/kafka-local.sh` in one terminal, then `sh scripts/run-local.sh` in another. The broker writes its random bootstrap address to `.runtime/kafka.bootstrap`; the startup script detects it. Do not run both broker options for the same demo. This helper requires Java/Maven test dependencies and is not packaged in production images.

## Implemented banking journeys

- OIDC Authorization Code + S256 PKCE, short-lived JWTs, refresh-token rotation, logout, role checks, login lockout and supported Keycloak authenticator MFA. Tokens remain in browser memory.
- Fictional profile registration/editing; admin KYC review; savings/current account opening at **zero balance**, ownership, filtering, available balance, status and zero-fund closure with retained history.
- Beneficiary registration/removal; immediate own-account and cross-customer MYR transfers; scale, funds, active status, KYC, per-transfer and Malaysian daily-limit validation.
- Customer-scoped idempotency, request fingerprint conflicts, deterministic row locking, atomic debit/credit, two balanced postings, audit and outbox in one SQL transaction.
- Append-only financial/audit records; deferred database checks for transfer postings, cached balances and held funds; compensating reversals, never history deletion.
- Scheduled transfers (up to 90 days), reserved funds, cancellation before settlement, concurrent settlement protection and failure notifications.
- Fictional DuitNow-style alias routing: `daniel@example.test` accepts; `reject@example.test` rejects. Both resolve to Daniel's fictional account and require his registered beneficiary. Accepted payments use the real simulation ledger; rejected payments release reservations. Network messages and unique references reconcile to settled transfers.
- Search/date/pagination, transaction details, ledger-derived monthly/custom-range PDF statements, and six-month live cashflow excluding own-account movements.
- Kafka outbox publishing, retries, consumer deduplication, durable poison-message quarantine and audited admin repair/replay.
- Separate admin UI for KYC, freeze/unfreeze, holds, transaction review/reversal, immutable audit, reconciliation, reports/CSV and notification recovery. SUPPORT has server-side read-only reconciliation access.
- Structured logs, Actuator readiness/liveness and Prometheus metrics, optional Prometheus/Grafana configuration, CI, Dockerfiles, Kubernetes and OpenShift application manifests.

All financial decisions execute on the server. Preview activity/charts are illustrative and intentionally distinct from the reconciled database seed.

## Seven executable services

| Service | Port | Responsibility |
|---|---:|---|
| api-gateway | 8080 | Routes, Redis token-bucket rate limiting |
| transfer-service | 8081 | Ledger, transfers, holds/orders, mock network, admin, outbox, migrations |
| notification-service | 8082 | Kafka consumption, idempotent notification storage, quarantine/recovery |
| customer-service | 8083 | Fictional profile provisioning and editing |
| account-service | 8084 | Owned account read models and zero-fund opening/closure |
| statement-service | 8085 | Authorized ledger-derived PDF statements |
| auth-service | 8086 | Public provider discovery and authenticated identity/session metadata |

`backend/banking-kernel` is a regular shared domain/security/DTO library. Each service imports only its designated controllers. Keycloak is the identity provider, not an application password database. The simulation deliberately shares PostgreSQL: a single ledger owner preserves financial consistency; this is **not database-per-service isolation**. See [architecture](docs/architecture.md) for the tradeoff and safe scaling path.

## Repository

```text
frontend/                       React, TypeScript, Vite, Tailwind and Playwright
backend/
  banking-kernel/               Shared domain/security/contracts/business modules
  api-gateway/ auth-service/ customer-service/ account-service/
  transfer-service/ statement-service/ notification-service/
infrastructure/
  docker/ kubernetes/ openshift/ monitoring/
docs/                           Architecture, API, security, tests and interview notes
scripts/                        Environment, local lifecycle, broker and reconciliation
.github/workflows/ci.yml         Java, frontend, security, containers and authenticated E2E
```

## Verify

```sh
mvn -B -f backend/pom.xml clean verify   # Docker needed for isolated PostgreSQL tests
cd frontend
npm ci
npm test
npm run build
npx playwright install --with-deps chromium
npm run test:e2e                       # Preview tests; authenticated tests require env below
```

For all browser journeys, start the complete stack and export `.env` before running Playwright:

```sh
set -a
. ./.env
set +a
cd frontend
npm run test:e2e
```

MFA testing creates and deletes a synthetic identity using the local Keycloak administrator, enrolls an authenticator and verifies a subsequent OTP login. Never enable traces or token/credential logging for this suite. `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium` uses an existing browser instead of downloading one.

See [verification evidence](docs/verification.md) for actual outcomes and execution limits, [testing/scenarios](docs/testing.md), [implementation checklist](docs/checklist.md), [security](docs/security.md), [API](docs/api.md), [deployment](docs/deployment.md), [troubleshooting](docs/troubleshooting.md), and [interview presentation/Q&A](docs/interview.md).

Swagger/OpenAPI is available on each domain service's loopback port at `/swagger-ui.html` and `/v3/api-docs`. Gateway publishes `/api/**` only. `/actuator/health/readiness`, `/actuator/health/liveness` and `/actuator/prometheus` remain internal/loopback endpoints.

## Scope and deployment boundaries

This implements the requested educational banking workflows, including simulated KYC/AML review flags, not a production AML/fraud engine, real network settlement, real identity verification or regulatory compliance. All mock-network recipients live in this simulation's ledger; there is no external bank integration. The admin UI highlights transfers above RM 5,000 for educational review.

A newly registered identity can provision a fictional profile, receive admin KYC verification and open zero-balance accounts. It receives no automatic grant of money. Use the seeded users for funded scenarios. Statement periods are at most one year/5,000 entries per PDF; narrow the range for larger histories.

Production TLS/secret provisioning, database privilege separation, durable identity/broker storage, HA/DR, load certification and a real cluster rollout require deployment infrastructure. Kubernetes/OpenShift schemas are validated, but schema validation is not an exercised cluster deployment. See deployment.md and verification.md; no unperformed checks are claimed.

`docker compose down` preserves PostgreSQL. To intentionally delete **all local simulation history**, stop local services and run `docker compose down -v`, then restart. Keycloak's development store is ephemeral. Never use this setup for real customer data.
