# Integrated SwiftDrop workflows

Implementation follow-up: 21 November 2026. This records the all-member integration
authorized by the user after the earlier M9-only work. Original reference files
remain unchanged. This guide supersedes old statements that booking confirmation,
notifications, automatic callbacks, or pricing/merchant administration are pending.

## What is connected

| Member | Implemented workflow |
| --- | --- |
| M1 | Verified password signup/login, rotating sessions, recovery, profile, guarded account status and operational roles, first-admin bootstrap, optional Google sign-in and explicit linking after password confirmation. |
| M2 | Business application/review, approved-owner contact editing with record version, hashed API credentials, merchant-owned quotes/orders, paginated order history and canonical delivery status in lists/details. API keys also check the owner's current account, verification and MERCHANT role. |
| M3 | Owned expiring quotations and breakdown, administrator zones/postcodes and rate versions, enable/disable controls. |
| M4 | Quote confirmation, transactional customer/merchant booking, immutable addresses/contacts/price, tracking number, idempotency, history, private details and delivery receipts. |
| M5 | Operational courier profiles, paginated parcel/unassigned/overdue board, live courier eligibility, authorized assignment/reassignment, workload, assignment history and daily results/age reporting. |
| M6 | Canonical lifecycle, guarded courier updates, ordered history, delivery proof, transactionally persisted events, terminal workload release and receipt issuance. |
| M7 | Public-safe timeline, private customer details, event-driven SSE, heartbeats, reconnect/snapshot recovery, validation and bounded public request/subscriber limits. |
| M8 | Editable versioned templates, mock SMS provider, durable jobs and attempts, duplicate protection, leases, retries, terminal failures and administrator logs/retry. |
| M9 | Subscription editing, secret rotation, signed test and automatic callbacks, durable attempts/retries, merchant isolation, DNS/IP destination checks and pinned outbound connections. |

There is one Java application, one React frontend and one `swiftdrop` PostgreSQL
schema per environment. RabbitMQ integrates committed parcel changes; normal
feature calls remain in-process service calls.

## Member setup and demonstration order

1. Clone the reviewed branch containing these changes. Follow
   [team onboarding](team-onboarding.md) for Docker Desktop and private configuration.
   Copy root `.env.example` to `.env` and `backend/.env.example` to `backend/.env`
   only when the destination does not already exist.
2. Put the three Neon `SPRING_DATASOURCE_*` values in private `backend/.env`.
   Keep the runtime and Flyway destinations on the same development branch/database.
   Keep the direct-connection guard enabled. Never use a teammate's production branch
   for tests. No PostgreSQL service is required in normal Compose when Neon is used.
3. Set a private RabbitMQ password in the root `.env`, then run
   `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-auth.ps1 -Compose`.
   This generates private signing/origin settings. Do not paste secrets into source files.
4. Coordinate migration versions **V11 and V12** with the team before shared rollout.
   Existing V1–V10 files are preserved, including V8. V9 still requires a working
   verification inbox before password users can sign in. Pending migrations run at
   application startup; this implementation does not directly migrate your Neon branch.
5. Run `docker compose up --build -d`, then `docker compose ps`.
   Open `http://localhost:8080`. Configure real SMTP delivery through private
   `SPRING_MAIL_*` settings in `backend/.env`; verify the sender setup before signup.
6. Register a real account and verify the link delivered to its configured inbox.
   There are no seeded users or passwords. Use the documented private
   [first-admin bootstrap](../contracts/identity-role-handoff.md) for a verified
   account, then disable that startup action again.
7. After Flyway completes, optionally run
   [the development pricing seed](../backend/src/main/resources/db/seed/m3_pricing_dev_seed.sql)
   in **your development branch's** Neon SQL editor. It supplies synthetic Colombo,
   Galle and Kandy routes, handles existing zone IDs, and can be repeated. It creates
   no accounts. Alternatively configure actual zones/rates at `/admin/pricing`.
8. As a customer, open `/bookings/new`. For seeded routes use pickup postcode `00600`
   and delivery postcode `80000` or `20000`. Enter both mobile numbers, parcel grams,
   size and contents. Request a quote, inspect its price/expiry, then confirm.
9. As an administrator, use `/admin/users` to grant a verified active user the
   COURIER role with a reason. The affected user's old sessions end; they must sign
   in again. At `/dispatch`, create their operational vehicle profile and assign
   a parcel from the board. Reassignment requires a reason and an active courier.
