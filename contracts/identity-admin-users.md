# Admin user management (IAM-05)

Only a signed-in, active `DISPATCHER_ADMIN` may call these endpoints. Use the access bearer token. State-changing requests also use the existing CSRF token and allowed Origin. Responses use `Cache-Control: no-store`.

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/admin/users?query=&page=0&size=20` | Search name/email; page is zero-based, size 1–100. Returns `items`, `page`, `size`, `totalElements`, `totalPages`. |
| `GET /api/v1/admin/users/{id}` | Safe account details. |
| `POST /api/v1/admin/users/{id}/suspend` | Suspend and revoke all sessions. |
| `POST /api/v1/admin/users/{id}/reactivate` | Reactivate; old sessions remain revoked. |
| `POST /api/v1/admin/users/{id}/roles/grant` | Grant CUSTOMER, COURIER or DISPATCHER_ADMIN. |
| `POST /api/v1/admin/users/{id}/roles/revoke` | Remove one of those roles. |

Status request: `{ "reason": "Support case 123", "expectedVersion": 0 }`. The reason is required and at most 500 characters. Use the current `version` returned by GET; reload after `409 ACCOUNT_STATUS_CONFLICT`. Responses include only ID, name, email, phone, roles, active/verified flags, timestamps and version. Password hashes and tokens never appear.

An administrator cannot suspend their own account or the last active administrator. The backend serializes status changes with a lock on the administrator role row, increments `authorization_version`, revokes sessions, and records the actor, target, reason and safe before/after state in `identity_audit_events`, all in one transaction. Merchant access remains controlled by M2's approval workflow.

Role request: `{ "role": "DISPATCHER_ADMIN", "reason": "Approved second administrator", "expectedVersion": 0 }`. Only active accounts may be changed; grants require verified email. Merchant roles are rejected here and continue through M2 approval. Duplicate grants, absent-role removals, stale versions, self-removal of the admin role, removal of the last active administrator and removal of an account's only role return `409 ROLE_CHANGE_CONFLICT`. Role changes lock the administrator definition before the target account, revoke all target sessions, increment authorization version and append an audit record in the same transaction. The target signs in again.

UI: sign in as an administrator, then open `/admin/users` or use **User management** from My profile. Both status and role actions need a reason. A suspended or role-changed person must sign in again.
