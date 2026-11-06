# Shared identity integration: Member 1 handoff

## Available now

One Spring application, one shared schema. Backend features inject the Java
`com.swiftdrop.service.identity.IdentityAccountService` interface. These are
in-process operations, not new HTTP endpoints. Other features must not inject M1
repositories, copy session validation or create parallel user tables.

| Operation | Caller and result | Failure |
| --- | --- | --- |
| `requireCurrentIdentity()` | Any authenticated application user. Returns current `IdentityContext(userId, sessionId, roles, authorizationVersion)`. | Missing/wrong principal: authentication exception; inactive/revoked/expired/version mismatch: identity authentication exception. |
| `requireCurrentRole(IdentityRole role)` | Current authenticated user must have the requested role. Returns the current context. Multiple-role users are supported. | Missing role: `AccessDeniedException`. |
| `requireActiveCourier(UUID userId)` | Current active `DISPATCHER_ADMIN` only. Returns `AccountSummary(id, fullName, active, roles)` for an active COURIER account. | Unauthorized caller: denied before target lookup. Missing/inactive/non-courier target: same access-denied failure. |

Roles: `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER_ADMIN`. Checks reload live
account/session/version/role state through existing identity services. The
security context must contain the application's validated `IdentityContext`
principal, not a mock name, anonymous principal or an OAuth framework principal.
Do not construct this context from JSON, query parameters or headers.

All operations are read-only, using normal REQUIRED transaction participation.
Returned role sets are immutable snapshots. They contain no password hashes,
access/refresh tokens, email or phone. Do not cache them for authorization.
Exceptions propagate: missing authentication/session maps to safe HTTP 401;
permission/target rejection maps to safe HTTP 403. Do not catch and bypass them.
DB failures propagate and must never be treated as permission granted.

This is a synchronous HTTP-request contract. It fails closed in background jobs
without an authenticated application principal. A separate reviewed machine/job
authorization contract is needed for async consumers; do not fabricate a user or
copy the security context into another thread. Current-state checks cannot cancel
an operation already executing when another transaction suspends the account.

## Backend recipe: the first feature

Inject the interface into your feature SERVICE using constructor injection:

```java
private final IdentityAccountService identities;

public BookingService(IdentityAccountService identities /* your own dependencies */) {
    this.identities = identities;
}

// Inside your transactional create method:
var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
// Persist customer.userId() as owner; never accept an owner ID from the browser.
```

This is an integration recipe, not an implemented booking API. M4 supplies the
actual request DTO, booking rules, persistence, controller and tests.

For access to an existing record, verify role AND ownership in its service:

```java
var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
var booking = /* load from your own booking repository */;
if (!booking.getCustomerId().equals(customer.userId())) {
    throw new AccessDeniedException("Access denied");
}
```

Imports: `com.swiftdrop.service.identity.IdentityAccountService`,
`com.swiftdrop.dto.identity.IdentityRole`, Spring's `AccessDeniedException`.
Controllers validate request DTOs and call services. An existing controller may
receive `@AuthenticationPrincipal IdentityContext`; keep service authorization via
the shared contract so it is not bypassed by another controller. Do not expose a
principal or JPA entity as a response. Public feature responses need their own DTO.

M5 dispatch example: call `identities.requireActiveCourier(courierUserId)` before
checking the M5-owned courier profile, availability, capacity and assignment.
The selected courier ID is a TARGET, never the acting user. M5 still authorizes
the dispatch operation and enforces its own resource constraints.

New APIs remain denied by default. Coordinate the exact method/path permission
in `SecurityConfig` with M1 and add HTTP security tests. Do not use broad
`permitAll`, disable CSRF or create a public account-directory endpoint.

## React recipe

Use `useAuth` from `src/features/identity/AuthProvider.jsx`, beneath the existing
provider. Never implement another token store or refresh loop:

```jsx
const { status, user, client } = useAuth();

// Call in your submit handler after authentication has completed:
const result = await client.request('/api/v1/bookings', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ /* agreed booking fields, no acting userId/roles */ }),
});
```

The example API is not available until M4 implements it. `client.request` accepts
relative `/api/` business paths and returns parsed JSON (not a Fetch Response).
It attaches bearer credentials and mutation CSRF headers, coordinates refresh,
retries a 401 at most once and never refreshes on 403. Use existing
`client.login`, `client.logout` and provider bootstrap for authentication paths.
Handle loading, pending, field errors and denied/expired states. Rendering controls
based on `user.roles` is UI only; the backend must enforce permission independently.
Never put tokens in localStorage, VITE variables, screenshots or logs.

## Pending: do not implement workarounds

- No general user directory, account administration or profile-update contract.
- No role grant/revoke API. M2 must agree an approval-to-role operation with M1;
  merchant approval remains M2-owned. MERCHANT role alone does not prove approval.
- No first-admin or courier account provisioning procedure is supplied here.
  Automated fixtures grant roles only in disposable test databases. Never seed a
  default admin password or manually edit production role tables to unblock a demo.
- Future role/status mutations must authorize the actor, lock user before session,
  update authorization version, revoke sessions and append safe audit records in
  the same transaction. Define the M2 transaction boundary before implementing it.
- Password recovery, Google linking and business features remain separate work.

## Team handoff checklist

1. M1 opens a PR into `dev` containing these contracts, implementation and tests.
   Review with M2/M5. Push/merge are team actions; this document does not do them.
2. Each member saves their work, updates from `dev`, and creates a small feature
   branch. Use README setup with private credentials for their own environment.
3. Agree a feature brief: owner, method/path, request/response/error DTOs, caller
   roles, record ownership, dependencies and migration reservation (or none).
4. Follow the backend/React recipes for one small working story. Open a draft PR
   early, before building the whole module. Shared-file changes need owner review.
5. Test missing auth, wrong role, another user's record, invalid data and rollback,
   in addition to success. Preserve data and already-applied migrations.
6. Update the feature branch from `dev`, resolve conflicts, run checks, then
   request review. Only merge passing, understood changes.

Before handing this to everyone, have one teammate try setup from a fresh clone
and build their first slice with M1. That human handoff is still a manual step.

## Verification commands (PowerShell, project root)

```powershell
cd backend
# Explicit fixture setting prevents private enabled Google config affecting tests.
.\mvnw.cmd "-Dswiftdrop.google.enabled=false" test
# Docker required: disposable PostgreSQL/RabbitMQ, never the private Neon branch.
.\mvnw.cmd "-Dswiftdrop.google.enabled=false" -Pintegration verify
cd ..
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
git diff --check
```

CI/branch protection is recommended but is not installed by this change. Record
actual passed/failed/unrun checks in each PR. Database/async contracts and migration
versions must be coordinated before another member applies them.

Recorded for this change (6 November 2026): 52 backend unit/security tests and
48 PostgreSQL/broker integration tests passed, and backend packaging succeeded.
The added PostgreSQL case verifies live role lookup, inactive target rejection and
session revocation. Host frontend checks could not start because `vitest` was
unavailable (dependencies not installed); no frontend source changed. Application
Docker images/browser flows were not rebuilt/retested for this service-only
change. Fresh-clone teammate onboarding, PR review and merge remain manual.
