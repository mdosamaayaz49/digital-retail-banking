# Interview presentation and discussion

## Five-minute presentation

**0:00–0:45 · Context.** “Nusantara is a personal Malaysian retail banking simulation using Java 21, Spring Boot, PostgreSQL and React. It uses fictional people and funds. I built it to study transaction correctness and secure API design; it is not professional banking employment.”

**0:45–1:30 · Customer journey.** Show secure Keycloak sign-in, MYR savings/current balances, a registered beneficiary and transfer review. Explain preview versus real simulation database. Demonstrate a rejection without a changed balance.

**1:30–2:45 · Correctness.** Walk through customer lock, idempotency lookup, ordered account locks, validation, debit+credit, two postings, audit and outbox in one transaction. Show the duplicate request test and concurrent-spend test. Explain why one ledger owner is a deliberate consistency boundary.

**2:45–3:30 · Security.** Explain PKCE, signed JWT issuer/audience validation, object-level ownership, admin authorization and identity-provider credential ownership. Show a 404 cross-customer account probe and a 403 admin probe.

**3:30–4:15 · Events and statements.** Show reliable outbox, at-least-once Kafka delivery and event-ID deduplication; show a PDF derived from ledger history. Explain a compensation reversal rather than deletion.

**4:15–5:00 · Delivery and limits.** Explain Docker, automated PostgreSQL/embedded Kafka tests and CI. Show the seven executable services and explain their shared-database tradeoff, the fictional mock payment network, production infrastructure controls and any unverified cluster rollout.

## Services

- Gateway: route ownership and Redis peer-IP limits; each downstream service independently validates JWTs.
- Auth-service: provider discovery and validated identity metadata. Keycloak remains the credential/MFA/session authority.
- Customer-service: profile provisioning and editing; no financial postings.
- Account-service: owned views, zero-balance opening and empty-account closure; no independent debit/credit.
- Transfer-service: one authoritative transaction for balances, ledger, holds/orders, compensation, audit and outbox; migrations and admin orchestration.
- Statement-service: owned metadata and repeatable-read, ledger-derived PDFs.
- Notification-service: actual Kafka listener, DB deduplication, bounded retry/quarantine and audited repair/replay.
- Banking-kernel: shared contracts/security/business implementations, not a deployable server. Services import designated modules instead of exposing the entire kernel API.

All processes have run independently; PostgreSQL is deliberately shared for this simulation. Do not describe this as seven isolated data stores or claim arbitrary microservice scaling of a single ledger lock boundary.

## Questions with answers

**Why BigDecimal?** Binary floating point cannot exactly represent decimal currency. PostgreSQL NUMERIC and BigDecimal preserve cents/sen; validate scale and use decimal strings from the browser. Display-only formatting may convert to Number.

**Why pessimistic locking?** A balance read followed by update must not race another debit. Row locks serialize changes; deterministic account ordering limits deadlocks. Customer serialization also protects daily-limit/idempotency checks. Optimistic versions protect against stale JPA writes, but core financial SQL uses explicit locks.

**How is a duplicate avoided?** Scoped unique key plus canonical payload digest under the customer lock. A replay returns the original reference; changed payload is a conflict. This is not a global “exactly once network” claim.

**What if the credit insert fails?** The same SQL transaction rolls back debit, credit, transfer, audit and outbox. An injected outbox failure test proves rollback after financial writes.

**Why Kafka?** Decouple notification availability from financial settlement. A SQL outbox bridges the commit-to-publish gap, with retries and deduplication. A broker by itself does not make the database atomic.

**Why not distributed accounts and transfers now?** Two independent databases cannot share this local transaction. Keep a single ledger owner rather than invent unsafe distributed debit/credit. Extract profile/read projections first, with event contracts and eventual consistency.

**How does reconciliation work?** Sum postings per account and compare with stored balance; each transfer must have exactly two postings totaling zero. Opening customer funds are offset by a simulation equity account.

**How does Spring Security protect data?** Resource server validates JWT; method/filter rules enforce admin roles; repositories and queries bind authenticated subject to customer/account ownership. UI hiding is not authorization.

**Why is CSRF disabled?** APIs are bearer-only with no cookie-based authentication, so ambient browser cookies do not authenticate mutations. A future cookie BFF would need CSRF protection.

**How would you scale?** Replicate stateless APIs, retain a consistent ledger owner/partition strategy, scale outbox workers with skip-locked, add Kafka partitions and separate consumer groups, read replicas/materialized projections, bounded pagination and operational monitoring. Customer locks intentionally cap concurrent writes per customer.

**How do you deploy?** CI verifies Maven/PostgreSQL and frontend checks; Docker packages immutable processes; Kubernetes templates express probes and restricted containers. GitHub/cluster rollout must be independently verified and secrets injected outside Git.

**What would a real bank require?** Domain experts, regulated controls, reconciliation against real networks, certified integration, production identity controls, HSMs, audit retention, dual control, fraud/AML operations, SRE and tested DR. Portfolio learning is useful but does not replace that experience.

## Honest résumé bullets

- Built a personal Java 21/Spring Boot and React retail banking simulation with fictional MYR accounts, Keycloak OIDC and PostgreSQL.
- Implemented transactional double-entry transfers with row locking, idempotency keys, compensating reversals and automated concurrency/rollback tests.
- Designed a transactional outbox and Kafka notification consumer with at-least-once delivery and event-ID deduplication.
- Added ledger-derived PDF statements, administrative KYC/account controls, Docker Compose and CI/deployment templates.

Use only bullets whose verification matches docs/verification.md. Do not claim real bank integrations, regulated production operations, professional banking-domain employment, database-per-service isolation, unverified production rollouts or zero-loss guarantees.

**How do pending payments work?** Reserve available funds with a durable hold, then serialize cancellation and settlement. Cancellation releases; mock rejection releases without postings; accepted settlement releases the reservation and posts both entries atomically. A separate immutable network reference links the simulated provider decision to the ledger.

**How do you recover a poison notification?** Exponential retries, durable quarantine before offset advancement, validated admin correction and a deterministic repair event ID. DB failure during quarantine does not lose the record; repeated repair cannot duplicate its notification.

Additional honest résumé bullet: “Delivered seven executable Spring services with scheduled reservations, mock-network reconciliation, supported OIDC MFA and authenticated browser tests; documented a deliberate shared-PostgreSQL consistency boundary.”
