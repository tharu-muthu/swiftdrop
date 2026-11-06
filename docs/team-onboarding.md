# Team quick start and first feature

M1 must first push a reviewed PR and merge the toolkit into `dev`. A local change
or unmerged feature branch is not yet available to other members. This guide does
not claim that the PR, CI or GitHub branch protections have been configured.

## First setup (Windows PowerShell)

Install Git and Docker Desktop using Linux containers, start Docker, then clone
the team's repository. Run these from the directory containing `compose.yaml`:

```powershell
git switch dev
git pull --ff-only origin dev
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
```

Edit private files in your editor, without committing them:

- Root `.env`: supply the local RabbitMQ password, preserving an existing volume's
  original credentials. Keep frontend port 8080 initially. The helper below
  generates the missing private Docker JWT signing key.
- `backend/.env`: replace the three `SPRING_DATASOURCE_*` placeholders with your
  own Neon branch's JDBC URL/role/password. Runtime/Flyway must target the same
  branch. The current base Compose file uses this database; it does not start a
  PostgreSQL service. An old orphan PostgreSQL container may contain valuable data;
  do not remove it just to silence a warning.
- Google: all `GOOGLE_*` settings belong in `backend/.env` only. Use
  `GOOGLE_ENABLED=true` only after supplying a real client ID and the replacement
  secret through the team's private sharing procedure (or your own development
  client). Use `GOOGLE_FRONTEND_ORIGIN=http://localhost:8080`. Register exactly
  `http://localhost:8080/login/oauth2/code/google` with Google. Testing-mode
  accounts must be approved test users. Keep Google disabled if unconfigured.
- No secrets belong in frontend/VITE configuration, commits, PRs or screenshots.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-auth.ps1 -Compose
docker compose config --quiet
docker compose up --build --detach --wait --wait-timeout 300
docker compose ps
```

Open http://localhost:8080/login. Register on your own database branch, log in,
reload and log out. Test Google separately if configured. First builds can take
minutes. For an error, collect `docker compose logs --tail=100 backend`, remove
sensitive data, and share the actual error. Do not disable tests/security or delete
volumes to make setup pass. Do not share expanded `docker compose config` output.
Stop with `docker compose down`, without `-v`.

## Daily feature work

Save/commit work on its feature branch before switching or pulling:

```powershell
git switch dev
git pull --ff-only origin dev
git switch -c feat/your-small-story
```

1. Agree the feature brief: owner, API method/path and DTOs, allowed actors, record
   ownership, service dependencies, migration reservation and acceptance tests.
2. Follow [the identity integration recipes](../contracts/identity-services.md).
   Backend services inject `IdentityAccountService`; React uses `useAuth().client`.
3. Add your feature's own controller/service/repository/DTOs and screens. Do not
   modify identity repositories or role tables. Coordinate shared security/router,
   dependency, Docker and migration edits before making them.
4. Implement/test one small vertical slice and open a draft PR early. New routes
   stay denied until their explicit security rule and tests are reviewed with M1.
5. Rebuild changed application images to see changes; base Compose has no live reload:

```powershell
docker compose up --build --detach --wait backend frontend
```

After environment-only edits use `docker compose up --detach --force-recreate
--wait backend frontend`; no image rebuild is needed.

Before final review, merge current `origin/dev` into your feature branch, resolve
conflicts with the affected owner, and rerun checks. Push the feature branch and
open a PR with base `dev`. Include story, behavior, migrations/contracts, test
commands/results, UI screenshots and limitations. Do not push directly to `dev`.

## Responsibilities and pending dependencies

M1 owns identity/authentication; M2 merchant approval; M3 pricing; M4 booking;
M5 dispatch/courier operations; M6 execution/canonical status; M7 tracking;
M8 notifications; M9 webhooks. Each feature owns its record authorization and
uses other features through services, not repositories or internal HTTP.

The toolkit's courier lookup requires DISPATCHER_ADMIN. Admin/courier provisioning,
merchant role mutations and account administration are still pending. Tell M1
when your story needs these; do not create a public role picker or default admin.
Role fixtures belong only in disposable test databases. Reserve migration versions
before applying changes; never modify an applied/shared migration.

Docker image builds run ordinary backend and frontend tests/builds. The separate
integration profile requires Docker plus host Java 21/Maven wrapper (or an agreed
CI runner). Commands are in the identity contract and README. Missing host npm
dependencies require `npm.cmd --prefix frontend ci` before host frontend checks.

## M1 release handoff

M1 and one teammate must perform a fresh-clone setup and the first reviewed
feature integration together before claiming team onboarding has been verified.
Configure PR review/required CI checks on `dev` once the actual workflow exists.
