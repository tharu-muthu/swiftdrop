# Reference review and foundation decisions

Reviewed on 1 November 2026 before creating application files. Existing references
were preserved. This is a reconciliation log, not a claim that planned features exist.

## Sources read

- `docs/reference/SwiftDrop_Design_Documentation.pdf`: all 38 pages, version 3.0,
  dated 31 October 2026; page 33 architecture also rendered for visual inspection.
- `docs/reference/architecture.png` and `ER diagram.png`.
- All 18 images in `docs/reference/Diagram-UseCase,Activity,Sequence/`: five use
  cases, nine activities, four sequences.
- All three images in `docs/reference/Figma design/`: login, register, forgot password.
- The four-page assignment was **not inside this repository**. It was found and
  read at `../Group Assignment.pdf` (beside the repository). The v3 source register
  calls it `Group Assignment(1).pdf`; the available filename differs. After reading
  all references, an unchanged copy was added as `docs/reference/Group Assignment.pdf`
  so fresh clones include it. SHA-256 of source and copy matched; the original was preserved.
- The earlier blueprint and conversation-only enterprise SRS cited by v3 are not
  present in `docs/reference/`; their contents are not treated as independently verified.

## Authority

The assignment defines assessed requirements. The user's explicit agreement and v3
define the implementation choices: React, one Java layered application, one shared
schema, RabbitMQ, nine feature owners. PNGs illustrate proposals and cannot override
those choices. This task implements checkpoint A infrastructure only, with no domain
sample, authentication or business tables.

## Conflicts, omissions and interpretations

| Source / location | Difference or ambiguity | Foundation decision / later action |
| --- | --- | --- |
| Assignment p1 vs v3 pp1,3 | Brief specifies **10** members; agreed team has **9**. | Keep nine ownership areas; team must obtain lecturer confirmation. |
| Assignment pp2-3 | Allows several frameworks, broker types and architectures; says backend “services” in deployment. | Agreed React + Spring Boot + PostgreSQL + RabbitMQ, one backend. Plural wording does not require microservices. |
| `architecture.png`, top right | Merchant order/webhook arrows connect to React. | Merchant API requests terminate at backend controllers; backend adapters send webhooks (v3 pp12,15,33). Browser never holds server integration secrets. |
| `architecture.png`, bottom of backend | Repositories -> outbox/consumers -> DB appears to put consumers in the normal persistence path. | Controller -> Service -> Repository -> DB. Relay and consumers are supporting background components (v3 pp4,33). |
| `architecture.png`, broker arrows | Broker appears to send HTTP notifications directly; consumers also look separately deployed. | Backend consumers call providers; RabbitMQ transports messages. Consumers initially run in the one backend, with a separate queue per concern (v3 pp15,33,38). |
| `ER diagram.png`, UserRole | Role attributes sit on `UserRole`, with no separate Role/join model. | Future identity follows Role + UserRole junction, as in v3 pp7,34. No identity migration in this task. |
| `ER diagram.png`, ISA and API-key link | Customer/Merchant/Courier inheritance, duplicate Courier boxes, and apparent Courier-to-MerchantAPIKey line differ from v3 actor profiles. | Users have roles; merchant and courier are optional unique profiles. API credentials belong to merchants (v3 pp19,34). |
| `ER diagram.png`, `api_key` / `secret_key` | Names do not distinguish key hashes from retrievable signing secrets. | Hash inbound API credentials; encrypt webhook signing secrets separately. Never infer plaintext storage (v3 pp10,19,26). |
| `ER diagram.png`, addresses | Mutable user Address records are referenced by bookings. | Booking stores pickup/drop-off snapshots; exactly one customer or merchant owner (v3 pp7,21,35). An address book would be a separate feature. |
| `ER diagram.png`, status and proof | Status is on ParcelBooking; proof attaches to assignment; explicit canonical lifecycle/sequence/version are absent. | M6 owns ParcelLifecycle; ordered StatusHistory; proof references history; at most one current assignment (v3 pp8,23,35). |
| `ER diagram.png`, PricingZone | Base/per-kg fields on a single zone omit route pairs, rate versions and owned expiring quotations. | Use v3 detailed pricing model pp20,36 when pricing work begins. |
| `ER diagram.png`, identity/integration support | OAuth accounts, refresh sessions, immutable quotes, subscriptions, delivery attempts and projections are missing or collapsed. | Use detailed v3 ownership plans, not this PNG as a migration specification. |
| `4.1.5_Dispatcher_Administrator_Use_Case.png` | Draws separate Dispatcher and Administrator actors; v3 p10 defines combined `DISPATCHER_ADMIN`. | Retain combined proposed role until contract agreement; actor grouping is not a finalized role model. No roles implemented now. |
| `4.1.2` customer use case; forgot-password screen | Saved addresses and password reset appear but are not explicit baseline stories in v3 M1/M4 or the brief. | Record as possible future scope; do not silently implement. |
| `4.3.4` Google sequence, steps 25/29 | `/api/auth/refresh`, `/api/auth/me` conflict with v3 p9's `/api/v1` prefix and `/me`. | Future routes are `/api/v1/auth/refresh` and `/api/v1/me`. Freeze full auth contracts in a later task. |
| `4.3.1`, `4.3.2`, `4.2.1` | Several routes omit `/api/v1`. | Treat shortened paths as diagram shorthand; actual APIs use the version prefix. |
| `4.3.4` participant “Identity DB” | Could be read as a separate database. | It means identity-owned tables in the single shared schema (v3 p3). |
| `4.3.1`, `4.3.2`, `4.3.4` | Repository lifelines omitted; service-to-DB arrows shown directly. | Conceptual simplification; implementation must retain repositories. |
| `4.3.2` merchant sequence | Lifecycle service is omitted during persistence. | Merchant adapter reuses M4 booking; M4 invokes M6 lifecycle within the same transaction (v3 pp11-12). |
| v3 p7 overview vs pp34-36 | Combined RateCard/Quote box and abbreviated identity fields are less detailed. | Later detailed ER views and invariants govern; overview is not literal SQL. |
| v3 p16 vs p24 | Multi-instance recovery is discussed, but first release hosts SSE in one backend. | Single backend baseline; scaling requires a later distribution design. |
| Assignment p2 vs p3; v3 p16 | System section permits polling, implementation section names WebSockets/SSE. | Planned SSE satisfies the more specific requirement; no tracking feature now. |
| Assignment pp1-3 | Overall “20 marks” contrasts with design/implementation/testing/demo categories totaling 100. | Ask lecturer about grading normalization; no invented weighting or coverage threshold. |

