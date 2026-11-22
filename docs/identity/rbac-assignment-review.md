# Assignment-aligned role and resource access

## Authority and scope

Reviewed the assignment's Customer/Merchant sections (page 1), Courier, Dispatcher/Admin and System Requirements (page 2), and authorization testing requirement (page 3). The v3 design's security scope (page 10), dispatch boundaries (page 22) and carried-over courier tasks (page 23) supply implementation detail. References remain unchanged.

The assignment requires four role scopes. It does not require separate accounts or login pages per role, public staff signup, an administrator bypass of ownership, or a photo uploader when recipient-name proof is used. One verified identity can hold several memberships; permissions are the union of those memberships. An administrator without CUSTOMER cannot use customer booking APIs, and a courier without CUSTOMER cannot use customer pricing/history.

## Access matrix

| Surface | Required access | Resource check |
| --- | --- | --- |
| Health, auth entry points, public tracking/SSE | Public through explicit implemented routes | Tracking excludes contact details, addresses and actor IDs; mutation entry points retain CSRF/origin controls |
| Profile and Google linking | Authenticated, active, verified, usable session | Current identity only |
| Customer quotes, dashboard, bookings, receipts, private tracking | CUSTOMER | Server-derived customer ID; own quote/booking/receipt only |
| New merchant application | CUSTOMER | Current account owns the application; it receives no merchant permissions while pending |
| Application status/contact editing | CUSTOMER or MERCHANT | Current account's application only |
| Business dashboard orders, browser API-key and webhook management | MERCHANT | Approved own business and active verified merchant membership |
| Partner order/quote API | Live merchant API key with required scope | Approved business and active verified owner with MERCHANT; keys are not browser sessions |
| Courier workday, detail and updates | COURIER | Current courier only; detail/update lookup also includes the current assigned courier ID |
| Users, role changes, business approval, pricing, dispatch, notifications and integration recovery | DISPATCHER_ADMIN | Administrative services revalidate the current identity; no implicit customer/merchant/courier privileges |
| Unimplemented routes/methods | Denied | No catch-all authenticated allowance |

Route-level requirements and service-level ownership checks both apply. Browser mutations require CSRF even when a valid Bearer token is present; the resource-server default exemption is overridden on the browser chain. The separate server-to-server API-key chain remains cookie-free and CSRF-exempt. React navigation mirrors those requirements, including hiding merchant application links from staff-only accounts. UI checks do not authorize API access.

## Role lifecycle

Public password/Google signup remains CUSTOMER only. Password accounts verify their inbox before authentication. Courier and administrator grants require an administrator, active verified target, expected record version and reason. The canonical role editor and compatibility endpoints share identity-owned policy, audit, authorization-version increment and session revocation. Missing compatibility endpoint grant/version values and unknown role-edit fields are rejected. Administrator self-removal, removal of the last active administrator and removal of an account's only role remain blocked.

M2 approval atomically grants MERCHANT; generic role editing cannot grant it. M5 separately manages courier vehicle/availability profiles for eligible identities. First-admin bootstrap remains a private, explicitly enabled startup action. No default accounts, role-selecting signup or new migration is introduced.

JWT validation checks current persisted account/session state and reloads roles. Role changes invalidate prior sessions; reauthentication loads current access. Partner API-key authentication independently checks current merchant account membership, so a key cannot bypass a removed role while the business row still says APPROVED. Browser key management also checks membership and approval.

## Courier workday and reassignment

`GET /api/v1/courier/deliveries?date=YYYY-MM-DD` defaults to today in Asia/Colombo. Today's list includes unfinished current assignments from earlier days, plus assignments or courier activity on that date. Earlier dates show assignment/activity on the selected day, but only while the parcel remains assigned to the requesting courier. Future dates return 400. The UI provides a date selector, Today and Refresh.

There is no scheduled-delivery-date field in the implemented schema. The workday therefore uses existing assignment and status-history timestamps; this explicitly implements v3 EXE-01's carried-over work instead of hiding overdue parcels. M6 obtains M5 assignment IDs through `CourierAssignmentQuery`, not a cross-feature repository. Times remain stored in UTC and day boundaries use Asia/Colombo.

Reassignment is permitted only while CREATED. The canonical lifecycle lock enforces this together with courier updates, so a pickup/reassignment race cannot silently transfer a parcel after pickup. Reassignment immediately removes the previous courier's detail/update access, including on historical workday screens. A rejected update clears stale delivery details in React.

## Verification

`RoleAccessSecurityTest` checks the route/role matrix, including anonymous requests, through the actual security chain. Service tests cover customer-only quotes, merchant-role/approval prerequisites, application access, workday boundaries and reassignment guards. `RbacAuthorizationIT` uses real JWT login and disposable PostgreSQL to check role-only accounts, courier/customer ownership, private/public tracking, dated carryover, reassignment, role/session revocation and live merchant API-key denial after membership removal. A real-JWT mutation without CSRF must return 403 and leave role memberships unchanged. Existing integration tests retain session rotation, admin concurrency, approval, event and webhook coverage.

Run the frontend tests/build and Maven verification/integration commands from README. Browser/provider delivery checks and production deployment are separate from these automated checks.

Spring Security's default Bearer CSRF exemption and the supported filter post-processor override are documented in [the upstream Spring Security issue](https://github.com/spring-projects/spring-security/issues/8668). The browser client already supplies CSRF tokens; callers previously relying on the exemption must now use the documented CSRF flow.

### Verification record (22 November 2026)

- Passed: 99 frontend tests and the production Vite build.
- Passed: 517 backend unit/security tests in clean verification, including 330 route/role matrix cases.
- Full Docker integration verification exercised 92 PostgreSQL/RabbitMQ tests. It caught the Bearer CSRF exemption and an outdated merchant-denial expectation. After correcting those, the two affected suites were rerun: 6 integration tests and 337 unit/security tests passed with BUILD SUCCESS. The latest XML reports collectively contain 92 passing integration tests, zero failures/errors/skips.
- Passed: git diff whitespace validation. Reports are in `backend/target/surefire-reports/`, `backend/target/failsafe-reports/`, and `backend/target/site/jacoco/`.
- Not run in this change: live Google/SMTP delivery, manual browser smoke checks, and deployment of the updated application image. No new migration or private credential change was needed.
