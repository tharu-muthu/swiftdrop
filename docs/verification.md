# Foundation verification

Checked on 1 November 2026 on the supplied Windows development machine. These
results apply to the foundation only, not to the unimplemented assignment features.

## Development tools inspected

| Tool | Available version / result |
| --- | --- |
| Git | 2.51.0.windows.1 |
| Java / javac | Oracle JDK 21.0.9, `C:\Program Files\Java\jdk-21`; compatible with Java target 21. |
| Maven | Global 3.9.11 available; the project wrapper independently selects 3.9.11. |
| Node / npm | 24.11.0 / 11.6.1; accepted by the project's engines; older patches than the recommended 24.22.2 / 11.19.1 pins. |
| Docker / Compose | Engine and CLI 29.2.0 / Compose 5.0.2. Docker Desktop was initially stopped; its Linux engine was started successfully. |
| PostgreSQL | Host PostgreSQL 18.1 binaries available. Project containers use 17.11 instead. |

No additional host compiler, package manager or database installation is required
for the Docker route. Local development already has the required Java and Node
major versions. Adopt the documented team patch pins when updating local tools.
RabbitMQ does not need a host installation because Compose supplies it.

The `java` command through the Oracle PATH shim hung during inspection; explicitly
using `JAVA_HOME\bin\java.exe` worked. The README explains putting the JDK's `bin`
first in PATH. Internet access is still needed for uncached dependencies/images.

## Passed checks

| Check | Observed result |
| --- | --- |
| Reference inspection | Read the four-page assignment, all 38 v3 pages, both standalone diagrams, all 18 use-case/activity/sequence images and all three Figma screen images before scaffolding. |
| Preservation | Original references preserved; the assignment beside the repository was copied into `docs/reference/`. Original and copy SHA-256 matched. |
| Maven wrapper | Official script-only wrapper generated; wrapper version 3.3.4, Maven distribution 3.9.11. Unix script has LF line endings. |
| Frontend clean install | `npm.cmd ci` succeeded with the repository lockfile; npm reported zero known vulnerabilities at install time. |
| Frontend tests | `npm.cmd test`: 4 passed, covering success, failure/retry, HTTP error and malformed health payload. |
| Frontend production build | `npm.cmd run build` succeeded and produced `frontend/dist/`. |
| Backend build/tests | `mvnw.cmd --batch-mode --no-transfer-progress verify` succeeded; 8 security/HTTP tests passed; executable JAR produced. |
| Backend integration suite | After image prefetch, `mvnw.cmd --batch-mode --no-transfer-progress -Pintegration verify` succeeded: 8 unit/security tests plus all 3 integration tests passed, zero skipped. Verified real PostgreSQL 17.11 migrations/revalidation, real HTTP access rules and a RabbitMQ 4.2.9 publish/receive round trip. |
| Coverage report | JaCoCo generated; 31 of 33 implemented source lines covered (93.94%) by the unit/security suite. This is not coverage of future business requirements. |
| Compose syntax | `docker compose config --quiet` passed for both the base file and the base + development override. |
| Compose infrastructure | Pinned PostgreSQL 17.11 and RabbitMQ 4.2.9 images downloaded. Both services started with the development override and reached healthy status on the documented localhost ports. |
| Normal local configuration | Packaged backend started using the root `.env` against Compose PostgreSQL 17.11 on port 5433. V1 applied successfully; Vite's proxy returned HTTP 200 with the expected payload. |
| Local backend startup | Packaged JAR started with Java 21.0.9 against an isolated PostgreSQL 18.1 test cluster on loopback port 55433. Existing PostgreSQL data was not used. |
| Flyway startup/restart | Fresh startup created `swiftdrop` and applied V1. Restart validated history without reapplying V1. SQL inspection found only `flyway_schema_history` in the application schema. |
| Actual HTTP security | GET `/api/v1/health`: 200 and exactly `{"status":"UP","application":"swiftdrop"}`. GET private and health subpath: 401. POST health without CSRF token: 403. |
| Development proxy | GET `http://127.0.0.1:5173/api/v1/health` returned the same 200 health response through Vite. |
| Browser check | The React page showed the offline state before startup, then “Backend connected” after retry; the connected page was visually inspected. |
| Secret handling | `.env`, temporary database password, dependencies and build outputs are ignored. A scan of Git candidate text files found neither generated local password. `.env.example` has blank passwords. |

