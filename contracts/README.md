# Shared contracts

Backend feature integration: [shared identity services](identity-services.md)
documents the implemented current-user/role/courier lookup contract, React API
client usage, examples, permissions and pending role-changing workflows.

Current endpoint: `GET /api/v1/health`, public, HTTP 200:

```json
{"status":"UP","application":"swiftdrop"}
```

This is liveness only. [IAM-01 registration](identity-registration.md) adds public
GET `/api/v1/auth/csrf` and POST `/api/v1/auth/register`. Unsafe requests without
CSRF protection return 403. Other routes remain denied. Registration creates a
CUSTOMER account; login/session issuance is not implemented yet.

Feature work adds reviewed OpenAPI and event schemas here. The M9 webhook event
envelope is documented in `webhook-events.md`; its merchant approval and canonical
parcel-status adapters still require the M2 and M6 handoffs.

Implemented login, refresh, logout and GET /me: see [session contracts](identity-sessions.md), which supersede the earlier no-login status above.

Email ownership verification and its rollout gate: see [email verification](identity-email-verification.md).
