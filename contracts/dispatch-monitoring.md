# Dispatch monitoring (M5)

All endpoints require a current `DISPATCHER_ADMIN`. M5 reads bookings and canonical
lifecycle state through M4/M6 services. It uses M1's identity contract for courier
eligibility and its own repository for operational profiles and assignments.

## Parcel board

`GET /api/v1/dispatch/bookings?page=0&size=50&unassigned=false&overdue=false`

- `page` is zero-based, nonnegative; `size` is 1–100 (default 50).
- `unassigned=true` selects unassigned CREATED lifecycles.
- `overdue=true` selects nonterminal lifecycles with `updatedAt` strictly before
  the request's UTC time minus `swiftdrop.dispatch.delay-hours` (default 24).
  DELIVERED and FAILED are excluded. It measures time since the last lifecycle
  update, not a contractual delivery deadline.
- Both filters combine with AND and are applied in PostgreSQL before pagination.
- Normal sorting is `updatedAt DESC, id DESC`; overdue sorting is
  `updatedAt ASC, id ASC` so the oldest updates appear first.

The response contains `items`, `page`, `totalPages`, `totalElements`, `delayHours`,
and UTC `overdueBefore`. Rows retain booking ID, tracking number, route, status,
courier ID and `updatedAt`, and add an `overdue` boolean using the same cutoff.

The daily report's `overdueOpenDeliveries` count uses the same rule. Completed
results use the selected UTC report date; the overdue count/list always describe
the current backlog. "View overdue parcels" opens the current board with the
overdue filter enabled and the unassigned filter cleared. Either checkbox resets
pagination and selection; they can subsequently be combined.

## Eligible courier choices

`GET /api/v1/dispatch/couriers` retains every operational profile and adds:

| Field | Meaning |
| --- | --- |
| `eligible` | Profile is active AND account is active, email-verified and currently has COURIER role. |
| `unavailableReason` | Null when eligible; `PROFILE_INACTIVE` when operationally inactive; otherwise `ACCOUNT_UNAVAILABLE`. |

`GET /api/v1/dispatch/couriers/active` returns only eligible profiles. M1's
admin-only `IdentityAccountService.eligibleCourierIds` performs a bulk current
database lookup; it does not trust role claims from an earlier login. Account
details/secrets are not returned. Inactive profiles remain visible for editing.

The assignment selector includes only eligible couriers and excludes the current
courier during reassignment. Selecting a parcel refreshes eligibility. A rejected
operation refreshes it again and clears any unavailable choice. Assignment and
reassignment still revalidate the target through M1 and lock/check the operational
profile at submission, so an earlier eligible list cannot authorize a withdrawn
role or suspended account. Reassignment still requires a reason.

No SQL migration, role policy change or delivery-state transition is introduced.