The initial host PostgreSQL smoke check was a fallback. Flyway warned that host
PostgreSQL 18.1 is newer than its tested support range. Startup was then separately
verified against the project's pinned PostgreSQL 17.11 container as recorded above.
Unit tests emitted a Mockito/JDK future dynamic-agent warning; the tests passed.

## Container validation

The first `docker compose build` attempt failed because Docker could not resolve
`production.cloudfront.docker.com`. A retry resolved the tags and began downloading
layers, but was stopped after continued very slow base-image transfers. Neither
application image reached its build steps. The four-container startup and Nginx
proxy are therefore **not verified**, and container builds are not counted as passed.

The first `mvnw.cmd -Pintegration verify` attempt passed the eight unit tests, then
failed during RabbitMQ image acquisition with `ContainerFetchException` and pull
timeouts. The three integration test methods did not execute on that attempt.
After `docker compose pull postgres rabbitmq` completed, the integration command
was rerun successfully. Its final result was **BUILD SUCCESS**, 8 unit/security
tests and 3 integration tests passed, no failures, errors or skipped tests.

## Configuration still needed

- Keep Docker Desktop's Linux engine running and allow Docker Hub/CDN downloads.
- This checkout has an ignored `.env` with two distinct random local passwords.
  A new clone must copy `.env.example` and fill both passwords as the README shows.
- No Google, JWT, email, payment or merchant credentials are needed for this scope.
- The team must confirm the nine-member exception, member names/IDs, deadline and
  hosting decisions. See `reference-review.md` for the document conflicts.

Use the README's exact run/test commands to reproduce checks. Temporary smoke-test
data and diagnostic logs are under ignored `tmp/`; build/test reports are under
ignored `backend/target/` and `frontend/dist/`.

At handoff, the Vite server, local Java process, isolated PostgreSQL smoke cluster
and project Compose containers were stopped. Testcontainers removed its disposable
containers. Docker Desktop remains available. Compose database/broker volumes and
the ignored `.env` were preserved, so the documented run commands reuse them.

## Neon configuration follow-up — 1 November 2026

