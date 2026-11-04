# SwiftDrop team setup and development guide

Audience: all nine members. Commands below use Windows PowerShell from the repository root unless stated otherwise. Replace `<REPOSITORY_URL>` and example branch names before use.

## 1. Our shared workflow

We develop one React frontend and one layered Spring Boot backend. Everyone works on a separate feature branch and submits a pull request (PR) into `dev`. The team lead integrates reviewed work into `dev`; `main` is reserved for agreed stable milestones.

```text
main                  stable milestones
  dev                 shared integration branch
    codex/m2-profile  one small feature/change
    codex/m3-quote    another independent feature/change
```

Docker uses the repository's pinned Java, Node, PostgreSQL and RabbitMQ versions. This reduces environment differences. It cannot prevent conflicting code edits, incompatible API changes, migration collisions or business-rule mistakes. We coordinate those before merging and test the combined result.

**Before inviting everyone to pull:** the lead must commit, review and push the shared foundation to `dev`. Changes present only on the lead's computer are not available to teammates. This guide does not create remote branches, configure GitHub rules or claim that CI exists.

## 2. Current setup choices and release gate

| Mode | Database used | Required host tools |
| --- | --- | --- |
| Full Docker, recommended common integration baseline | Each computer's local PostgreSQL container | Git, Docker Desktop; PowerShell for the setup helper |
| Local Java/React with a private Neon branch | That developer's Neon branch | Above plus Java 21 JDK and Node 24/npm |

Each mode still has one database and one shared `swiftdrop` application schema. Separate Neon branches are isolated development environments, not feature-specific schemas. Their users and other data are separate.

**Important current limitations:**

- Base `compose.yaml` explicitly uses local PostgreSQL. It does not load `backend/.env` or connect its backend container to Neon. Putting Neon credentials there does not change full Docker mode. A reviewed Compose override for Neon would be a separate configuration task.
- The current checkout contains newer Google OAuth wiring, while some identity documents still describe Google as future work. The full Docker environment does not yet pass Google client settings to the backend, and its Nginx configuration only proxies `/api`, not the OAuth routes. Do not treat Google-in-Docker as ready or supply invented credentials to bypass setup errors.
- The lead must verify a fresh-clone full Docker startup with the current OAuth configuration before making it the mandatory team baseline. Agree either a tested optional-Google configuration or completed Google configuration/proxy support first. Merely copying this guide does not fix that gap.

Previously recorded identity tests passed, but this documentation change has not rerun application builds, fresh-clone startup, Google login or live Neon checks. Report current failures to the lead instead of weakening security locally.

## 3. Install and check tools

Install Git, Docker Desktop and an editor. Start Docker Desktop using Linux containers. First builds need internet access and sufficient disk space for images and dependencies. Host Java, Node, Maven, PostgreSQL and RabbitMQ installations are unnecessary for full Docker execution.

```powershell
git --version
docker --version
docker compose version
docker info
```

`docker info` must show a running server. Use `docker compose`, not legacy `docker-compose`. The project's container versions are pinned in Dockerfiles and Compose. Do not independently upgrade those versions or dependency lockfiles as part of unrelated feature work.

## 4. First clone and private configuration

```powershell
git clone <REPOSITORY_URL>
cd swiftdrop
git fetch origin
git switch dev
git pull --ff-only origin dev
```

If `dev` is missing, ask the lead to publish it. Do not create a different local project history.

Copy the template only when the destination does not exist:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Fill `POSTGRES_PASSWORD` and `RABBITMQ_PASSWORD` with different randomly generated alphanumeric passwords of at least 24 characters. These are private local credentials. Keep the other defaults initially. Then generate private Compose authentication settings:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1 -Compose
docker compose config --quiet
```

The helper preserves existing non-placeholder settings and does not print the signing key. `config --quiet` validates configuration only; it does not establish that the application can start. Avoid sharing plain `docker compose config` output because expanded configuration can contain secrets.

| Private file | Purpose |
| --- | --- |
| `.env` | Local Compose passwords, ports and Compose signing settings |
| `backend/.env` | Private Neon, authentication and provider settings for the documented Maven launch |
| `frontend/.env` | Optional backend proxy address; no database/provider secrets |

Commit placeholder `.env.example` files, never real environment files. All developers use the same variable names with their own private values. Frontend `VITE_*` variables are visible to browsers and must never contain secrets.

## 5. Run and check full Docker mode

After the lead resolves and tests the release gate in section 2:

```powershell
docker compose up --build --detach --wait --wait-timeout 300
docker compose ps
```

Open `http://localhost:8080/register`. Use the same hostname throughout a session. The backend and database are accessed through the application containers; the base file only publishes the frontend on loopback.

