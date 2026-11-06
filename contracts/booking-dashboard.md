# M4 booking home dashboard contract

`GET /api/v1/bookings/dashboard` is an authenticated **CUSTOMER** endpoint. It
uses the request's current `IdentityContext`; the browser cannot select a
customer ID. It returns only that customer's M4-owned aggregate:

```json
{
  "activeBookings": 2,
  "pendingReceipts": 1,
  "thisMonthShipments": 4
}
```

`activeBookings` counts M4 `CREATED` and `CONFIRMED` booking-administration
states; it is not a delivery-state claim. `pendingReceipts` and monthly bookings
also come from `swiftdrop.parcel_bookings`. Results have `Cache-Control: no-store`.

The dashboard deliberately does not invent M3 pricing or M6/M7 delivery/tracking
data. Until those owners publish an in-process service/projection contract, the
UI identifies the delivered metric and tracking flow as unavailable rather than
returning fabricated statistics or querying another feature's tables.
