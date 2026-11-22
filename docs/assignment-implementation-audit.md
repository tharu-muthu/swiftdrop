# Assignment implementation audit

Reviewed against the supplied four-page Group Assignment and the repository
baseline in `docs/reference-review.md` and `docs/full-system-implementation.md`.

## Requirement coverage

| Assignment area | Current implementation | Evidence |
| --- | --- | --- |
| Customer signup, login and Google login | Implemented with verified email, rotating refresh sessions, in-memory access JWTs and optional OIDC | `frontend/src/features/identity`, `backend/src/main/java/com/swiftdrop/controller/identity`, `docs/identity/implementation-checklist.md` |
| Customer quote, booking, history and receipt | Implemented through M3/M4 services with immutable booking snapshots, idempotency and ownership checks | `frontend/src/features/booking`, `backend/src/main/java/com/swiftdrop/service/booking`, `contracts/booking-dashboard.md` |
| Public real-time tracking | Implemented with public-safe snapshots, SSE, reconnect and sequence guards | `frontend/src/features/tracking`, `backend/src/main/java/com/swiftdrop/service/tracking` |
| Merchant registration, approval, API orders and webhooks | Implemented with admin approval, hashed API keys, merchant isolation, signed callbacks and retries | `frontend/src/features/merchant`, `frontend/src/features/webhook`, `backend/src/main/java/com/swiftdrop/service/merchant`, `backend/src/main/java/com/swiftdrop/service/webhook` |
| Courier assigned work and status updates | Implemented with active courier eligibility, workday/carry-over views, guarded transitions, recipient proof and failure reasons | `frontend/src/features/execution`, `backend/src/main/java/com/swiftdrop/service/execution` |
| Dispatcher/admin operations | Implemented with user/role administration, merchant review, zones/rates, assignments, workload and daily reports | `frontend/src/features/dispatch`, `frontend/src/features/identity/AdminUsersPage.jsx`, `frontend/src/features/pricing` |
| Async notification integration | Implemented with RabbitMQ parcel events, durable notification jobs, mock SMS, leases, retries and dead-letter replay | `backend/src/main/java/com/swiftdrop/integration`, `backend/src/main/java/com/swiftdrop/service/notification` |
| Containerization | Implemented with frontend/Nginx, backend/JRE and RabbitMQ Compose services | `compose.yaml`, `frontend/Dockerfile`, `backend/Dockerfile` |
| Testing and authorization | Unit/security/React tests plus Docker-backed PostgreSQL/RabbitMQ integration tests are present; integration execution still requires Docker | `backend/src/test`, `frontend/src/**/*.test.*`, `docs/full-system-verification.md` |

The assignment's optional Kubernetes and generative-AI bonus items are not part of
the required implementation. The team still needs to confirm the brief's stated
ten-person group size, hosting and submission arrangements with the lecturer.

## Changes made in this review

- The customer dashboard now receives a live `deliveredBookings` count from the
  M6 lifecycle projection and renders it instead of a permanent placeholder.
- Shared frontend request handling now classifies timeout and network failures as
  stable error codes. Registration uses those codes, so its retry-safe messages
  work in browsers as well as test environments.
- Regression coverage now checks the delivered dashboard metric and the request
  error classification.
- README setup text was corrected to describe the connected M1–M9 branch and the
  current migration range.

## Checks still required before submission

The local checks for this review passed: 129 frontend tests, the Vite production
build, 518 backend unit/security tests, backend packaging, and `docker compose
config --quiet`. Run the integration profile with Docker before submission. A real
Google OAuth callback, SMTP delivery, provider credentials, shared Neon rollout,
production TLS/hosting and the lecturer's final group/submission decisions are
environment or team-delivery checks rather than repository-only checks.
