# M4 booking home dashboard contract

`GET /api/v1/bookings/dashboard` is an authenticated **CUSTOMER** endpoint. It
uses the request's current `IdentityContext`; the browser cannot select a
customer ID. It returns only that customer's M4-owned aggregate:

```json
{
  "activeBookings": 2,
  "deliveredBookings": 3,
  "pendingReceipts": 1,
  "thisMonthShipments": 4
}
```

`activeBookings` counts M4 `CREATED` and `CONFIRMED` booking-administration
states; it is not a delivery-state claim. `pendingReceipts` and monthly bookings
also come from `swiftdrop.parcel_bookings`. Results have `Cache-Control: no-store`.

`deliveredBookings` is read through M6's lifecycle service contract. Customer
history and private tracking also expose an `assigned` flag; when a courier has
been assigned while the canonical lifecycle remains `CREATED`, the UI displays
the operational state as **Assigned**. This does not create a new M6 status.
