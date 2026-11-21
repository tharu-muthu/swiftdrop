# Admin user management (IAM-05, first slice)

Only a signed-in, active `DISPATCHER_ADMIN` may call these endpoints. Use the access bearer token. State-changing requests also use the existing CSRF token and allowed Origin. Responses use `Cache-Control: no-store`.

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/admin/users?query=&page=0&size=20` | Search name/email; page is zero-based, size 1–100. Returns `items`, `page`, `size`, `totalElements`, `totalPages`. |
| `GET /api/v1/admin/users/{id}` | Safe account details. |
| `POST /api/v1/admin/users/{id}/suspend` | Suspend and revoke all sessions. |
| `POST /api/v1/admin/users/{id}/reactivate` | Reactivate; old sessions remain revoked. |

Status request: `{ "reason": "Support case 123", "expectedVersion": 0 }`. The reason is required and at most 500 characters. Use the current `version` returned by GET; reload after `409 ACCOUNT_STATUS_CONFLICT`. Responses include only ID, name, email, phone, roles, active/verified flags, timestamps and version. Password hashes and tokens never appear.

An administrator cannot suspend their own account or the last active administrator. The backend serializes status changes with a lock on the administrator role row, increments `authorization_version`, revokes sessions, and records the actor, target, reason and safe before/after state in `identity_audit_events`, all in one transaction. General role editing belongs to the next IAM-05 slice; merchant access remains controlled by M2's approval workflow.

UI: sign in as an administrator, then open `/admin/users` or use **User management** from My profile. The action needs a reason. A suspended person must sign in again after reactivation.
