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

Add agreed OpenAPI and event schemas here as feature work begins. Proposed roles,
service DTOs, business paths and events in v3 are not implemented contracts yet.

Implemented login, refresh, logout and GET /me: see [session contracts](identity-sessions.md), which supersede the earlier no-login status above.
