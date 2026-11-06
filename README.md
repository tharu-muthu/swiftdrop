# SwiftDrop

Teammates: start with the [identity integration handoff](contracts/identity-services.md)
before adding authenticated feature endpoints or calling identity services.
Use the [team quick start](docs/team-onboarding.md) for Docker setup, private
configuration and the first feature PR.

Team onboarding and collaboration: see [the team development guide](docs/team-development-guide.md)
for Docker setup, private configuration, feature branches, PRs into `dev`, migration
coordination and the current Google/Docker readiness limitations.

Customer registration, password login/logout, rotating refresh sessions and safe
profile reads now work end to end. Open `/register`, `/login` and `/account`.
See [authentication setup and request flow](docs/identity/sessions.md) and the
[current API contract](contracts/identity-sessions.md). Google, recovery, admin
and other business features remain future work.

**New setup step:** from the root run
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1`
before starting the backend. This adds private signing/origin settings while
preserving Neon credentials. For full Compose use `-Compose` instead.

## Architecture and folders

```text
Browser (React) -> /api proxy -> Spring Security -> Controller -> Service
                                                               -> Repository -> PostgreSQL
                               One Java application            -> RabbitMQ (future async work)
```

Controllers handle HTTP, services apply business rules, and repositories access
the database. All Java layers run in **one backend application**. All nine members
share **one PostgreSQL database and one application schema per environment**, named
`swiftdrop`. Each developer can use their own Neon development branch with the same
application schema and Flyway migrations; features still share one database.
The health service is a liveness response and needs no repository or business table.

| Folder / file | What it does |
| --- | --- |
| `frontend/` | React browser app, Vite development server, npm dependencies and tests; Docker builds static files served by Nginx. |
| `frontend/src/app/` | Page entry point, preserved foundation page and shared styles. |
| `frontend/src/features/` | Feature screens and API calls; `identity/` implements registration. |
| `frontend/src/components/` | Reusable auth layout, brand and form fields. |
| `backend/` | Single Spring Boot Maven project and its Windows/Linux/macOS Maven wrappers. |
| `backend/src/main/java/com/swiftdrop/` | `controller`, `service`, `repository`, `entity`, `dto`, `security`, `config`, `exception`, `integration` layers; add feature subpackages as needed. |
| `backend/src/main/resources/` | Environment-based configuration and `db/migration/` Flyway SQL. |
| `backend/src/test/` | HTTP/security tests and optional real PostgreSQL/RabbitMQ integration tests. |
| `contracts/` | Health and registration API contracts; future OpenAPI/event schemas. |
| `docs/reference/` | Original design PDF, diagrams and screen references, preserved unchanged. |
| `docs/reference-review.md` | Conflicts, source provenance, scope decisions and unresolved team questions. |
| `docs/identity/` | IAM checklist, registration guide, ER/table guide and connection rules. |
| `docs/verification.md` | Actual environment and checks from this scaffold run. |
| `compose.yaml` | Three containers: frontend, backend and RabbitMQ; the backend connects to Neon. |
| `compose.dev.yaml` | Optional localhost ports for connecting locally running Java to RabbitMQ. |
| `AGENTS.md` | Short architecture and coding rules for the team and coding assistants. |
| `.env.example` | Root template for RabbitMQ and app ports; real `.env` files are ignored. |
| `backend/.env.example` | Shared variable names and placeholders for each developer's private Neon connection. |
| `frontend/.env.example` | Optional proxy URL only; never database credentials. |

Feature ownership: M1 identity, M2 merchant, M3 pricing, M4 booking, M5 dispatch,
M6 execution, M7 tracking, M8 notification, M9 webhook. Ownership is across layers;
it does not mean nine deployed backend applications.

## Prerequisites and pinned versions

For the easiest all-container setup, install **Git and Docker Desktop with its
Linux engine running** (or Docker Engine + Compose on Linux). You do not need host
Java, Node, Maven, PostgreSQL or RabbitMQ for that option. Internet access is needed
for the first image and dependency downloads. Use Compose v2.20+ or v5, with
`docker compose` (space), not the old `docker-compose` command.

For editing with automatic React reload, also install a Java 21 **JDK** and Node 24.
`JAVA_HOME` must point to the JDK folder, not its `bin` folder. Global Maven is
optional because the wrapper downloads the pinned version.

| Component | Pin / policy |
| --- | --- |
| Java | Language target 21; container Temurin `21.0.13_9` on Ubuntu Jammy. Recommend current team pin 21.0.13 locally. |
| Spring Boot | `3.5.16`; its parent manages Spring Security, JPA, Validation, AMQP, Flyway, JDBC driver and test dependency versions together. |
| Maven / wrapper | Maven `3.9.11`, official script-only wrapper `3.3.4`; no wrapper JAR is needed. |
| Node / npm | Team pin `24.22.2` / `11.19.1`; `.nvmrc`, `.node-version`, packageManager and Dockerfile record it. Existing Node 24.11+/npm 11.6.1+ within those majors is accepted. |
| React / React DOM | `19.3.1` |
| Vite / React plugin | `8.3.5` / `6.1.2` |
| Vitest | `5.0.5` |
| PostgreSQL | Neon-hosted PostgreSQL; each developer uses a separate Neon branch. |
| RabbitMQ | `4.2.9-management-alpine` |
| Nginx | `1.28.3-alpine` |

Direct npm dependencies are exact pins; `package-lock.json` fixes the complete
dependency tree. Use `npm ci` after cloning. Container tags pin versions rather
than immutable image digests. Update pins and lockfiles together after review.

Check tools in PowerShell:

```powershell
git --version
docker --version
docker compose version
docker info
# Only needed for local Java/React development:
java -version
javac -version
node --version
npm.cmd --version
```

`docker info` must show a Server section. If it says the Linux engine pipe is
missing, open Docker Desktop and wait until the engine is running.

## 1. Create local configuration

The root file configures RabbitMQ and app ports. Every developer must also put
their private Neon connection in `backend/.env` as described in **2A**.

Open a terminal in the repository root (the folder containing `compose.yaml`).
In PowerShell:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

On macOS/Linux:

```bash
test -f .env || cp .env.example .env
# Open .env in your text editor.
```

Set `RABBITMQ_PASSWORD` to a random alphanumeric password of at least 24
characters. Do not use real university/service passwords. Keep plain values
without quotes, spaces or `$`. Never commit `.env`; never put credentials in
`VITE_*` variables, which would expose them to browsers.

The database is hosted in Neon; no local PostgreSQL container or password is
needed. No Google client ID, JWT key, mail account or merchant key is needed yet.

## 2A. Run the application with Neon and RabbitMQ

First create the private Neon configuration:

```powershell
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
notepad backend/.env
```

In Neon **Connect**, select your own development branch, database and role. Fill
the three `SPRING_DATASOURCE_*` values in `backend/.env`; keep credentials out
of the JDBC URL. The pooled runtime URL is converted to the matching direct
endpoint for Flyway. Then, from the repository root:

From the repository root, in PowerShell or Bash:

```text
docker compose config --quiet
docker compose up --build --detach --wait --wait-timeout 300
docker compose ps
```

The first build downloads several large images and may take a long time on a slow
connection. It runs frontend tests/build and backend unit/security tests as part
of building the images. Compose starts RabbitMQ, waits for it, then starts the
backend (connected to Neon) and frontend.

Open **[http://localhost:8080/register](http://localhost:8080/register)**. You should
see the SwiftDrop registration form. A valid submission shows **“Account created”**
without signing you in. The original health/retry page is at `/foundation`; it
shows **“Backend connected”** when the backend is reachable.

Check HTTP responses from another PowerShell terminal:

```powershell
curl.exe -i http://localhost:8080/api/v1/health
curl.exe -i http://localhost:8080/api/v1/private
```

Use `curl` instead of `curl.exe` on macOS/Linux. Expected: health returns HTTP 200
with `{"status":"UP","application":"swiftdrop"}`; the private URL returns HTTP 401.
POST without a CSRF token returns 403. React obtains that token automatically for
registration. Login is available at `/login`; registration does not sign you in.

Flyway runs against the selected Neon branch during backend startup. Verify its
history in the Neon SQL editor, for example:

```sql
SELECT version, description, success
FROM swiftdrop.flyway_schema_history
ORDER BY installed_rank;
```

Expect successful V1 through V4 entries. Health reports **application liveness**; it
does not probe broker availability or assert that business integrations work.

Useful commands:

```text
docker compose logs --tail=100 backend
docker compose logs --tail=100 rabbitmq
docker compose logs --follow backend
# Ctrl+C exits log following without stopping containers.
docker compose down
```

`down` preserves the RabbitMQ volume. Neon data is managed by Neon, not Docker.
Changing `.env` passwords later does not change credentials already stored in the
RabbitMQ volume; rotate those explicitly.

## 2B. Run React and Java locally (daily development)

Choose either 2A or 2B. Stop the full stack with `docker compose down` before
switching modes. Start only RabbitMQ, exposing its localhost ports:

```text
docker compose -f compose.yaml -f compose.dev.yaml up --detach --wait rabbitmq
```

In **terminal 1**, from the repository root, PowerShell:

```powershell
cd backend
.\mvnw.cmd --version
.\mvnw.cmd spring-boot:run
```

On macOS/Linux use `sh ./mvnw --version` and `sh ./mvnw spring-boot:run`.
The backend explicitly imports the root `.env`, then `backend/.env`, and listens
on port **8081**. You should see Flyway migration/validation output against Neon
and `Started SwiftDropApplication`.

In **terminal 2**, from the repository root, PowerShell:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

On macOS/Linux use `npm ci` and `npm run dev`. Open
**[http://127.0.0.1:5173](http://127.0.0.1:5173)**. Vite forwards `/api/...` to
`BACKEND_URL` from optional `frontend/.env` (default `http://localhost:8081`),
retaining the full path. Vite does not read root or backend `.env` files. Browser
requests use a relative URL, so no development CORS exception is needed.
If you change `BACKEND_PORT`, update `BACKEND_URL` in `frontend/.env` too and restart
Vite. An old root-level `BACKEND_URL` is no longer used by Vite.