10. As that courier, open `/courier`, select the delivery, and follow the status
    controls. Delivery requires a recipient name; failure requires a reason.
    `FAILED` is supported after pickup, in transit, or out for delivery.
11. Open the parcel's `/tracking/SDP-...` link. Public results omit names, phones,
    addresses, actor IDs and failure notes. Customers can view their private timeline
    from booking details. After delivery, `/receipts` supports printing and downloading
    a text delivery receipt. It does not record a payment.
12. For a merchant journey, submit a business at `/merchant`; an administrator approves
    it at `/admin/merchants`. Sign in again after the role grant. Generate a server API
    key or create an order through the merchant workspace. Optional external references
    are unique within the merchant. Configure callbacks at `/merchant/webhooks`.
13. At `/admin/notifications`, inspect mock SMS jobs/attempts and edit templates.
    Queue diagnostics and authorized dead-letter replay are available on the same screen.

Google is optional: with `GOOGLE_ENABLED=false`, password workflows still work and
the sign-in page disables the Google action. To enable it, supply backend-only
Google client credentials and the exact callback origin from the existing Google
setup guide. Linking an existing password account starts from its account page,
requires the current password, expires after five minutes, and binds the verified
Google subject to that authenticated account. Matching emails never auto-link.

## Booking and partner API contract

All routes below have the `/api/v1` prefix. Browser mutations retain CSRF protection.

| Caller | Quote | Confirm booking | Read |
| --- | --- | --- | --- |
| Customer session | `POST /quotes` | `POST /bookings` | `GET /bookings`, `GET /bookings/{id}` |
| Approved merchant session | `POST /merchant/dashboard/orders/quote` | `POST /merchant/dashboard/orders` | `GET /merchant/dashboard/orders`, `GET /merchant/dashboard/orders/{id}` |
| Merchant API key | `POST /merchant/quotes` | `POST /merchant/orders` | `GET /merchant/orders`, `GET /merchant/orders/{id}` |

Partner requests use `X-API-Key`; quote/order scopes remain enforced. Browser
credentials never become partner keys. Owners come from trusted identity/merchant
contexts. The booking body contains `quoteId`, `pickup`, `recipient`, `parcel`, and
an optional merchant `externalReference`. Addresses contain `fullName`,
`contactNumber`, `address`, `city`, `postalCode`; parcel contains `weightGrams`,
`size`, `description`.

Confirmation requires an `Idempotency-Key` of 8–100 letters, digits, hyphens or
underscores. Keep the same key/body after an uncertain network response. A replay
returns the original booking, even after its quote expires; a changed body with
that key conflicts. A quote can produce only one booking. The quote's owner,
expiry and price-driving inputs are checked through M3 before persistence.
Booking, initial unassigned CREATED lifecycle, initial history and outbox event
commit in one database transaction. PostgreSQL uniqueness and transaction locks
serialize concurrent requests.

Merchant order lists are paginated: the browser shows 20 orders per page, with
previous/next navigation and a total count. List/detail responses include M6's
`deliveryStatus`, `statusUpdatedAt`, and assignment flag, independently of administrative
`bookingState`. Refresh the order list or open details to read the latest status.
Legacy orders without an initialized lifecycle show "Not initialized".
Partner lists retain an array body, accept zero-based `page` and `size` (default
100, maximum 100), and return pagination headers; see
[the merchant contract](../contracts/merchant-api.md). Customer history, receipts
and the dispatch board are also paginated. Rate amounts are LKR minor units and grams
are integers. The existing booking amount storage supports up to 2,147,483,647
minor units; confirmation rejects larger quotations.

## Event delivery and recovery

The durable topic exchange is `swiftdrop.parcel.events`, with routing key and event
type `parcel.status.changed`. Each envelope contains `eventId`, `eventType`,
`schemaVersion=1`, UTC `occurredAt`, nullable `merchantId`, `parcelId`, `lifecycleId`,
`trackingNumber`, canonical `status`, and positive `sequence`. One event ID is
persisted in its immutable outbox body and retained on broker redelivery and HTTP retry.
V11 upgrades compatible old unpublished lifecycle envelopes using status history.

