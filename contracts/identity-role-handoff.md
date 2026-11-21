# Member 1 role handoff

Role changes are identity-owned. Public registration still grants only `CUSTOMER`. Neither merchant approval nor a courier profile should write `user_roles` directly. All grants change `users.authorization_version`, revoke the target's active sessions, and append a safe record to `identity_audit_events` in the same database transaction. The target must sign in again. No Flyway migration is needed.

## First administrator

1. Register an account and verify its email. Stop using its current session.
2. In private `backend/.env`, set `BOOTSTRAP_ADMIN_ENABLED=true` and `BOOTSTRAP_ADMIN_EMAIL` to that account's address. These settings are not frontend variables and must not be committed.
3. From the repository root run `docker compose up --detach --force-recreate --wait backend`. Startup locks the seeded admin role, requires an active verified account and no existing administrator, grants `DISPATCHER_ADMIN`, revokes its previous sessions, and records `BOOTSTRAP_ADMIN` with a `SYSTEM` actor. No password is created or logged.
4. Remove or set `BOOTSTRAP_ADMIN_ENABLED=false` in `backend/.env`, then run `docker compose up --detach --force-recreate --wait backend` again. Sign in afresh with the account's existing password or Google login.

The audit record prevents a second bootstrap from promoting another account, including after a restart. If an administrator already exists without a bootstrap audit record, startup refuses the bootstrap. Do not delete audit rows or seed a default password to bypass this.

## Merchant approval (Member 2)

`MerchantReviewService.approve` persists the M2-owned `APPROVED` transition, then calls `IdentityRoleManagement.grantMerchantForApprovedOwner(ownerUserId, reason)` within the same Spring transaction. The M1 service checks the current `DISPATCHER_ADMIN`, locks the owner, grants `MERCHANT`, revokes sessions and writes an audit record. Any failure rolls back both approval and role grant. M2's live approval status remains the merchant API access gate; `MERCHANT` alone is insufficient. Rejection does not grant a role. For a future M2 suspension/reversal transition, call `revokeMerchantForOwner(ownerUserId, reason)` in that same transaction; it removes the role if present, invalidates sessions and audits the access change. No suspension endpoint is published yet.

## Courier onboarding (Member 5)

An active `DISPATCHER_ADMIN` first calls `GET /api/v1/admin/users/{id}/roles` for the target's current roles and `version`. Then call `POST /api/v1/admin/users/{id}/courier-role` with JSON `{ "reason": "Approved courier onboarding", "expectedVersion": 0 }` using that version, a bearer access token, and the normal CSRF token/origin. The target must be an existing active, verified account. Success returns `200` with `id`, current `roles`, and updated `version`. Missing/invalid input returns `400`; anonymous `401`; wrong role/CSRF `403`; changed version, unavailable account, or an existing `COURIER` grant `409 ROLE_CHANGE_CONFLICT`. There is no public courier signup or role picker.

The role grant makes the account eligible for M5's separate courier operational profile. M5 owns vehicle, availability, capacity and assignment rules. Dispatch should use the trusted `IdentityAccountService.requireActiveCourier` check before using a target ID; it must also check M5's operational profile.

## Verification

From `backend/` run `./mvnw.cmd --batch-mode --no-transfer-progress -Pintegration '-Dtest=MerchantReviewServiceTest' '-Dit.test=IdentityRoleIT' verify` on Windows. The integration test uses disposable PostgreSQL, checks role persistence, audit, admin-only access, and invalidation of prior sessions. It does not use Neon credentials.
