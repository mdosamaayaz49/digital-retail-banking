# API contract

Base `/api/v1`. All endpoints require an OIDC access token with `aud=banking-api`; admin endpoints additionally require ADMIN. JWT signature, issuer and expiry are validated by the resource server. OpenAPI is generated from executable controllers at `/v3/api-docs`; Swagger is local developer tooling. Pagination uses `page` (zero-based) and `size` (1–100); page responses contain `items,page,size,total`.

| Method / route | Request / response | Authorization |
|---|---|---|
| GET /accounts | Account[] with balance and availableBalance | Owner |
| GET /accounts/{id} | Account | Owner |
| GET /accounts/{id}/transactions | Page of signed postings | Owner |
| GET /transactions | Page of signed postings | Owner |
| POST /beneficiaries | accountNumber, nickname → beneficiary | Owner |
| GET /beneficiaries | Beneficiary[] | Owner |
| DELETE /beneficiaries/{id} | Empty success response | Owner |
| POST /transfers | TransferRequest → settled transfer | Owner, verified KYC |
| GET /transfers/{id} | Transfer | Initiator or destination owner |
| GET /customers/me | Profile | Self |
| POST /customers/me | displayName, email, phone → profile provisioning | Self |
| PATCH /customers/me | displayName, email, phone → profile | Self |
| GET /statements | Up to 100 statement metadata records | Owner |
| POST /statements/generate | accountId, from, to → metadata | Owner |
| GET /statements/{id}/download | application/pdf attachment | Owner |
| GET /notifications | Last 50 deduplicated records | Owner |
| GET /admin/summary | Counts of customers/accounts/transfers/pending KYC/outbox | ADMIN |
| GET /admin/customers | Page | ADMIN |
| GET /admin/accounts | Page | ADMIN |
| GET /admin/transactions | Page | ADMIN |
| GET /admin/audit-logs | Page | ADMIN |
| PATCH /admin/customers/{id}/kyc | status: PENDING, VERIFIED or REJECTED | ADMIN |
| PATCH /admin/accounts/{id}/status | status: ACTIVE or FROZEN | ADMIN |
| POST /admin/transfers/{id}/reverse | Compensating transfer | ADMIN |

Transaction queries accept `search`, `from` and `to` (ISO dates inclusive, Asia/Kuala_Lumpur). Own-account transfers appear as two account postings in combined history; they must not be interpreted as external cashflow. Live analytics aggregates complete monthly ledger cashflow and excludes own-account movements.

## Transfer example

