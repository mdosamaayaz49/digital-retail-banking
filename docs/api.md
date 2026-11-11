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

Transaction queries accept `search`, `from` and `to` (ISO dates inclusive, Asia/Kuala_Lumpur). Own-account transfers appear as two account postings in combined history; they must not be interpreted as external cashflow. Dashboard live analytics currently summarizes latest five entries and labels this limitation.

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
