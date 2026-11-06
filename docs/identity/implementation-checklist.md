# Member 1: Identity and Access implementation checklist

Reviewed: 1 November 2026; updated after IAM-01. **Database foundation, IAM-01, IAM-02 and IAM-07 are implemented. IAM-03–IAM-06 and IAM-08 remain incomplete (GET /me and a trusted IdentityContext now provide part of their foundation).** See [current session implementation and verification limits](sessions.md). See [the registration guide](registration.md), [API contract](../../contracts/identity-registration.md) and [ER/table guide](schema.md). Unchecked boxes remain future acceptance criteria; later API/policy proposals still need implementation review.

## 1. What we have and what Member 1 owns

The foundation already connects React to Spring Boot and configures PostgreSQL, Flyway and RabbitMQ. Member 1 will add the account system: who a person is, whether their account/session is usable, and which roles they hold. Each business module must still decide whether that person owns or may change a particular business record.

Keep one backend and one shared PostgreSQL application schema, `swiftdrop`. A developer's Neon branch is an isolated development environment containing the same schema and migration history; it is not a separate database for their feature. Keep the local PostgreSQL Compose option.

| Existing implementation | Evidence in this repository | Missing work / reuse decision |
| --- | --- | --- |
| Layered health endpoint | `backend/src/main/java/com/swiftdrop/controller/system/HealthController.java`, `service/system/HealthService.java`, `dto/system/HealthResponse.java` | Reuse the layering. GET `/api/v1/health` stays public and returns only status/application. It is liveness, not a full database/broker probe. |
| Security foundation | `backend/src/main/java/com/swiftdrop/security/SecurityConfig.java`, `SwiftDropApplication.java` | GET health/CSRF and POST registration are permitted; everything else is denied, including requests with a mock authenticated principal. CSRF remains enabled. Form login, HTTP Basic, default logout and generated user remain disabled. No password login or JWT authentication works yet. |
| PostgreSQL and Flyway | `backend/src/main/resources/application.yaml`, `db/migration/V1__initialize_shared_schema.sql`, `V2__create_identity_tables.sql`, `entity/identity/` | V1 is unchanged; V2 adds eight identity tables and the four roles. No accounts/passwords are seeded. Hibernate validates all eight mappings. Constraints are tested on PostgreSQL. |
| Private Neon configuration | `backend/.env.example`, `backend/pom.xml`, `application.yaml`, `config/DatabaseMigrationConfig.java`, `.gitignore` | Spring imports root `.env`, then `backend/.env`; Maven anchors startup to `backend/`. Runtime uses the application connection and Flyway a separate direct connection to the same endpoint/database. A guard rejects mismatches. Private-file binding was verified without displaying values or connecting to Neon; live Neon reachability remains unverified. |
| Dependencies and packaging | `backend/pom.xml`, Maven wrapper, `frontend/package.json`, `package-lock.json`, Dockerfiles, Compose files | Reuse pinned toolchains. Security/JPA/validation/testing are present. OAuth client, JWT verification/issuance and email delivery still need implementation and suitable dependencies managed by the Boot parent where available. |
| React shell and proxy | `frontend/src/app/App.jsx`, `frontend/vite.config.js`, `frontend/nginx.conf` | Registration is at `/` and `/register`; the original health/retry page is at `/foundation`. Reusable form/layout components, validation, confirmation, pending/errors/success and real API wiring exist. No signed-in state or login screen. Proxies forward `/api`; Google routes still need coverage. |
| Tests | `backend/src/test/java/com/swiftdrop/`, `frontend/src/app/App.test.jsx` | 31 backend unit/configuration/security cases and 29 integration cases cover foundation/schema/registration. Sixteen React tests cover health and registration. These do not establish working login, role enforcement, reset delivery or Google. |
| API contracts | `contracts/README.md` | Health and `contracts/identity-registration.md` are implemented contracts. No OpenAPI generator or browser end-to-end automation command exists; an actual browser signup was checked manually through the development proxy. |

Ownership boundaries:

- **M1:** user accounts, password hashes, provider identities, roles, identity sessions, recovery and account-level audit records; shared `IdentityContext` and account lookup/service contracts.
- **M2:** merchant application/approval, merchant profile, API credentials and merchant access policy. A `MERCHANT` role alone must not bypass approval. Coordinate a trusted service call for role changes; do not implement approval in M1.
- **M5:** courier operational profile, availability, capacity and assignment. M1 can supply an existing active user with `COURIER`; granting that role does not create an operational profile.
- **M3/M4/M6 and other owners:** quote, booking, assignment, lifecycle and other record ownership checks. Authentication is not proof that a record belongs to the caller.
- **M8:** parcel notifications. Password-reset delivery needs its own agreed mail adapter/transport contract; the existing broker and planned mock SMS flow do not provide reset email.

## 2. Requirements and acceptance criteria

### IAM-01 — Customer registration

**Implemented:** strict registration DTO/controller, transactional service, existing user/role repositories, adaptive encoder, safe errors, CSRF, bounded signup throttle, responsive React form and feature tests. No schema changes. See [the actual request flow and password policy](registration.md).

