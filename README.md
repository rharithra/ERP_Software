# RetailFlow

RetailFlow is a multi-tenant ERP for Indian retail shops: kirana, garments, mobile, electronics, appliances, and general stores.

Milestones 1–7 are complete:

- company self-signup, JWT login, BCrypt passwords
- tenant isolation in the API and in PostgreSQL row-level security (Hibernate filter + FORCE RLS)
- ERP shell with dashboard and company profile
- categories and products (SKU, barcode, prices, GST slab, unit)
- inventory balances, opening stock, adjustments, and movement history
- suppliers, draft purchases, goods receiving, and PURCHASE_RECEIPT stock movements
- customers, POS/sales, SALE stock movements, and printable invoices
- tenant business type and sales experience (Quick Sale / Pipeline / Hybrid)
- sales pipeline CRM: leads, follow-ups, quotations, sales orders, payments, outstanding
- owner-managed users and roles (OWNER / MANAGER / CASHIER, activate/deactivate, password reset)

Remaining modules stay coming-soon (reports, expenses, employees/HR).

There is one Sale engine and one inventory ledger. Quotations and sales orders never decrease stock; completing a sale does.

## Architecture

| Layer | Stack |
| --- | --- |
| API | Java 21, Spring Boot 3.4, Spring Security, JWT, Spring Data JPA |
| Database | PostgreSQL 16, Flyway |
| Web | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn-style UI |
| Runtime | Docker Compose for local/production-shaped deploys |

See [ARCHITECTURE.md](ARCHITECTURE.md) for tenancy, auth, and module boundaries.

## Local setup

### Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22+
- PostgreSQL 16 (or Docker)

### Database

```bash
createuser retailflow
createdb retailflow -O retailflow
createdb retailflow_test -O retailflow
psql -c "ALTER USER retailflow PASSWORD 'retailflow_dev';"
```

Copy environment defaults:

```bash
cp .env.example .env
```

### API

```bash
cd backend
mvn test
mvn spring-boot:run
```

The API listens on `http://localhost:38422`.

OpenAPI UI: `http://localhost:38422/swagger-ui.html`

### Web

```bash
cd frontend
npm install
npm run dev
```

The UI listens on `http://localhost:38421` and proxies `/api` to the backend.

### Docker

Docker Compose does **not** ship a JWT secret. Set `JWT_SECRET` in the environment or in a gitignored `.env` file. Compose refuses to start if it is missing. The Docker Spring profile also rejects the documented local-development default.

```bash
cp .env.example .env
# Put a unique 64+ character value in JWT_SECRET. Example:
#   openssl rand -base64 48
docker compose up --build
```

- Web: `http://localhost:38421`
- API: `http://localhost:38422`
- Postgres: `localhost:5432`

Do not commit real secrets. Runtime Docker verification must be done on a machine that has Docker; this environment may not have it.

`mvn spring-boot:run` with profile `local` still works without `JWT_SECRET` and uses a **development-only** default documented in `application.yml`. Never use that value on a VPS.

MANAGER may update company identity (name/address) via `PUT /api/v1/tenant`. Only OWNER may change business type or sales mode. OWNER creates MANAGER and CASHIER accounts from Users & Roles. There is no email invitation flow.

## Environment variables

| Variable | Used by | Notes |
| --- | --- | --- |
| `DATABASE_URL` | API | JDBC URL |
| `DATABASE_USERNAME` | API | Default `retailflow` |
| `DATABASE_PASSWORD` | API | Local default in `.env.example` |
| `JWT_SECRET` | API | Required for Docker/production (min 32 chars). Optional only for `local` profile |
| `JWT_EXPIRATION_MS` | API | Default 8 hours |
| `CORS_ALLOWED_ORIGINS` | API | Comma-separated origins |
| `SERVER_PORT` | API | Default `38422` |
| `VITE_API_BASE_URL` | Web (Docker) | Empty when nginx proxies `/api` |

