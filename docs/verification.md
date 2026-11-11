# Release verification · 11 November 2026

## Application and tests

- **Clean Java 21 / Maven reactor build passed**: seven executable services plus the banking-kernel library. JDK 21.0.13, Maven 3.9.11, Spring Boot 3.5.16, Spring Cloud 2025.0.3.
- **66 Java tests passed, zero failures or skipped**: identity boundary 4, customer boundary 3, statement boundary 3, banking integration 51, outbox publisher 2, Kafka delivery/quarantine 2, notification persistence 1.
- Real PostgreSQL Testcontainers cover atomic settlement, funds/precision/KYC/beneficiary/status/ownership checks, gross daily limits, concurrent spend/reservation/duplicate requests, injected rollback, immutable postings/audit, cached balance/hold integrity, compensation/reversal ownership, account opening/closure, holds, scheduled settlement/cancellation/future protection, mock accept/reject, reporting, Malaysian midnight boundaries, analytics and PDF permissions.
- The embedded KRaft tests exercise the **production** backoff/quarantine handler, transient retry and poison-message recovery. PostgreSQL verifies event-ID notification deduplication.
- **React Testing Library/Vitest: two tests passed**. TypeScript/Vite production build passed; chart/form/identity vendor chunks are separated.
- **npm audit: 0 known vulnerabilities** across installed production and development packages at verification time.
- **Six Playwright tests passed with no skips** on installed Chromium: preview validation, mobile layout/navigation, authenticated opening/closure/custom-range PDF/logout, transfer with admin compensation/reconciliation, mock-network rejection, and supported Keycloak authenticator enrollment plus subsequent fresh-code MFA login.
- MFA test identities are generated synthetically and deleted. Tokens, passwords and OTP secrets stayed in test process memory; authenticated tracing/video/screenshots are disabled. An initial test incorrectly reused an enrollment code within the same TOTP window; the final test respects provider replay protection and waits for a fresh code.
- Desktop/mobile screenshots were inspected for internal QA; no authenticated secrets were captured.

## Live topology and integration

- The documented run-local.sh/stop-local.sh lifecycle was exercised successfully, including readiness waits and seven recorded process IDs.
- All **seven service processes** returned readiness 200 against real local PostgreSQL, Redis and Keycloak, with a real native KRaft broker for the disk-constrained environment. Other domain services route through Spring Cloud Gateway; they do not depend on a hidden monolith API.
- **17 live API/OIDC checks passed again on the final topology**: real Authorization Code + S256 PKCE, access-token acceptance, signed ID-token rejection by API audience, anonymous rejection, object and role permissions, transfer settlement/replay/conflict, insufficient funds, history, admin compensation/replay, restored balances, PDF generation/download and audit access.
- Full SQL outbox → actual Kafka → notification consumer delivery was observed. Pending outbox drained to zero. Duplicate event reception did not create another notification.
- A deliberately malformed event was durably quarantined after production-policy retries. Corrected admin replay delivered one notification; repeating the repair returned safely without duplication. Quarantine/original/repaired metadata and audit were retained.
- Keycloak authentication/admin events were enabled with demo retention; a real OIDC login appeared in its native event store. Stale synthetic identities from an earlier failed test attempt were removed.
- SQL reconciliation returned **zero balance differences and zero unbalanced transfers**. Live financial tests reverse their demonstration transfers instead of deleting history. Opening/closure and rejected-order histories remain available.
- The final public customer/transfer/beneficiary/history/statement/order APIs use typed response DTOs; internal fingerprints and mock outcome decisions remain private.

## Configuration validation

- Main Compose and the optional Prometheus/Grafana overlay passed `docker compose config --quiet`.
- **18 Kubernetes resources** in ten files passed strict kubeconform validation.
- **OpenShift Route passed** against the official openshift/api OpenAPI definition, using the frontend Service's named http port.
- Prometheus/Grafana datasource, provisioned dashboard, service metrics, seven Dockerfiles, local lifecycle/native-broker scripts and CI are supplied.
- Git staging review excludes .env variants, runtime logs/PIDs, generated test reports, target, dist and node_modules. Actual generated local secret values are checked against staged contents before publication.

## Remote CI and deployment status

The initial foundation commit's remote CI exposed an ambiguous mobile-test selector and a nonexistent Trivy action tag. Both are corrected: navigation is scoped explicitly and the action uses the verified published v0.36.0 release.

The first full-topology run [42973100581](https://github.com/mdosamaayaz49/digital-retail-banking/actions/runs/42973100581) passed backend/frontend checks, the authenticated browser/MFA job, **all seven Java image builds and the frontend image build** on fresh GitHub runners. Its security gate correctly rejected BOM-managed PostgreSQL JDBC 42.7.11 for CVE-2026-54291. The driver is explicitly upgraded to 42.7.12, with all 66 Java regression tests passing again; the security gate remains enabled. Consult the repository's latest Actions run for the patched release outcome.

The eight images were built on GitHub, not in the small local rootless-vfs filesystem. Local integration ran the seven Java processes with real supporting services, while the fresh-runner browser job used the standard Docker PostgreSQL/Redis/Kafka/Keycloak dependencies. No full application-container Compose smoke test was performed. Optional Prometheus/Grafana containers were not launched locally; their manifests, provisioned dashboard, image tags and service metrics endpoints were validated.

No Kubernetes/OpenShift cluster rollout, HA/DR exercise, load certification, real network integration, production identity/secret provisioning or regulatory certification was performed. Shared PostgreSQL is a deliberate simulation architecture; no database-per-service isolation is claimed.

## Environment details

Workspace caches and a downloaded JDK were used because the base Java installation lacked javac and home is read-only. Maven used the inherited proxy through local settings and the existing system Java CA store, with TLS verification enabled. Only this project's disposable Kafka image/container was removed after Docker vfs exhausted disk; the native KRaft helper then supplied real broker verification. These scratch tools and credentials are outside tracked source.