- [x] Form follows `Figma design/register.png`: four fields, show/hide, pending, field errors and success. No role picker. Sign-in is explicitly marked coming soon until IAM-02; no broken login link.
- [x] Server validates full name/email/password with Bean Validation. **Confirmation is frontend-only**, as the user's API instruction requires. Store/return canonical email from PostgreSQL `lower(btrim(...))`, with database uniqueness under concurrency. Future login/recovery must reuse this rule.
- [x] One transaction stores the user and **CUSTOMER only**. Reject all undeclared fields, including roles/status/IDs/confirmation. Membership failure rolls back account creation.
- [x] Spring `DelegatingPasswordEncoder` uses PBKDF2-HMAC-SHA256, 600,000 iterations, random 16-byte salt and 256-bit output. Accept 8–128 Unicode code points with no password alteration/truncation. Unit/integration tests verify exact matching and randomized hashes; deployment work-factor/load tuning remains future validation.
- [x] Success is `201 {id,email,fullName,roles:["CUSTOMER"]}`. Clear the form and show success without login, auth cookies or tokens (only the separate CSRF cookie exists).
- [x] Invalid input returns safe `400`; normalized-email races return generic `409 REGISTRATION_UNAVAILABLE`. A distinct 409 still permits account inference. The 20-attempt/ten-minute remote-address throttle is process-local; proxy/distributed deployment needs gateway integration.
- [x] Service/PostgreSQL/frontend tests cover success, concurrent duplicates, invalid input, privileged fields, exact-password hashing, atomic rollback, CSRF/protected routes, pending/error/confirmation/success behavior and no credential leakage in responses/logs.

**Deferred outside this slice:** login navigation/flow, approved Terms/Privacy pages, compromised-password blocklist, production load/TLS/proxy validation and live Neon connectivity. These are not claimed as implemented. Registration does not verify ownership of the supplied email.

### IAM-02 — Password login and logout

**Implemented:** password login/logout, safe profile read, JWT/session enforcement and shared React authentication. Current contracts and configuration are in [sessions.md](sessions.md).

- [x] Email/password login accepts only active accounts with a valid password hash; unknown email, wrong password, suspended user and Google-only account get the same safe `401` response. Bound attempts by account and source; test throttling without permanently locking out victims.
- [x] Successful login creates a persisted session, returns a short-lived access JWT and sets the refresh cookie described in section 3. Passwords and tokens never appear in logs or URLs.
- [x] UI implements password visibility, field labels, pending state, generic failure, registration navigation; Google sign-in and recovery remain visibly unavailable until IAM-03/IAM-08. Use account-neutral wording instead of promising every user a courier dashboard.
- [x] Logout revokes the current session/family server-side, clears the refresh cookie using the same cookie attributes, and removes the access token/user state from React. Repeated logout is safe; logout does not require an unexpired access JWT when a refresh cookie can identify the session.
- [x] Test correct/incorrect credentials, suspended/Google-only accounts, CSRF rejection and reuse of both access and refresh credentials after logout. Unknown routes remain denied.

### IAM-03 — Google OAuth/OIDC login

**Existing:** OAuth account table/mapping and Google sequence/UI references; no OAuth dependency or registration configuration. **Missing:** the provider flow and account-linking policy implementation.

- [ ] Use Spring Security's authorization-code OIDC flow with PKCE, state and nonce validation. Validate ID-token signature, issuer, audience and expiry, and require verified email. Provider cancellation, invalid state/nonce, unverified email and token exchange failure produce a safe UI outcome.
- [ ] Identify a returning Google account by unique `(provider, provider_subject)`, not email. A genuinely new provider account creates a CUSTOMER with nullable password hash and no staff privileges.
- [ ] Matching an existing email must **not** silently link accounts. Require explicit linking from a freshly authenticated existing account, with state bound to that user and the linking action; reject a Google identity already linked elsewhere. Never accept a user ID from the callback as proof of ownership.
- [ ] Callback creates the SwiftDrop refresh session/cookie and redirects to a fixed frontend completion route without tokens in the URL. React obtains the application access token through refresh, then `/me`. Do not use Google's ID/access token as the SwiftDrop API bearer token.
- [ ] Keep Google client secret and token exchange on the backend. Register exact callback URLs for each intended origin and forward both Google endpoints through Vite and Nginx. Do not use a user-controlled redirect URL.
- [ ] Test with a controlled OIDC provider/fixtures, including repeat login, collision/linking, concurrent creation and suspended accounts. Separately demonstrate a real Google login when private credentials and consent-screen access are available; a mocked test is not that demonstration.

### IAM-04 — View/update permitted profile details

**Existing:** user/profile field mappings and security/validation foundation. **Missing:** profile DTO, repository operations, service, endpoint and screen.

- [ ] `/me` resolves the user from the authenticated context. Return `id`, display email, `fullName`, optional `phone`, `roles`, `active`, timestamps and an update `version`; omit credential/session secrets.
- [ ] Proposed editable fields are `fullName` and optional `phone` only. Email, ID, roles, active status, provider identity and authorization version are read-only. Email change/password change require separate verified flows and are not hidden in generic profile patching.
- [ ] PATCH uses an allowlist and optimistic version check. Reject unsupported fields; stale updates return `409`. Validate a supplied phone consistently; an explicit null may clear it, while an omitted field stays unchanged.
- [ ] UI provides load, edit, validation, save, success and expired-session states with accessible labels/focus. Saved addresses, merchant details and courier operating details are excluded.
- [ ] Test anonymous access, trying another user's ID, mass assignment, stale writes and safe serialization. The caller can only edit their own account through this route.

### IAM-05 — Admin user management and suspension

**Existing:** role/state/version columns and append-only administrative audit persistence. **Missing:** admin account, query/update services, API/screen, administrative authorization, audit-writing service and bootstrap procedure.

