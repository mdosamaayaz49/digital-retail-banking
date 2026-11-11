# Testing guide

```sh
mvn -f backend/pom.xml verify  # Docker required for PostgreSQL Testcontainers
cd frontend
npm ci
npm test
npm run build
npx playwright install --with-deps chromium
npm run test:e2e
```

Use real PostgreSQL, not H2, for locking, constraint triggers and rollback tests. `BankingIntegrationTest` resets only its isolated Testcontainers database between tests. Never point it at a shared or real database. It tests settled internal/own transfers, insufficient funds, invalid beneficiary, frozen accounts, KYC, invalid precision, concurrent spending, concurrent duplicate keys, outbox-failure rollback, immutable history, database balanced-entry enforcement, reversals, account ownership, HTTP authentication/admin authorization and PDF access.

`OutboxPublisherTest` uses Mockito to verify broker-acknowledged publication versus retained retry after failure. `KafkaDeliveryTest` runs an actual embedded KRaft Kafka broker and injects transient database failures to verify listener redelivery. `NotificationPersistenceTest` verifies event-ID deduplication against a real PostgreSQL primary key and `ON CONFLICT`. The embedded retry test uses a JDBC mock and does not establish DB/Kafka exactly-once semantics. Add deployment fault injection for process crashes, poison messages and replay/reconciliation before operational use.

Frontend React Testing Library checks the preview label and account total. All six Playwright tests have executed successfully: preview validation/mobile layout, authenticated account opening/closure/PDF/logout, transfer/admin compensation, mock rejection, and supported Keycloak MFA enrollment plus a subsequent OTP login. The authenticated suite requires the generated local .env; provider SMTP/email recovery remains an infrastructure setup task.

See verification.md for the checks actually performed in the authoring environment. CI is a configuration until a GitHub run proves it; local tests do not establish cluster deployment success.

## Demo scenarios

1. Sign in as Amira. Transfer RM 500 from Everyday Savings to Daniel. Verify source 12,350 and Daniel 3,700 on fresh seed; two balanced postings and audit/outbox records exist.
2. Send RM 9,000 from Lifestyle Current (5,680.50). Expect insufficient-funds rejection and no changed balances.
3. Replay the API request with the same key/payload; original reference and unchanged balances. Change amount with same key → 409.
4. Attempt GET Daniel's account ID with Amira's token → 404; CUSTOMER requests /admin → 403.
5. Reviewer reverses the settled transfer. Verify new compensating reference, original REVERSED and restored balances.
6. Generate a statement spanning the opening seed and transfer period, download PDF; opening plus signed period entries equals closing.
7. Reviewer verifies Daniel KYC, freezes/unfreezes an account, examines immutable business audit logs and transaction review flags.
8. Stop Kafka, settle a transfer, observe committed balance and pending outbox; restart Kafka and observe notifications without a second debit. This outage scenario requires manual stack verification beyond the embedded-broker tests.

## Expanded financial and operational coverage

Additional PostgreSQL tests cover scheduled hold reservation/release, concurrent reservations, cancel/settle races, future settlement protection, mock acceptance/rejection, disabled accounts at settlement, order ownership, manual hold restrictions, failed-request notification deduplication, closed-account reopening, read-only SUPPORT permissions, Malaysian midnight boundaries, monthly analytics and gross-versus-compensation reporting. Boundary tests boot the identity/customer/statement apps and reject foreign domain routes. Production Kafka backoff/quarantine policy runs against the embedded KRaft broker.

Run `sh scripts/run-local.sh` after starting infrastructure, export `.env`, then run frontend `npm run test:e2e` to execute all journeys. Global setup waits for every service's readiness. Without DEMO_PASSWORD, authenticated tests are explicitly skipped and only preview tests run. The MFA test also requires KEYCLOAK_ADMIN_PASSWORD and deletes only its own generated synthetic identity. Test tokens and OTP secrets remain in memory; tracing/video/screenshot capture is disabled for authenticated tests.

Full-stack checks also exercised committed outbox delivery, duplicate event reception, durable poison-message quarantine, corrected admin replay and repeated repair idempotency with a real local KRaft broker and PostgreSQL. The standard Docker broker uses the same Kafka protocol; the native development broker is provided for disk-constrained environments.
