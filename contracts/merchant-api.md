
# Merchant API contract (M2)

Owner: M2. Implemented: registration, approval (with the MERCHANT role grant), API keys, API key authentication and merchant quotes. The order endpoints are reserved and **not implemented yet** (they need M4 booking).

All paths are under `/api/v1`. Errors use the shared shape `{code, message, correlationId, fieldErrors}`.

## Authentication modes

| Mode | Used by | How |
| --- | --- | --- |
| Signed-in user | Registration, API keys, admin review | Bearer access JWT, plus CSRF header on POST |
| Partner API key | `/merchant/verify`, `/merchant/quotes`, reserved `/merchant/orders` | `X-API-Key` header. No cookies, no CSRF |

The merchant is **always** derived from the session or the key. A `merchantId` in a request body is never trusted.

## Merchant states

`PENDING` (on registration), `APPROVED`, `REJECTED` (reason required), `SUSPENDED` (reserved, no endpoint yet).

Only `APPROVED` businesses can create keys or use the partner API. Approving an application also grants the owner the `MERCHANT` role through M1's identity service in the same transaction, and revokes the owner's sessions, so the owner must sign in again. The live approval status stays the access gate. The `MERCHANT` role alone is not enough.

## Owner endpoints (signed-in user)

| Method and path | Who | Request | Success | Errors |
| --- | --- | --- | --- | --- |
| `POST /merchant/registration` | CUSTOMER | `businessName` (required, max 150), `contactEmail` (required, valid), `contactPhone` (optional), `businessAddress` (optional) | 201 `MerchantResponse` | 400 validation, 401, 403, 409 `MERCHANT_ALREADY_REGISTERED` |
| `GET /merchant/registration` | Owner | none | 200 `MerchantResponse` | 401, 404 (never applied) |
| `POST /merchant/api-keys` | Owner of an APPROVED business | none | 201 `ApiKeyCreatedResponse` (key shown once) | 403 `MERCHANT_NOT_APPROVED`, 404 `MERCHANT_NOT_FOUND`, 409 `API_KEY_LIMIT_REACHED` (max 5 active) |
| `GET /merchant/api-keys` | Owner | none | 200 list of `ApiKeySummary` (no secrets) | 404 `MERCHANT_NOT_FOUND` |
| `POST /merchant/api-keys/{id}/revoke` | Owner | none | 200 `ApiKeySummary` | 404 `API_KEY_NOT_FOUND` (also for another merchant's key), 400 bad id |

`MerchantResponse`: `id, businessName, contactEmail, contactPhone, businessAddress, approvalStatus, reviewReason, createdAt`.

`ApiKeySummary`: `id, keyPrefix, scopes, createdAt, lastUsedAt, revokedAt, active`.

`ApiKeyCreatedResponse`: `credential` (an `ApiKeySummary`), `apiKey`, `notice`.

## Admin endpoints (DISPATCHER_ADMIN)

| Method and path | Request | Success | Errors |
| --- | --- | --- | --- |
| `GET /admin/merchants?status=&page=&size=` | `status` optional, `size` default 50, max 100 | 200 `MerchantPageResponse` | 401, 403 |
| `POST /admin/merchants/{id}/approve` | none | 200 `MerchantAdminResponse` | 403, 404 `MERCHANT_NOT_FOUND`, 409 `MERCHANT_ALREADY_DECIDED` |
| `POST /admin/merchants/{id}/reject` | `reason` (required, max 500) | 200 `MerchantAdminResponse` | 400, 403, 404, 409 |

A decision can be made once. Two admins deciding at the same time cannot both succeed (row lock). Approval and the role grant succeed or fail together.

## Partner API (X-API-Key)

Key format: `sdk_` + 10 hex characters + `.` + 43 URL-safe characters. The key is shown once at creation. Only its prefix and SHA-256 hash are stored. Treat it like a password and never log it.

| Method and path | Scope | Success | Errors |
| --- | --- | --- | --- |
| `GET /merchant/verify` | any valid key | 200 `{merchantId, businessName, scopes}` | 401 `INVALID_API_KEY`, 403 `MERCHANT_NOT_APPROVED`, 429 `RATE_LIMITED` |
| `POST /merchant/quotes` | `quote` | 201 quote (see below) | 400 `VALIDATION_FAILED`, 401, 403 `MERCHANT_NOT_APPROVED` or `FORBIDDEN` (key lacks the scope), 422 pricing errors, 429 |
| `GET /merchant/quotes/{quoteId}` | `quote` | 200 quote | 401, 403, 404 `QUOTE_NOT_FOUND` (also for another merchant's quote), 429 |
| `/merchant/orders`, `/merchant/orders/**` | `order` | **reserved** (MER-05) | 403 `FORBIDDEN` if the key lacks the scope |

Checked on **every** request: key format, prefix lookup, constant-time hash comparison, key not revoked, business still APPROVED. Revoking a key or suspending a business takes effect on the next request.

Limits per source address: 300 requests per minute, and 10 failed key attempts per minute (then 429 with `Retry-After`).

Example:

```bash
curl -i -H "X-API-Key: sdk_0123456789.<secret>" https://<host>/api/v1/merchant/verify
```

### Merchant quotes

Prices a delivery with the shared M3 pricing service. The quote is stored under **your business**, valid for 10 minutes, and can only be read or used by you.

Request body (all required):

```json
{ "pickupPostalCode": "00100", "dropoffPostalCode": "10100", "weightGrams": 1500, "size": "SMALL" }
```

`size` is one of `SMALL`, `MEDIUM`, `LARGE`, `EXTRA_LARGE`. Weight is an integer in grams. The amount is always calculated by the server, and any amount in the request is ignored.

Response (201 and 200):

```json
{ "quoteId": "…", "amountMinor": 45000, "currency": "LKR", "baseMinor": 40000,
  "extraWeightMinor": 5000, "sizeSurchargeMinor": 0, "expiresAt": "2026-11-18T10:10:00Z" }
```

Money is in LKR minor units. Pricing errors share the standard error shape:

| `code` | Status | Meaning |
| --- | --- | --- |
| `UNSUPPORTED_POSTCODE` | 422 | A postal code is not mapped to an active delivery zone |
| `UNSUPPORTED_ROUTE` | 422 | There is no current rate for this pickup and drop-off pair |
| `WEIGHT_NOT_SUPPORTED` | 422 | The parcel weight is above the supported limit |
| `QUOTE_NOT_FOUND` | 404 | Unknown quote, or it belongs to another business |
| `QUOTE_EXPIRED` | 409 | The quote is older than 10 minutes (raised when an order uses it) |
| `QUOTE_INPUTS_CHANGED` | 409 | The order details differ from the quoted ones (raised when an order uses it) |

## For other modules: MerchantAccessService

`com.swiftdrop.service.merchant.MerchantAccessService` (in-process, read-only):

| Method | Use it when | Returns | Fails with |
| --- | --- | --- | --- |
| `requireCurrentApiMerchant()` | The request was authenticated by an API key (M4 order creation, M9 test callbacks) | `MerchantContext(merchantId, ownerUserId, businessName, credentialId, scopes)` | `InvalidApiKeyException` (401), `MerchantNotApprovedException` (403) |
| `requireApprovedOwnerMerchant()` | A signed-in owner is acting (dashboard, M9 webhook settings) | `MerchantContext` (credentialId null) | `MerchantNotFoundException` (404), `MerchantNotApprovedException` (403) |

Both re-read live key and approval state. Never cache the result for authorization, never accept a merchant id from a request body, and bind `externalReference` and idempotency keys to `merchantId`.

## Tables (V6)

`merchants(id, owner_user_id UNIQUE FK users, business_name, contact_email, contact_phone, business_address, approval_status, review_reason, reviewed_by, reviewed_at, version, created_at, updated_at)` and `api_credentials(id, merchant_id FK, key_prefix UNIQUE, secret_hash, scopes, created_at, last_used_at, revoked_at)`.

## Open items

1. M4: `parcel_bookings` needs `merchant_id` (customer XOR merchant), `external_reference` and an idempotency key, plus a `BookingService` interface, before MER-05 and MER-06.
2. M1: [merchant role grant and first-admin bootstrap](identity-role-handoff.md) are implemented. A verified first-admin account must still be bootstrapped in each environment before an administrator can approve merchants.
3. Suspend endpoint (it must call `revokeMerchantForOwner` in the same transaction) and an audit table (`api_request_audit`) are not built yet.