```powershell
curl.exe -i http://localhost:8080/api/v1/health
```

Expected: HTTP 200 with `{"status":"UP","application":"swiftdrop"}`. On normal startup Flyway applies pending migrations and Hibernate validates mappings. Health reports application liveness; it does not prove every feature or RabbitMQ workflow works.

Register a new account, see success without automatic sign-in, log in at `/login`, view `/account`, reload to check session restoration, and log out. Public registration assigns `CUSTOMER`. Passwords accept 8–128 Unicode code points. No default administrator account/password is seeded. Test other roles only through an agreed safe provisioning procedure.

Useful commands:

```powershell
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
docker compose logs --tail=100 postgres rabbitmq
docker compose down
```

`down` preserves data volumes. Do not add `--volumes` during routine work. Changing passwords in `.env` does not change passwords already stored in existing database/broker volumes.

## 6. Editing and rebuilding

Edit source files in your editor on your feature branch. Current Dockerfiles copy source into images: there are no source bind mounts or live reload in full Docker mode. Rebuild changed services to see changes:

```powershell
# Backend changes, including Flyway migrations:
docker compose up --build --detach --wait backend
# Frontend changes:
docker compose up --build --detach --wait frontend
# Both, or after pulling combined changes:
docker compose up --build --detach --wait
```

The backend image build runs Maven verification (ordinary unit/security tests). The frontend image build runs `npm ci`, frontend tests and the production build. These image builds do not run the separate PostgreSQL/RabbitMQ integration profile. Read build failures; do not skip checks to produce an image.

After dependency/configuration changes, rebuild the relevant images. A simple restart does not incorporate changed source or Docker environment configuration.

## 7. Optional daily development with Neon

Use this mode when you need your assigned Neon branch or React live reload. Install Java 21 JDK and Node 24/npm matching the team policy. Use the Maven wrapper and `npm ci`. Do not run this and the full Docker application simultaneously on conflicting ports.

Create root `.env` as above, then:

```powershell
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
notepad backend/.env
```

Select your own branch in Neon Connect and fill the actual JDBC details:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_NEON_HOST:5432/YOUR_DATABASE?sslmode=require&channelBinding=require
SPRING_DATASOURCE_USERNAME=YOUR_NEON_ROLE
SPRING_DATASOURCE_PASSWORD=YOUR_NEON_PASSWORD
```

Use Java-properties syntax: `KEY=value`, no `export` or surrounding quotes. Keep credentials out of URLs. Runtime uses this connection; Flyway derives the direct Neon endpoint and checks the same destination. Optional explicit `SPRING_FLYWAY_URL/USER/PASSWORD` must target the same branch/compute/database. See [connection details](identity/schema.md).

Generate settings for both launch configurations, preserving existing private values:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1 -Compose
docker compose -f compose.yaml -f compose.dev.yaml up --detach --wait rabbitmq
```

Supply the real backend-only Google development configuration required by the current OAuth wiring through the agreed team procedure. Provider credentials, callback URLs and frontend origin must match the chosen launch mode; template placeholders do not enable Google login.

In another terminal at the repository root:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

Maven anchors the application working directory to `backend/`. Spring explicitly imports root `.env` then `backend/.env` before connection creation; backend values override matching root keys. OS variables can override both, so remove stale connection overrides when switching databases. Startup runs migrations against the selected branch.