The activity diagrams otherwise align with v3 on atomic booking, CREATED lifecycle,
concurrency checks, public-safe tracking, outbox publishing after commit, durable jobs
before acknowledgement, retries, and explicit Google account linking. Their presence
does not demonstrate completed integrations.

## Recorded implementation decisions

1. Java 21 language target, Spring Boot 3.5.16 parent manages Spring, JPA, Flyway,
   PostgreSQL driver and Testcontainers versions; Maven wrapper pins 3.9.11.
2. React + Vite JavaScript keeps the initial teaching surface small. Direct packages
   use exact versions; npm's lockfile pins transitive dependencies.
3. Database `swiftdrop`, one schema `swiftdrop`, Flyway V1, JPA validation only.
   The schema contains only migration history at this stage.
4. `GET /api/v1/health` is a public **liveness** response. It does not probe RabbitMQ
   or prove that future domain workflows work. PostgreSQL/Flyway must initialize
   successfully for normal application startup.
5. Everything else is denied; no generated user, login form, HTTP Basic, token
   issuer, OAuth client or public business route. CSRF remains enabled. M1 will
   replace deliberate deny-all with tested role/resource rules later.
6. Vite and Nginx forward `/api` on the same browser origin. No permissive CORS.
   Base Compose publishes only the frontend on localhost; development port mapping
   is explicitly opt-in. This is a local development baseline, not a TLS deployment.
7. RabbitMQ connection configuration and an infrastructure integration test are
   provided. Exchanges, domain queues, outbox relay and business consumers wait
   for their owners; the broker is not a substitute for in-process service calls.
8. Follow-up development configuration: the user authorized one Neon development
   branch per developer. Each still runs one application with the same shared
   schema and migration history; these are isolated environments, not feature
   databases. Private `backend/.env` files use the same three `SPRING_DATASOURCE_*`
   names. The later identity-database task explicitly supersedes the initial
   shared-DataSource-instance rule: runtime keeps its application connection while
   Flyway uses a guarded direct connection to the same endpoint/database. Optional
   `SPRING_FLYWAY_URL/USER/PASSWORD` overrides remain backend-only. Frontend
   configuration has no database connection information.
9. Identity database follow-up: V2 implements the eight M1 tables and JPA mappings;
   see `docs/identity/schema.md` for the current ER and table explanations. UUID
   `roles.id` plus unique `roles.name` supersedes the proposed role-code primary key
   to satisfy the user's UUID requirement. The combined RefreshSession becomes
   stable `auth_sessions` plus rotating `refresh_tokens`. There was no existing
   audit mechanism, so `identity_audit_events` supplies append-only identity audit
   persistence. No authentication APIs or seed accounts were added.

## Remaining team decisions

Confirm group size, names/IDs, deadline and hosting with the lecturer/team. Later
feature tasks must agree contracts, rates/zones, proof and retention policies,
Google project ownership and callbacks, production secrets and backup/restore.
None of those credentials are required for this foundation.

## Version compatibility sources

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html): Java 21 and Maven 3.9.11 fit the supported tool ranges.
- [Vite getting started](https://vite.dev/guide/): Node runtime requirements.
- Exact package versions verified against npm registry metadata; Maven versions
  against Maven Central; container tags against official Docker Hub repositories.
- Recommended Node 24.22.2/npm 11.19.1 and container Java 21.0.13+9 are patch-pinned.
  Existing compatible Java 21.0.9 and Node 24.11.0 are recorded separately in the
  verification report. Docker tags are explicit version pins, not immutable digests;
  controlled patch updates remain team maintenance work.
