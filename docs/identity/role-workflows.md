# Role workspaces and onboarding

One identity can hold CUSTOMER, MERCHANT, COURIER and DISPATCHER_ADMIN memberships. Public registration and new Google accounts still grant CUSTOMER only. Existing credentials are reused for every assigned workspace. No migration or default account is introduced.

## Sign in and use access

Password sign-in and Google completion both enter `/workspaces`. Users with exactly one assigned role go directly to its portal (CUSTOMER: `/booking`, MERCHANT: `/merchant`, COURIER: `/courier`, DISPATCHER_ADMIN: `/dispatch`), replacing the landing history entry. Users with multiple roles see “Choose your portal” with only their assigned roles. “Switch portal” appears only for those users. Refreshing a portal keeps its current route. `/account` and `/profile` remain profile aliases. Shared navigation hides functions for unassigned roles. Unassigned protected screens show an access message; unknown signed-in paths show a missing-page screen. Portal selection is navigation; it does not change memberships or server authorization.

- CUSTOMER: open Customer, create a booking, review bookings and receipts.
- MERCHANT: open Merchant for orders, credentials and webhook settings. Live business approval and account/role checks still gate server operations.
- COURIER: open Courier for assigned deliveries and status updates. Active operational profiles are required for dispatch eligibility.
- DISPATCHER_ADMIN: open Administration & dispatch; use navigation for users, business approvals, pricing and notifications.

## Assign access

Portal redirects are resolved by the app router, including repeated login landing navigation. Portal cards and shared navigation use same-document links so switching does not repeat cookie refresh and `/me`. A full browser reload still restores and validates the session before showing protected content.

1. The person registers and verifies email (or uses verified Google signup).
2. An administrator opens User management and searches name/email.
3. Select View, choose CUSTOMER, COURIER or DISPATCHER_ADMIN, choose Grant/Remove and enter a reason.
4. The API uses the displayed record version. Conflicts require Reload and review. Self-removal of administrator access, removal of the last active administrator and removal of the only remaining role are rejected.
5. Every successful membership change increments the authorization version, revokes sessions and writes an audit record in the same transaction. The person signs in again and sees the updated workspace cards. Changes to the administrator's own account return them to sign-in.

For COURIER, the selected account now includes a Courier setup form. Existing profile details load before editing. Supply vehicle type, optional license plate and availability, then save. Identity owns the membership; M5 dispatch owns this profile through its existing API. The two steps are deliberately visible and independently retryable: a granted role without a profile is not operationally eligible. Open dispatch to assign deliveries.

Vehicle type is now a shared dropdown: Bicycle, Electric bicycle, Motorcycle, Scooter, Three-wheeler, Car, Van, Pickup truck and Light truck. New profiles store stable uppercase codes. Historical free-text values remain visible and can be retained while editing; selecting a standard option normalizes that profile. No migration rewrites existing data.

MERCHANT is excluded from direct role editing. A CUSTOMER can open Merchant application to submit business details or review their status. An administrator uses Merchant approvals. Approval and the identity grant remain atomic; the owner then signs in again. Rejection does not grant access.

The first administrator is provisioned only through the private startup bootstrap described in `contracts/identity-role-handoff.md`. There is no public administrator signup, role picker, credential seed or invitation endpoint.

## API compatibility

The canonical role editor uses `POST /api/v1/admin/users/{id}/roles/grant` and `/roles/revoke` with role, reason and required expectedVersion. Existing courier-role and operational-roles endpoints remain as compatibility adapters and use the same identity policy. Merchant changes continue through their separate service contract. See `contracts/identity-admin-users.md`.

## Verification

Frontend regression tests cover workspace visibility, direct unauthorized routes, several assigned roles, missing pages, login destination, account role changes and courier profile loading/saving/errors. Backend tests cover role policy and the HTTP role editor, including version validation, session revocation and concurrent administrator removals on disposable PostgreSQL. Run the README commands, including Docker integration verification. Live Google provider, SMTP and private Neon delivery/configuration require environment-specific smoke checks.

## Assignment access review

See [the assignment-aligned access matrix](rbac-assignment-review.md) for current route/service requirements, merchant approval and membership checks, courier daily work with carryover, and before-pickup reassignment. Administrative access does not implicitly grant other roles.
