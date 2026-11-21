# SwiftDrop

A parcel-delivery platform for customers, merchants, couriers and dispatch administrators. SwiftDrop combines booking and pricing, courier assignment, delivery execution, live tracking, notifications and merchant webhooks in one application.

**Architecture:** one React frontend, one Java/Spring Boot backend, one PostgreSQL database and a shared `swiftdrop` schema per environment. RabbitMQ supports asynchronous integrations.

[Quick start](#quick-start-docker) | [Local development](#local-development) | [Configuration](#configuration) | [Tests](#tests-and-builds) | [Documentation](#documentation) | [Troubleshooting](#troubleshooting)

> Backend startup runs Flyway against the configured database. Select your own Neon development branch and coordinate migration versions before starting against a shared environment. V13 adds profile security and saved addresses; its rollout requires team coordination. Existing password accounts require inbox verification after V9.

## Features

| Area | Available workflows |
| --- | --- |
| Identity | Registration, inbox verification, password and optional Google sign-in, rotating refresh sessions, recovery and profile editing |
| Account settings | Verified email change, password change/set-password, verification status and saved addresses |
| Customer | Delivery quotes, parcel booking, booking history/details, tracking and delivery receipts |
| Merchant | Business application/approval, orders, API credentials and webhook configuration |
| Dispatch | Courier operational profiles, booking assignment, workload monitoring and daily reporting |
| Courier | Assigned deliveries, dated worklists with unfinished carryover, status updates and delivery/failure details |
| Tracking | Public shareable tracking links and live SSE updates; signed-in tracking uses the shared portal layout |
| Notifications and integrations | Event-driven processing, notification administration, webhook delivery logs and retries; SMS uses a mock provider |
| Shared UI | Role-aware navigation, expandable icon rail, account dropdown and consistent identity/booking theme |

Source implementation does not imply deployment verification. Run the checks below before merging or deploying. Historical results in `docs/` describe their recorded revisions; recent profile/security and UI changes still need compilation, full tests and real-provider smoke checks.

## Roles and sign-in

Public registration creates **CUSTOMER only**. Password accounts verify their inbox before signing in; registration does not automatically sign users in. Email verification and connecting Google are separate actions.

| Role | Portal | How access is assigned |
| --- | --- | --- |
| `CUSTOMER` | `/booking` | Public registration or new verified Google signup |
| `MERCHANT` | `/merchant` | Merchant approval, handled by the merchant feature |
| `COURIER` | `/courier` | Administrator grant; dispatch also configures an operational courier profile |
| `DISPATCHER_ADMIN` | `/dispatch` | Guarded administrator grant or explicitly enabled private first-admin bootstrap |

An account with one role opens its portal directly. Multiple roles open `/workspaces` to choose a portal. Customers without merchant access see **Apply for merchant**. A courier role alone does not make the account eligible for assignments without its operational profile.

Administrators manage accounts and supported role grants at `/admin/users`; merchant approvals remain at `/admin/merchants`. Changes require a reason/version and revoke affected sessions. The last active administrator and self-removal are protected.

There are no seeded accounts or passwords. To establish the first administrator, register and verify an account, then follow the [private bootstrap procedure](contracts/identity-role-handoff.md). Disable bootstrap after provisioning.

## Technology

| Component | Repository version / policy |
| --- | --- |
| Java / Spring Boot | Java 21 / Spring Boot `3.5.16` |
| Maven | Wrapper-managed `3.9.11`; no global Maven installation required |
| React / Vite / Vitest | `19.3.1` / `8.3.5` / `5.0.5` |
| Node / npm | Team pins `24.22.2` / `11.19.1`; accepted engine ranges are in `frontend/package.json` |
| Database | PostgreSQL on Neon; isolated PostgreSQL containers for integration tests |
| Broker / static server | RabbitMQ `4.2.9-management-alpine` / Nginx `1.28.3-alpine` |
| Persistence | Flyway migrations; JPA validates schema and never writes it |

Use `npm ci` with the committed lockfile and the Maven wrapper. Coordinate dependency/toolchain changes with the team. Exact image pins live in the Dockerfiles and Compose configuration.

## Quick start: Docker

### 1. Prerequisites

Install Git and Docker Desktop with its Linux engine running, or Docker Engine with Compose on Linux. Use the `docker compose` command. Host Java, Node, PostgreSQL and RabbitMQ installations are unnecessary for the container setup. First builds need network access for images and dependencies.

All commands below run from the repository root in **PowerShell**. On macOS/Linux, use the shell equivalents, `npm` instead of `npm.cmd`, and `sh ./backend/mvnw` instead of `.\backend\mvnw.cmd`.

### 2. Create private configuration

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
```

Edit the files in your editor:

- **`.env`:** set a unique random `RABBITMQ_PASSWORD` of at least 24 alphanumeric characters. Keep the supplied development username/vhost or configure your own.
- **`backend/.env`:** enter your private Neon connection and real SMTP sending configuration. Leave Google disabled until its credentials and callback are configured.

Generate private Compose signing settings without replacing existing non-placeholder values:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1 -Compose
```

The helper configures signing/origin values; it does not configure Neon or SMTP. On other platforms, populate the documented `AUTH_*` settings manually and generate a cryptographically random Base64 signing key of at least 32 bytes.

### 3. Start the application

After choosing the database branch and coordinating migrations:

```powershell
docker compose config --quiet
docker compose up --build --detach --wait --wait-timeout 300
docker compose ps
```

Open **[http://localhost:8080](http://localhost:8080)**. Register, confirm the inbox link, then sign in. The default frontend port can be changed with `FRONTEND_PORT`.

Compose starts **frontend, backend and RabbitMQ**. PostgreSQL remains on Neon. Docker image builds run frontend tests/build and backend unit verification; they do **not** run the PostgreSQL/RabbitMQ integration suite.

Check liveness:

```powershell
curl.exe http://localhost:8080/api/v1/health
```

Expected: `{"status":"UP","application":"swiftdrop"}`. This is liveness, not a complete database, broker or delivery-provider health assessment. `/foundation` also provides the preserved connection-check screen.

### 4. Update or stop

```powershell
# Rebuild after source changes, including backend/profile changes.
docker compose up --build --detach --wait

# Frontend-only changes, when no backend/migration changes are needed.
docker compose up --build --no-deps --detach --wait frontend

# Inspect recent logs or stop the stack.
docker compose logs --tail=100 backend
docker compose down
```

Use **Ctrl + Shift + R** after a frontend rebuild. `down` preserves the RabbitMQ volume; it does not remove Neon data. Changing a password in `.env` does not rotate credentials already persisted in RabbitMQ.

## Local development

Use this mode for Java debugging and Vite hot reload. Install a **Java 21 JDK**, Node 24 and npm 11. Set `JAVA_HOME` to the JDK directory, not its `bin` directory.

1. Complete private configuration above. Stop the full Compose stack before switching modes.
2. Generate backend-local signing settings:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1
   ```

3. In `backend/.env`, set `RESET_FRONTEND_ORIGIN=http://127.0.0.1:5173`. If Google is enabled, set `GOOGLE_FRONTEND_ORIGIN` to the same origin and update its registered callback. Ensure `AUTH_ALLOWED_ORIGINS` includes that exact origin; the setup helper preserves existing non-placeholder values.
4. Start only RabbitMQ with localhost ports:

   ```powershell
   docker compose -f compose.yaml -f compose.dev.yaml up --detach --wait rabbitmq
   ```

5. Start Java in terminal 1, from the repository root:

   ```powershell
   .\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
   ```

6. Start React in terminal 2, from the repository root:

   ```powershell
   npm.cmd --prefix frontend ci
   npm.cmd --prefix frontend run dev
   ```

Open **[http://127.0.0.1:5173](http://127.0.0.1:5173)**. The backend listens on `8081`. Vite proxies `/api`, `/oauth2` and `/login/oauth2` to the backend; it reads only frontend configuration. Optional `frontend/.env` can set `BACKEND_URL` (default `http://localhost:8081`).

Broker management is available at `http://localhost:15672` only with the development override; use your private root `.env` credentials. Stop Java/Vite with Ctrl+C and infrastructure with:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml down
```

## Configuration

### Files and scope

| File | Contents | Loaded by |
| --- | --- | --- |
| `.env` | Broker credentials, host ports and Compose signing settings | Docker Compose; local backend also imports it |
| `backend/.env` | Neon, backend-local auth, SMTP, Google and optional bootstrap settings | Spring configuration; Compose backend `env_file` |
| `frontend/.env` | Optional `BACKEND_URL` proxy target | Vite only |

Copy the matching `.env.example` templates; never commit populated files. Backend `.env` uses Java-properties syntax: `KEY=value`, no `export` or surrounding quotes, and escaped literal backslashes. Compose environment entries override backend `env_file` values; exported environment variables can also override local Spring file configuration.

The documented Maven command anchors runtime configuration to `backend/`, importing the root `.env` before `backend/.env`. Private configuration is excluded from Docker build contexts. Never put secrets in `VITE_*` variables or frontend source.

### Neon and migrations

Select your own branch, database and role in Neon Connect. Keep credentials separate from the JDBC URL:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_ENDPOINT-pooler.YOUR_REGION.aws.neon.tech:5432/YOUR_DATABASE?sslmode=require&channelBinding=require
SPRING_DATASOURCE_USERNAME=YOUR_NEON_ROLE
SPRING_DATASOURCE_PASSWORD=YOUR_NEON_PASSWORD
```

Runtime accepts the selected pooled/direct connection. `DatabaseMigrationConfig` derives a direct Flyway connection from the same Neon endpoint by removing `-pooler`. Optional `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` belong in private `backend/.env`. The destination guard rejects a different endpoint/database/port or pooled migration URL. Do not bypass it.

Flyway is the only schema writer, and JPA uses `ddl-auto: validate`. The repository contains **V1-V13**; V13 adds account-action tokens and saved addresses. Coordinate the single migration sequence before shared rollout, never modify an applied migration, and never reuse V8 or another existing version. Coordinate SMTP before applying V9 to shared password accounts.

See the [schema guide](docs/identity/schema.md) and [migration notes](backend/src/main/resources/db/migration/README.md).

### Email and Google

- Configure `SPRING_MAIL_*` and `MAIL_FROM` privately for verification, recovery and account-security emails. **Mailpit is not used.** See [real SMTP setup](contracts/identity-password-recovery.md#real-gmail-delivery).
- `RESET_FRONTEND_ORIGIN` must match the browser entry point. Compose supplies its frontend origin automatically; local development needs its own value.
- Google is optional: configure `GOOGLE_ENABLED`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and `GOOGLE_FRONTEND_ORIGIN` on the backend. Register the exact callback `<frontend-origin>/login/oauth2/code/google` with Google.
- Google-only security changes require recent signed authentication-time metadata. Missing/stale claims fail closed; check the configured provider with the [Google OIDC reference](https://developers.google.com/identity/openid-connect/reference) and the [account-settings contract](contracts/identity-account-settings.md).

### Security model

Access JWTs stay in React memory. Refresh tokens rotate, are hashed in persistent sessions, and travel only through protected HttpOnly cookies. Protected requests check current account, session and role state. Cookie-authenticated/browser mutations retain CSRF protections.

Recovery and verification links use expiring single-use hashed tokens. Email changes keep the current email until new-inbox confirmation; successful email/password changes revoke sessions. Matching email alone never links a Google identity. Saved addresses are owner-scoped and copied into booking snapshots.

Base Compose exposes loopback HTTP for development. Deployment requires HTTPS, secure-cookie configuration, exact trusted origins and privately managed secrets. Do not treat the development Compose file as a production deployment specification.

## Tests and builds

Run these from the repository root. Use the pinned toolchain and install frontend dependencies first:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress verify
```

Backend unit verification does not require Docker. For the complete integration suite, start Docker Desktop and run:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress -Pintegration verify
```

Integration tests create disposable PostgreSQL/RabbitMQ containers and override both runtime and Flyway connections. They do not use your Neon database. Docker/image-download failures are test failures, not evidence of a passing suite.

Focused checks:

```powershell
# Shared UI, account dropdown and signed-in/public tracking.
npm.cmd --prefix frontend test -- src/components/AccountMenu.test.jsx src/components/PortalShell.test.jsx src/features/tracking/PublicTrackingPage.test.jsx

# Profile settings, address ownership, email/password changes and session revocation.
.\backend\mvnw.cmd -f backend/pom.xml -Pintegration -Dit.test=AccountSettingsIT verify
```

| Output | Location |
| --- | --- |
| Backend unit results | `backend/target/surefire-reports/` |
| Integration results | `backend/target/failsafe-reports/` |
| JaCoCo coverage | `backend/target/site/jacoco/index.html` |
| Backend JAR | `backend/target/swiftdrop-backend.jar` |
| Frontend production build | `frontend/dist/` |

Use `npm.cmd --prefix frontend run preview` to inspect a built frontend at `http://127.0.0.1:4173` with the backend running. Automated tests do not replace real inbox delivery, Google callback/reauthentication, desktop/mobile visual checks or the assignment demonstration.

## Project layout

```text
backend/
  src/main/java/com/swiftdrop/<layer>/<feature>/
  src/main/resources/db/migration/
  src/test/
frontend/
  src/app/          # Routing, theme tokens and in-memory navigation
  src/components/   # Brand, portal shell, sidebar and account dropdown
  src/features/     # Identity and domain screens/API clients
contracts/          # API and cross-feature service contracts
docs/               # Onboarding, schema, UI and verification guides
  reference/        # Preserved assignment, design PDF and diagrams
scripts/            # Local setup helpers
compose.yaml        # Frontend + backend + RabbitMQ; Neon remains external
compose.dev.yaml    # Optional localhost broker ports
```

Requests flow **React -> Controller -> Service -> Repository -> PostgreSQL**. Services own business rules, resource authorization and transactions. Features call each other through service contracts, not repositories or internal HTTP.

Feature ownership: **M1** identity, **M2** merchant, **M3** pricing, **M4** booking, **M5** dispatch, **M6** execution, **M7** tracking, **M8** notification, **M9** webhook. These are modules in one backend, not separate services/databases.

## Documentation

| Topic | Guide |
| --- | --- |
| Team setup and first contribution | [Team onboarding](docs/team-onboarding.md), [development guide](docs/team-development-guide.md) |
| Architecture, workflows and demo order | [Integrated system guide](docs/full-system-implementation.md) |
| Identity integration and trusted actor context | [Identity service handoff](contracts/identity-services.md) |
| Roles, bootstrap, merchant/courier onboarding | [Role workflows](docs/identity/role-workflows.md), [role handoff](contracts/identity-role-handoff.md) |
| Role/ownership requirements | [Assignment RBAC matrix](docs/identity/rbac-assignment-review.md) |
| Profile, security changes and saved addresses | [Profile contract](contracts/identity-profile.md), [account settings](contracts/identity-account-settings.md) |
| Authentication and email verification | [Sessions](docs/identity/sessions.md), [verification rollout](contracts/identity-email-verification.md) |
| Merchant APIs and events | [Integration guide](docs/merchant-integration-guide.md), [OpenAPI](contracts/merchant-openapi.yaml), [webhook events](contracts/webhook-events.md) |
| UI conventions | [Shared UI guidelines](docs/frontend/ui-guidelines.md) |
| Requirement provenance and conflicts | [Reference review](docs/reference-review.md), [original references](docs/reference/) |
| Historical verification evidence | [System verification](docs/full-system-verification.md), [foundation verification](docs/verification.md) |

## Contributing

Read [AGENTS.md](AGENTS.md), the [reference review](docs/reference-review.md) and relevant contracts before changing code. Work on small feature branches and submit peer-reviewed PRs into `dev`.

- Keep authorization and transactions in services; validate DTOs and return safe responses.
- Coordinate migrations and feature contracts with their owners.
- Pin direct npm dependencies and commit lockfile changes; use the Maven wrapper.
- Include meaningful behavior/security tests and report passed, failed and unrun checks separately.
- Keep secrets, tokens and personal data out of commits, logs and issue reports.

The assignment defines assessed requirements; agreed implementation decisions and recorded conflicts are documented separately. Assignment completion requires the team's validation, evidence and demonstration, not just available source code.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Docker daemon unavailable | Start Docker Desktop, wait for its Linux engine and check `docker info`. |
| PowerShell blocks `npm.ps1` | Use `npm.cmd`; no execution-policy change is needed. |
| Wrong Java version | Point `JAVA_HOME` at a Java 21 JDK and place its `bin` first in `PATH`; check `javac -version`. |
| Compose reports missing values | Populate the private templates, run the appropriate auth helper, then `docker compose config --quiet`. |
| Backend exits or frontend waits indefinitely | Read `docker compose logs --tail=100 backend`; check Neon credentials, migration validation and RabbitMQ authentication. |
| RabbitMQ authentication fails after an `.env` change | Rotate the existing broker credentials explicitly; editing `.env` does not reset its persisted volume. |
| Wrong Neon branch or Flyway destination error | Check both datasource/Flyway values and stale exported overrides. Keep the same endpoint/database and direct migration connection. |
| Flyway checksum mismatch | Restore the original applied migration; add a new migration. Do not disable validation. |
| Verification/reset email is missing | Check real SMTP configuration and backend warnings. Google OAuth credentials are separate from SMTP credentials. |
| Google callback or security verification fails | Match exact browser/callback origins, enable the provider, and verify credentials and authentication-time metadata. |
| Protected API returns 401/403 | Check session validity and assigned roles. Verification, suspension and role changes can end sessions; unsafe requests need CSRF. |
| UI still looks unchanged | Rebuild the correct service(s), then hard-refresh the browser. Backend/profile changes require a backend rebuild too. |
| Port already in use | Change host ports as appropriate; keep proxy targets, trusted origins and Google callbacks aligned. |
| Dependency or image download fails | Check network/proxy access to registries, retry downloads and rerun the failed checks. Do not disable TLS validation. |
