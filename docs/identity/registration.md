# IAM-01: customer registration

Registration works from the React page through Spring Boot into the existing
`swiftdrop.users` and `swiftdrop.user_roles` tables. It uses the CUSTOMER definition
already seeded in V2. V1/V2 and existing data are unchanged; no new migration,
administrator account, login, OAuth or other module was added.

The reviewed API is in [the registration contract](../../contracts/identity-registration.md).

## Run it

Use the [README prerequisites and environment setup](../../README.md) first.
Keep your own Neon values in private `backend/.env`; do not copy them into React.
Keep the existing root `.env` broker configuration. If using Neon, start the
development broker from the repository root:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d rabbitmq
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

Maven sets the backend working directory. Spring imports `../.env[.properties]`,
then `./.env[.properties]`; backend values override root values, while explicit
OS variables/command-line properties take precedence. Runtime uses
`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`. Flyway derives a direct connection to
the same endpoint/database, or uses guarded `SPRING_FLYWAY_URL/USER/PASSWORD`
overrides. Normal startup applies any pending migrations to your selected branch.
See [the connection guide](schema.md#neon-connections-and-environment-loading).

In another terminal, also from the repository root:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run dev
```

Open **http://127.0.0.1:5173/register** (the root URL also shows registration).
Fill all four fields with matching passwords. Success says **Account created**
and explicitly says you are not signed in. Login is marked coming soon; IAM-02
is still unimplemented. The original health/retry page remains at `/foundation`.
Vite forwards `/api` to `http://localhost:8081` by default. If using a custom
backend port, set only `BACKEND_URL` in private `frontend/.env` and restart Vite.

Use the selected Neon branch in `backend/.env` and start RabbitMQ through Compose
for Docker or local Java development. Full Compose remains available at
`http://localhost:8080/register`; its image-build verification limit is recorded
in [verification](../verification.md). No new credentials are required for IAM-01.

## How the request travels through the actual files

1. [RegisterPage.jsx](../../frontend/src/features/identity/RegisterPage.jsx) holds
   the four field values temporarily in React state. It checks confirmation and
   password length, displays field errors and prevents repeat submits while
   pending. [FormField.jsx](../../frontend/src/components/FormField.jsx),
   [AuthLayout.jsx](../../frontend/src/components/AuthLayout.jsx) and
   [Brand.jsx](../../frontend/src/components/Brand.jsx) supply reusable UI.
2. [registrationApi.js](../../frontend/src/features/identity/registrationApi.js)
   first fetches the CSRF association, then sends only `fullName`, `email` and
   `password` to the real registration endpoint. Confirmation never crosses the
   network or enters browser storage. There is a 15-second timeout and no
   automatic retry: a timed-out request might already have committed.
3. [SecurityConfig.java](../../backend/src/main/java/com/swiftdrop/security/SecurityConfig.java)
   permits the exact registration/CSRF methods and paths while retaining CSRF
   and deny-by-default protection. The bounded
   [registration limiter](../../backend/src/main/java/com/swiftdrop/security/RegistrationRateLimitFilter.java)
   limits CSRF-valid attempts before controller/password work.
4. [RegistrationController.java](../../backend/src/main/java/com/swiftdrop/controller/identity/RegistrationController.java)
   receives [RegistrationRequest.java](../../backend/src/main/java/com/swiftdrop/dto/identity/RegistrationRequest.java).
   Jackson rejects any undeclared field. Bean Validation checks required name,
   email and the [password policy](../../backend/src/main/java/com/swiftdrop/validation/identity/PasswordPolicyValidator.java).
   Controllers never call repositories.
5. [RegistrationService.java](../../backend/src/main/java/com/swiftdrop/service/identity/RegistrationService.java)
   owns one transaction. It looks up the seeded CUSTOMER role, calls
   [UserRepository.java](../../backend/src/main/java/com/swiftdrop/repository/identity/UserRepository.java)
   to normalize email with the database's exact `lower(btrim(...))` behavior,
   hashes the unchanged password using
   [PasswordConfig.java](../../backend/src/main/java/com/swiftdrop/config/PasswordConfig.java),
   and saves both the user and its membership through JPA repositories. A failed
   membership insert rolls back the user too.
6. PostgreSQL's existing `uq_users_email_normalized` constraint decides conflicts,
   including concurrent requests. There is no unsafe check-then-insert assumption.
   After rollback, [ApiExceptionHandler.java](../../backend/src/main/java/com/swiftdrop/exception/ApiExceptionHandler.java)
   converts that constraint into a safe 409. Other errors use the shared
   [ApiError.java](../../backend/src/main/java/com/swiftdrop/exception/ApiError.java)
   shape. Hibernate SQL/error-detail/bind logging is disabled because database
   errors can include a whole credential-bearing row.
7. After commit, [RegistrationResponse.java](../../backend/src/main/java/com/swiftdrop/dto/identity/RegistrationResponse.java)
   returns only ID, canonical email, name and CUSTOMER role with HTTP 201. React
   clears the fields and shows success. It creates no logged-in state or tokens.

## Password and design decisions

- Both frontend and backend require **8–128 Unicode code points**, permit
  spaces/Unicode, and impose no mandatory digit/symbol rule. A JavaScript spread
  and Java `codePointCount` count supplementary Unicode characters consistently.
  The form shows the entered character count and a specific required/minimum/
  maximum error. Spaces/Unicode are permitted, not required. The visibility
  toggle uses an eye icon with an accessible Show/Hide label on both fields.
  The backend does not trim or otherwise alter the password; confirmation must
  match exactly. Use a unique passphrase. A compromised-password blocklist is
  future hardening, not an implemented check.
- Spring Security's adaptive **PBKDF2-HMAC-SHA256**, 600,000 iterations, random
  16-byte salt and 256-bit derived key is used through `DelegatingPasswordEncoder`.
  The `{pbkdf2-sha256-600k}` prefix records the algorithm/work factor for a future
  upgrade. PBKDF2 supports the accepted length without BCrypt's byte limit and
  needs no new dependency. Parameters follow
  [OWASP password-storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html);
  see also [Spring's encoder documentation](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
  Reassess work factor on deployment hardware; these tests are not a load test.
- The actual reference is [Figma design/register.png](<../reference/Figma design/register.png>).
  The implementation follows its navy illustration panel, white form card,
  SwiftDrop branding and field arrangement, with a responsive single-column
  mobile layout. The courier illustration is a local SVG approximation because
  no separate illustration asset was supplied.
- The user selected an eight-character minimum, replacing the initial 15-character
  implementation. The maximum remains 128 and no digit/symbol rule is added.
  Login remains clearly unavailable until IAM-02. Terms/Privacy links
  are deferred until approved destination/content exists; no legal agreement
  is invented. The user's three-field API overrides the checklist's earlier
  proposal to send confirmation. Email is stored canonically rather than keeping
  a differently cased display value; future login/recovery must reuse the same
  database normalization.

## Verify

From the repository root in PowerShell, with Java 21, installed frontend
dependencies and Docker Desktop's Linux engine running:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress -Pintegration verify
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
git diff --check
```

Without Docker, run backend `verify` without `-Pintegration`; this runs unit tests
and packaging only, not PostgreSQL integration tests. All integration tests
override both application/Flyway connections with disposable databases.

[RegistrationServiceTest](../../backend/src/test/java/com/swiftdrop/service/identity/RegistrationServiceTest.java)
checks service rules, exact-password preservation, salted adaptive hashing and
Unicode validation. [RegistrationIT](../../backend/src/test/java/com/swiftdrop/RegistrationIT.java)
uses real HTTP and PostgreSQL to test atomic success, parallel normalized-email
conflicts, invalid/malformed input, privileged fields, hashing, role-insert
rollback, safe logs and CSRF/route protection.
[RegistrationRateLimitFilterTest](../../backend/src/test/java/com/swiftdrop/security/RegistrationRateLimitFilterTest.java)
covers throttling and spoofed forwarding headers.
[RegisterPage.test.jsx](../../frontend/src/features/identity/RegisterPage.test.jsx)
covers confirmation/visibility, the three-field request, pending state, server
errors, network failure and success. See the [verification record](../verification.md)
for executed checks and remaining limitations.
