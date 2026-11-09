# Influencer Core API (Spring Boot 3 + Java 21)

Production-ready backend for influencer campaign curation, creator discovery, and marketing automation.

## 🚀 Quick Run (Instant Local Dev - Zero Docker Build Wait)
With Java 21 installed on your Windows machine:
```powershell
# 1. Set your database password
$env:SUPABASE_DB_PASSWORD="your-db-password"

# 2. Run immediately with Spring Boot DevTools live reload
./mvnw spring-boot:run
```
That's it! Any changes you make to Java files or templates automatically hot-reload in under 1 second.

## 🐳 Docker Deployment
To run containerized:
```bash
docker compose up --build
```

## 🌐 Deploy to Render
1. Push this folder to a GitHub repository.
2. In Render Dashboard, click **New +** -> **Blueprint** or **Web Service**.
3. Select your repo. Render automatically detects `Dockerfile` and `render.yaml`.
4. Fill in the environment variables:
   - `DATABASE_URL`: `jdbc:postgresql://db.dhfstnonquoyfgnpswsw.supabase.co:5432/postgres?sslmode=require`
   - `DATABASE_USERNAME`: `postgres`
   - `DATABASE_PASSWORD`: `your-supabase-password`
   - `BACKEND_BASE_URL`: `https://your-service-name.onrender.com`


## Security integration and API access policy

This Spring Boot 3.3.5 / Java 21 backend verifies tokens from the separate deployed Adrovis security
service. Business and security databases remain separate. No private JWT key, security DB password,
Postmark token or master frontend API key is shared with this backend.

### Effective endpoint policy

| API | Guest | CREATOR / BRAND / AGENCY | ADMIN |
|---|---|---|---|
| GET /api/v1/campaigns and /{id}, with existing filters | Public | Allowed | Allowed |
| GET /api/v1/campaigns/{id}/apply | Configured guest budget | Valid account bypasses guest budget | Allowed |
| POST /api/v1/campaigns/{id}/apply-access | Same guest budget, JSON response | Valid account bypasses guest budget | Allowed |
| POST /api/v1/inquiries | Public | Allowed | Allowed |
| GET /api/v1/inquiries and /{id}; PATCH /{id}/status | 401 | 403 | Allowed |
| POST /api/v1/newsletter/subscribe | 401 | Own verified email only | Own verified email only |
| GET /api/v1/newsletter/subscribers | 401 | 403 | Allowed |
| GET /api/v1/agencies | 401 | 403 | Allowed |
| GET /api/v1/collector/creators, /export, /members; /collector/dashboard/overview | 401 | 403 | Allowed |
| GET /api/v1/analytics/overview, /sessions, /sessions/{id}, /leads | 401 | 403 | Allowed |
| POST /api/v1/analytics/track, /heartbeat, /conversion, /lead | Public visitor event ingestion | Allowed | Allowed |
| POST /api/v1/payments/create-order | 401 | Own account, possessed role | Own account, possessed role |
| POST /api/v1/payments/verify | 401 | Own account's order only | Own account's order only |
| GET /api/v1/admin/payments/stats and /transactions | 401 | 403 | Allowed |
| POST /api/v1/webhooks/razorpay | Mandatory provider HMAC, not a browser Bearer token | Same | Same |
| GET /api/v1/account/me | 401 | Own identity | Own identity |
| Health endpoints and Swagger documentation GETs | Public operational metadata | Public | Public |
| Unlisted routes/methods | Denied by default | Denied by default | Denied by default |

Roles are checked on the server, never accepted from frontend flags. Collector data has no brand
membership or creator-profile ownership model; future brand/agency access requires an explicit
resource-permission design, not simply adding those roles to the platform-wide read rule.

### Render / local configuration

Set on this business backend:

```dotenv
ADROVIS_AUTH_ISSUER=https://adrovis-influencer-security.onrender.com
ADROVIS_AUTH_AUDIENCE=adrovis-platform
ADROVIS_AUTH_PUBLIC_KEY_LOCATION=file:/etc/secrets/adrovis-public.pem
ADROVIS_AUTH_STATUS_URL=https://adrovis-influencer-security.onrender.com/api/v1/auth/me
ADROVIS_AUTH_STATUS_TIMEOUT=10s
ADROVIS_FRONTEND_ORIGIN=https://brandcollabz.in,https://www.brandcollabz.in
GUEST_APPLY_LIMIT=3
GUEST_APPLY_COUNTING_MODE=CLICKS
GUEST_APPLY_COOKIE_SECURE=true
GUEST_APPLY_COOKIE_SAME_SITE=None
PAYMENT_MAX_WEBHOOK_BYTES=262144
```

