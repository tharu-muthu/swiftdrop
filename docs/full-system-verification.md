# All-member implementation verification

Verified locally on 21 November 2026 against the completed M1–M9 integration,
including the M2 order-status/pagination and M5 overdue/eligibility follow-up.
The implementation and setup order are in
[the integrated system guide](full-system-implementation.md).

The latest follow-up reran the full backend suite and frontend tests/build.
The receiver, image-build and container-tool checks below are retained from the
earlier all-member baseline; they were not rerun for this follow-up. No migration
was added or edited for these monitoring changes.

## Passed

| Check | Result |
| --- | --- |
| Java 21 Maven wrapper `-Pintegration clean verify` | 175 unit/configuration/security tests and 87 integration tests; zero failures, errors or skips. Backend JAR and JaCoCo report generated. |
| Frontend `npm test -- --reporter=dot`, `npm run build` | 70 tests across 23 files passed; production build succeeded using the existing installed locked dependencies. `npm ci` passed in the earlier baseline. |
| Receiver `node --test examples/webhook-receiver/server.test.mjs` | One signing/tampering/freshness test passed. |
| `docker compose --progress plain build backend frontend` | Both application images built successfully, including their test/build stages. |
| `docker compose config --quiet` | Configuration validated without printing private values. |
| Frontend image `nginx -t` | Configuration passed, including the unbuffered SSE location. A loopback host alias was used for syntax validation; no backend connection was opened. |
| Backend image runtime tools | Java 21.0.13 and the health-check `curl` executable confirmed. The application was not started against private Neon. |
| Repository checks | `git diff --check` passed. Existing V1–V12 SQL migrations, including V8, are unchanged by the monitoring follow-up. |

The final focused tracking/security/journey rerun also passed after adding a real
HTTP SSE subscriber to `FullJourneyIT`. It verifies an initial tracking snapshot,
courier transitions through the persisted outbox and RabbitMQ consumer, and a live
DELIVERED snapshot containing the complete five-entry timeline.

Integration coverage includes:

- Fresh PostgreSQL migration through V12, repeat migration, prior-data preservation,
  JPA validation and the existing identity/session/registration/merchant/pricing checks.
- Owned quotes, customer booking confirmation, persisted snapshots, missing headers,
  invalid paging, expired quotes, changed idempotency payloads and simultaneous retries.
- A single initial lifecycle/history for duplicate booking submissions; other-owner
  access denied; current courier/dispatcher authorization and operational profiles.
- Real courier detail/history/dispatch-board endpoints, lifecycle updates, terminal
  workload release, public privacy, private customer access and delivery receipt issuance.
- Merchant application/approval and role refresh, API-key orders, business isolation,
  revoked keys and immediate suspension enforcement.
- Mock SMS jobs, event deduplication, dead-letter replay, claimed-job leases and recovery
  after an interrupted attempt.
- Signed callbacks to a real local HTTP receiver, HTTP 503 followed by successful retry,
  persisted attempts and suppression of callbacks after the owner loses access.
- Read-safe lifecycle snapshots, tracking sequence guards and denial of unknown feature
  routes, plus focused Google-link consent/session/expiry and operational-role guards.
- Development seed execution twice with an existing non-default Colombo zone ID:
  nine synthetic rate routes, five postcode mappings, no duplicated reference rows
  and no users/passwords.
- Merchant history with 105 same-timestamp orders: stable pagination, totals,
  canonical delivery statuses in list/details, ownership denial, invalid page
  bounds, and unchanged partner array shape with pagination headers.
- Overdue parcel filtering before pagination, oldest-first order, combined
  unassigned filtering, and exclusion of DELIVERED/FAILED parcels. Unit tests also
  cover the exact cutoff boundary and administrator authorization.
- Courier selection excludes suspended/unverified accounts, missing COURIER roles
  and inactive operational profiles. Withdrawing a previously eligible courier's
  role through the real administrator API removes them from the active endpoint;
  a subsequent stale assignment is denied without changing parcel/assignment state.
- React interactions for merchant paging beyond 100, detail-status freshness,
  overdue/report filters and clearing an invalid courier choice after rejection.

## Failed

No outstanding failing checks. Earlier runs and review found stale migration and
eligibility fixtures, the unfiltered history query failure, read-only lifecycle
locking, and rollback handling for a denied background merchant lookup. Those
were corrected and the affected checks were rerun successfully.
The monitoring follow-up initially could not run integration tests while Docker
Desktop was stopped. After starting it, one new fixture expectation was corrected
to the existing 404 for a customer without a merchant profile; the complete clean
verification then passed.

## Unrun and environment boundaries

- No shared Neon migration, live application startup against private Neon, deployment,
  database cleanup or credential changes were performed. Coordinate V11/V12 and the
  existing V9 SMTP rollout before starting against a shared environment.
- Live Google authorization/linking and external SMTP inbox delivery were not verified
  with real provider credentials. Google is optional and disabled when unconfigured.
- No real mobile SMS was sent. The assignment's mock SMS adapter records simulated
  delivery and is identified as such in the UI.
- Full manual browser/visual acceptance and a complete production Compose deployment
  were not performed. HTTP journeys, actual SSE transport, component tests, production
  builds, image builds and container configuration checks passed as described above.

Reports are generated under `backend/target/surefire-reports`,
`backend/target/failsafe-reports`, and `backend/target/site/jacoco`.
Local command transcripts are in ignored `tmp/project-verification/`; they are
development evidence, not committed production configuration.
