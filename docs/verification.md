# Verification evidence · 11 November 2026

## Passed

- Maven reactor `verify`, Java 21.0.13 JDK, Maven 3.9.11, Spring Boot 3.5.16 / Cloud 2025.0.3. All three modules built and packaged.
- **31 Java tests, zero failures, zero skipped:** 27 PostgreSQL banking integration tests, 2 outbox publisher tests, 1 embedded KRaft Kafka delivery/retry test and 1 PostgreSQL notification deduplication test.
- Banking tests cover atomic settlement, own/cross-customer transfers, insufficient funds, idempotency replay/conflict, precision, missing beneficiary, KYC, frozen accounts, concurrent spending and duplicate requests, injected rollback, ledger/audit immutability, balanced-posting enforcement, cached-balance reconciliation, gross daily limits, reversal replay, HTTP authentication/admin/ownership, profile KYC protection, beneficiary ownership, search/pagination and PDF access.
- React Testing Library/Vitest: one preview/dashboard test passed. It asserts explicit preview labeling and the RM 18,530.50 illustrative total.
- TypeScript and Vite production build passed. Vendor chart/form/identity chunks separated; largest generated chunk below Vite's 500 kB warning threshold.
- `npm audit`: 0 known vulnerabilities at check time, across all severity levels.
- `docker compose config --quiet` passed. PostgreSQL, Redis, Keycloak and Kafka infrastructure initially started; Kafka was removed to recover disk after PostgreSQL bootstrap ran out of space. PostgreSQL/Keycloak/Redis then ran successfully.
- Core and gateway ran against real local PostgreSQL/Redis/Keycloak. An HTTP test performed Authorization Code + S256 PKCE with synthetic identities; access and refresh tokens remained in process memory. Python's test client explicitly attached localhost-only secure cookies to emulate browser localhost cookie semantics; it did not disable TLS or alter provider security settings.
- **17 live HTTP checks passed:** anonymous 401, owned account list, foreign account 404, customer-admin 403, validly signed ID token rejected by API audience validation, admin customer list, settled transfer, same-key replay, changed-payload 409, insufficient funds 422, transaction search, admin reversal and replay, restored account balances, statement generation/PDF download, admin audit access.
- Final runtime applied Flyway V3 reconciliation guards successfully; live HTTP checks passed again after restarting the newly packaged services. SQL reconciliation returned zero balance differences and zero unbalanced transfers.
- OpenAPI endpoint served 21 documented paths and the final bearerAuth security scheme.
- Generated local .env, node_modules and target directories are ignored by Git. No real identities, banking credentials or funds are used.

The live financial test intentionally reversed its transfer; history/audit remain as evidence rather than being deleted. The live publisher was disabled while Kafka was absent. Embedded broker, publisher retry, and PostgreSQL consumer deduplication were tested separately; this does not prove a complete deployment-level crash recovery workflow.

## Not performed / limits

- **Browser/Playwright:** the environment advertises managed Sites preview, whose required `cua_repl.js` browser tool is unavailable in this session. The Sites skill prohibits substituting another browser-control path. Playwright specs are provided but not executed here. No visual/mobile/keyboard or authenticated browser/MFA verification is claimed.
- **Full container image builds / all-service Compose runtime:** rootless Docker with vfs consumed the small environment disk. Java services ran directly with real supporting containers. Dockerfiles and the full stack still need a sufficiently large build host and deployment validation.
- **GitHub CI / Trivy / cluster rollout:** configuration and manifests provided, but no remote workflow, scanner run or Kubernetes/OpenShift deployment was performed.
- No load/stress benchmark, real payment integration, production identity compliance or regulatory certification was performed.

## Environment repairs applied

Build caches were moved to workspace scratch because home is read-only. A real JDK 21 was downloaded because the preinstalled Java 21 runtime lacked javac. Maven proxy settings and the existing system Java CA trust store were used with TLS verification enabled. Only this project's disposable Kafka container/image was removed for disk recovery. These scratch tools/configurations are outside the repository and are not deployment dependencies.
