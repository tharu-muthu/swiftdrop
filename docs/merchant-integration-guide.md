\# Merchant integration guide (M2)



For shop developers connecting an online store to SwiftDrop. Implemented today: onboarding, API keys and key verification. Quote and order creation are \*\*planned\*\* and marked as such below.



\## 1. Onboarding



1\. A signed-in SwiftDrop customer registers a business (`POST /api/v1/merchant/registration`). The application starts as `PENDING`.

2\. A SwiftDrop administrator approves or rejects it. A rejection carries a reason.

3\. Once `APPROVED`, the owner creates an API key (`POST /api/v1/merchant/api-keys`). The full key is shown \*\*once\*\*. SwiftDrop stores only a hash, so a lost key cannot be recovered. Revoke it and create a new one.

4\. The shop sends the key in the `X-API-Key` header on every partner API request.



An owner can hold up to 5 active keys, for example one per environment.



\## 2. Check your key



```bash

curl -i -H "X-API-Key: sdk\_0123456789.<your-secret>" \\

&#x20; https://<host>/api/v1/merchant/verify

```



Success (200):



```json

{ "merchantId": "5b0c...", "businessName": "Lanka Crafts", "scopes": \["order", "quote"] }

```



Failures:



| Status | `code` | Meaning |

| --- | --- | --- |

| 401 | `INVALID\_API\_KEY` | Missing, malformed, wrong or revoked key. The reason is deliberately not revealed |

| 403 | `MERCHANT\_NOT\_APPROVED` | The key is valid but the business is not approved (or was suspended) |

| 403 | `FORBIDDEN` | The key lacks the scope this endpoint needs |

| 429 | `RATE\_LIMITED` | Too many requests or failed attempts. Wait for `Retry-After` seconds |



Limits per source address: 300 requests per minute, and 10 failed key attempts per minute.



\## 3. Planned endpoints (not available yet)



These depend on the pricing (M3) and booking (M4) services. Shapes may change when those contracts are published.



| Planned call | Scope | Purpose |

| --- | --- | --- |

| `POST /api/v1/merchant/quotes` | `quote` | Price a delivery. Returns a quote id, amount and expiry (10 minutes) |

| `POST /api/v1/merchant/orders` | `order` | Create a delivery from a valid quote. Requires an `Idempotency-Key` header and your own `externalReference` |



Retry rule for orders: if a request times out, \*\*resend the same `Idempotency-Key` and the same body\*\*. The same key with the same body returns the saved result. The same key with a different body returns 409. Never invent a new key or reference for a retry, or you may create a second parcel.



Parcel updates are pushed back to the shop by signed webhooks (M9), covered in the webhook guide.



\## 4. Handling your key safely



\- Keep it on your server. Never put it in browser code, a mobile app or a public repository.

\- Send it only over HTTPS.

\- Never log the header. Log the key prefix (the part before the dot) if you need to identify a key.

\- If a key may have leaked, revoke it immediately. Revocation applies on the next request.



\## 5. Order flow



```mermaid

sequenceDiagram

&#x20;   participant Shop as Partner store

&#x20;   participant API as SwiftDrop API (M2)

&#x20;   participant Price as Pricing (M3)

&#x20;   participant Book as Booking (M4)

&#x20;   participant DB as PostgreSQL



&#x20;   Shop->>API: POST /merchant/quotes (X-API-Key)

&#x20;   API->>API: Verify key, merchant APPROVED, scope quote

&#x20;   API->>Price: Price request with server-derived merchant

&#x20;   Price-->>API: quoteId, amount, expiry

&#x20;   API-->>Shop: 200 quote



&#x20;   Shop->>API: POST /merchant/orders (X-API-Key, Idempotency-Key)

&#x20;   API->>API: Verify key, merchant APPROVED, scope order

&#x20;   API->>Book: Create booking (merchantId from the key, never the body)

&#x20;   Book->>DB: One transaction: parcel, addresses, lifecycle CREATED, outbox

&#x20;   DB-->>Book: Committed

&#x20;   Book-->>API: parcelId, trackingNumber

&#x20;   API-->>Shop: 201 order



&#x20;   Note over Shop,API: A timeout means: retry with the SAME Idempotency-Key

```



Today the flow stops at "Verify key". The Price and Book steps are added when M3 and M4 publish their services.

