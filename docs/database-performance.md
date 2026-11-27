# Database query performance

## Changes

- User Management loads roles for all users on the current page in one query.
  A full page needs three repository queries (users, count, roles), rather than
  two plus one per user. Current-role authorization checks still run.
- Booking dashboard and history counters are computed by PostgreSQL instead of
  transferring every customer booking and lifecycle to Java. Counts remain scoped
  to the customer and independent of table filters. Missing lifecycles, cancelled
  bookings and terminal delivery states retain their previous meaning.
- Booking history loads lifecycle details only for the visible page. The M4
  services call the M6 LifecycleReadService contract; M6 exposes
  CustomerDeliveryCounts using a read-only reporting join to booking ownership.
  Neither module writes the other module's records.
- Booking history sorts equal creation timestamps by ID, giving stable ordering.
- V14 adds indexes matching user-list ordering, dispatch-board ordering and
  filters, assignment history, courier assignment dates, daily completion reports
  and recent notification jobs. Existing ownership, unique-email, role, token,
  lifecycle and worker indexes are reused.

## Safe rollout

V14 is the next version in this checkout. Confirm it is free in the team's shared
migration sequence **before starting the updated backend against shared Neon**.
If another unapplied feature uses V14, agree on the next version before either
is deployed. Never renumber or change an already-applied migration.

Flyway runs the new migration on backend startup. No reset, repair, manual SQL,
or credential change is required. Hibernate remains in validation mode.
This migration only creates indexes; it preserves all existing rows.
Standard CREATE INDEX runs transactionally and can block writes during index
construction. Deploy during a quiet period. Large production tables need a
separately planned concurrent-index rollout rather than editing an applied V14.
Indexes also take disk space and add write work; avoid indexing every column.

## Verify locally (PowerShell from the repository root)

```powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress -Pintegration '-Dit.test=DatabasePerformanceIT,AdminUserIT' verify
cd ..
```

Docker Desktop and Java 21 are required. Integration tests create disposable
PostgreSQL 17 containers. DatabasePerformanceIT disables private .env loading and
overrides both application and Flyway connections. It checks migration validity,
counter semantics and customer isolation, constant query counts for user pages,
and a real EXPLAIN (ANALYZE, BUFFERS) plan on 6,000 synthetic users.
BookingQueryBoundsTest verifies that history never requests an unpaged list.

After tests and migration-number coordination:

```powershell
docker compose up -d --build backend
```

Check User Management, booking history/dashboard, dispatch filters and notification
lists. Compare browser Network timings using the same data, filters and page size.
For database investigations, use EXPLAIN (ANALYZE, BUFFERS) on read-only SELECTs
in your isolated development branch. PostgreSQL may correctly choose a sequential
scan on small tables; index presence alone does not prove a speed improvement.

## Limits and follow-up measurements

Verification in this checkout passed: 528 existing unit tests, the additional
BookingQueryBoundsTest, and six selected PostgreSQL integration tests across
DatabasePerformanceIT and AdminUserIT. User pages of 5 and 20 rows each used
three repository queries. PostgreSQL selected ix_users_created_id for the
ordered 6,000-user fixture. All migrations through V14 applied successfully.
The coverage report emitted stale execution-data warnings; no coverage percentage
is claimed from that report. Shared Neon latency and browser loading times were
not measured, and these changes have not been applied to the shared database.

These changes reduce query count and data transfer. They do not promise a measured
end-to-end latency improvement on shared Neon without representative measurements.
Dashboard counts still inspect the customer's matching records inside PostgreSQL.
Deep offset pagination still gets more expensive; consider cursor pagination if
real volumes justify an API change.

Contains searches (LIKE '%text%') cannot generally use ordinary B-tree indexes.
Keep existing search behavior; measure before adding pg_trgm expression indexes,
which need extension privileges and extra write/storage cost.
No authentication or authorization caching is introduced: suspension and session
revocation must continue taking effect immediately.
