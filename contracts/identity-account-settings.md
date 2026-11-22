# Account security and saved addresses

The user authorised verified email change, password change and a saved address
book after the profile UI review. This supersedes the old profile-only scope;
PATCH /me still accepts only fullName, phone and version.

## Reviewed policy and API choices

- POST /api/v1/me/security/email-change accepts email and current password, or
  a single-use Google reauthentication token. It returns a generic 202. The old
  email stays current until POST /api/v1/auth/email-change/confirm receives the
  token from the new inbox. Token lifetime: 30 minutes; only its SHA-256 hash is
  stored. Duplicate-email races remain governed by normalized DB uniqueness.
- POST /api/v1/me/security/password accepts currentPassword and password, or
  a Google reauthentication token for a Google-only account setting a password.
  Preserve the 8?128 Unicode-code-point policy and adaptive encoder.
- POST /api/v1/me/security/google-reauth creates a five-minute, session-bound
  server-side OIDC intent. Only the already-linked verified Google subject and
  a recent signed auth_time can satisfy it; email matching never links accounts.
  The callback returns a hashed-in-storage single-use five-minute proof through
  a fragment, consumed into React memory and removed from the URL. Provider
  metadata must support auth_time; missing/stale claims fail closed.
- Password/email changes increment authorization version, revoke all sessions,
  invalidate pending action/reset/verification tokens, append a safe audit event,
  and notify the current/old inbox after commit. No passwords/tokens/email
  addresses are stored in audit payloads. Role membership and Google links stay
  unchanged. Confirmation needs inbox possession even after reauthentication.
- Security actions are throttled by current user; public confirmation is also
  source-throttled and CSRF/origin-protected. Authentication errors for step-up
  are 403, never 401 (avoid accidental refresh/replay of a sensitive mutation).
- GET/POST /api/v1/me/addresses and PUT/DELETE /api/v1/me/addresses/{id} use the
  current verified account, never client owner IDs. PUT includes version;
  DELETE uses ?version=. Cross-owner IDs return 404. Mutations lock the owner
  before addresses, enforce at most 20 records and one default pickup address.
  Address fields: label, contactName, contactPhone, addressLine1, addressLine2,
  city, postalCode, defaultPickup. Address responses are DTOs, never entities.
- Booking selectors copy contacts/addresses into the booking draft and its
  existing immutable snapshots. No address FK rewrites historical bookings.
- V13 is additive and must be coordinated against the team's single migration
  sequence before shared deployment. Do not edit any applied migration. Existing
  SMTP settings are reused; no migration is applied by the agent.

Google freshness reference: https://developers.google.com/identity/openid-connect/reference

Tests/build and live SMTP/Google checks are left for the user to execute.
