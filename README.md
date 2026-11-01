# SwiftDrop

Shared foundation for a nine-member university courier management project.
The application currently shows a React connection-check page and a public backend
health endpoint. Sign-in, Google authentication, bookings and other business
features are intentionally not implemented.

## Architecture and folders

```text
Browser (React) -> /api proxy -> Spring Security -> Controller -> Service
                                                               -> Repository -> PostgreSQL
                               One Java application            -> RabbitMQ (future async work)
```

Controllers handle HTTP, services apply business rules, and repositories access
the database. All Java layers run in **one backend application**. All nine members
share **one PostgreSQL database and one application schema**, named `swiftdrop`.
The health service is a liveness response and needs no repository or business table.

| Folder / file | What it does |
| --- | --- |
| `frontend/` | React browser app, Vite development server, npm dependencies and tests; Docker builds static files served by Nginx. |
| `frontend/src/app/` | Minimal shared page and styles. |
| `frontend/src/features/` | Future feature screens and API calls, one area per member. |
| `backend/` | Single Spring Boot Maven project and its Windows/Linux/macOS Maven wrappers. |
| `backend/src/main/java/com/swiftdrop/` | `controller`, `service`, `repository`, `entity`, `dto`, `security`, `config`, `exception`, `integration` layers; add feature subpackages as needed. |
| `backend/src/main/resources/` | Environment-based configuration and `db/migration/` Flyway SQL. |
| `backend/src/test/` | HTTP/security tests and optional real PostgreSQL/RabbitMQ integration tests. |
| `contracts/` | Current health contract; future agreed OpenAPI and event schemas. |
| `docs/reference/` | Original design PDF, diagrams and screen references, preserved unchanged. |
| `docs/reference-review.md` | Conflicts, source provenance, scope decisions and unresolved team questions. |
| `docs/verification.md` | Actual environment and checks from this scaffold run. |
| `compose.yaml` | Four containers: frontend, backend, PostgreSQL, RabbitMQ; persistent data volumes. |
| `compose.dev.yaml` | Optional localhost ports for connecting locally running Java to container infrastructure. |
| `AGENTS.md` | Short architecture and coding rules for the team and coding assistants. |
| `.env.example` | Safe configuration template; `.env` is local and ignored by Git. |

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
| PostgreSQL | `17.11-bookworm` container (independent of any PostgreSQL already installed on your computer). |
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

Set the two blank values `POSTGRES_PASSWORD` and `RABBITMQ_PASSWORD` to **different,
random alphanumeric passwords**, at least 24 characters each. Leave the other
values unchanged initially. Do not use real university/service passwords. Keep
plain values without quotes, spaces or `$`: the local backend also reads this
file as Java properties. Never commit `.env`; never put credentials in `VITE_*`
variables, which would expose them to browsers.

This setup session created an ignored `.env` with random local passwords in the
current checkout. Keep it; a fresh clone needs the steps above. No Google client
ID, JWT key, mail account or merchant key is needed yet.

## 2A. Run everything in Docker (easiest first check)

From the repository root, in PowerShell or Bash:

```text
docker compose config --quiet
docker compose up --build --detach --wait --wait-timeout 300
docker compose ps
```

The first build downloads several large images and may take a long time on a slow
connection. It runs frontend tests/build and backend
unit/security tests as part of building the images. Compose starts PostgreSQL and
RabbitMQ first, waits for them, then starts the backend and frontend.

