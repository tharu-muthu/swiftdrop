# M6 execution and parcel lifecycle contract

M6 owns the canonical parcel status. M5 owns courier profiles and assignment
selection. M4 creates an unassigned CREATED lifecycle in the booking transaction;
M5 later supplies `assignedCourierId` through the lifecycle service contract.
M6 never accepts an acting courier ID from an HTTP request.

## Courier API

All routes require an active authenticated `COURIER` and use `/api/v1`.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/courier/deliveries` | List deliveries assigned to the current courier |
| GET | `/courier/deliveries/{lifecycleId}` | Read delivery details and ordered history |
| POST | `/courier/deliveries/{lifecycleId}/status` | Apply one validated status transition |

The status request contains `expectedStatus` and `status`. The expected value
is compared while the lifecycle row is locked, so stale mobile screens receive
`409 LIFECYCLE_CONFLICT` instead of overwriting a newer update.

Valid transitions are:

```text
CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY
OUT_FOR_DELIVERY -> DELIVERED | FAILED
PICKED_UP | IN_TRANSIT -> FAILED
```

`DELIVERED` requires a non-blank `recipientName`. `FAILED` requires a
non-blank `failureReason`. Terminal statuses cannot transition again.

Each successful update writes the lifecycle, append-only status history,
delivery proof when applicable, and a versioned `parcel.status.changed` outbox event in one
transaction. The outbox payload contains identifiers and status values only;
address and recipient details are not published.

Terminal execution also releases active assignment workload through M5; successful
delivery issues M4's delivery receipt in that same transaction. See
[the integrated event and recovery contract](../docs/full-system-implementation.md#event-delivery-and-recovery).
