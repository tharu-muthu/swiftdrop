# IAM-02 / IAM-07: login and persistent sessions

These endpoints are implemented. See [setup and request flow](../docs/identity/sessions.md).
All authentication responses have `Cache-Control: no-store`. Errors use
`{code,message,correlationId,fieldErrors}` without credentials or SQL details.

| Endpoint | Request / protection | Response |
| --- | --- | --- |
| GET `/api/v1/auth/csrf` | Same-origin browser request | 200 `{token,headerName}` and HttpOnly CSRF cookie. |
| POST `/api/v1/auth/login` | JSON `{email,password}`, CSRF header/cookie and allowed Origin | 200 `{accessToken,tokenType:"Bearer",expiresIn:600}` plus refresh cookie. Unknown email, wrong password, suspended and Google-only accounts all return 401 `INVALID_CREDENTIALS` with the same message. Unknown fields/malformed input return 400. |
| POST `/api/v1/auth/refresh` | No body; refresh cookie, CSRF header/cookie and allowed Origin | 200 TokenResponse and rotated refresh cookie; invalid/expired/revoked/reused token returns 401 `SESSION_EXPIRED` and clears the cookie. |
| POST `/api/v1/auth/logout` | No body; refresh cookie, CSRF header/cookie and allowed Origin; no access JWT needed | 204, revoke that session and clear the cookie. Missing, unknown or previously revoked cookie is idempotent. |
| GET `/api/v1/me` | `Authorization: Bearer <accessToken>` | 200 `{id,email,fullName,phone,roles,active,createdAt,updatedAt,version}`; invalid identity/session returns 401. |

Fetch CSRF before each auth POST. Send its returned token in the returned header
name and retain its cookie. Non-browser API clients must additionally send an
`Origin` exactly matching the configured allowed origins. Missing/disallowed
origins or missing/invalid CSRF return 403. There is no cross-origin CORS allowance.
The shared client never refreshes automatically after 403.

Access tokens use **HS256 only**, through Spring Security/Nimbus JOSE. The decoder
verifies the signature, issuer, audience, expiry, issue time, UUID `sub`/`sid`/`jti`
and integer authorization-version `av`. Default lifetime is ten minutes; it is
clamped to the session's absolute expiry. Every protected request additionally
checks the active user, unrevoked/unexpired session and matching current database
authorization version. Roles are read from PostgreSQL, not trusted JWT role claims.
Unknown/unimplemented routes remain denied even to authenticated users.

The refresh cookie is `swiftdrop_refresh`, HttpOnly, host-only, SameSite=Lax,
Path=`/api/v1/auth`, with Max-Age bounded by the remaining absolute session life.
Secure is always set unless explicit loopback development mode is enabled. The
CSRF cookie is HttpOnly, SameSite=Lax, Path=/ with the same Secure policy.

Refresh secrets contain 32 random bytes encoded with Base64url. Only SHA-256 hashes
are stored. Transactions lock user then session, consume the old token, insert a
successor and link the retained history. Expiry stays seven days from login by
default and never slides. Confirmed consumed-token reuse revokes the whole family
and commits before returning 401. With two simultaneous requests using the same
old token, only one rotation succeeds; the second revokes the family, including
the winner's replacement. There is deliberately no replay grace interval.
Unknown random tokens do not revoke unrelated sessions. Database errors roll back
rotation; protected requests fail closed if state cannot be checked.

Rate limits are bounded, process-local fixed windows using the actual connection
address; untrusted forwarding headers are ignored. Login also uses a hash of the
database-normalized email. Limits apply to attempts, including invalid ones:

| Operation | Default limit |
| --- | --- |
| Login per source | 10/minute (`swiftdrop.auth.source-login-limit`) |
| Login per normalized email | 10/five minutes (`swiftdrop.auth.account-login-limit`) |
| Refresh / logout per source | 60/minute each |
| CSRF bootstrap per source | 120/minute |
| Registration | Existing 20/ten minutes |

429 includes `Retry-After`. Counters expire and reset on backend restart; there
is no permanent account lockout. Proxied clients can share a source quota. A
multi-instance deployment needs trusted gateway/shared rate limiting.

API security uses a request-only security context. A separate security chain
retains framework HTTP-session support for future OIDC. Existing framework state
is neither used as an API bearer identity nor invalidated by application logout.
Google login is not implemented in this slice.
