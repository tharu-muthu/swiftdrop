# M4 My Bookings history contract

`GET /api/v1/bookings` and `GET /api/v1/bookings/{id}` require the current
`CUSTOMER` identity. The service obtains the owner from M1's trusted
`IdentityContext`; neither endpoint accepts a customer ID.

History supports `search`, `state` (`CREATED`, `CONFIRMED`, `CANCELLED`), UTC
calendar `from`/`to` dates, `page`, and `size`. All returned rows come from the
customer's real `swiftdrop.parcel_bookings` records. The V5 recipient and city
snapshot fields are nullable so a migration does not rewrite older bookings;
the UI labels absent legacy fields as “Not recorded”. No mock/demo booking data
is inserted.

M4 `booking_state` is displayed as the booking-administration status. M6 owns
canonical `IN_TRANSIT` and `DELIVERED` lifecycle data, so their summary cards
remain explicitly unavailable until M6 provides a service/projection contract.