- [ ] Only active `DISPATCHER_ADMIN` users can page/filter users, read a safe account record, change permitted roles and suspend/reactivate an account. Ordinary roles receive `403`; anonymous/invalid-session callers receive `401` after applicable CSRF checks.
- [ ] Role vocabulary is exactly CUSTOMER, MERCHANT, COURIER, DISPATCHER_ADMIN. Proposed admin role editing covers CUSTOMER/COURIER/DISPATCHER_ADMIN; MERCHANT grants/removals go through the M2 approval/access workflow and its M1 service contract.
- [ ] Suspension and role changes update the user authorization version and revoke all sessions atomically. Subsequent requests using already-issued JWTs must fail. Reactivation does not resurrect old sessions.
- [ ] Require an audit reason and expected record version; record actor, target, action, safe before/after values and timestamp in the same transaction. No hard deletion of users referenced by other modules.
- [ ] Prevent self-suspension/self-removal of the admin role and loss of the last active administrator, including concurrent changes. A separately reviewed recovery procedure is needed for exceptional administration.
- [ ] Bootstrap the first admin through a one-time, explicitly enabled backend operational procedure with private input; never ship a default password, public admin signup, or secret-bearing Flyway seed. Exact procedure remains a decision before this slice.
- [ ] Test the role matrix, pagination/limits, stale writes, concurrent last-admin changes, audit records and immediate revocation; add admin UI loading/empty/denied/error states.

### IAM-06 — Role enforcement and shared IdentityContext

**Existing:** all unimplemented routes are denied. **Missing:** authenticated principal construction, role rules and the shared service-facing contract.

- [ ] Provide an immutable `IdentityContext(userId, roles, active, sessionId, authorizationVersion)` from validated authentication. No passwords, refresh tokens, provider tokens or mutable JPA entity. Fail closed when a required identity is absent.
- [ ] Validate JWT signature with an algorithm allowlist, issuer, audience and expiry, then check persisted account/session state and authorization version. Load authoritative roles; a stale or edited claim must not grant authority.
- [ ] Controllers receive the principal through Spring Security and services enforce action permissions. Never trust body/query/header `userId`, role or merchant IDs as authentication. Other modules use their own ownership checks in addition to this context.
- [ ] Publish in-process contracts for safe account lookup and controlled role updates. M2/M5 must not read M1 repositories, mint tokens or create parallel user tables. Merchant API-key authentication remains M2's contract and must not impersonate an end-user session.
- [ ] Explicitly allow only implemented entry points. Keep GET health public and preserve denial of unknown routes. React navigation guards improve usability; backend authorization remains mandatory.
- [ ] Test all four roles, multiple-role users, missing/expired/tampered/wrong-audience tokens, suspended users and cross-user access attempts. Include service-level authorization tests, not only UI visibility tests.

### IAM-07 — Session refresh, expiry and revocation

**Implemented:** token processing, atomic rotation/replay revocation, current-state checks, protected cookies and coordinated React refresh. API identities are request-scoped; a separate chain preserves future OAuth framework sessions.

- [x] Proposed v3 lifetimes: access JWT 10 minutes; refresh family absolute lifetime 7 days from login, never extended by rotation. Persist a stable session ID referenced by JWT `sid` and the authorization version captured at issue time.
- [x] Store only hashes of cryptographically random refresh tokens. Each successful refresh atomically consumes the old token and creates its replacement within the same family; exactly one concurrent request may succeed.
- [x] Reuse of an already consumed refresh token revokes the entire family/session, including the replacement token. Retain enough consumed-token history to detect reuse for the family's lifetime.
- [x] Every protected request checks active account, unrevoked/unexpired session and matching authorization version. Logout affects the current session; suspension, role changes and password reset revoke every session. Fail closed on state lookup failure.
- [x] Cookie-authenticated operations have CSRF and origin protection. Secure cookie attributes and exact-origin proxy behavior are tested; do not disable CSRF globally because access tokens are JWTs.
- [x] React keeps access tokens in memory, bootstraps with refresh then `/me`, and permits one coordinated refresh/retry per failed API operation. Serialize refresh across tabs too; never loop on `401` or automatically retry an ambiguous network failure with a potentially consumed refresh token. Fall back to sign-in when necessary.
- [x] Test rotation races, replay, expiry using an injectable clock, logout, authorization changes and client bootstrap. No localStorage/sessionStorage token fallback.
- [ ] Execute a real browser reload and backend process-restart smoke check; these were not run in this slice.

### IAM-08 — Password recovery

**Existing:** reset-token table/mapping and `forget password.png` request-screen design. **Missing:** request/completion endpoints, token generation/consumption service, reset form, delivery adapter and feature tests. IAM-08 explicitly adds recovery to M1 even though v3 enumerates IAM-01 through IAM-07.

- [ ] Implement the supplied email request form, generic acknowledgement and back-to-login link. For valid email input, return the same `202` body for existing, unknown, suspended and Google-only accounts, with comparable response behavior. Rate-limit without revealing whether an account exists.
- [ ] Proposed eligibility: send reset instructions only for active accounts with a local password. Google-only users continue with Google; recovery must not silently add a local credential or reactivate a suspended account.
- [ ] Generate a random, single-use reset token; store only its hash and expiry (proposed 30 minutes), and invalidate prior outstanding reset tokens for that user. Deliver through the agreed backend mail adapter. No reset token in normal API responses or application logs.
- [ ] Add the missing completion UI: new password, confirmation, show/hide, submitting, invalid/expired/used-link and success states. Use the same password policy as registration.
- [ ] In one transaction, consume a valid token, change the password hash, increment authorization version and revoke all sessions/other reset tokens. Two simultaneous submissions cannot both succeed. Sign in normally afterwards; do not automatically log in or change account roles/status.
- [ ] Construct links from a configured trusted frontend origin, not the request Host header. Keep reset-page URLs out of analytics/referrers and clear the token from browser history after capture. Email delivery failures are recorded safely; do not expose provider details or claim delivery was verified when it was not.
- [ ] Test unknown email responses, expired/reused/invalid tokens, rate limiting, transaction races, old-password rejection, session invalidation and mail failures. Use a test mail adapter; real delivery remains a separate configured smoke test.

