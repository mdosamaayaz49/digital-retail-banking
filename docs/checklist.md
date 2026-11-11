# Phased implementation checklist

Checked items have source and relevant build/API/test evidence. UI source is compiled and component-tested; interactive browser verification remains open.

## Phase 1 — foundation

- [x] Inspect empty workspace; initialize monorepo and dependency versions.
- [x] Define architecture, ownership boundaries and extraction plan.
- [x] Java 21 Spring Boot, React/TypeScript/Vite and PostgreSQL/Flyway.
- [x] Keycloak OIDC + PKCE and JWT issuer/audience validation.
- [x] Account dashboard and synthetic customer seed with reconciled opening equity.
- [x] Run core and gateway; verify real Keycloak tokens and SQL-backed APIs.
- [x] React build and component test.
- [ ] Authenticated dashboard/browser/MFA walkthrough verification.

## Phase 2 — core banking

- [x] Owned accounts, filtering UI, transaction listings and details.
- [x] Customer profile provisioning/editing; beneficiary management.
- [x] Atomic own-account and cross-customer transfers.
- [x] BigDecimal/NUMERIC, available funds, per-transfer and daily limits.
- [x] Idempotency, concurrency locks, append-only double-entry ledger.
- [x] Database balance/posting reconciliation guards and compensating reversals.
- [x] Monthly PDF statements and arbitrary past date-range API.
- [x] PostgreSQL integration tests for correctness, rollback, ownership and PDF access.
- [ ] Account creation/closure workflow and account-hold lifecycle.
- [ ] Custom date-range selection in the statement UI (API supports it).
- [ ] Pending/cancellable/scheduled transfers.
- [ ] Mock DuitNow-style external network, reconciliation and rejection lifecycle.

## Phase 3 — architecture/security

- [x] Executable gateway with Redis rate limiting.
- [x] Transactional outbox and broker-acknowledged publisher with retained retries.
- [x] Independent Kafka consumer, PostgreSQL event-ID deduplication.
- [x] Embedded Kafka test for actual broker delivery and transient consumer retry.
- [x] Admin dashboard, KYC review, account freeze/unfreeze, reversal and audit APIs/UI.
- [x] Server-side object and role permissions; consistent business errors; security design.
- [x] Keycloak development lockout, optional MFA enrollment and event-retention configuration.
- [ ] Full live-stack Kafka outage/restart and crash-window fault injection.
- [ ] Failed-transfer asynchronous notification workflow, poison-event operations and metrics.
- [ ] Independent customer/account/statement services with isolated stores.
- [ ] Production BFF evaluation, privilege separation, identity hardening and monitoring.

## Phase 4 — delivery

- [x] All three Maven modules package; 31 Java tests pass.
- [x] React test/build and npm dependency audit pass.
- [x] Dockerfiles, Compose configuration and successful infrastructure startup (Kafka removed locally after disk exhaustion).
- [x] CI workflows, Kubernetes and OpenShift application templates.
- [x] Architecture/ER/lifecycle diagrams, API/security/testing/deployment/troubleshooting documents.
- [x] Demo scenarios, five-minute presentation, interview Q&A and honest résumé notes.
- [ ] Browser Playwright execution and authenticated E2E automation.
- [ ] End-to-end Docker image build verification in a sufficiently large environment.
- [ ] Actual GitHub Actions run, Trivy scan and Kubernetes/OpenShift rollout.

No seven-service completion, production-readiness or regulated banking experience is claimed. See verification.md for exact evidence and README.md for commands and limitations.
