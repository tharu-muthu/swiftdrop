# M9 webhook development

M9 currently provides the contract, approved-merchant settings API, and delivery
core. M2 approval and ownership are available through the shared service
contract. M6 event ownership data is still pending.
The shared application remains one Spring Boot backend, one `swiftdrop` schema,
and one RabbitMQ broker.

## Implemented slice

- `contracts/webhook-events.md` defines the versioned parcel-status envelope,
  signature headers, signed input, retry classification, and ownership boundary.
- `WebhookSigningService` creates 32-byte Base64 secrets and signs the exact
  `<timestamp>.<body>` bytes with HMAC-SHA256.
- `WebhookSecretProtector` encrypts signing secrets with AES-GCM. Set the
  optional `WEBHOOK_SECRET_ENCRYPTION_KEY` in `backend/.env` for a dedicated
  32-byte Base64 key. Without it, the backend derives a context-separated key
  from the required JWT secret.
- `WebhookEndpointPolicy` accepts HTTPS destinations and an explicitly enabled
  loopback HTTP receiver for local tests. It rejects userinfo, fragments,
  unsupported schemes, loopback HTTPS, and non-loopback IP literals.
- `WebhookRetryPolicy` classifies transient failures and calculates bounded
  exponential backoff. The final attempt count and delay values remain team
  configuration decisions.
- `WebhookDeliveryService` prepares signed headers and delegates network I/O to
  `WebhookTransport`. `JdkWebhookTransport` rejects redirects and bounds the
  response body read.
- V4 adds subscription, durable delivery, and attempt tables. `merchant_id` is
  resolved through the M2 service contract because V4 predates the merchant
  profile migration and V8 is reserved.
- Approved merchant owners can now list, create, update, deactivate, rotate, and
  test subscriptions through `/api/v1/merchant/webhooks`. Secrets are returned
  only on creation or rotation. Delivery history is available per subscription.

## Pending integration

M6 must add merchant ownership and tracking data to the committed status event
and outbox envelope. The RabbitMQ exchange, queue, routing key, consumer
acknowledgement boundary, dead-letter policy, and retry scheduler must be agreed
before M9 binds a consumer. The React settings screen remains a frontend follow-up
to these authenticated API routes.

## Verification

Run the independent M9 tests from `backend`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=Webhook*Test" test
```

The full integration profile validates Flyway V1 through V7 and Hibernate mappings
against a disposable PostgreSQL database. It never uses a developer's Neon
branch.
