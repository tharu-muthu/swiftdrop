\# Merchant API contract (M2)



Owner: M2. Status: registration, approval, API keys and key authentication are implemented. Quote and order endpoints are reserved and \*\*not implemented yet\*\* (they need M3 pricing and M4 booking).



All paths are under `/api/v1`. Errors use the shared shape `{code, message, correlationId, fieldErrors}`.



\## Authentication modes



| Mode | Used by | How |

| --- | --- | --- |

| Signed-in user | Registration, API keys, admin review | Bearer access JWT, plus CSRF header on POST |

| Partner API key | `/merchant/verify`, reserved `/merchant/quotes`, `/merchant/orders` | `X-API-Key` header. No cookies, no CSRF |



The merchant is \*\*always\*\* derived from the session or the key. A `merchantId` in a request body is never trusted.



\## Merchant states



`PENDING` (on registration), `APPROVED`, `REJECTED` (reason required), `SUSPENDED` (reserved, no endpoint yet). Only `APPROVED` businesses can create keys or use the partner API. Approval status is the gate, not the MERCHANT role, which is not granted on approval yet (pending an M1 role-grant method).



\## Owner endpoints (signed-in user)



| Method and path | Who | Request | Success | Errors |

| --- | --- | --- | --- | --- |

| `POST /merchant/registration` | CUSTOMER | `businessName` (required, max 150), `contactEmail` (required, valid), `contactPhone` (optional), `businessAddress` (optional) | 201 `MerchantResponse` | 400 validation, 401, 403, 409 `MERCHANT\_ALREADY\_REGISTERED` |

| `GET /merchant/registration` | Owner | none | 200 `MerchantResponse` | 401, 404 (never applied) |

| `POST /merchant/api-keys` | Owner of an APPROVED business | none | 201 `ApiKeyCreatedResponse` (key shown once) | 403 `MERCHANT\_NOT\_APPROVED`, 404 `MERCHANT\_NOT\_FOUND`, 409 `API\_KEY\_LIMIT\_REACHED` (max 5 active) |

| `GET /merchant/api-keys` | Owner | none | 200 list of `ApiKeySummary` (no secrets) | 404 `MERCHANT\_NOT\_FOUND` |

| `POST /merchant/api-keys/{id}/revoke` | Owner | none | 200 `ApiKeySummary` | 404 `API\_KEY\_NOT\_FOUND` (also for another merchant's key), 400 bad id |



`MerchantResponse`: `id, businessName, contactEmail, contactPhone, businessAddress, approvalStatus, reviewReason, createdAt`.

`ApiKeySummary`: `id, keyPrefix, scopes, createdAt, lastUsedAt, revokedAt, active`.

`ApiKeyCreatedResponse`: `credential` (an `ApiKeySummary`), `apiKey`, `notice`.



\## Admin endpoints (DISPATCHER\_ADMIN)



| Method and path | Request | Success | Errors |

| --- | --- | --- | --- |

| `GET /admin/merchants?status=\&page=\&size=` | `status` optional, `size` default 50, max 100 | 200 `MerchantPageResponse` | 401, 403 |

| `POST /admin/merchants/{id}/approve` | none | 200 `MerchantAdminResponse` | 403, 404 `MERCHANT\_NOT\_FOUND`, 409 `MERCHANT\_ALREADY\_DECIDED` |

| `POST /admin/merchants/{id}/reject` | `reason` (required, max 500) | 200 `MerchantAdminResponse` | 400, 403, 404, 409 |



A decision can be made once. Two admins deciding at the same time cannot both succeed (row lock).



\## Partner API (X-API-Key)



Key format: `sdk\_` + 10 hex characters + `.` + 43 URL-safe characters. The key is shown once at creation. Only its prefix and SHA-256 hash are stored. Treat it like a password and never log it.



| Method and path | Scope | Success | Errors |

| --- | --- | --- | --- |

| `GET /merchant/verify` | any valid key | 200 `{merchantId, businessName, scopes}` | 401 `INVALID\_API\_KEY`, 403 `MERCHANT\_NOT\_APPROVED`, 429 `RATE\_LIMITED` |

| `/merchant/quotes`, `/merchant/quotes/\*\*` | `quote` | \*\*reserved\*\* (MER-04) | 403 `FORBIDDEN` if the key lacks the scope |

| `/merchant/orders`, `/merchant/orders/\*\*` | `order` | \*\*reserved\*\* (MER-05) | 403 `FORBIDDEN` if the key lacks the scope |



Checked on \*\*every\*\* request: key format, prefix lookup, constant-time hash comparison, key not revoked, business still APPROVED. Revoking a key or suspending a business takes effect on the next request.



Limits per source address: 300 requests per minute, and 10 failed key attempts per minute (then 429 with `Retry-After`).



Example:



```bash

curl -i -H "X-API-Key: sdk\_0123456789.<secret>" https://<host>/api/v1/merchant/verify

```



\## For other modules: MerchantAccessService



`com.swiftdrop.service.merchant.MerchantAccessService` (in-process, read-only):



| Method | Use it when | Returns | Fails with |

| --- | --- | --- | --- |

| `requireCurrentApiMerchant()` | The request was authenticated by an API key (M4 order creation, M9 test callbacks) | `MerchantContext(merchantId, ownerUserId, businessName, credentialId, scopes)` | `InvalidApiKeyException` (401), `MerchantNotApprovedException` (403) |

| `requireApprovedOwnerMerchant()` | A signed-in owner is acting (dashboard, M9 webhook settings) | `MerchantContext` (credentialId null) | `MerchantNotFoundException` (404), `MerchantNotApprovedException` (403) |



Both re-read live key and approval state. Never cache the result for authorization, never accept a merchant id from a request body, and bind `externalReference` and idempotency keys to `merchantId`.



\## Tables (V6)



`merchants(id, owner\_user\_id UNIQUE FK users, business\_name, contact\_email, contact\_phone, business\_address, approval\_status, review\_reason, reviewed\_by, reviewed\_at, version, created\_at, updated\_at)` and `api\_credentials(id, merchant\_id FK, key\_prefix UNIQUE, secret\_hash, scopes, created\_at, last\_used\_at, revoked\_at)`.



\## Open items



1\. M4: `parcel\_bookings` needs `merchant\_id` (customer XOR merchant), `external\_reference` and an idempotency key, plus a `BookingService` interface, before MER-05 and MER-06.

2\. M3: `PricingService` interface for MER-04.

3\. M1: [merchant role grant and first-admin bootstrap](identity-role-handoff.md) are implemented. A verified first-admin account must still be bootstrapped in each environment before an administrator can approve merchants.

4\. Suspend endpoint, and an audit table (`api\_request\_audit`), are not built yet.