In another terminal:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run dev
```

Open `http://127.0.0.1:5173/register`. The checked-in Vite server requires port 5173; preview uses 4173. If changing ports, coordinate exact allowed origins, proxy settings and OAuth callbacks. Do not disable CSRF or origin checks to work around a mismatch.

## 8. Start each feature and stay current

Start with a clean working tree. Commit ongoing work on its own branch before switching branches.

```powershell
git status
git switch dev
git pull --ff-only origin dev
git switch -c codex/m2-merchant-profile
```

Use one branch per small story or cohesive change, not one branch containing a member's whole semester of work. Open a draft PR early if another member depends on your contract.

Regularly bring `dev` into your feature branch, especially before review:

```powershell
git fetch origin
git merge origin/dev
```

If conflicts occur, inspect the affected files and resolve them with the other owner. Preserve both intended behaviors; do not accept every change from one side automatically. After editing, stage the resolved files explicitly, run tests, and complete the merge with `git commit`. If uncertain, `git merge --abort` cancels that attempted merge. Do not force-push shared branches.

Stage intentional files, inspect the diff and push:

```powershell
git add <FILES_YOU_CHANGED>
git diff --cached
git commit -m "Add merchant profile validation"
git push -u origin codex/m2-merchant-profile
```

Open a PR with **base `dev`**, explain behavior and tests, and request review. The feature author resolves conflicts on their branch before merge. The lead checks the combined behavior after each merge. Git reporting no conflicts does not prove the application works.

## 9. Prevent conflicts before coding

Each story owner announces its affected tables, endpoints, shared files and dependencies. Agree request/response/error contracts in `contracts/` before another member builds against them. Agree service interfaces for in-process feature calls.

Coordinate edits to `SecurityConfig`, shared routing/authentication, common UI components, `pom.xml`, `package.json`, `package-lock.json`, Dockerfiles and Compose. Keep unrelated formatting and dependency upgrades out of feature PRs. Use a separate reviewed PR for shared toolchain changes.

| Member | Ownership |
| --- | --- |
| M1 | Identity and access |
| M2 | Merchant management/approval |
| M3 | Pricing |
| M4 | Booking |
| M5 | Dispatch and courier operational profiles |
| M6 | Execution and canonical parcel status |
| M7 | Tracking |
| M8 | Notifications |
| M9 | Webhooks |

Ownership covers frontend and backend layers. It does not create separate backend applications. Read `AGENTS.md`, the assignment/v3 documents and your relevant diagrams before implementation; record disagreements in `docs/reference-review.md` rather than guessing.

## 10. Database migration coordination

Schema changes belong in `backend/src/main/resources/db/migration/`. Current migrations are V1 and V2. Reserve the next free number with the lead before writing/applying a migration, for example `V3__create_merchant_profiles.sql` only if V3 is actually free. Keep a shared reservation list in the relevant PRs/issues. Merge dependent migrations in order; finish or release reservations promptly. The filename example is not a reservation.

- Commit SQL migrations and corresponding entity/service changes in the same PR.
- Never edit or renumber an already-shared/applied migration. Add a new version for corrections.
- Do not apply another person's unmerged migration to your long-lived development database. Coordinate dependency work first.
- Do not manually alter shared tables in Neon or enable `ddl-auto: update`. Flyway changes schema; Hibernate validates it.
- Use UUID identifiers, UTC timestamps and the existing naming conventions. Preserve data and existing identity constraints.
- Pull merged migrations before starting new schema work. Pending migrations apply at backend startup on each selected database.
- If an unmerged experiment has already been applied and its version collides, stop and tell the lead. Do not hide it with Flyway repair, disabled validation or data deletion. Resolve according to which histories have been shared/applied.

## 11. Architecture and security rules for every feature

```text
React -> Spring Security -> Controller -> Service -> Repository -> PostgreSQL
                                      -> integration adapters / RabbitMQ
```

Java packages follow `com.swiftdrop.<layer>.<feature>`; React features live in `frontend/src/features/<feature>/`. Controllers validate DTOs and call services. Services own rules, transactions and resource ownership. Repositories own persistence. Return safe DTOs, not entities.

