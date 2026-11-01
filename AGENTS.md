# SwiftDrop shared rules

- Read relevant `docs/reference/` files and `docs/reference-review.md` before changes. Preserve references. The assignment defines requirements; v3 and the user's agreed decisions define this implementation baseline. Record conflicts instead of guessing.
- One React frontend, one Java 21 / Spring Boot 3.5.16 application, one PostgreSQL database and shared `swiftdrop` schema. RabbitMQ handles asynchronous integrations. No microservices or per-member databases/schemas.
- Request direction: React -> Controller -> Service -> Repository -> PostgreSQL. Java packages are `com.swiftdrop.<layer>.<feature>`; frontend features live in `src/features/<feature>`.
- Controllers validate DTOs and call services, never repositories. Services own business rules, resource authorization and transactions. Repositories own persistence. Never expose entities as API responses. Use constructor injection and `jakarta.validation`.
- Features: M1 identity; M2 merchant; M3 pricing; M4 booking; M5 dispatch; M6 execution; M7 tracking; M8 notification; M9 webhook. Call other features through service contracts, not their repositories or internal HTTP.
- APIs use `/api/v1`. Only GET health is public in this foundation. Keep other routes denied until explicitly implemented with authentication and authorization tests. No generated demo login, fake production principals or broad `permitAll`. Preserve CSRF for future cookie authentication.
- Flyway is the only schema writer; JPA uses `ddl-auto: validate`. Coordinate the single migration sequence; never edit an applied migration. Use PostgreSQL integration tests. Keep demo fixtures separate.
- Future domain data uses UUIDs, UTC timestamps, integer grams and LKR minor units. M6 owns canonical parcel status. Plan outbox persistence, independent durable queues and idempotent consumers when event features are implemented.
- Pin direct npm dependencies and commit `package-lock.json`; install with `npm ci`. Use the Maven wrapper; let the Spring Boot parent manage Spring dependency versions. Coordinate toolchain changes with the team.
- Never commit secrets, `.env`, tokens or personal data. Never put secrets in `VITE_*` variables. Return safe errors and avoid sensitive logging.
- Use small feature branches and peer review. Add meaningful tests for behavior/security changes. Run frontend tests/build and backend verification; run integration tests with Docker. Report passed, failed and unrun checks separately. Exact commands are in `README.md`.
- Current scope is shared foundation only. Do not implement authentication, Google, booking or other business features without a new task.