Use an access token obtained through Keycloak Authorization Code + PKCE (Swagger's manual bearer authorization also accepts it). Do not copy tokens into source control or persistent shell history. The illustrative shell uses a token already loaded into process memory.

```sh
curl http://localhost:8080/api/v1/accounts -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X POST http://localhost:8080/api/v1/transfers   -H "Authorization: Bearer $ACCESS_TOKEN"   -H 'Content-Type: application/json'   -H 'Idempotency-Key: 99999999-9999-4999-8999-999999999999'   -d '{"sourceId":"10000000-0000-0000-0000-000000000001","destinationId":"10000000-0000-0000-0000-000000000003","amount":"500.00","description":"Interview demo"}'
```

Keep the same key **and identical fields** on a retry after an unknown network result. Do not generate a new key until you know a new transfer is intended. A repeated key with different fields is 409. Amount is decimal MYR, strictly positive, at most two fraction digits and at most RM 10,000. Source/destination must differ; daily gross transfer limit RM 25,000. UUIDs and enums are validated. Use decimal strings in JavaScript requests to preserve user-entered precision.

## Errors and status codes

Success uses 200 (including create and replay). Framework authentication failures use 401 or 403. Banking errors use RFC 9457 `application/problem+json` with `type,status,detail,code`. Missing objects use 404, input parsing/DTO validation 400, conflicting records or changed idempotency payload 409, business-rule rejection 422, peer rate limit 429 and unexpected failure 500. Spring Security/filter-generated errors may use their framework body instead of banking ProblemDetail. Financial failures never return a successful debit with an incomplete credit.

```json
{"type":"urn:nusantara:error:INSUFFICIENT_FUNDS","status":422,"detail":"There are insufficient available funds in this account.","code":"INSUFFICIENT_FUNDS"}
```

Statement generation accepts `from`/`to` past inclusive dates, no more than one year. PDFs derive opening balance from pre-period postings, totals from period postings and closing from opening+credits−debits. Owner check applies to both generation and download.

## Extended workflows

| Method / route | Validated request / response | Authorization |
|---|---|---|
| GET /auth/config | OIDC issuer, client, discovery and account-console links | Public |
| GET /auth/me | Validated subject, roles, expiry and provider metadata; no tokens | Bearer |
| POST /accounts | name (1–100), type SAVINGS/CURRENT → zero-balance account | Owner, VERIFIED KYC |
| POST /accounts/{id}/close | Empty request; requires ACTIVE, zero balance and no holds | Owner |
| GET /accounts/{id}/holds | Active/released holds (bounded) | Owner |
| POST /admin/holds | accountId, decimal amount, reason + Idempotency-Key | ADMIN |
| POST /admin/holds/{id}/release | Idempotent release; order holds require cancellation | ADMIN |
| GET /admin/accounts/{id}/holds | Account hold history | ADMIN |
| POST /payment-orders | OrderRequest + Idempotency-Key → Order view | Owner, VERIFIED KYC |
| GET /payment-orders | Page, size ≤100 | Owner |
| GET /payment-orders/{id} | Order details/reference/status | Owner |
| POST /payment-orders/{id}/cancel | Cancel PENDING only, release reservation | Owner |
| GET /analytics?months=6 | 1–12 monthly cashflow points from ledger | Owner |
| GET /admin/reconciliation | Balance, posting, hold and network differences | ADMIN or SUPPORT (read only) |
| GET /admin/reports?from=…&to=… | One-year bounded gross/compensation/channel aggregates | ADMIN |
| GET /admin/notification-dead-letters | Page of retained recovery metadata | ADMIN |
| POST /admin/notification-dead-letters/{id}/retry | customerId, eventType, corrected message | ADMIN |

Customer profile, beneficiary, account, transfer, history, statement and order APIs use explicit public response DTOs. Customer responses omit persistence request hashes, internal outcome decisions and token material. Date/time fields use ISO representation; all statement/history/report calendar boundaries are Malaysia time. Optional phone may be omitted or empty; supplied non-empty numbers are validated.

```json
{
  "sourceId":"10000000-0000-0000-0000-000000000001",
  "destinationId":null,
  "amount":"25.00",
  "description":"Fictional alias payment",
  "channel":"DUITNOW_MOCK",
  "alias":"daniel@example.test",
  "scheduledAt":"2026-11-11T08:00:00Z"
}
```

Use **the current instant** for an immediate mock payment, or an instant within 90 days for SCHEDULED (with destinationId and no alias). The timestamp above is illustrative; do not replay a stale timestamp. Order creation reserves funds, returns PENDING, and a worker settles or rejects. Same key/body returns the order; changed body returns 409. An original reservation key must be retained across uncertain retries. SETTLED/CANCELLED/REJECTED are terminal. An unexpected infrastructure failure rolls back and retains PENDING for retry.

For mock alias rejection use `reject@example.test`; no debit occurs. Both aliases resolve to Daniel and require his registered beneficiary. Reconciliation links accepted network references to actual simulation ledger postings. Reversals retain the original network acceptance record and add separate compensating ledger history; no real external-network refund is implied.

Statements cap at 5,000 entries per PDF and reject larger periods with STATEMENT_TOO_LARGE. Select a narrower period; totals are never calculated from a silently truncated list. Prometheus/readiness/liveness remain on service ports, outside gateway `/api/**` routing.
