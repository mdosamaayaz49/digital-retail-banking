# Nusantara · Digital Retail Banking

A Java 21 and React portfolio simulation of Malaysian retail banking. Fictional identities, MYR funds, accounts and payment networks only. No affiliation with any bank, DuitNow, PayNet or Bank Negara Malaysia.

## Run locally

Prerequisites: **JDK 21 (including javac)**, Maven 3.9+, Node 22.12+ or 24 LTS, Docker Engine and Docker Compose. Allow at least **10 GB free disk and 8 GB RAM**; rootless Docker with the vfs driver may require substantially more disk.

```sh
sh scripts/init-env.sh
docker compose up -d postgres redis kafka keycloak
# Read the generated DEMO_PASSWORD from your local .env; never commit it.
set -a
. ./.env
set +a
mvn -f backend/pom.xml verify
mvn -f backend/pom.xml package -DskipTests
```

In three terminals, after exporting `.env` in each:

```sh
java -jar backend/transfer-service/target/transfer-service-1.0.0-SNAPSHOT.jar
java -jar backend/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
java -jar backend/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
```

```sh
cd frontend
npm ci
npm run dev
```

Visit **http://localhost:5173**. The initial dashboard is explicitly a **read-only, illustrative preview**. Choose **Sign in to banking** to use database-backed accounts. Keycloak runs at http://localhost:8180. Users `amira`, `daniel` and `reviewer` share the locally generated `DEMO_PASSWORD`; these are fictional developer identities. Amira is verified; Daniel starts with pending simulated KYC; reviewer is ADMIN and SUPPORT. `platform-admin` is the identity-server administrator, with a separate generated password.

Alternative, on a normal Docker host:

```sh
sh scripts/init-env.sh
docker compose up --build -d
# When finished:
docker compose down
```

Wait for Keycloak's realm endpoint and PostgreSQL health. Banking Flyway migrations must finish before starting the notification consumer; it restarts on startup failure. Container builds need outbound Maven/npm access. In managed proxies, see docs/troubleshooting.md for CA trust. The first Java startup applies schema and fictional funding migrations.

## Repository

```text
digital-retail-banking/
├── frontend/                 React + TypeScript + Vite
├── backend/
│   ├── api-gateway/          Executable Spring Cloud Gateway, Redis rate limiting
│   ├── auth-service/         Boundary notes; Keycloak supplies identity
│   ├── customer-service/     Extraction boundary; implemented in banking core
│   ├── account-service/      Extraction boundary; implemented in banking core
│   ├── transfer-service/     Executable banking core and atomic ledger owner
│   ├── statement-service/    Extraction boundary; implemented in banking core
│   └── notification-service/ Executable Kafka consumer
├── infrastructure/{docker,kubernetes,openshift}/
├── docs/
├── scripts/
├── .github/workflows/
└── docker-compose.yml
```

## What this release implements

- Keycloak Authorization Code + PKCE, JWT issuer/audience checks, CUSTOMER/ADMIN/SUPPORT realm roles; no application password storage.
- Customer profile provisioning/editing, mock KYC review, account ownership, balance and status views, admin freeze/unfreeze.
- Beneficiary registration/removal; internal own-account and cross-customer transfers.
- `BigDecimal` + PostgreSQL `NUMERIC(19,2)`, customer serialization, ordered account locks, available-funds and transfer-limit checks.
- Customer-scoped idempotency keys with payload fingerprint checks; two ledger postings, balance updates, audit and outbox in one transaction.
- Append-only ledger and audit triggers, deferred database balance check, compensating reversal entries.
- Searchable, date-filtered, paginated transaction history and details; ledger-derived PDF statements with ownership checks.
- Transactional outbox publisher, Kafka notifications with event-ID deduplication and persistent retry.
- Responsive customer and admin interfaces; explicit loading, error, empty, confirmation and success states.

## Honest scope

This is a **modular banking core with three executable services**, not seven independently deployed domain services. Accounts and ledger share a PostgreSQL transaction. Notification storage currently shares the database; the consumer has a distinct deployable process. Extraction notes describe the next architecture without presenting empty directories as completed microservices.

Not yet implemented: DuitNow-style mock external routing/reconciliation, scheduled or cancellable pending transfers (internal settlement is immediate), account opening/closure UI, hold lifecycle, production AML engine, period-accurate live analytics (live overview shows only latest five entries), MFA enrollment automation, automated identity-session browser tests, database-per-service isolation, monitoring dashboards, production TLS/secret management or a fully exercised Kubernetes rollout. Monthly statements work; the API also accepts arbitrary past ranges up to one year. MFA enrollment and credential recovery use Keycloak's account console; SMTP must be configured for email flows.

A new self-registered identity can provision its profile but receives no accounts or funds automatically. Use seeded identities for the complete demo. Read-only preview charts/activity are illustrative and differ from the reconciled database seed. This project does not confer regulated banking experience or production readiness.

## Verification and learning

See [verification](docs/verification.md) for exact checks run and limits, [checklist](docs/checklist.md) for phase progress, [architecture](docs/architecture.md) for diagrams and consistency, [security](docs/security.md), [API](docs/api.md), [testing](docs/testing.md), [deployment](docs/deployment.md), and [interview notes](docs/interview.md).

OpenAPI: http://localhost:8081/swagger-ui.html and http://localhost:8081/v3/api-docs. The gateway serves `/api/**` only. Health: ports 8080, 8081 and 8082 `/actuator/health`. Development ports bind to loopback. Production must expose only a TLS ingress, not PostgreSQL, Keycloak management or core ports.

## Demo reset

`docker compose down` preserves PostgreSQL data. To intentionally delete all local simulation history, first stop the app, then run `docker compose down -v` and restart. Keycloak's development store is ephemeral; reimported demo subjects match the seed. Never use this setup for real data.