Independent durable queues are `swiftdrop.tracking`, `swiftdrop.notification`, and
`swiftdrop.webhook`, each with a `.dead` queue. The relay marks an event published
only after broker confirmation and checks returned messages. A crash between
publishing and marking may duplicate an event; consumer receipts and job uniqueness
make this safe. Consumer effects commit before acknowledgement. Repeated consumer
failures go to dead-letter storage; administrators can replay up to 20 messages at
a time after fixing the cause. Unsupported/invalid envelopes are rejected.

Notification/webhook workers claim jobs with PostgreSQL row locks and a two-minute
lease. Network calls happen after the claim commits. Attempts are stored before
sending; interrupted attempts remain visible and are marked when their lease is
recovered. Fenced completion prevents an old claim from overwriting a new attempt.
Both workers retain state across backend restarts. Automatic retry is bounded to
five ordinary attempts with delays starting at 30 seconds. Webhooks retry transport
failures, HTTP 408/429 and 5xx; redirects/other permanent failures stop. Administrators
or merchant owners can explicitly retry terminal jobs. SMS uses an idempotent mock
provider and is labelled as simulated delivery in the UI.

Callback signing remains HMAC-SHA256 over the exact `<timestamp>.<body>` bytes;
headers are `X-Swiftdrop-Event-Id`, `X-Swiftdrop-Timestamp`, and
`X-Swiftdrop-Signature: v1=<base64>`. Delivery uses validated DNS answers and connects
to the chosen IP directly while retaining TLS SNI/certificate hostname verification.
Private, loopback, link-local, metadata and shared address space are blocked for
normal callbacks. Redirects are not followed. Only explicit loopback development
allows HTTP callbacks. Responses have bounded headers/read time and their bodies
are not consumed. Keep a stable private `WEBHOOK_SECRET_ENCRYPTION_KEY`; if the
development JWT-derived fallback is used, rotating that JWT root requires rotating
existing webhook subscription secrets too.

Dispatch daily reports use UTC. The open-delivery age count and the "Overdue only"
board filter use the same configurable `swiftdrop.dispatch.delay-hours` threshold,
default 24 hours since the last lifecycle update. Delivered/failed parcels are
excluded; overdue results show the oldest update first. The report's "View overdue
parcels" button opens that current list, independently of the report date. Combine
it with "Unassigned only" to find old parcels awaiting assignment. This is an
operational age indicator, not a promised delivery SLA.

Courier choices require an active operational profile plus a currently active,
verified account with the COURIER role. Unavailable profiles remain visible for
administration, with a reason, but are excluded from assignment choices. Selection
refreshes the fleet and submission rechecks eligibility; a role withdrawal while
the screen is open cannot authorize an assignment. See
[dispatch monitoring](../contracts/dispatch-monitoring.md).
## Local receiver demonstration

Use [the Node receiver](../examples/webhook-receiver/server.mjs) with a backend running
on the host and local development enabled. Copy the subscription's one-time signing
secret into a private `examples/webhook-receiver/.env` as
`WEBHOOK_SIGNING_SECRET=...`, then run:

```powershell
node --env-file=examples/webhook-receiver/.env examples/webhook-receiver/server.mjs
```

Subscribe to `http://127.0.0.1:8088/webhooks` and select `parcel.status.changed`.
Send a test and refresh its delivery log, or create a merchant order. The receiver
verifies exact bytes and a five-minute freshness window, then deduplicates event IDs.
Its in-memory deduplication is a demonstration; a real merchant persists processed
IDs together with business effects. In full Compose, container loopback is not the
Windows host: use a reachable HTTPS receiver for that demonstration.

## Verification and environment boundary

Use the exact commands in the root README: `npm ci`, `npm test`, `npm run build`,
and Java 21 `mvnw --batch-mode --no-transfer-progress -Pintegration clean verify`.
The integration profile uses disposable PostgreSQL/RabbitMQ and overrides both
runtime and Flyway credentials. It never targets private Neon configuration.
`node --test examples/webhook-receiver/server.test.mjs` verifies receiver signing
and freshness. Final counts and limitations are recorded in
[the completion verification record](full-system-verification.md).

Real Google authorization and production SMTP/SMS/provider credentials, shared
Neon rollout, hosting/TLS, backups and team approval are deployment/account tasks.
Live provider success is not established by the controlled-provider tests. No live
Neon migration or deployment is performed by the coding verification commands.
