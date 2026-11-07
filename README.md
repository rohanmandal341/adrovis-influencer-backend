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


## Security-service integration

This backend now verifies access tokens from the separate Adrovis security service. The services
retain their own databases. No identity table, duplicate login endpoint or migration was added.
Existing collector records represent collected data, not automatically linked account profiles.

Set these variables on THIS backend's Render service:

```dotenv
ADROVIS_AUTH_ISSUER=https://adrovis-influencer-security.onrender.com
ADROVIS_AUTH_AUDIENCE=adrovis-platform
ADROVIS_AUTH_PUBLIC_KEY_LOCATION=file:/etc/secrets/adrovis-public.pem
ADROVIS_AUTH_STATUS_URL=https://adrovis-influencer-security.onrender.com/api/v1/auth/me
ADROVIS_AUTH_STATUS_TIMEOUT=10s
ADROVIS_FRONTEND_ORIGIN=https://YOUR_ACTUAL_FRONTEND_DOMAIN
```

Copy the exact JWT_ISSUER from the security deployment instead of assuming it equals the host URL.
Use the real frontend origin without trailing slash/path. Comma-separated exact origins are allowed;
prod requires HTTPS. Local development permits http://localhost:3000. Wildcard controller CORS was
removed. The old CORS_ALLOWED_ORIGINS setting is superseded by ADROVIS_FRONTEND_ORIGIN.

Add Render Secret File adrovis-public.pem containing ONLY the production public PEM from the
security project. Never share its private key, mail-provider token or security database password.
The Git-ignored local public key is .secrets/adrovis-public.pem; for local runs set
ADROVIS_AUTH_PUBLIC_KEY_LOCATION=file:.secrets/adrovis-public.pem. Keep the existing primary and
collector database variables. Spring does not automatically load .env files.

Missing trust settings fail startup; configure these before deploying. No OIDC discovery/JWKS
endpoint is assumed. Key rotation currently needs coordinated public-key distribution.

### Validation and revocation

Every Bearer-authenticated request checks RS256 signature, issuer, audience, expiration, issuance,
maximum 30-minute lifetime, token_use=access, UUID sub/jti, nonnegative account version and known
CREATOR/BRAND/AGENCY/ADMIN roles. Onboarding tokens are rejected.

After local validation, the user's token is forwarded only to the fixed configured HTTPS
/api/v1/auth/me endpoint, which checks current account status/token version in the security DB.
UUID, verified-email state and roles must match. This rejects subsequent requests after logout,
password reset or suspension; role changes require fresh credentials. There is no cached result
or master service password. Redirects are not followed and tokens are not logged.

An unavailable account service returns 503 AUTHENTICATION_UNAVAILABLE and never bypasses validation.
Invalid/revoked credentials return 401 UNAUTHORIZED; insufficient roles return 403 FORBIDDEN.
There is one extra HTTPS request per authenticated API request. Render Free cold starts may exceed
the default 10-second timeout; frontend clients should handle 503 with a retryable message.
Already-running authorized requests are not retroactively interrupted.

### Route permissions

