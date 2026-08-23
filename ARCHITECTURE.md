# Architecture

RetailFlow is a modular monolith. One Spring Boot process and one React SPA serve every tenant. There are no microservices in Milestone 1.

## Request flow

```
Browser
  → Vite/nginx
  → Spring Security (JWT)
  → Controller
  → Service
  → JPA repository
  → PostgreSQL (RLS policies)
```

Controllers never return JPA entities. Requests and responses use records with Bean Validation.

## Identity model

- `users` is a global identity table. Email is unique (case-insensitive).
- A user joins a company through `tenant_memberships` with role `OWNER`, `MANAGER`, or `CASHIER`.
- Milestone 1 signup always creates one tenant, one user, and one OWNER membership in a single transaction.

Passwords are stored only as BCrypt hashes.

## JWT

Access tokens are signed HS256 JWTs. Claims:

- `sub` — user id
- `email`
- `tenantId`
- `role`
- `iat` / `exp`

The API never trusts a tenant id from the request body. After signature verification it reloads membership from the database (with RLS bypass limited to that lookup) and then binds tenant context.

## Tenant isolation

Defense in depth:

1. **Application** — `TenantContext` ThreadLocal from the JWT membership. Hibernate filter `tenantFilter` restricts tenant-owned rows.
2. **Database** — `FORCE ROW LEVEL SECURITY` on `tenants` and `tenant_memberships`. Policies allow a row only when `app.current_tenant_id` matches, unless `app.bypass_rls` is `on` for bootstrap/auth lookups.

`SET LOCAL` via `set_config(..., true)` keeps the GUC transaction-scoped on pooled connections.

Users table has no RLS so login can resolve email without a tenant session.

## Authorization

Method security:

- `GET /api/v1/tenant` — OWNER, MANAGER, CASHIER
- `PUT /api/v1/tenant` — OWNER, MANAGER

Unauthenticated callers receive `401` with the standard error envelope. Forbidden callers receive `403`.

## API envelope

Success:

```json
{ "success": true, "data": { } }
```

Error:

```json
{ "success": false, "error": { "code": "VALIDATION_ERROR", "message": "...", "details": [] } }
```

## Frontend

The SPA stores the access token in `localStorage` for Milestone 1 (same-origin, no refresh cookie yet). Protected routes load `/api/v1/auth/me`. Future modules render a professional coming-soon page instead of fake CRUD.

Theme uses CSS variables and `next-themes` (`class` on `html`) for light and dark mode.

## What this repo will not do in M1

Product, inventory, purchase, POS, invoice, report, expense, employee, billing, S3, and hardware modules stay out of the schema and UI until a later milestone.