The issuer MUST equal JWT_ISSUER on the security service. Add Render Secret File adrovis-public.pem
with the matching production PUBLIC PEM. For native local runs use file:.secrets/adrovis-public.pem;
for Docker bind-mount it and configure its in-container path. The file is ignored by Git and does
not arrive through a clone. Keep the existing primary/collector DB and Razorpay credentials.
Spring does not automatically load .env; .env.example contains placeholders only.
Local development YAML now also reads SUPABASE_DB_PASSWORD and COLLECTOR_DB_PASSWORD instead of
literal passwords. Set these in the terminal, IDE or Docker environment before running. Existing
running containers are not modified by this configuration cleanup. Production credential fallbacks
were removed: explicitly set RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET rather
than relying on fallback credentials.

CORS accepts exact configured frontend origins without trailing paths/slashes. Payment-controller
wildcards were removed so they cannot broaden the central policy. Credentialed requests are enabled
for the core guest cookie; the security host still owns its separate refresh cookie. Native local
HTTP needs GUEST_APPLY_COOKIE_SECURE=false and SameSite=Lax. SameSite=None requires Secure/HTTPS.
Browser third-party-cookie blocking may still prevent quota persistence across unrelated domains;
same-site custom frontend/API domains or a same-origin proxy are more reliable.

### Authentication and current account validation

JWTs must pass RS256 signature, issuer, audience, expiry/issuance, access-token type, UUID identity,
account-version and known-role checks. Onboarding tokens are rejected. After local verification,
the backend forwards the token only to the configured HTTPS /api/v1/auth/me endpoint. Current UUID,
verified-email state and roles must match. The verified email is added only as transient server-side
principal metadata and overwrites any input claim; it is not put into a newly issued JWT here.

Logout, reset or suspension invalidates subsequent protected requests through this uncached check.
401 means invalid/revoked credentials; 403 means insufficient role/ownership; 503 means identity
validation is unavailable. No error falls back to anonymous access. There is one extra HTTPS call
per Bearer request. Render Free cold starts may exceed the configured timeout; clients must retry
503 rather than bypass security. Existing in-flight requests are not retroactively interrupted.

### Guest Apply allowance: configurable 3 or 5 requests

In application.yml, app.guest-apply.limit defaults to ${GUEST_APPLY_LIMIT:3}; set the environment
variable to 5 for five free requests. CLICKS is the default counting mode: every permitted guest
Apply request counts, INCLUDING reopening the same campaign. The fourth/sixth request returns
403 SIGNUP_REQUIRED. DISTINCT_CAMPAIGNS is an optional compatibility policy where repeats do not
consume a new slot. It is no longer the default.

The Secure HttpOnly host-only cookie adrovis_guest_apply carries a random 256-bit guest identity;
only its SHA-256 hash is stored. A per-guest row lock and database transaction prevent concurrent
requests from exceeding the selected policy. Quota and click tracking share the primary transaction.
Campaign lookup and URL validation happen before usage is consumed. Invalid Bearer tokens return
401, not a new guest allowance. Clearing cookies, changing browser or waiting for the 30-day cookie
to expire creates a new identity: this is a soft signup trial, not a per-person anti-fraud guarantee.
Do not issue parallel first-click requests before the initial guest cookie is stored.

Legacy GET /api/v1/campaigns/{id}/apply retains its 302 redirect only when allowed; both routes
return no-store responses and enforce the same gate. Use POST /{id}/apply-access for frontend buttons,
because GET prefetchers/crawlers can otherwise consume guest requests. POST success returns
{success:true,applicationUrl:...}; exhausted guests receive {success:false,errorCode:"SIGNUP_REQUIRED",message:...}.
Frontend code must use credentials:'include', show signup/login on this specific 403, preserve the
campaign ID, and retry with an access token after verification/onboarding. This task does not edit
the frontend repository or create the signup popup itself. External form URLs, once disclosed, can
be bookmarked/shared; this gate does not secure submissions at Google Forms.

### Payments: preserved flow with identity boundaries

All user-facing payment routes require a valid access token. Each new transaction stores account_id
from the trusted principal. The submitted email must match the verified account email, and the
submitted userRole must be one of the caller's actual roles. Admin status does not permit verifying
another person's order through the customer endpoint. Missing/unowned/unlinked orders all return
404 without revealing who owns them. Historical account_id=NULL rows remain admin-readable and
require reviewed linking; they are never silently linked by email.

The frontend-supplied plan and price contract is intentionally retained at the user's request.
Positive amounts and two-decimal numeric precision are enforced, and paise conversion is exact.
There is NO authoritative server-side plan-price catalogue in this scope. The code therefore does
not prevent a signed-in client from submitting a different positive price. Create-order retries
also do not have a complete durable idempotency contract; any such product changes need separate
agreement. Preserve that distinction during production sign-off.