- Added backend-only Neon placeholders using `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Existing
  `POSTGRES_*` fallback changes and private configuration were preserved.
- Maven explicitly anchors `spring-boot:run` to `backend/`; Spring imports the root
  environment file and then the backend file before configuring the datasource.
- Verified the actual root-directory Maven launch,
  `backend/mvnw.cmd -f backend/pom.xml spring-boot:run`, using a temporary
  `backend/.env` and a disposable PostgreSQL 17.11 instance. The file selected the
  `developer_branch` database, `envcheck` role and test port 18081; health returned
  200 and Flyway V1 succeeded in that database. No shell datasource exports were
  used to supply the test connection.
- `-Pintegration verify` passed all 8 unit/security tests and 3 integration tests,
  including an assertion that Flyway holds the exact application DataSource
  instance. Packaging and verification ran in an isolated source copy under
  `tmp/neon-config-build/backend/`: the first in-place packaging attempt could not
  rename a JAR held by an already-running Java process. That process was left alone.
- Frontend tests: 4 passed. Production build passed. Vite now reads only its own
  frontend environment files. Neither private password appeared in built assets.
- Git ignores real root/backend/frontend `.env` files and includes the example
  templates. `git diff --check` passed. Docker build contexts exclude `.env*`.
- The temporary backend process, test `.env` and disposable database were removed.
  The original private root `.env` had the same SHA-256 before and after the check;
  pre-existing application/infrastructure services were not stopped.
- **Not tested:** a live Neon endpoint or migration against a developer's actual
  branch. Each developer must fill the three placeholders with their own branch
  connection values before using the documented startup command. Local validation
  verifies environment loading and shared datasource wiring, not remote reachability.

Follow-up logs are in ignored `tmp/neon-config-isolated-verify.log` and
`tmp/neon-env-startup.log`; isolated test reports are under
`tmp/neon-config-build/backend/target/`.

## Identity database follow-up — 1 November 2026

V2 adds users, UUID roles/memberships, provider identities, stable sessions,
rotating refresh-token records, password-reset tokens and administrative audit.
All eight JPA mappings validate with Hibernate. V1 and the original reference
documents were preserved; no live developer database was reset or migrated.

The user's new instruction supersedes the earlier same-DataSource-instance rule.
Runtime uses the application connection; Flyway uses a direct connection to the
same endpoint/database. It derives the direct Neon hostname or accepts optional
backend-only `SPRING_FLYWAY_URL/USER/PASSWORD` settings. A destination guard rejects
mismatches before migration. Local PostgreSQL Compose configuration still works.

### Passed

- `mvnw.cmd -f backend/pom.xml --batch-mode --no-transfer-progress --offline
  -Pintegration verify` from the repository root: **BUILD SUCCESS**, **24 unit/
  configuration/security tests + 14 integration tests**, zero failures/errors/skips.
  Used Java 21.0.9 and disposable PostgreSQL 17.11/RabbitMQ 4.2.9 containers.
- Fresh installation, V1-to-V2 upgrade with preserved unrelated data, restart with
  zero reapplied migrations, only four role definitions seeded and no accounts.
- Database uniqueness/FKs/checks, generated normalized email, nullable local
  password, token hash format and absolute expiry, retained rotation chain,
  cross-family link rejection, two concurrent conditional rotations with one
  winner, session/account authorization-state queries, reset consumption and
  audit update/delete rejection. These validate persistence capabilities, not an
  implemented login/recovery service.
- Real application startup, Hibernate validation of all eight identity entities,
  public health/private-route protection and a RabbitMQ round trip. Runtime and
  Flyway use distinct DataSources with matching database destinations.
- A subsequent targeted `FoundationIT` run passed all three tests after adding a
  runtime JPA query that reads the four roles created through Flyway's connection.
  Its log is `tmp/identity-runtime-query-verify.log`.
- A child-JVM test loads the actual application YAML from a temporary backend
  working directory with synthetic root/backend `.env` files, proving backend
  precedence and runtime/migration credential binding without network access.
- A separate configuration-only probe used this developer's actual private
  `backend/.env`: values bound correctly, a Neon endpoint was configured, and the
  direct destination guard passed. Only boolean status messages were displayed.
  No database connection was opened; the private file's SHA-256 was unchanged.
- Packaging produced `backend/target/swiftdrop-backend.jar`; JaCoCo report generated.
- `git diff --check`, local documentation-link checks and V1/reference preservation
  checks passed. `backend/.env` remains Git-ignored; no private file was edited.

The first verification attempt exposed missing explicit YAML placeholders for
uppercase Flyway keys loaded from a Java-properties `.env`. Those placeholders
were added; the final full verification above passed. No credentials were printed.
Docker/Java test-agent access required running tests outside the sandbox; no
existing application services or developer databases were stopped.

### Limits / not run

- No live Neon connectivity, migration or remote branch-metadata lookup. The URL
  guard verifies the same compute's pooled/direct endpoint pair and database; it
  does not call Neon's control-plane API.
- No Google, login, token issuance, account management or recovery delivery is
  implemented. All eight end-to-end IAM stories remain incomplete.
- Full four-service application image builds/startup and production TLS were not
  rerun. Frontend code was unchanged; earlier frontend results remain historical.
- Separate least-privilege runtime/migration roles require deployment-specific
  grants and have not been tested against Neon.

Main log: ignored `tmp/identity-database-verify.log`; reports are under
`backend/target/surefire-reports/` and `backend/target/failsafe-reports/`.
The current database model and exact launch command are in
[`identity/schema.md`](identity/schema.md).
