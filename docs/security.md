# Security design and boundaries

## Identity and browser

Keycloak owns credentials, registration, brute-force lockout, session events, optional TOTP enrollment and account console. The development realm enforces S256 PKCE, disables the password grant, restricts redirect URIs and web origins to localhost:5173, uses 5-minute access tokens and rotating refresh tokens. Set up an authenticator in the account console to demonstrate MFA. Required MFA for all users, production SMTP/password-recovery, compromised-password checks and identity audit retention need deployment configuration and verification.

keycloak-js holds tokens in runtime memory; no tokens or passwords are persisted in application localStorage, sessionStorage, database or logs. SessionStorage holds only a non-secret login-attempt marker; the provider SDK may persist short-lived PKCE callback state. The access token refreshes before API calls and expiry; sign-out uses the provider's end-session flow. A production BFF with HttpOnly/SameSite cookies offers a stronger token-isolation option and would require CSRF protection.

The API uses stateless bearer authentication and no ambient session cookies. CSRF is disabled for this specific architecture; an attacker's cross-site form cannot attach the Authorization header. CORS allows one configured SPA origin and only required methods/headers. Identity redirect state and PKCE are managed by Keycloak's maintained SDK. JWT checks include signature, expiry/not-before, exact issuer and audience. TLS is required outside loopback development.

## API authorization

- Accounts, transfers, beneficiaries, statements, history, profile and notifications enforce ownership server-side.
- Account/statement probes return 404 to conceal other customers' objects.
- Administrative routes require ADMIN at both filter and method level; self-registration never grants ADMIN.
- CUSTOMER is the customer realm role; SUPPORT receives read-only reconciliation access and cannot mutate administrative resources.
- Simulated KYC must be VERIFIED to send funds. External customer destinations must be registered beneficiaries.
- Payment descriptions are length constrained; DTOs validate amounts and dates; SQL values are parameterized. Table/column names in the admin pagination helper are fixed server constants, never request values.
- Gateway rate limit is 20 requests/second with burst 40 per transport peer IP. Redis failure fails closed. A production reverse proxy must establish trusted client-address handling; never trust arbitrary X-Forwarded-For.

## Storage and observability

SQL transactions protect balances, ledger entries, audit records and outbox. Append-only triggers protect history from ordinary API mutations. Operational/admin database users can still alter data: separate migration credentials, least-privilege application roles, backups and external audit sinks are required for production. Docker Compose deliberately uses a developer DB owner and shares its database with notifications.

Structured ECS application logs exclude passwords/tokens by design; Hibernate SQL parameter logging is off. API unexpected errors produce generic RFC 9457 ProblemDetail responses; server logs retain exception context. Keycloak stores auth/admin events with one-day demo retention; a real OIDC login was verified in the provider event store. Business audit entries capture actor/action/resource, not profile contents. Account lists and details show full synthetic account numbers to their owner; summary cards mask to the final four digits. No real personal data belongs in this system.

Nginx adds CSP, frame denial, nosniff and referrer policy. The dev Vite server is not a hardened production host. Configure TLS, HSTS at ingress, production CSP connect/frame origins, secure Keycloak hostname/proxy configuration, confidential operational credentials and durable identity storage before external use.

## Controls that are deliberately outside this simulation

Regulatory compliance, real KYC/AML screening, sanction checks, fraud detection, customer consent, PayNet certification, HSM-backed bank signing, dual control on reversals, production DR/RPO/RTO, privileged-operator isolation and continuous monitoring are not supplied. The RM 5,000 UI review flag is educational and does not reject or screen a transfer.

CI performs npm audit and Trivy vulnerability/secret scans. Scanner results can change after a successful build; maintain a patch process and review false positives explicitly. A successful scan is not a security certification.

## Scheduled payments and operational recovery

Reservations and manual holds change available funds, never book balances. Order cancellation/settlement serialize on the customer/order/account locks. Unsupported aliases, missing beneficiaries, changed KYC/status, late-day limits and mock rejection cannot partially settle. Beneficiary removal also takes the customer lock so validation and removal have a defined ordering. Deferred hold guards prevent unpaired reservation changes.

Public typed response DTOs omit request fingerprints and expected mock-network outcomes. PDF generation bounds period and row count and never silently truncates financial totals. Direct service API routes remain protected even when bypassing the gateway; core ports are loopback/internal in deployment.

The notification recovery API requires ADMIN. Validated corrected payloads, deterministic repair IDs and retained original/repaired records make repeated repair safe. If quarantine storage is unavailable, Kafka offsets are not advanced. Metrics expose aggregate counts on internal service ports only; gateway does not publish actuator routes.

The browser MFA test creates a synthetic user, enrolls a supported provider authenticator and waits for a new TOTP window before logging in again. OTPAuth generates test codes from a provider-generated secret held only in test memory. No custom login/OTP API, persisted tokens or custom cryptography is implemented.
