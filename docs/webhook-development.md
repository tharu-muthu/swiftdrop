# M9 webhook development

M9 currently provides the contract and delivery core that can be developed
before M2 merchant approval and M6 parcel lifecycle integration are merged.
The shared application remains one Spring Boot backend, one `swiftdrop` schema,
and one RabbitMQ broker.

## Implemented slice

- `contracts/webhook-events.md` defines the versioned parcel-status envelope,
  signature headers, signed input, retry classification, and ownership boundary.
- `WebhookSigningService` creates 32-byte Base64 secrets and signs the exact
  `<timestamp>.<body>` bytes with HMAC-SHA256.
- `WebhookSecretProtector` encrypts signing secrets with AES-GCM. The encryption
  key is supplied by the eventual backend secret-management configuration.
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
  intentionally resolved through the M2 service contract until the merchant
  profile table is available for a foreign key migration.

## Pending integration

M2 must provide approved-merchant ownership and merchant profile identifiers.
M6 must provide the committed status event and outbox envelope. The RabbitMQ
exchange, queue, routing key, consumer acknowledgement boundary, dead-letter
policy, and retry scheduler must be agreed before M9 binds a consumer. M9 APIs
and the React settings screen should be enabled with M2 authorization after
those contracts are reviewed.

## Verification

Run the independent M9 tests from `backend`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=Webhook*Test" test
```

The full integration profile also validates Flyway V4 and Hibernate mappings
against a disposable PostgreSQL database. It never uses a developer's Neon
branch.
