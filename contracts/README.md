# Shared contracts

Current endpoint: `GET /api/v1/health`, public, HTTP 200:

```json
{"status":"UP","application":"swiftdrop"}
```

This is liveness only. Other GET routes return 401 to anonymous callers. Unsafe
requests without CSRF protection return 403. No authentication flow exists yet.

Add agreed OpenAPI and event schemas here as feature work begins. Proposed roles,
service DTOs, business paths and events in v3 are not implemented contracts yet.
