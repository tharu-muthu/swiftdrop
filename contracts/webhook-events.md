# Merchant webhook contract

This contract defines the M9 boundary for signed merchant callbacks. M2 owns
merchant approval and ownership. M6 owns canonical parcel status and publishes
committed status events. M9 consumes the event through an adapter and never
reads another feature's repositories.

## Event envelope

The first supported event is `parcel.status.changed`.

```json
{
  "eventId": "8c1a8d08-18d0-4b28-a0d6-516a03d52ca9",
  "eventType": "parcel.status.changed",
  "schemaVersion": 1,
  "occurredAt": "2026-11-06T10:00:00Z",
  "merchantId": "11111111-1111-4111-8111-111111111111",
  "parcelId": "22222222-2222-4222-8222-222222222222",
  "trackingNumber": "SDP-ABC123",
  "status": "IN_TRANSIT",
  "sequence": 3
}
```

`eventId` is stable across retries. `occurredAt` is the committed event time in
UTC. `sequence` is the M6 lifecycle sequence and allows a receiver to reject an
older status arriving after a newer one. M9 must not invent or recalculate the
status.

## Signature

The sender serializes the exact UTF-8 JSON body that it sends. The signed input
is `<timestamp>.<body>`, where `timestamp` is the Unix epoch seconds string used
in `X-Swiftdrop-Timestamp`. The signature header is:

```text
X-Swiftdrop-Signature: v1=<base64 HMAC-SHA256>
```

Every request also includes:

```text
X-Swiftdrop-Event-Id: <eventId>
X-Swiftdrop-Timestamp: <epoch seconds>
Content-Type: application/json
```

Receivers should reject an old timestamp, verify the HMAC with a constant-time
comparison, and use `X-Swiftdrop-Event-Id` for their own idempotency key.

## Delivery policy

M9 retries network failures, HTTP 408, HTTP 429, and HTTP 5xx responses. HTTP
2xx is successful. Other HTTP 3xx and 4xx responses are terminal failures. The
maximum attempt count and backoff values remain configuration decisions for the
team. Each attempt is recorded with a bounded error message and response status.

## Ownership boundary

M9 accepts a trusted merchant ownership result from M2 and a trusted committed
event from M6. A browser request never supplies the merchant ID used for
authorization. A delivery is created only for an approved merchant's active
subscription that is subscribed to the event type.
