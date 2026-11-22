# One shared migration history

Flyway creates `swiftdrop` and records V1 in `swiftdrop.flyway_schema_history`.
V1 adds a schema comment only and is unchanged. V2 adds eight identity tables and
seeds the four role definitions, with no accounts or passwords. V3 adds the M4
booking dashboard table. V4 adds M9 webhook subscription and delivery tables.
V5 extends booking history snapshots. V6 adds M2 merchant and API credential
tables. V7 adds M6 lifecycle, status history, proof, and outbox tables.
The current ER and table explanations are in `docs/identity/schema.md` at the
repository root.

The chain now reaches V12. V8 implements dispatch, V9 email verification and V10
pricing. V11 integrates customer/merchant booking ownership, idempotency, consumer
receipts and notification persistence; V12 adds merchant external references.
V1–V10 are preserved. Coordinate V11/V12 before shared rollout and verify SMTP
before applying V9 to shared password accounts. Never edit an applied migration;
add a new one. JPA validates mappings and must not create or update tables.
Use foreign keys across feature-owned tables in this same schema.

Flyway uses a direct connection to the same database as runtime queries. Neon
pooler hostnames are converted to their direct counterparts; optional
`SPRING_FLYWAY_URL/USER/PASSWORD` overrides are read from private `backend/.env`.
Startup rejects mismatched destination URLs. The migration role needs schema/DDL
privileges; the local Compose role provides these. Separate runtime grants and
production least-privilege policies remain deployment decisions.


V13 extends profile security with hashed account-action tokens and owned saved
addresses. It is pending team migration-number coordination; no old migration
was changed. Confirm the next free version before shared application startup.