| Routes | Access |
|---|---|
| GET /api/v1/health, /actuator/health and health subpaths | Public |
| GET /api/v1/agencies, /api/v1/campaigns, /api/v1/campaigns/{id}, /api/v1/campaigns/{id}/apply | Public |
| POST /api/v1/inquiries, /api/v1/newsletter/subscribe | Public |
| POST /api/v1/analytics/track, /heartbeat, /conversion, /lead | Public telemetry |
| GET /api/v1/account/me | Valid account access token |
| Collector routes, inquiry reads/status changes, subscriber lists, analytics reads, admin routes | ADMIN |
| GET /swagger-ui.html, /swagger-ui/**, /v3/api-docs/** | Public documentation |
| Other routes | Denied by default |

Collector endpoints expose platform-wide records and have no ownership/membership model; they are
ADMIN-only initially. Never grant normal creator/brand accounts unrestricted record access as an
integration shortcut. Internal collector services and management methods also enforce ADMIN.
Analytics uses ADMIN Bearer tokens instead of X-Admin-Key. Admin provisioning must remain trusted
and separate from public registration. Public submission rate limiting remains separate work.
Actuator exposes only health and never returns detailed infrastructure information.

### Frontend connection and live checks

1. Configure two frontend base URLs: the deployed security API and this business API.
2. Signup/login/refresh/logout use the security service's existing CSRF and cookie flow. Keep its
   access token in memory; the security host owns the refresh cookie.
3. Business requests send Authorization: Bearer <access-token>, without refresh cookies or master keys.
4. First test GET /api/v1/account/me on THIS backend: expect success, accountId (JWT sub) and roles.
5. Anonymous requests return 401. A CREATOR requesting collector exports must receive 403.
6. Logout via the security service, then retry account/me with the old access token: expect 401.
7. Use a legitimately provisioned ADMIN for internal operations. Swagger's Authorize button accepts
   the access token for protected operations. An X-Admin-Key alone no longer grants access.

account/me is an identity connection check, not a creator business profile. Future user-owned
operations require an explicit account_id relationship and resource authorization; never silently
link collected creator records solely by email. Frontend code has not been edited because its
repository was not supplied. No push or deployment of this backend was performed by this task.
End-to-end deployed testing and browser cookie behavior still need verification.

### Verification

Run mvn verify with Java 21. Tests use disposable generated RSA keys, mocked account HTTP responses,
and mocked repositories. They never connect to Supabase or send email. Local verification used
Java 24 targeting 21 with command-only -Dmaven.compiler.proc=full and -Dlombok.version=1.18.48
for the installed compiler. Production POM dependency versions and the Java 21 Docker build remain
unchanged. The CI workflow verifies Java 21 independently when pushed; it has not run remotely yet.
Tests cover signatures/claims, stale roles/revocation, provider failures, protected routes,
actual public inquiry/newsletter contracts, approved/denied CORS and the identity API.


## Guest campaign Apply allowance

GET /api/v1/campaigns/{id}/apply still redirects (302) to the external form for permitted requests.
Guests get access to three DISTINCT campaigns per browser identity. Reopening a previously permitted
campaign does not consume another slot and still works after the limit. The fourth distinct campaign
returns 403 with success=false and errorCode=SIGNUP_REQUIRED, without revealing the destination.
Valid completed-account Bearer tokens bypass the guest quota; invalid tokens return 401 rather than
silently falling back to a guest. Other JWT/permission protections remain unchanged.

A cryptographically random 256-bit cookie (adrovis_guest_apply) identifies the guest. Only its SHA-256
hash is stored in the primary database. A per-guest row lock and unique guest/campaign key prevent
simultaneous requests from exceeding the limit. Quota writes and click tracking share the primary
transaction. Campaign lookup/destination validation happens before the allowance is consumed.
No raw cookies, IP addresses or personal data are stored in these quota tables.

### Required database setup BEFORE deployment

Run src/main/resources/db/manual/001_guest_campaign_allowance.sql once in the CORE backend's PRIMARY
PostgreSQL database (not the separate security Supabase DB or collector DB). It adds public.guest_apply_sessions
and public.guest_campaign_access, foreign keys, indexes and RLS/browser-role restrictions. Run as the
backend table owner; a separate runtime role needs explicit backend-only grants and an RLS policy.
This backend has no migration framework, so the SQL file is NOT run automatically at startup.
The script has only been executed on a disposable local database during this task.
No production database was modified. Apply these tables before deploying this feature.

Configuration:

```dotenv
GUEST_APPLY_LIMIT=3
GUEST_APPLY_COOKIE_SECURE=true
GUEST_APPLY_COOKIE_SAME_SITE=Lax
```

Local HTTP tests need GUEST_APPLY_COOKIE_SECURE=false. Production requires true. If frontend/API
are on different sites, None is needed with Secure; browser third-party-cookie restrictions can
still block guest cookies. Same-site custom frontend/API domains or a same-origin proxy are more
reliable. CORS now permits credentials only for exact approved frontend origins. The cookie is
host-only, HttpOnly, scoped to /api/v1/campaigns and expires after 30 days without extending on each
click. Clearing cookies or switching browsers resets the guest identity. This is a soft signup
allowance, not a guaranteed per-person limit or protection of an externally shared Google Form URL.
Campaign deletion cascades its usage rows. Database history is retained until explicitly cleaned up;
operators can remove sessions older than 30 days when defining their retention policy.

### Frontend Apply button

POST /api/v1/campaigns/{id}/apply-access provides the same gate as JSON, avoiding automatic redirects
while the frontend decides whether to show signup. On success it returns 200 with success=true and
applicationUrl. On exhaustion it returns 403 SIGNUP_REQUIRED. Database failures return a sanitized
503 APPLICATION_ACCESS_UNAVAILABLE. Neither denied response includes the external destination.
Both paths share the same quota, so calling the legacy GET does not bypass the gate. Because legacy
GET consumes a distinct grant, crawlers/prefetching can count; use POST for the actual button.

```javascript
async function handleApply(campaignId, accessToken) {
  const response = await fetch(
    `${BUSINESS_API_URL}/api/v1/campaigns/${encodeURIComponent(campaignId)}/apply-access`,
    {
      method: 'POST',
      credentials: 'include',
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
    }
  );
  const body = await response.json();
  if (response.status === 403 && body.errorCode === 'SIGNUP_REQUIRED') {
    // Use your frontend router; preserve the campaign for after verification/onboarding.
    window.location.assign(`/signup?campaignId=${encodeURIComponent(campaignId)}`);
    return;
  }
  if (!response.ok) {
    throw new Error(body.message || 'Application access failed. Please try again.');
  }
  window.location.assign(body.applicationUrl);
}
```

The frontend repository has not been provided, so this button handler/signup navigation has NOT been
installed there. After successful signup/verification/onboarding, retry with the access token; normal
anchor navigation does not attach a Bearer header. Never trust an isLoggedIn flag in a request body.
The core guest cookie is separate from the security service's HttpOnly refresh cookie.

### Guest allowance tests

Unit/HTTP tests cover signup-required responses on both paths, hashed/scoped cookies, authenticated
bypass, invalid token rejection and missing-campaign behavior. Real local PostgreSQL tests exercise
three allowed/fourth denied, repeated campaigns, separate guests, 12 simultaneous different/same
campaign requests and transaction rollback. Set GUEST_APPLY_TEST_DATABASE_URL to a disposable local
guest_apply_test DB only; the tests intentionally reject other databases and truncate their fixture
tables. Optional GUEST_APPLY_TEST_DATABASE_PASSWORD is for that disposable DB. Without the URL,
five database tests skip; the Java 21 CI supplies a disposable PostgreSQL 16 service to run them.
Local Java 24 testing additionally used -Dnet.bytebuddy.experimental=true for the old test mock agent.
