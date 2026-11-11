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

Frontend React Testing Library checks the preview label and account total. Playwright specifications cover navigation, transfer validation and mobile overflow; these specs are provided even if the session's browser capability prevents executing them. Full authenticated browser journeys, MFA and identity recovery are still manual verification tasks.

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