## Testing

```bash
cd backend && mvn test
cd frontend && npm test && npm run build
```

Backend tests cover password hashing, JWT claims, signup/login, authorization, tenant isolation (including RLS), catalog, inventory, purchases, customers, sales, pipeline CRM, payments, concurrent stock, and user/role management.

## Current milestone

**Milestone 7 — User & role management**

OWNER manages MANAGER/CASHIER for the current tenant. See [docs/M7_USER_AND_ROLE_MANAGEMENT.md](docs/M7_USER_AND_ROLE_MANAGEMENT.md).

**Milestone 6 — Sales pipeline & CRM**, **Milestone 5.1**, **Milestone 5**, **Milestone 1**, **Milestone 1 Hardening**, **Milestone 2**, **Milestone 3**, and **Milestone 4** remain complete.

## Catalog API (authenticated; tenant from JWT)

| Method | Path | Roles |
| --- | --- | --- |
| GET | `/api/v1/categories` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/categories/active` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/categories/{id}` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/categories` | OWNER, MANAGER |
| PUT | `/api/v1/categories/{id}` | OWNER, MANAGER |
| PATCH | `/api/v1/categories/{id}/status` | OWNER, MANAGER |
| GET | `/api/v1/products` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/products/{id}` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/products` | OWNER, MANAGER |
| PUT | `/api/v1/products/{id}` | OWNER, MANAGER |
| PATCH | `/api/v1/products/{id}/status` | OWNER, MANAGER |

List query params: `q`, `active`, `page` (1-based), `size` (max 100). Products also accept `categoryId`. Tenant id in query/body/path is ignored.

UI routes: `/app/categories`, `/app/products`, `/app/inventory`, `/app/suppliers`, `/app/purchases`, `/app/customers`, `/app/sales`.

## Inventory API (authenticated; tenant from JWT)

| Method | Path | Roles |
| --- | --- | --- |
| GET | `/api/v1/inventory` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/inventory/summary` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/inventory/{productId}` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/inventory/{productId}/movements` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/inventory/{productId}/opening-stock` | OWNER, MANAGER |
| POST | `/api/v1/inventory/{productId}/adjustments` | OWNER, MANAGER |
| PATCH | `/api/v1/inventory/{productId}/reorder-level` | OWNER, MANAGER |

List query params: `q`, `categoryId`, `status` (`IN_STOCK` / `LOW_STOCK` / `OUT_OF_STOCK`), `page`, `size`. Tenant id in query/body/path is ignored.

## Supplier and purchase API (authenticated; tenant from JWT)

| Method | Path | Roles |
| --- | --- | --- |
| GET | `/api/v1/suppliers` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/suppliers/summary` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/suppliers/active` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/suppliers/{id}` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/suppliers` | OWNER, MANAGER |
| PUT | `/api/v1/suppliers/{id}` | OWNER, MANAGER |
| PATCH | `/api/v1/suppliers/{id}/status` | OWNER, MANAGER |
| GET | `/api/v1/purchases` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/purchases/summary` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/purchases/{id}` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/purchases` | OWNER, MANAGER |
| PUT | `/api/v1/purchases/{id}` | OWNER, MANAGER |
| POST | `/api/v1/purchases/{id}/receive` | OWNER, MANAGER |
| POST | `/api/v1/purchases/{id}/cancel` | OWNER, MANAGER |

Purchase list query params: `q` (purchase number), `supplierId`, `status` (`DRAFT` / `RECEIVED` / `CANCELLED`), `fromDate`, `toDate`, `page`, `size`. Totals are always recalculated on the server. Receiving is atomic and posts through `InventoryService`.

Signup and `PUT /api/v1/tenant` accept `businessType` and `salesMode`. Reads: OWNER, MANAGER, CASHIER. Only OWNER may change those two fields.

## Customer and sales API (authenticated; tenant from JWT)

