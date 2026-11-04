# IAM-01 registration contract

Implemented in the single SwiftDrop backend. Requests use same-origin `/api` URLs
through Vite/Nginx. No login is required. HTTPS is required for deployment.

## 1. Prepare CSRF protection

`GET /api/v1/auth/csrf` returns `200`, `Cache-Control: no-store` and:

```json
{"token":"<masked CSRF token>","headerName":"X-XSRF-TOKEN"}
```

Keep the response's `XSRF-TOKEN` cookie in the HTTP client's cookie jar and send
the returned token using the returned header name on registration. The cookie
is HttpOnly, host-only, SameSite=Lax, Path=/ and Secure on HTTPS. It is a CSRF
association, not a signed-in session. The frontend fetches it before each attempt;
JavaScript does not read the cookie. No cross-origin CORS allowance is configured.

## 2. Register

`POST /api/v1/auth/register`, `Content-Type: application/json`:

```json
{
  "fullName": "Example Customer",
  "email": "customer@example.test",
  "password": "<your 8-128 character passphrase>"
}
```

Exactly these three fields are accepted. Additional fields, including `roles`,
`active`, `accountStatus`, `id`, `authorizationVersion`, `passwordHash` and
`confirmPassword`, return `400 INVALID_REQUEST`. Missing/null/invalid fields
return validation errors. Confirmation exists only in the React form's memory.

| Field | Rule |
| --- | --- |
| `fullName` | Required, nonblank after surrounding whitespace removal, maximum 200 UTF-16 code units. |
| `email` | Required, valid Bean Validation email, maximum 320 UTF-16 code units. Ordinary surrounding spaces are removed; PostgreSQL `lower(btrim(email))` determines the canonical stored/returned address and database uniqueness. No provider-specific dot/plus rewriting. |
| `password` | Required, 8–128 Unicode code points. Spaces and Unicode allowed; no forced character classes. Never trimmed, case-folded, normalized or truncated. |

Successful response: **201 Created**, `Cache-Control: no-store`:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "email": "customer@example.test",
  "fullName": "Example Customer",
  "roles": ["CUSTOMER"]
}
```

The UUID above is illustrative. The server generates the real ID and assigns
CUSTOMER. It creates no auth session, JWT or refresh token and does not log the
user in. Local email ownership is not verified by registration.

## Errors

All error responses use the same shape; no submitted values or SQL details:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Check the highlighted fields.",
  "correlationId": "<generated UUID>",
  "fieldErrors": [{"field": "password", "message": "Password must contain at least 8 characters."}]
}
```

`fieldErrors` is empty for errors unrelated to a particular input field.
Password feedback distinguishes missing input (`Enter a password.`), fewer than
8 characters and more than 128 characters. These messages do not echo passwords.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Missing/invalid fields or password length. |
| 400 | `INVALID_REQUEST` | Unknown fields, malformed JSON or unreadable request. |
| 403 | `FORBIDDEN` | Missing/invalid CSRF protection. |
| 409 | `REGISTRATION_UNAVAILABLE` | Database-enforced email conflict, including a concurrent signup. Generic message; does not identify the account/provider. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Use JSON. |
| 429 | `RATE_LIMITED` | Retry after 600 seconds. |
| 500 | `INTERNAL_ERROR` | Failed account creation; transaction rolls back. |

A distinct 409 still reveals that an address cannot be registered; it is not full
account-enumeration protection. Signup currently allows 20 CSRF-valid requests per
remote address per ten-minute window per backend process. Untrusted forwarding
headers are ignored; through a proxy, clients can share one limit. Deployment
needs trusted proxy/gateway rate limiting before scaling. No password, hash,
submitted request body or raw database exception is logged by this feature.

See [implementation and run guide](../docs/identity/registration.md) for the actual
files, tests and implementation decisions. GET health and CSRF, and POST register
are public; other backend routes remain denied by default.