Recovery safeguards follow [OWASP's Forgot Password guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html); the 30-minute expiry and eligibility choices above are project proposals, not requirements from that source.

### Shared UI and test acceptance

- [ ] Use the supplied navy/orange SwiftDrop login/register/recovery layouts, responsive down to 360px. Preserve labels, keyboard navigation, visible focus and announced errors; target WCAG 2.2 AA and the v3 product choice of 44px controls.
- [ ] Never expose passwords/hashes/tokens in a DTO, frontend bundle, screenshot fixture or diagnostic log. Auth responses use `Cache-Control: no-store`; redact sensitive URL/query data.
- [ ] Add meaningful unit, Spring Security HTTP, real PostgreSQL integration and React interaction tests for each slice. Assignment asks for coverage evidence; v3's suggested 80% service coverage is not an existing enforced repository threshold.

## 3. Proposed shared contracts

### Authentication and browser behavior

The browser presents `Authorization: Bearer <accessToken>` to business APIs. The signed JWT includes `sub` (user UUID), `iss`, `aud`, `iat`, `exp`, `jti`, `sid` (stable session UUID), and an authorization-version claim. Roles may be included for UI convenience but cannot override persisted authorization state.

Proposed refresh cookie: `swiftdrop_refresh`, `HttpOnly`, `Secure` under HTTPS, `SameSite=Lax`, host-only, `Path=/api/v1/auth`. Clear it with identical attributes. Keep it out of JavaScript. A development-only loopback HTTP exception for Secure must be explicit and tested; deployment requires HTTPS. The current Compose setup is local HTTP and is not yet a production authentication deployment.

Implemented CSRF bootstrap: GET `/api/v1/auth/csrf` returns `{token, headerName}` and establishes the browser's CSRF association. React sends that header on registration. Future login/refresh/logout must reuse the mechanism and refresh the CSRF value when authentication changes; review allowed origins for cookie-changing requests. Bearer-only APIs may have narrowly scoped CSRF exclusions only after tests establish that they do not accept ambient cookie credentials.

Google authorization-request state is temporary and distinct from application bearer authentication. Use a bounded, expiring authorization-request store (a short-lived server session is an option); clear it after callback/failure. Do not assume the current global `STATELESS` setting provides OIDC state storage. If a temporary HTTP session is used, it must not become an alternate authentication mechanism for protected business APIs.

### HTTP API proposal

Health, CSRF, registration, login, refresh, logout and GET /me below are **implemented**; other routes remain **planned**. The current auth contract is [identity-sessions.md](../../contracts/identity-sessions.md). `Public + CSRF` means no application login is required, but unsafe browser requests still need the CSRF header. All auth entry points are validated and rate-limited. Protected routes need a live bearer identity and, where indicated, a role.

| Method and path | Access | Request | Success / important failures |
| --- | --- | --- | --- |
| GET `/api/v1/health` | Public; existing | None | Existing `200 {status:"UP", application:"swiftdrop"}`; stays unchanged. |
| GET `/api/v1/auth/csrf` | Public | None | `200 {token, headerName}`; no-store. |
| POST `/api/v1/auth/register` | Public + CSRF | `{fullName, email, password}` | `201 {id, email, fullName, roles:["CUSTOMER"]}`; `400`, generic `409`, `429`. |
| POST `/api/v1/auth/login` | Public + CSRF | `{email, password}` | `200 TokenResponse` + refresh cookie; generic `401`, `429`. |
| POST `/api/v1/auth/refresh` | Refresh cookie + CSRF | No token in body | `200 TokenResponse` + rotated cookie; `401` invalid/expired/replayed/revoked; `403` bad CSRF/origin. |
| POST `/api/v1/auth/logout` | Refresh cookie + CSRF; bearer optional | Empty | `204`, revoke matching current session and clear cookie; missing/already revoked cookie also `204`; `403` bad CSRF/origin. |
| GET `/api/v1/me` | Any active authenticated role | None | `200 UserProfile`; `401`. |
| PATCH `/api/v1/me` | Any active authenticated role | `{fullName?, phone?, version}` | `200 UserProfile`; `400` invalid/forbidden fields; `409` stale version. |
| GET `/api/v1/admin/users` | DISPATCHER_ADMIN | `page=0`, `size=50` (max 100), optional `role`, `active`, `query`; fixed/allowlisted sorting | `200 {items, page, size, totalElements, totalPages}`; `400`, `401`, `403`. |
| GET `/api/v1/admin/users/{id}` | DISPATCHER_ADMIN | User UUID | `200 UserProfile`; `404` absent user. |
| PATCH `/api/v1/admin/users/{id}` | DISPATCHER_ADMIN | `{active?, roles?, reason, version}`; at least one mutable field | `200 UserProfile`; `400` unsupported role operation; `409` stale update/last-admin protection. MERCHANT edits require M2 workflow. |
| POST `/api/v1/auth/forgot-password` | Public + CSRF | `{email}` | Generic `202 {message}` for valid input regardless of account; `400` malformed input; `429` throttled. |
| POST `/api/v1/auth/reset-password` | Public + CSRF + reset token | `{token, newPassword, confirmPassword}` | `204`; generic `400 INVALID_RESET` for bad/expired/used token; `429`. |
| GET `/oauth2/authorization/google` | Public OIDC entry | Server-controlled provider/settings | Redirect to Google; must establish state/nonce/PKCE. |
| GET `/login/oauth2/code/google` | OIDC callback | Provider `code`, `state` or error | Validate, set refresh cookie, redirect to fixed `/auth/complete`; safe failure route on error; no token URLs. |
| POST `/api/v1/me/oauth/google/link` | Freshly authenticated identity + CSRF | `{currentPassword}` for a local-password account | `200 {authorizationUrl}` with short-lived state bound to this user/action; same callback completes linking. Invalid reauthentication `401`; existing provider link `409`. Broader provider management is outside this scope. |

`TokenResponse = {accessToken, tokenType:"Bearer", expiresIn:600}`. The refresh token is cookie-only. `UserProfile = {id, email, fullName, phone, roles, active, createdAt, updatedAt, version}`. Internal authorization version and credential/provider/session secrets are not profile-editable fields.

Use a shared safe error shape `{code, message, correlationId, fieldErrors}`; `fieldErrors` is an optional array of `{field, message}` and never contains rejected passwords/tokens. Registration and security errors now use this shape with an always-present array; raw input values and database details are excluded. Use `400` for validation, `401` for unusable identity/session, `403` for denied authority/CSRF, `404` for an unavailable permitted resource, `409` for conflicts and `429` with retry guidance for throttling. Security filters may reject a request before its controller runs.

Proposed frontend routes: `/login`, `/register`, `/forgot-password`, `/reset-password`, `/auth/complete`, `/profile`, `/admin/users`. These are React pages, not additional JSON API paths. Business dashboards wait for their feature owners.

### In-process contracts for teammates

| Contract | Consumer / responsibility |
| --- | --- |
| `IdentityContext` | Authenticated backend services receive userId, authoritative roles, active, sessionId and authorizationVersion. Obtain from trusted authentication, never deserialize from a client body. |
| `UserIdentityService.getAccount(userId)` -> safe account summary | M2/M5 can verify account existence, active status and roles through a service. No credential access or repository dependency. |
| `UserIdentityService.requireActiveRole(userId, role)` | M5 validates a courier account before its own profile operation. Domain rules/transactions remain in M5. |
| Controlled merchant-role update service | M2 invokes after its approval/access transition, with actor/reason and transaction agreement. M1 persists roles, increments authorization version and revokes sessions; M2 persists approval. Coordinate atomicity in the single backend, not RabbitMQ-delayed authorization. |

These teammate-facing Java interfaces will be published with IAM-06. They remain proposed responsibilities; IAM-01's `RegistrationService` is not an authenticated IdentityContext or account-lookup API.

## 4. Database foundation — implemented in V2

All tables below exist in **`swiftdrop`**, with UUID identifiers and UTC `timestamptz` fields. [The implemented ER guide](schema.md) is authoritative for exact columns, constraints and transaction requirements. Registration service/API now uses these tables; other IAM services/APIs remain future work.

- [x] Add V2 without editing V1; preserve existing data in upgrade tests.
- [x] Add eight JPA mappings and validate them against migrated PostgreSQL.
- [x] Seed the four role definitions only; enforce email/role/provider/token constraints and indexes.
- [x] Separate direct Flyway from runtime connections, guard the destination and verify private environment binding without opening a live Neon connection.

| Table | Required fields / constraints | Purpose |
| --- | --- | --- |
| `users` | `id` PK; `email`, unique non-null `email_normalized`; `full_name`; optional `phone`; nullable `password_hash`; `active`; nonnegative `authorization_version`; optimistic `version`; `created_at`, `updated_at` | One identity regardless of roles/provider. Nullable password supports Google-only accounts. Keep historical references when suspended. |
| `roles` | UUID `id` PK; unique `name` constrained to the four agreed role values | UUID requirement supersedes the earlier proposed code PK. No account/password seed. |
| `user_roles` | Composite UUID PK `(user_id, role_id)`; FKs to users/roles | Many-to-many roles; no duplicate role assignment. |
| `oauth_accounts` | `id` PK; `user_id` FK; `provider`, `provider_subject`; unique pair; unique `(user_id, provider)`; `created_at` | Stable Google identity link. No Google access/refresh token persistence is needed for sign-in-only scope. |
| `auth_sessions` | `id` PK used as JWT `sid`; `user_id` FK; unique `family_id`; `authorization_version`; `created_at`, absolute `expires_at`, nullable `revoked_at`, safe `revocation_reason`; optimistic version/locking | Stable sign-in session and family revocation boundary. One family per login; rotation never changes `sid`. |
| `refresh_tokens` | `id` PK; `session_id` FK; unique `token_hash`; `created_at`, `expires_at`, nullable `consumed_at`, `revoked_at`; nullable unique `replaced_by` self-FK | Rotation chain and replay detection. At most one unconsumed/unrevoked token per session, enforced with locking/constraint. Keep consumed hashes until family expiry. |
| `password_reset_tokens` | `id` PK; `user_id` FK; unique `token_hash`; `created_at`, `expires_at`, nullable `used_at`, `revoked_at` | One-time recovery; lock/conditional update when consuming. Invalidate older outstanding requests transactionally. |
| `identity_audit_events` | `id` PK; actor user FK (nullable only for explicitly identified system/bootstrap action); target user FK; action, reason, redacted before/after state, `occurred_at`, correlation ID | Append-only application audit for role/status/reset/bootstrap actions. Never store passwords or token values. Agree retention before deployment. |

**Implemented refinement of v3:** its combined `RefreshSession` is split into `auth_sessions` plus `refresh_tokens`. JWT `sid` will refer to the stable session ID while consumed token hashes remain available for replay detection. Tokens share the parent's absolute expiry and replacement chains cannot cross sessions.

Indexes should cover normalized email, provider/subject, user roles, sessions by user, token hashes, expiry cleanup and audit target/time. Enforce expiry-after-creation and foreign-key rules. Do not cascade-delete users referenced by business history. Session rotation, reset consumption and last-admin checks require PostgreSQL concurrency tests; optimistic DTO versions alone are insufficient for those invariants.

Schema workflow:

1. Read all migrations and coordinate the next global number in the team PR. V1 through V4 now exist; coordinate V5 or the next free version before merging further changes.
2. Add an immutable SQL file to `backend/src/main/resources/db/migration/`. This folder is where schema changes belong; do not create a competing `schema.sql` or rename the migration folder for this task.
3. Extend the existing `com.swiftdrop.entity.identity` mappings and add repositories under `com.swiftdrop.repository.identity` with feature behavior. Keep `ddl-auto: validate`.
4. Run fresh-database and upgrade/restart tests. Confirm Flyway's direct connection and JPA's runtime connection target the same database and `swiftdrop` schema.
5. Commit reviewed migration files. Teammates pull them and start the backend against their own Neon branch or local database. Flyway applies pending changes; do not ask each teammate to reproduce table edits manually in Neon.

M1 does not create merchant/courier operational tables, bookings, outbox consumers or parcel notifications here. Other features reference `users.id` through their own coordinated migrations.

## 5. Reference review and conflict resolutions

Reviewed the four-page [assignment](<../reference/Group Assignment.pdf>), all 38 pages of the [v3 design](../reference/SwiftDrop_Design_Documentation.pdf), standalone architecture/ER images, all 18 images in [the diagram folder](<../reference/Diagram-UseCase,Activity,Sequence/>), and all three [UI references](<../reference/Figma design/>). Relevant anchors are v3 pp7–10 (data/API/security), pp14,16–18 (Google/UI/M1), pp19,22 (M2/M5), pp28–29 (integration/testing) and p34 (identity ER). This checklist adds M1 detail to the existing [reference review](../reference-review.md).

| Conflict / omission and source | Proposed resolution / status |
| --- | --- |
| Assignment p1 says ten members; user/v3 use nine. | Preserve nine agreed owners. Lecturer confirmation is still needed; code cannot settle assessment rules. |
| Standalone ER puts role fields in `UserRole`, uses user subtype inheritance and shows duplicate Courier/API-key relationships; v3 p34 has Role + join and optional profiles. | Follow v3's normalized user/roles/provider model. M2/M5 own unique optional profiles; no separate Customer identity table. Merchant API keys belong to M2. |
| Google sequence calls its participant “Identity DB”. | Identity-owned tables live in the same `swiftdrop` schema; no separate identity database/service. Agreed architecture governs. |
| Architecture PNG routes merchant traffic to React and draws outbox/consumers between repositories and DB; sequence diagrams omit repositories. | HTTP integrations terminate at backend controllers; normal persistence remains Controller -> Service -> Repository -> PostgreSQL. Background components run in the one backend. Diagrams are conceptual, not implementation wiring. |
| Use case 4.1.5 draws separate Dispatcher and Administrator actors. | Use the explicitly agreed combined `DISPATCHER_ADMIN` role. Do not invent a fifth role. |
| Merchant/courier activity diagrams could be read as public role signup; user/v3 distinguish ownership. | Public signup always CUSTOMER. M2 approval controls merchant access; M5 creates courier operational profiles for suitable existing identities. No self-promotion via signup/profile. |
| Google sequence steps 25/29 use `/api/auth/refresh` and `/api/auth/me`; other diagrams abbreviate paths. | Use `/api/v1/auth/refresh` and `/api/v1/me`, consistent with v3 p9 and existing health prefix. |
| v3 uses Spring Google redirect routes while existing Vite/Nginx proxy only `/api`. | Proposed contract retains `/oauth2/authorization/google` and `/login/oauth2/code/google`; extend both proxies during IAM-03 and configure the externally visible callback origin. Do not claim the current proxy already supports this. |
| V3 IAM list stops at IAM-07; forgot-password UI exists; earlier `reference-review.md` deferred recovery. | User's current explicit IAM-08 request brings recovery into scope. That earlier deferral is historical. Saved addresses remain outside this scope. |
| Forgot-password screenshot shows only the request screen. | Add reset completion, invalid/expired-link and success states in the same visual style. Endpoint/token/mail contracts are proposals in this checklist. |
| Register UI says at least eight characters including a number; v3 does not settle a password policy. | IAM-01 implements 8–128 Unicode code points, spaces/Unicode allowed, no mandatory digit rule and no truncation/normalization. UI and server share the policy. A common/compromised-password blocklist remains future hardening; no remote password-check service is called. This project choice supersedes the screenshot hint. |
| Login subtitle says courier dashboard although four roles use it. | Use neutral account wording; route after login based on permitted modules. Do not build other members' dashboards. |
| UI references Terms/Privacy but no corresponding documents/pages are supplied. | Obtain approved text/destination before making links live; do not invent legal acceptance semantics. |
| V3's short access JWT and immediate logout/suspension checks can be misread as fully stateless authentication. | JWT validation also checks persisted account/session/version state. Revocation cannot wait for JWT expiry. Current deny-all `STATELESS` setup is only the foundation. |
| V3 `RefreshSession` has token replacement and session identity in one box. | V2 implements the stable-session/rotating-token split in section 4. Service-level rotation/replay enforcement remains future work. |
| V3 does not specify first-admin provisioning, reset lifetime/mail provider or exact profile editable fields. | Use explicit proposals here; finalize bootstrap procedure, delivery configuration, lifetime and allowlist before their implementation slice. Never silently seed credentials. |
| No supplied local-email verification flow; Google requires verified email. | Do not mark password signup email as verified merely because it was entered. Recovery proves mailbox control only for its action. If verified local email is required, add a separately reviewed story/schema/contract before relying on it for business eligibility. |
| V3 suggests coverage targets; assignment asks for testing/coverage but gives no hard percentage. | Add behavior/security tests and report coverage honestly. Existing foundation coverage is not evidence that IAM is covered. |

The user selected the current eight-character minimum, replacing the initial 15-character implementation. The maximum remains 128 code points, with no mandatory character classes. [OWASP's Authentication guidance](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) informed the original policy; this project choice does not claim compliance with its password-only minimum. The referenced blocklist recommendation is not implemented in this slice.

The remaining booking/pricing/tracking/notification/webhook diagrams were reviewed for boundaries: they consume a trusted identity or their own public/API-key contract. They do not transfer business-record ownership checks into M1. General non-IAM conflicts remain recorded in `docs/reference-review.md`.

## 6. Implementation order and team handoff

Each numbered item should be a small reviewed change with its own tests. An end-to-end slice means its database, backend API and React behavior work together.

1. **Agree the contracts:** review the proposals above with M2/M5 and the team; settle role-update boundaries, session mapping, password/profile rules and the next migration number. Publish the API/service contracts under `contracts/`.
2. **Build account and security basics together:** migrations, repositories, password encoding, DTO validation, IdentityContext and token/session primitives; then customer registration, password login/logout and `/me`. Do not expose incomplete authentication routes during an intermediate merge.
3. **Complete session behavior:** rotation, replay detection, CSRF, expiry/revocation checks and coordinated React refresh. The login slice must not be called complete until these controls work.
4. **Add profile and admin workflows:** allowed profile edits, guarded role/status changes, first-admin provisioning and audit/concurrency tests. Teammates can then integrate against a stable user/context service.
5. **Add Google:** provider registration, proxies, callback, safe explicit linking, controlled-provider tests and real-provider demonstration.
6. **Add recovery:** mail adapter, reset tokens and the request/completion screens, then real delivery verification with supplied configuration.

No booking, pricing, merchant approval, courier operations, tracking or parcel notification implementation belongs in these changes. Team members still need their feature code, tests and shared migrations; changing only their database credentials does not implement their part.

Proposed code locations, following the existing layer-first structure:

| Location | Responsibility |
| --- | --- |
| `backend/src/main/java/com/swiftdrop/controller/identity/` | HTTP DTO validation and calls to services. |
| `backend/src/main/java/com/swiftdrop/service/identity/` | Authentication, user/profile/admin/reset rules and transactions; shared service contracts. |
| `backend/src/main/java/com/swiftdrop/repository/identity/`, `entity/identity/` | Persistence queries, mappings and locking. |
| `backend/src/main/java/com/swiftdrop/dto/identity/` | Explicit request/response records. |
| `backend/src/main/java/com/swiftdrop/security/` | Bearer validation, security rules and trusted IdentityContext construction. |
| `backend/src/main/java/com/swiftdrop/integration/identity/` | Google and reset-mail adapters. |
| `backend/src/main/resources/db/migration/` | Shared, ordered schema changes for every member. |
| `frontend/src/features/identity/` | Auth/profile/admin screens, API client and auth state; no database credentials. |
| `contracts/`, `docs/identity/` | Reviewed interface contracts and implementation decisions/checklist. |

## 7. Configuration still needed

Every developer keeps private `backend/.env` with the same `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` names and their own Neon branch values. Flyway now derives a separate direct connection, with optional `SPRING_FLYWAY_URL/USER/PASSWORD` overrides; the destination guard requires the same endpoint/database. See [the connection guide](schema.md#neon-connections-and-environment-loading). Backend startup also reads root broker configuration. Keep all credentials out of frontend configuration and clear stale shell overrides when switching environments.

For later IAM implementation, supply backend-only Google client ID/secret and approved callback origins, JWT signing/verification keys plus issuer/audience, trusted frontend origin, and reset-mail provider credentials/sender. Choose and document common environment variable names with placeholder examples when adding the actual configuration; **these settings are not wired today**. Decide the first-admin bootstrap input and production HTTPS/cookie settings. Never commit a real `.env`, signing key, password, token or provider secret.

Local Compose remains the alternative database environment; use the README's local setup and avoid a conflicting Neon override when intentionally targeting local PostgreSQL. Normal startup may apply pending migrations, so select and verify the intended private branch before running it. Integration tests should use disposable PostgreSQL, never a teammate's Neon database.

## 8. Commands supported by this repository

Run these from the repository root in PowerShell unless stated otherwise. Follow [README setup](../../README.md) first; do not overwrite an existing private environment file. Java 21, compatible Node 24/npm 11, the Maven wrapper and installed frontend dependencies are required. Docker Desktop's Linux engine is additionally required for infrastructure/integration tests.

### Existing automated checks

```powershell
# Use the real JDK if the Oracle PATH shim hangs on this machine.
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Unit/configuration/security cases; no live Neon needed.
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress test

# Package and generate JaCoCo report (stop your own running JAR first if Windows locks it).
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress verify

# Adds disposable PostgreSQL/RabbitMQ integration tests; Docker required.
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress -Pintegration verify

# Fresh clone/install, then existing four frontend tests and production build.
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build

# Validate Compose without printing expanded private credentials.
docker compose config --quiet
docker compose -f compose.yaml -f compose.dev.yaml config --quiet
git diff --check
```

`--offline` may be added to Maven only when all dependencies are already cached; it was used for this review's unit-test run. Reports are in `backend/target/surefire-reports/`, `backend/target/failsafe-reports/` and, after `verify`, `backend/target/site/jacoco/index.html`. Do not invent `lint`, `test:e2e`, OAuth demo or IAM-specific commands: they have not been added yet.

### Existing startup and manual smoke check

For Neon, configure private `backend/.env` first and keep the root RabbitMQ settings from README setup. Start the broker if needed:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d rabbitmq

# Exact launch method: explicitly loads the backend environment file.
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

In another terminal at the repository root:

```powershell
npm.cmd --prefix frontend run dev
```

Then visit `http://127.0.0.1:5173` or run:

```powershell
curl.exe -i http://127.0.0.1:8081/api/v1/health
curl.exe -i http://127.0.0.1:5173/api/v1/health
curl.exe -i http://127.0.0.1:8081/api/v1/me
```

**Today:** both health requests should return `200` and `{"status":"UP","application":"swiftdrop"}`. React shows registration at `/register` and “Backend connected” at `/foundation`. `/me` should return `401`, because login/profile access is not implemented. Seeing that denial is not a failed login feature. The health response alone does not prove you selected the intended Neon branch; inspect non-secret startup/Flyway information and your branch configuration.

For the existing local-database option, follow README section 2B, start `postgres rabbitmq` using the same Compose files, and select the local `POSTGRES_*` configuration instead of a Neon override. The full container route remains `docker compose up --build` with frontend at `http://localhost:8080`; see the verification limitations below.

### Historical verification record for the planning step

- Reviewed existing source, migration/configuration, reference documents, diagrams and UI screens. No real `.env` contents were needed or exposed.
- Passed in this review: **4 frontend tests**, the frontend production build, and **8 backend unit/security tests** (zero failures/errors/skips). The first backend attempt stalled because the sandbox denied Mockito's Java attach pipe; it was stopped, then the same offline unit-test command passed outside that restriction. No application code change was needed.
- Documentation checks passed: all eight IAM sections present, local Markdown links resolve, code fences/whitespace are valid, and `git diff --check` is clean. Existing unrelated working-tree changes were preserved.
- Prior foundation verification reports **8 backend unit/security + 3 integration tests**, frontend build, PostgreSQL/Flyway and broker smoke checks passing. See [verification report](../verification.md). Those historical results are not new IAM verification.
- Backend packaging, Docker integration tests and application startup were **not rerun in this documentation-only step**. Full application container builds/startup were previously blocked by image download issues. Live Neon remains **unverified**; Google login, reset-email delivery and IAM end-to-end behavior remain **unimplemented/unverified**. This documentation step does not change that status or require credentials.
- No application code, migration, dependency or runtime configuration is implemented by this checklist. Future implementation PRs must update evidence and check boxes only when acceptance criteria actually pass.

### Database implementation follow-up

V2, JPA mappings, direct migration configuration and database tests are now implemented as described above. Backend verification passed 24 unit/configuration/security tests and 14 integration tests against disposable PostgreSQL/RabbitMQ, with no skips. A configuration-only check confirmed this developer's private file bindings without printing credentials or opening a Neon connection. Full details and remaining limits are in [the verification record](../verification.md). That historical database step left all IAM feature boxes unchecked; IAM-01 is now completed above.


### IAM-01 implementation follow-up

Implemented registration against V2 without changing migrations or private credentials. See the checked IAM-01 criteria and [registration guide](registration.md) for actual files, decisions and commands. Verification: 31 backend unit/configuration/security tests, 27 PostgreSQL/broker integration tests, nine frontend tests, both builds and a real browser signup through Vite. Desktop and 360px mobile layouts were checked; no mobile horizontal overflow. The disposable browser account/database were removed afterward. Live Neon and full Compose application image startup were not retested. IAM-02–IAM-08 remain unchecked.


### Password feedback and minimum-length follow-up

The user selected an eight-character minimum. Frontend and backend now consistently
accept 8–128 Unicode code points and return specific missing/too-short/too-long
messages. The form shows the character count and uses accessible eye icons for
both password fields. Tests cover seven-character rejection, eight-character
acceptance, the 128-character boundary, unchanged password values and confirmation
correction. Final verification passed 31 backend unit/configuration/security tests,
29 PostgreSQL/broker integration tests, 16 frontend tests and both builds. No
migration, database credential or hashing algorithm changed.


### IAM-02 / IAM-07 implementation follow-up

The [session guide](sessions.md) and [API contract](../../contracts/identity-sessions.md) supersede earlier proposed session/security descriptions above. V2 is reused unchanged. Final checks passed 38 backend unit/security tests, 47 integration tests and 30 frontend tests, plus both builds. Live Neon, browser visual checks, full Compose image deployment and process-restart checks were not run. No Google, recovery, admin or profile editing is implemented.
