# Password login, refresh and logout

IAM-02 and IAM-07 now reuse V2's users, roles, auth_sessions and refresh_tokens.
No migration or credential seed was added. The existing eight-character signup
minimum remains unchanged. [API contract](../../contracts/identity-sessions.md).

## Private configuration and exact startup

From the repository root in PowerShell, after the existing database/broker setup:

```powershell
# Adds only missing/blank/placeholder AUTH_* values to private backend/.env.
# Keeps existing real values and Neon credentials. Does not print the signing key.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1

docker compose -f compose.yaml -f compose.dev.yaml up -d rabbitmq
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

In a second terminal at the repository root:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run dev
```

Open `http://127.0.0.1:5173/login`. Register at `/register` first if needed. Login
opens `/account`, showing safe profile/role information and a logout button.
Reload renews the session through refresh, then loads `/me`. Registration still
does not automatically sign in. Google and password recovery are clearly marked
coming soon; the login wording applies to all roles.

Maven still anchors startup to backend/. Spring imports root `.env` then
`backend/.env`; OS variables override those files. Runtime and direct Flyway
connections still target the same database/Neon branch. The helper does not
change database settings or connect to Neon. Compose provides RabbitMQ only, and
the backend uses the selected Neon branch for PostgreSQL.

| Shared environment variable | Value / purpose |
| --- | --- |
| `AUTH_JWT_SECRET` | Private Base64 encoding of at least 32 cryptographically random bytes. Required; no built-in development key. Helper generates it once. |
| `AUTH_JWT_ISSUER` | Default `https://swiftdrop.local`; identifier, not a service to contact. |
| `AUTH_JWT_AUDIENCE` | Default `swiftdrop-api`. |
| `AUTH_ACCESS_TTL` | Default `10m`; positive duration up to one hour. |
| `AUTH_REFRESH_TTL` | Default `7d`; at least the access lifetime and at most 30 days. |
| `AUTH_LOCALHOST_DEVELOPMENT` | Defaults false. Helper explicitly enables it for local HTTP only. |
| `AUTH_ALLOWED_ORIGINS` | Comma-separated exact browser origins, no trailing slash, path or wildcard. Helper supplies localhost/127.0.0.1 ports 5173 and 4173. |

Production requires `AUTH_LOCALHOST_DEVELOPMENT=false`, HTTPS origins and a
private deployment signing key. Development mode rejects non-loopback origins.
Both application instances in a scaled deployment must use consistent keys and
settings. Keep the signing key out of frontend configuration and Git. Changing
it invalidates existing access JWTs; valid persisted refresh sessions can obtain
new JWTs. Never turn on credential/request-body/SQL-bind logging.

For full local Compose, run the helper with `-Compose` to initialize the root
private signing configuration, then `docker compose up --build --detach --wait`.
Compose explicitly selects loopback development origins using `FRONTEND_PORT`.
Its database stays local. Application image builds were not rerun in this slice.

## Actual request flow

1. `frontend/src/features/identity/LoginPage.jsx` renders the reusable auth layout
   and eye-icon fields; it passes the exact password to `apiClient.js`.
2. `apiClient.js` obtains CSRF, posts login, holds only the returned access JWT in
   memory, then calls `/me`. `AuthProvider.jsx` supplies shared state to `App.jsx`
   and `AccountPage.jsx`. No authentication token uses local/session storage.
3. `SecurityConfig.java` enforces the exact public methods, CSRF, origin checks,
   rate limits and bearer authentication. `JwtConfig.java` fixes HS256 and validates
   JWT claims. `IdentityJwtConverter.java` asks the service for current database
   state and constructs a trusted `IdentityContext` with current roles.
4. `SessionController.java` handles HTTP/cookies; `IdentitySessionService.java`
   verifies the adaptive password hash and account state, owns transactions and
   session rotation/revocation. Repositories perform all database access.
   Missing/Google-only accounts perform dummy password verification; login errors
   do not disclose which condition failed.
5. `AccessTokenService.java` issues signed JWTs. `TokenSecrets.java` creates random
   opaque refresh values and hashes them. Only controllers send raw refresh
   values, exclusively in the HttpOnly cookie. `/me` returns a safe DTO through
   `ProfileController.java` and the service.

Refresh uses one promise per client and a Web Lock shared across tabs. A failed
API request gets at most one refresh and one retry; a 403 never triggers renewal.
Refresh network failures are not retried because the old token might already
have been consumed. Browsers without Web Locks fail closed on automatic renewal;
manual login still works. BroadcastChannel carries only identity invalidations,
never tokens. Logout clears memory immediately, queues behind any rotation and
revokes the newest cookie's session. Generation checks prevent late requests from
restoring a logged-out identity. Failed server logout displays a retry option
instead of claiming revocation succeeded.

Current-role/state checks take effect for requests starting after the relevant
database transaction commits; they cannot cancel requests already executing.
Future account-management code must increment authorization_version and revoke
sessions transactionally for suspension/role/password changes, following the same
user-then-session lock order. No admin/profile editing was added here.

## Verification

```powershell
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress -Pintegration verify
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
git diff --check
```

Passed: 38 backend unit/security/configuration tests, 47 isolated PostgreSQL/
broker integration tests, 30 frontend tests and both builds. Tests cover valid/
invalid login, expired/tampered/wrong-algorithm/issuer/audience JWTs, rotation,
consumed-token reuse, concurrent refresh, absolute expiry, transaction rollback,
logout, revoked-session access, account/version changes, current roles, CSRF,
origin checks, secure cookies, rate limits and preserved framework session state.
Frontend tests cover bootstrap, cross-tab serialization, one retry, no 403 refresh,
network failure, memory clearing and logout during refresh.

Not run in this slice: live Neon, real Google, full Compose application image
deployment, browser visual/end-to-end checks, or a process-restart smoke check.
Logs are in ignored `tmp/sessions-final-verify.log`; test reports are in Maven's
target directories. Earlier failing runs exposed test-fixture issues (an expiry
before issue time and MockMvc servlet/cookie representation); final checks pass.