The optional broker management page is
[http://localhost:15672](http://localhost:15672); use your `.env` RabbitMQ username
and password. No PostgreSQL port is published or required.

Stop Java and Vite with Ctrl+C in their terminals, then stop infrastructure:

```text
docker compose -f compose.yaml -f compose.dev.yaml down
```

## 2C. Neon connection details

Keep the same variable names for every developer. Only their values change.
Complete step 1 for local RabbitMQ configuration, then, from the repository root:

```powershell
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
notepad backend/.env
```

In Neon **Connect**, select your own development branch, database and role. Choose
the Java/JDBC format. Runtime can use a pooled or direct connection; Flyway uses a
separate **direct** connection to the same endpoint/database.
Fill all three placeholders in `backend/.env`:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_ENDPOINT-pooler.YOUR_REGION.aws.neon.tech:5432/YOUR_DATABASE?sslmode=require&channelBinding=require
SPRING_DATASOURCE_USERNAME=YOUR_NEON_ROLE
SPRING_DATASOURCE_PASSWORD=YOUR_NEON_PASSWORD
```

Use the hostname displayed for the selected branch; do not construct it from a
branch name. Keep `sslmode=require&channelBinding=require` (the JDBC spelling is
`channelBinding`). Put the role and password in their separate variables, not in
the URL. These parameters follow [Neon's Java connection guidance](https://neon.com/docs/guides/java).
This file is loaded as Java properties: use `KEY=value`, no `export`, no surrounding
quotes; double a literal backslash. Keep passwords private and out of frontend
files, `VITE_*` variables, commits, and command-line arguments.

The setup changes do not overwrite existing private files.

Start the broker from the repository root (no local PostgreSQL is needed for Neon):

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up --detach --wait rabbitmq
```

**Exact backend startup command**, from the repository root:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

On macOS/Linux: `cd backend` then `sh ./mvnw spring-boot:run`. A root-directory
alternative on Windows is `.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run`.
The Maven plugin explicitly sets its working directory to `backend/`.
`application.yaml` explicitly imports `../.env[.properties]`, then
`./.env[.properties]`, **before datasource creation and Flyway migrations**. Thus
backend values override matching root file keys. There is no assumption that Maven
or the operating system automatically sources `.env`, and no dotenv dependency.
Exported OS variables and command-line properties can override file values; clear
stale `SPRING_DATASOURCE_*` and `SPRING_FLYWAY_*` overrides if the wrong branch is selected.

JPA/runtime queries use the application DataSource. `DatabaseMigrationConfig`
derives Flyway's direct URL by removing `-pooler` only from a Neon hostname, while
keeping the same database, port and TLS parameters. Direct/runtime and test fallback PostgreSQL URLs are unchanged. Migration credentials default to the runtime role
and password. This follows [Neon's pooled/direct endpoint convention](https://neon.com/docs/connect/connection-pooling).

Optional explicit overrides belong in the same private `backend/.env`:

```properties
SPRING_FLYWAY_URL=jdbc:postgresql://YOUR_ENDPOINT.YOUR_REGION.aws.neon.tech:5432/YOUR_DATABASE?sslmode=require&channelBinding=require
SPRING_FLYWAY_USER=YOUR_MIGRATION_ROLE
SPRING_FLYWAY_PASSWORD=YOUR_MIGRATION_PASSWORD
```

Omit these overrides to use the derived direct URL and existing credentials.
If specifying a separate migration role, supply its matching password and ensure
the runtime role has the required table/schema grants. The destination guard
rejects a pooled migration URL or a different endpoint/database/port before
migrating; use the same branch/compute's pooled/direct pair. Never embed passwords
in URLs. The migration role needs schema/DDL privileges. The backend runs Flyway
automatically during startup, so choose your development branch before launching.
See [the identity schema guide](docs/identity/schema.md) for guard limits and exact
environment loading; private files are preserved.

Expected startup: Flyway applies/validates V1 through V4, Hibernate validates the
identity and webhook tables, followed by `Started SwiftDropApplication`.
Check it with `curl.exe http://localhost:8081/api/v1/health`; expect
`{"status":"UP","application":"swiftdrop"}`. Run the frontend as shown in 2B;
it communicates only with the backend API and needs no Neon information.

Compose loads `backend/.env` at runtime; it is excluded from Docker builds and is
not copied into the backend image.

## 3. Run tests and builds

From the repository root, PowerShell:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
cd ../backend
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Frontend tests cover registration validation, confirmation, visibility, pending
state, API errors/success and the preserved health page. Backend unit tests cover
service rules, validation, password encoding, throttling, security and configuration.
These tests do not need a running database, broker or Docker engine.

With Docker running, from `backend/`:

```powershell
# Optional first-time prefetch; useful on slow connections:
docker pull rabbitmq:4.2.9-management-alpine
.\mvnw.cmd --batch-mode --no-transfer-progress -Pintegration verify
```

On macOS/Linux, use `sh ./mvnw` in place of `.\mvnw.cmd`. The integration profile
starts disposable PostgreSQL and RabbitMQ containers on random ports, verifies
Flyway on a fresh real database and on a second migration run, checks actual HTTP
security, and round-trips an infrastructure-only broker message. It also checks
V1-to-V2 data preservation, identity constraints, token rotation races, audit
protection and Hibernate mappings. Registration tests cover real HTTP/database
success, duplicate races, injection, safe errors/logs and transaction rollback.
Both connection configurations are overridden:
it does not touch Neon or the Compose database, and it is not a login or domain
event/notification implementation.
It fails if Docker is unavailable; it does not silently report a skipped suite as a pass.

Reports appear in `backend/target/surefire-reports/`, integration reports in
`backend/target/failsafe-reports/`, and JaCoCo coverage in
`backend/target/site/jacoco/index.html`. Coverage measures only implemented code;
it says nothing about unimplemented assignment features. No mandated percentage
was found in the brief. Build outputs are `frontend/dist/` and
`backend/target/swiftdrop-backend.jar`.

To inspect the production frontend build locally while the backend is running:

```powershell
cd frontend
npm.cmd run preview
```

Open [http://127.0.0.1:4173](http://127.0.0.1:4173); the preview also proxies `/api`.
Nginx serves the built page and proxies API requests in Docker.

## Troubleshooting

| Symptom | What to do |
| --- | --- |
| `java` hangs or resolves the wrong JDK | Set `JAVA_HOME` to your Java 21 installation and put its `bin` first in PATH. For the inspected Windows machine: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'` then `$env:Path="$env:JAVA_HOME\bin;$env:Path"`. Check `javac -version`. |
| PowerShell blocks `npm.ps1` | Use the documented `npm.cmd` commands; no execution-policy change is needed. |
| Wrapper/dependency download fails | Check internet/proxy access to Maven Central and npm. Retry the same command. Do not disable TLS verification. |
| Windows build cannot rename `swiftdrop-backend.jar` | Stop the Java process running that JAR before packaging again. `spring-boot:run` uses compiled classes and avoids running from the packaged JAR. |
| Docker image downloads fail or integration tests report `ContainerFetchException` | Ensure Docker Desktop can reach Docker Hub and its CDN (`production.cloudfront.docker.com`). Retry the image pulls above after connectivity recovers, then rerun the check. A failed image pull is not a passed or skipped integration test. |
| Compose says a password is missing | Fill both blank values in root `.env`; run `docker compose config --quiet`. |
| DB authentication fails | Check `.env` against the existing volume's credentials; changing the file does not reset the volume. |
| Neon connection fails or uses the wrong branch | Check the runtime values and any optional Flyway overrides in private `backend/.env`. Use the same branch/compute/database, a direct Flyway endpoint, TLS parameters and suitable grants. Clear stale exported overrides. Restart after editing; never paste credentials into logs/issues. |
| Port already in use | Change the relevant host port in `.env`. For local backend changes, keep `BACKEND_URL` aligned. |
| React says backend unavailable | Check backend logs and health URL; ensure correct startup mode, `.env`, DB connectivity and proxy target. |
| Flyway checksum mismatch | Restore the original applied migration; add a new migration for changes. Do not disable validation. |
| A protected URL returns 401/403 | Expected. Health/CSRF and implemented auth entry points are public; auth POSTs require CSRF and an allowed Origin. GET /me requires a valid bearer identity. |

## Before feature work

Read [the reference review](docs/reference-review.md) and [coding rules](AGENTS.md).
Coordinate one ordered Flyway history, feature DTOs and service contracts. Keep
business authorization in services as features arrive. The detailed v3 ER views
are the design baseline; the implemented M1 model is in [the identity ER guide](docs/identity/schema.md).
The standalone ER PNG has recorded conflicts. V2 is used by identity; coordinate
V5 or the next free migration number before merging another schema change.

The team still needs lecturer approval for nine rather than ten members, member
names/IDs, deadline and hosting decisions. The assignment found beside this
repository was copied unchanged into `docs/reference/` for teammates. Later
Google, JWT, provider and deployment secrets belong to their feature/deployment
tasks. This repository foundation does not fulfill the complete assignment yet.