Open **[http://localhost:8080](http://localhost:8080)**. You should see “SwiftDrop”,
“One foundation. Built together.” and **“Backend connected”**. “Check again” repeats
the health request. The page should show “Backend unavailable” if it cannot reach
the backend, rather than pretending the connection works.

Check HTTP responses from another PowerShell terminal:

```powershell
curl.exe -i http://localhost:8080/api/v1/health
curl.exe -i http://localhost:8080/api/v1/private
```

Use `curl` instead of `curl.exe` on macOS/Linux. Expected: health returns HTTP 200
with `{"status":"UP","application":"swiftdrop"}`; the private URL returns HTTP 401.
POST without a CSRF token returns 403. There is no login page or account to use yet.

Check that Flyway ran (default database/user names):

```text
docker compose exec postgres psql -U swiftdrop -d swiftdrop -c "SELECT version, description, success FROM swiftdrop.flyway_schema_history ORDER BY installed_rank;"
```

Expect successful V1 `initialize shared schema` (Flyway may also record its schema
creation row). There are no business tables yet. Normal backend startup requires
PostgreSQL and a successful migration. Health reports **application liveness**;
it does not probe broker availability or assert that business integrations work.

Useful commands:

```text
docker compose logs --tail=100 backend
docker compose logs --tail=100 postgres rabbitmq
docker compose logs --follow backend
# Ctrl+C exits log following without stopping containers.
docker compose down
```

`down` preserves database/broker volumes. Do not add `--volumes` unless you intend
to erase local data. Changing `.env` passwords later does not change credentials
already stored in existing database/broker volumes; rotate those explicitly.

## 2B. Run React and Java locally (daily development)

Choose either 2A or 2B. Stop the full stack with `docker compose down` before
switching modes. Start only the infrastructure, exposing localhost ports:

```text
docker compose -f compose.yaml -f compose.dev.yaml up --detach --wait postgres rabbitmq
```

In **terminal 1**, from the repository root, PowerShell:

```powershell
cd backend
.\mvnw.cmd --version
.\mvnw.cmd spring-boot:run
```

On macOS/Linux use `sh ./mvnw --version` and `sh ./mvnw spring-boot:run`.
The backend reads the root `.env` automatically and listens on port **8081**.
You should see Flyway migration/validation output and `Started SwiftDropApplication`.

In **terminal 2**, from the repository root, PowerShell:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

On macOS/Linux use `npm ci` and `npm run dev`. Open
**[http://127.0.0.1:5173](http://127.0.0.1:5173)**. Vite forwards `/api/...` to
`BACKEND_URL` (default `http://localhost:8081`), retaining the full path. Browser
requests use a relative URL, so no development CORS exception is needed.
If you change `BACKEND_PORT`, update `BACKEND_URL` too and restart Vite.

The optional broker management page is
[http://localhost:15672](http://localhost:15672); use your `.env` RabbitMQ username
and password. PostgreSQL is available at `localhost:5433`, database/user
`swiftdrop`. This avoids the usual `5432` port of an existing PostgreSQL installation.
These infrastructure ports are not published by the base Compose file.

Stop Java and Vite with Ctrl+C in their terminals, then stop infrastructure:

```text
docker compose -f compose.yaml -f compose.dev.yaml down
```

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

The frontend tests exercise connection success, failure, retry and bad responses.
The backend tests exercise public health, protected routes, CSRF and denied writes.
These tests do not need a running database, broker or Docker engine.

With Docker running, from `backend/`:

```powershell
# Optional first-time prefetch; useful on slow connections:
docker pull postgres:17.11-bookworm
docker pull rabbitmq:4.2.9-management-alpine
.\mvnw.cmd --batch-mode --no-transfer-progress -Pintegration verify
```

On macOS/Linux, use `sh ./mvnw` in place of `.\mvnw.cmd`. The integration profile
starts disposable PostgreSQL and RabbitMQ containers on random ports, verifies
Flyway on a fresh real database and on a second migration run, checks actual HTTP
security, and round-trips an infrastructure-only broker message. It does not touch
the Compose database and is not a domain event or notification implementation.
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
| Docker image downloads fail or integration tests report `ContainerFetchException` | Ensure Docker Desktop can reach Docker Hub and its CDN (`production.cloudfront.docker.com`). Retry the image pulls above after connectivity recovers, then rerun the check. A failed image pull is not a passed or skipped integration test. |
| Compose says a password is missing | Fill both blank values in root `.env`; run `docker compose config --quiet`. |
| DB authentication fails | Check `.env` against the existing volume's credentials; changing the file does not reset the volume. |
| Port already in use | Change the relevant host port in `.env`. For local backend changes, keep `BACKEND_URL` aligned. |
| React says backend unavailable | Check backend logs and health URL; ensure correct startup mode, `.env`, DB connectivity and proxy target. |
| Flyway checksum mismatch | Restore the original applied migration; add a new migration for changes. Do not disable validation. |
| A protected URL returns 401/403 | Expected. Only GET health is public and no authentication mechanism exists yet. |

## Before feature work

Read [the reference review](docs/reference-review.md) and [coding rules](AGENTS.md).
Coordinate one ordered Flyway history, feature DTOs and service contracts. Keep
business authorization in services as features arrive. The detailed v3 ER views
are the future data model baseline; the standalone ER PNG has recorded conflicts.

The team still needs lecturer approval for nine rather than ten members, member
names/IDs, deadline and hosting decisions. The assignment found beside this
repository was copied unchanged into `docs/reference/` for teammates. Later
Google, JWT, provider and deployment secrets belong to their feature/deployment
tasks. This repository foundation does not fulfill the complete assignment yet.