Checkout signature verification uses the stored order ID and gateway secret. Invalid signatures
never mutate status; repeated identical verified callbacks are idempotent, and successful/refunded
transactions cannot be overwritten by a different payment ID or a late success notification.
The existing checkout SUCCESS semantics are preserved; gateway capture/settlement/reconciliation
must still be validated in the real Razorpay test environment before billing production sign-off.

Razorpay callbacks cannot carry a customer's JWT. The only payment authentication exception is
POST /api/v1/webhooks/razorpay, where the exact raw UTF-8 body MUST pass constant-time HMAC-SHA256
verification. Blank secrets, missing/malformed/wrong signatures fail closed. Signed-body hashes
and a database unique claim deduplicate concurrent retries. Captures lock the order and match its
stored amount/currency before updating it. Unknown orders return a retryable error rather than
acknowledging and losing an event. Claims roll back if processing fails. Other signed event types
retain the existing audit-only scope; this does not add refund/subscription business workflows.

Admin payment lists use a DTO instead of returning the gateway signature in an entity. Existing
admin table fields are preserved and accountId is additive. No provider response body, secret,
request token, raw signature or payment email is written to application logs by the new paths.
Webhook payload storage is retained from the original payment audit design and needs an operator
retention/access policy. The configured webhook size limit is an application parsing limit, not
a replacement for upstream request-size or abuse controls.

### Database rollout: manual, additive and reviewed

This backend has no migration runner. Apply these scripts to the CORE primary database before
deployment; none were applied to Supabase by this task:

1. db/manual/001_guest_campaign_allowance.sql: original guest tables, only if not already applied.
2. db/manual/002_payment_account_ownership.sql: adds nullable account_id and indexes to EXISTING
   payment tables. A unique event_id index supports atomic webhook retries. Review any historical
   duplicate event IDs before applying; do not delete payment history as a shortcut. This script
   also enables RLS and revokes PUBLIC/anon/authenticated payment-table access. A separate backend
   runtime role needs explicit grants and a backend-only RLS policy; owner/service credentials must
   be reviewed before applying.
3. db/manual/003_guest_apply_click_counter.sql: adds apply_count and seeds at least the previous
   distinct-grant count without reducing existing counters. Earlier history cannot reconstruct
   clicks that were never recorded. The old script was not edited to change applied schemas.

There is no cross-database foreign key to security accounts. Existing payment tables must already
exist from the payment module's deployment. No campaign, collector, inquiry or newsletter table
was rebuilt. Runtime role privileges/RLS must be reviewed for the actual database role before
production; API route protection does not replace database access policies.

### Review notes: what was removed or changed, and why

| Previous behavior | Replacement and reason |
|---|---|
| Frontend-only role decisions | Backend filters plus method guards; clients can bypass frontend UI |
| Public newsletter subscription | Authenticated own verified email; requires signup and prevents subscribing another address |
| Public agency listing | Admin-only per current business scope |
| Wildcard CORS on payment controllers | One exact origin allowlist; wildcard annotations could broaden intended browser access |
| Blank webhook secret accepted any signature | Fail-closed raw-body HMAC; missing configuration must not authorize money updates |
| Read-then-save webhook dedupe | Unique atomic database claim; concurrent deliveries must not process twice |
| Payment ownership inferred from request email | Stored account UUID; user-controlled contact fields are not authorization |
| Invalid checkout signature marked payment failed | No state mutation; forged input must not change financial state |
| Raw payment entity returned to Admin | DTO preserving table fields while excluding the gateway signature |
| Provider/SQL exception messages echoed in 500s | Stable sanitized HTTP errors, including method-authorization 403 and input-validation 400 |
| Distinct-campaign default guest policy | Configurable click counter matching the latest 3/5-click requirement |
| Legacy X-Admin-Key analytics access | Account ADMIN authority (removed in the earlier integration commit) |

Controller/business filters, inquiry creation, existing campaign filters, Razorpay order creation,
and supported capture processing remain in place. Existing staged changes were not reset or committed.
Method comments explain security decisions and exceptions instead of claiming blanket readiness.

### Verification and release checks

The suite exercises the actual controller authorization matrix for guests and all four roles,
verified newsletter consent, payment owner/role checks, real signed token claims and webhook HMAC,
callback state guards, PostgreSQL/JPA ownership round-trips, atomic webhook claims/rollbacks, and
concurrent click/distinct guest budgets. No test charges money, sends email, or connects to Supabase.
Java 21 CI uses a disposable PostgreSQL 16 database. Set GUEST_APPLY_TEST_DATABASE_URL only to a
disposable localhost guest_apply_test database; DB tests reject other URLs and truncate fixture tables.
Without that test URL the database tests skip; production/local application settings are not test credentials.

