# One shared migration history

Flyway creates `swiftdrop` and records V1 in `swiftdrop.flyway_schema_history`.
V1 adds a schema comment only; there are no business tables or seed accounts.

Coordinate the next version with the team before merging. Use
`V2__short_description.sql`, then V3, and so on. Never edit an applied migration;
add a new one. JPA validates mappings and must not create or update tables.
Use foreign keys across feature-owned tables in this same schema.

Startup requires a database user with schema-creation and migration privileges.
The local Compose user provides these. Production migration/runtime credentials
and least-privilege grants are a later deployment decision.