Use `/api/v1` APIs. New routes remain denied until explicit security rules and tests are added. Call other features through services, not their repositories or internal HTTP. Use trusted `IdentityContext` for identity and current roles; never trust client-supplied user IDs/roles as authorization. Each feature enforces ownership of its own records. Public signup creates CUSTOMER only.

Preserve CSRF, origin protections, safe logging, session revocation and backend-only secrets. Browser access JWTs remain in memory; refresh cookies are HttpOnly. RabbitMQ is for asynchronous integrations; coordinate event contracts, durable queues and idempotency before publishing events. Full coding rules remain in `AGENTS.md`.

## 12. Tests and PR acceptance

For the Docker build baseline:

```powershell
docker compose config --quiet
docker compose build backend frontend
docker compose up --detach --wait --wait-timeout 300
git diff --check
```

Also run real PostgreSQL/RabbitMQ integration tests. The currently documented runner requires host Java 21 and Docker, even if the app itself runs entirely in Docker:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress -Pintegration verify
```

These tests override both database connections and use disposable containers. They do not use private Neon or Compose data. Do not assume mounting the Docker socket into the backend application container is a supported test runner. The lead or a configured CI runner can run the integration suite for contributors without host Java; until CI is set up, record who ran it and against which commit.

For contributors with host Node:

```powershell
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
```

Every PR should include:

```text
Problem/story:
Resulting behavior:
API/service/event contract changes:
Migration number and data impact (or none):
Security/ownership decisions:
Tests passed (commands and results):
Tests failed or not run (with reasons):
Screenshots for UI changes:
Dependencies on other PRs and remaining limitations:
```

No real credentials, tokens or personal data in PRs, screenshots or logs. Review behavior/security tests, backward compatibility, schema changes and combined feature behavior. Do not merge failing required checks without an explicit team decision and recorded reason.

## 13. Lead's integration checklist

1. Publish a reviewed foundation on `dev`; verify it from a fresh clone using the chosen mode, including the Google release gate above.
2. Configure repository rules for `dev` and `main`: PR review, no direct pushes/force pushes, and required checks once actual CI exists. These rules are recommendations, not configured by this document.
3. Require up-to-date feature branches and coordinate migration reservations/API dependencies.
4. Review and merge one PR at a time. When `dev` advances, update/retest dependent PRs before merging them.
5. Rebuild/start the integrated application and run integration tests at the merged commit. Smoke-test affected workflows together.
6. Fix a broken `dev` promptly through a reviewed fix or coordinated revert that accounts for any applied migrations. A Git revert alone does not undo database changes.
7. Promote tested milestones from `dev` to `main` through a PR. Tag agreed demonstrations/releases.

## 14. Troubleshooting and safe stopping

| Symptom | Action |
| --- | --- |
| Docker unavailable | Start Docker Desktop, select Linux containers, check `docker info`. |
| Missing environment variable | Fill private configuration and rerun `docker compose config --quiet`; never commit real values. |
| Google/client registration startup error | Report the section 2 configuration gap; complete the agreed provider/optional-provider setup. Do not disable authentication checks. |
| Old UI/API after editing | Rebuild the changed image; current full Docker mode has no live reload. |
| Port already in use | Stop the old stack/process or coordinate a port and origin/callback configuration change. |
| Database authentication failure after editing `.env` | Existing volumes retain their original credentials. Use those credentials or arrange a deliberate rotation. |
| Flyway checksum/version failure | Stop and compare migration history with the lead; preserve applied files and data. |
| 401 | Authentication is missing/expired/revoked; inspect the intended login/session flow. |
| 403 | Check authorization, CSRF and exact origin. Never add broad `permitAll` to bypass it. |
| Dependency/image download fails | Check network access and retry; do not unpin versions or disable TLS verification. |

Stop Docker mode with `docker compose down`. Stop local Java/Vite with Ctrl+C in their terminals. Preserve database volumes and private configuration when changing branches. See [README](../README.md), [identity schema](identity/schema.md), [session guide](identity/sessions.md), and [reference decisions](reference-review.md) for more detail.
