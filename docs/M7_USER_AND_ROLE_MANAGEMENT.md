# M7 — User & Role Management

RetailFlow tenants have a fixed team model. Signup still creates exactly one **OWNER**. The owner then creates **MANAGER** and **CASHIER** accounts with a temporary password.

This milestone does **not** include subscription billing, custom roles, multiple owners, ownership transfer, email invitations, forgot-password email, OTP, payroll/HR, or multi-company switching.

## Roles

| Capability | OWNER | MANAGER | CASHIER |
| --- | --- | --- | --- |
| Dashboard | yes | yes | yes (read as appropriate) |
| Categories / products (write) | yes | yes | no |
| Inventory adjust / opening stock | yes | yes | no |
| Suppliers / purchases / receive | yes | yes | no |
| Customers (write) | yes | yes | no (lookup/select yes) |
| POS / sales / invoices / payments | yes | yes | yes |
| Leads, follow-ups, quotations, sales orders | yes | yes | no |
| Reports / expenses (when shipped) | yes | yes | no |
| Company identity (`PUT /api/v1/tenant` name/address) | yes | yes | no |
| Business type / sales mode | yes | no | no |
| Users & roles | yes | no | no |

Server-side `@PreAuthorize` remains the enforcement point. Hiding a menu is not enough.

Fixed permission names (`USER_MANAGE`, `SALES_CONFIGURATION`, …) live in `Permission.forRole`. They document the map; controllers still authorize by role. There is no permission editor.

## Lifecycle

Membership `status` is `ACTIVE` or `INACTIVE`. Users are never hard-deleted so `createdBy` / `assignedTo` history stays valid.

- Inactive memberships cannot log in (`ACCOUNT_INACTIVE`).
- The JWT filter reloads membership from the database and requires `ACTIVE`. A stale token cannot keep calling APIs after deactivation. Role changes take effect on the next request without issuing a new JWT.

There is exactly one OWNER per tenant (`uk_memberships_one_owner`). The owner cannot deactivate themselves, change their own role, or create another OWNER.

## Security

Unchanged path:

JWT → membership (DB) → `TenantContext` → Hibernate `tenantFilter` → PostgreSQL **FORCE RLS**

User-management queries never take `tenantId` from the browser. Cross-tenant user ids return **404** (not found in this business), not a leak.

`users` stays global (unique email, no RLS) so login can resolve identity. `tenant_memberships` and `user_management_events` are tenant-scoped with FORCE RLS.

Passwords are BCrypt-hashed with the existing encoder (8–72 characters). APIs never return password or hash.

## APIs (OWNER only)

| Method | Path |
| --- | --- |
| GET | `/api/v1/users` |
| GET | `/api/v1/users/{id}` |
| POST | `/api/v1/users` |
| PATCH | `/api/v1/users/{id}/role` |
| PATCH | `/api/v1/users/{id}/status` |
| POST | `/api/v1/users/{id}/reset-password` |

`GET /api/v1/tenant/members` remains OWNER+MANAGER for CRM assignee dropdowns (active members only). It is not the Users & Roles admin API.

Error codes include `EMAIL_ALREADY_REGISTERED`, `INVALID_ROLE`, `CANNOT_MODIFY_SELF`, `CANNOT_MODIFY_OWNER`, `ACCOUNT_INACTIVE`, `MEMBERSHIP_NOT_FOUND`, `ACCESS_DENIED`.

## Audit

`user_management_events` records `USER_CREATED`, `USER_ROLE_CHANGED`, `USER_DEACTIVATED`, `USER_REACTIVATED`, `USER_PASSWORD_RESET` with actor, target, timestamp, and a short message. Passwords are never stored. This is separate from lead timeline activities.

## UI

Settings → **Users & Roles** (`/app/settings/users`). Owner-only nav item. Managers/cashiers who open the URL see **Access denied**; the API still returns 403.

The header menu shows name, email, and role from `/api/v1/auth/me`.

## Testing

Backend: `UserManagementIT` (owner lifecycle, self-protection, manager/cashier 403, tenant isolation, inactive login, role change on existing JWT). Regression suite for M1–M6 is unchanged.

Frontend: `users.test.tsx` (nav visibility, list, add-user validation, confirmations, 403, errors).

## Schema

Flyway `V9__user_and_role_management.sql`: membership `status`, one-OWNER unique index, `user_management_events`.
