# Completed application checklist

## Phase 1 — working foundation

- [x] Java 21/Spring Boot, React/TypeScript/Vite, PostgreSQL/Flyway and reconciled synthetic seed.
- [x] Keycloak OIDC/PKCE, JWT issuer/audience/expiry/subject checks, roles and logout.
- [x] Supported authenticator enrollment and subsequent MFA login verified in a real browser.
- [x] Secure-session restoration, profile provisioning and responsive account dashboard.

## Phase 2 — banking workflows

- [x] Savings/current account opening, zero-fund closure, ownership, filtering and retained history.
- [x] Beneficiaries, profile editing, simulated KYC, account freeze/unfreeze.
- [x] BigDecimal/NUMERIC, per-transfer/daily limits, available funds and manual holds.
- [x] Atomic debit/credit, two-entry ledger, customer/account locks, idempotency and compensation.
- [x] Deferred ledger/balance/hold integrity guards and immutable financial/audit history.
- [x] Scheduled reservations, cancellation, concurrent settlement and future-date protection.
- [x] Mock DuitNow-style aliases, acceptance/rejection, references and reconciliation.
- [x] Search/date/pagination/details, monthly/custom-range PDF and ledger-based cashflow.

## Phase 3 — architecture and operations

- [x] Seven executable service entry points and a regular shared banking-kernel library.
- [x] Specific gateway routes, Redis peer-IP rate limit, independent JWT/object/role enforcement.
- [x] Customer/account/statement routes excluded from the deployed ledger service.
- [x] Outbox publisher, Kafka consumer, deduplication and failure notifications.
- [x] Production retry policy, durable quarantine and audited idempotent repair/replay.
- [x] Admin KYC/accounts/holds/transactions/audit/reconciliation/reports/CSV/recovery UI.
- [x] Keycloak authentication audit, optional MFA and brute-force policy configuration.
- [x] Structured logs, readiness/liveness, Prometheus metrics and monitoring configuration.

## Phase 4 — verification and delivery

- [x] Clean Java reactor builds and database/broker/boundary tests.
- [x] React test, TypeScript/Vite build and npm audit.
- [x] Six real-browser Playwright journeys, including MFA, PDF, reversal and mobile layout.
- [x] All seven processes started with real PostgreSQL/Redis/Keycloak/Kafka supporting services.
- [x] Live OIDC/API and outbox → Kafka → notification → quarantine/repair verification.
- [x] Dockerfiles/Compose, local lifecycle/broker scripts, CI and authenticated E2E job.
- [x] Kubernetes and OpenShift schemas validated.
- [x] Architecture/ER/lifecycle diagrams, API/security/testing/deployment/troubleshooting and interview notes.

## Deployment boundaries, not unfinished simulation workflows

The current service topology intentionally shares PostgreSQL. Database-per-service isolation, production TLS/secrets/privilege separation, HA/DR, certified KYC/AML/fraud controls and real payment-network connectivity are not claimed. No regulated banking experience is implied. Actual container image/remote CI/cluster execution status is recorded separately in verification.md; schema validation does not prove a cluster rollout.
