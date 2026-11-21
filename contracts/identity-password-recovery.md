# IAM-08 password recovery

The existing `swiftdrop.password_reset_tokens` table stores SHA-256 token hashes, expiry, use and revocation timestamps. No database migration is required.

## API

Both endpoints are anonymous `POST` operations under `/api/v1/auth/password-reset/`. They require the existing CSRF token and an allowed `Origin`; responses use `Cache-Control: no-store`.

| Endpoint | JSON body | Success | Error |
| --- | --- | --- | --- |
| `request` | `{ "email": "person@example.com" }` | `202` with the same acknowledgement for eligible, unknown, suspended and Google-only accounts | `400` for invalid input, `429` for request limit |
| `confirm` | `{ "token": "...", "password": "..." }` | `204`, no body and no automatic sign-in | `400 INVALID_RESET_TOKEN` for invalid, expired or used links; validation and throttling errors |

Only active accounts with a local password receive mail. A new request revokes older unused links. Tokens expire after 30 minutes and work once. The confirmation transaction changes the adaptive password hash, increments the user's authorization version and revokes all sessions. It never changes roles or account status. The password policy is the same 8–128 Unicode code-point policy as registration.

The link is built from private `RESET_FRONTEND_ORIGIN`, never the request Host header. The raw token appears only in the email link's URL fragment. The reset page reads the fragment and removes it from browser history immediately. The backend stores only the token hash; API responses and application logs contain no link or token. Mail is queued on a bounded local worker after commit so transport delay does not reveal eligibility through the response time. Mail transport failures are logged without addresses or token details, while the request keeps its generic response. The local worker is not durable across process restarts; operators must monitor mail delivery separately.

Request limits are 10 per source IP per minute and 3 per normalized email per 15 minutes. Confirmation allows 20 attempts per source IP per minute. The current limiter is in memory for the agreed single-backend application.

## Local Docker walkthrough

Run `docker compose up --detach --build --wait backend frontend`. Open `http://localhost:8080/forgot-password`, enter an existing password account email, and submit. Open the private local inbox at `http://localhost:8025`, follow the message link, choose a new password and sign in. Mailpit is local development mail capture; it does not send real email. For Maven on the host, configure an SMTP server through private `backend/.env` using `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, and optional SMTP credentials/TLS flags. Set `RESET_FRONTEND_ORIGIN` to the exact frontend origin users open. Never commit private mail credentials.

## Real Gmail delivery

Google sign-in (`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`) does not authorize this backend to send Gmail messages. To send a reset message to a user's real Gmail inbox, configure a separate sending Gmail account in private `backend/.env` using `SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` (Google App Password), `SPRING_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_SMTP_STARTTLS=true`, `SPRING_MAIL_SMTP_STARTTLS_REQUIRED=true`, and `MAIL_FROM` equal to the sender address. Docker's Mailpit host/port are overridden by those private `SPRING_MAIL_*` values. Keep `RESET_FRONTEND_ORIGIN` as the exact public frontend origin. Rebuild/restart the backend with `docker compose up --detach --build --wait backend` after editing the file. The sender must have Google 2-Step Verification and be eligible for App Passwords; organization-managed accounts may restrict them. Do not paste the App Password into chat, Git, frontend variables or logs. Real SMTP delivery has not been smoke-tested in this repository.

From `backend/`, run `./mvnw -Pintegration -Dit.test=IdentitySessionsIT verify` (Windows: `./mvnw.cmd`). From `frontend/`, run `npm test` and `npm run build`.
