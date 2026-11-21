# Identity profile contract (IAM-04)

Authentication: send the in-memory Bearer access token. Both endpoints return `Cache-Control: no-store`. The browser client obtains the CSRF token before PATCH. The service resolves the account from the validated identity context; clients cannot select another user.

## GET `/api/v1/me`

Returns `200` with `id`, `email`, `fullName`, `phone` (nullable), `roles`, `active`, `createdAt`, `updatedAt`, `version`, `passwordConfigured`, and `googleConnected`. The latter two are safe booleans for display, not permission grants. Missing or revoked authentication returns `401`.

## PATCH `/api/v1/me`

Send JSON with exactly `fullName`, `phone`, and `version`, for example:

```json
{"fullName":"Alex Perera","phone":"+94771234567","version":2}
```

`fullName` is required, nonblank, and at most 200 characters after trimming. `phone` must be present; use `null` or `""` to clear it. A nonempty phone accepts an optional leading `+`, digits, spaces and hyphens; it must have 7–15 digits and at most 32 characters. The server stores it without spaces or hyphens. `version` is the nonnegative value from the last GET response. The response is `200` with the same safe fields and a new version.

Unsupported fields such as `email`, `roles`, `active`, `passwordHash`, `authorizationVersion`, or another `userId` are rejected. Invalid input returns `400` (`VALIDATION_FAILED` with field errors, or `INVALID_REQUEST` for malformed/extra fields). A stale version returns `409 PROFILE_CONFLICT`; reload before retrying. Authentication/session failure returns `401`. CSRF/origin failure follows the shared security response.

The profile UI is at `/profile`. Email and roles are read-only. Password changes, address book, merchant details and courier operations belong to separate features.

## Verification

From `backend/`, run `./mvnw -Dtest=ProfileServiceTest test` (on Windows, `./mvnw.cmd`). With Docker Desktop running, `./mvnw -Pintegration -Dit.test=IdentitySessionsIT verify` runs the identity integration tests against isolated PostgreSQL through Testcontainers. From `frontend/`, run `npm test` and `npm run build`.