Before release: review the role table with the frontend owner; apply reviewed SQL; configure actual
trust/key/origin/provider secrets; test real verified-account login/logout; test the newsletter and
signup popup in a browser; run Razorpay TEST-mode checkout and signed callback/retry tests; and review
public inquiry/telemetry abuse limits and DB grants. Public telemetry remains intentionally anonymous
for guest measurement and is not proof of a person's identity. This work does not assert that external
email, Google login, real payment settlement or the deployed frontend were verified.

No push or deployment is performed by this task. Only backend code, tests, docs and additive SQL are
updated on the current feature branch. Deploy changes only after the required configuration/schema
is ready; simply merging code into an auto-deployed main branch can otherwise fail startup.


### Executed verification (8 November 2026)

A clean build and subsequent full verify passed on Java 21.0.7 with disposable PostgreSQL 16:
93 tests, 0 failures, 0 errors, 0 skipped. This includes all four account roles, 14 management GET
routes, real webhook HMAC, payment service guards, actual JPA locking/ownership/transaction rollback,
payment-table RLS for a non-owner browser role, and configurable 3/5-click guest concurrency.
Gateway SDK calls were mocked; no real payments, emails, Supabase changes or deployed/frontend
requests were made. The original primary/collector business datasets were not used by these tests.
The disposable review containers are task-owned and removed after verification. Reports are in
target/surefire-reports and target/security-review-verification.txt (local build outputs, not Git files).
The latest environment-only credential cleanup is repackaged separately without repeating unchanged
domain tests. Run-time provider secrets must be supplied explicitly before deployment.


## Adversarial regression review (8 November 2026)

Added eight negative tests and reproduced three validation gaps before fixing them:

- JWT versions were truncated or overflowed through `Number.longValue()`; only non-negative JSON integers in the signed-long range are now accepted.
- Java's UUID parser accepted abbreviated identifiers; subject and token ID now require the full UUID representation.
- Signed webhook IDs were coerced from non-string JSON and lacked a length bound; malformed fields now return 400 before order lookup rather than a misleading retryable 503.

These were input-contract gaps, not demonstrated signature or unauthenticated access bypasses.
Additional regression checks cover role/header spoofing, query-string tokens, method overrides,
oversized webhook payloads and malformed signed JSON. Full Maven verify passed on Java 21.0.7
with disposable PostgreSQL 16: 101 tests, zero failures, errors or skips. Existing payment
ownership, callback replay/rollback and concurrent 3/5 guest click checks remain included.

No shared database, running application container, frontend or deployed service was changed.
Apply pending manual SQL and rebuild the application image before local/live acceptance.
This pass does not establish live gateway settlement or remove the existing frontend-price,
create-order idempotency, public ingestion abuse-control and cookie-reset trial limitations.
Local reproduction logs: `.test-runtime/adversarial-before.log`,
`.test-runtime/adversarial-webhook-repro.log` and `.test-runtime/adversarial-final.log`.


### Local frontend access to a deployed core backend

Keep `SPRING_PROFILES_ACTIVE=prod` and configure:

```env
ADROVIS_FRONTEND_ORIGIN=https://brandcollabz.in,https://www.brandcollabz.in
ADROVIS_LOCAL_DEVELOPMENT_ORIGINS=http://localhost:5173
CORS_ALLOWED_ORIGINS=https://brandcollabz.in,https://www.brandcollabz.in,http://localhost:5173
```

The security filter uses the first two settings as an exact credentialed allowlist.
The optional development list accepts only HTTP localhost/127.0.0.1 origins, never wildcards
or public HTTP domains. Leave it empty to disable local frontend access. Deploy this code
before enabling the setting. CORS controls browser access; existing JWT/role checks remain.
All frontend core requests, including analytics, must use the intended deployed API URL.
Use POST `/api/v1/campaigns/{id}/apply-access` to get JSON, then navigate to applicationUrl;
fetching the redirecting GET Apply endpoint cannot fix Google Forms' CORS policy.
Cross-site guest cookies can still be blocked by browsers when using localhost with Render.

Validation on 9 November 2026: 52 focused tests passed, with zero failures/errors/skips,
including production-profile CORS preflights, route authorization and PostgreSQL concurrency checks.
Compiled with the configured Java 17 release target on a Java 21 runtime. Existing executor tests
were changed to finally/shutdownNow cleanup so they compile against Java 17. No shared database
or deployed configuration was changed by this verification.