| Method | Path | Roles |
| --- | --- | --- |
| GET | `/api/v1/customers` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/customers/summary` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/customers/active` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/customers/{id}` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/customers` | OWNER, MANAGER |
| PUT | `/api/v1/customers/{id}` | OWNER, MANAGER |
| PATCH | `/api/v1/customers/{id}/status` | OWNER, MANAGER |
| GET | `/api/v1/sales` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/sales/summary` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/sales/dashboard` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/sales/{id}` | OWNER, MANAGER, CASHIER |
| GET | `/api/v1/sales/{id}/invoice` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/sales` | OWNER, MANAGER, CASHIER |
| PUT | `/api/v1/sales/{id}` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/sales/{id}/complete` | OWNER, MANAGER, CASHIER |
| POST | `/api/v1/sales/{id}/cancel` | OWNER, MANAGER, CASHIER |

Sales list query params: `q` (sale or invoice number, customer name), `customerId`, `status` (`DRAFT` / `COMPLETED` / `CANCELLED`), `fromDate`, `toDate`, `page`, `size`. Completing is atomic and posts through `InventoryService.applySale`. Invoice numbers are allocated on complete. Tenant id in query/body/path is ignored.

UI: `/app/customers`, `/app/sales`, `/app/sales/new` (POS), `/app/sales/:id`, `/app/sales/:id/invoice`.

## Pipeline, payments, notifications (authenticated; tenant from JWT)

CRM writes: OWNER, MANAGER. Payments and outstanding: OWNER, MANAGER, CASHIER.

| Method | Path |
| --- | --- |
| GET/POST | `/api/v1/leads` |
| GET | `/api/v1/leads/board` |
| GET/PUT | `/api/v1/leads/{id}` |
| GET | `/api/v1/leads/{id}/activities` |
| POST | `/api/v1/leads/{id}/status` |
| POST | `/api/v1/leads/{id}/convert-customer` |
| GET/POST | `/api/v1/follow-ups` |
| PUT | `/api/v1/follow-ups/{id}` |
| POST | `/api/v1/follow-ups/{id}/complete` and `/cancel` |
| GET/POST | `/api/v1/quotations` |
| GET/PUT | `/api/v1/quotations/{id}` |
| POST | `/api/v1/quotations/{id}/send`, `/accept`, `/reject`, `/cancel` |
| GET/POST | `/api/v1/sales-orders` |
| POST | `/api/v1/sales-orders/from-quotation/{quotationId}` |
| POST | `/api/v1/sales-orders/{id}/confirm`, `/process`, `/ready`, `/cancel`, `/convert-sale` |
| GET/POST | `/api/v1/payments` |
| GET | `/api/v1/payments/outstanding` |
| GET | `/api/v1/pipeline/dashboard` |
| GET | `/api/v1/notifications`, `/notifications/unread-count` |
| POST | `/api/v1/notifications/{id}/read`, `/notifications/read-all` |
| GET | `/api/v1/tenant/members` |

UI: `/app/pipeline`, `/app/leads`, `/app/follow-ups`, `/app/quotations`, `/app/sales-orders`, `/app/outstanding`.

## Users & roles (authenticated; tenant from JWT)

OWNER only. Email uniqueness is global. Temporary passwords are hashed. Status is membership `ACTIVE` / `INACTIVE`.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/users` | List tenant members including inactive |
| GET | `/api/v1/users/{id}` | |
| POST | `/api/v1/users` | MANAGER or CASHIER + temporary password |
| PATCH | `/api/v1/users/{id}/role` | MANAGER ↔ CASHIER |
| PATCH | `/api/v1/users/{id}/status` | ACTIVE / INACTIVE |
| POST | `/api/v1/users/{id}/reset-password` | |

UI: `/app/settings/users`.

## Later milestones

1. Reports, expenses, employees/HR
2. Purchase returns, sales returns, credit notes
3. VPS deployment with Caddy/Nginx
