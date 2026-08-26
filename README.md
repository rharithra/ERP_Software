# RetailFlow

RetailFlow is a multi-tenant ERP for Indian retail shops: kirana, garments, mobile, and general stores.

Milestones 1 and 1 Hardening are complete. Milestone 2 adds a tenant-scoped product catalog:

- company self-signup, JWT login, BCrypt passwords
- tenant isolation in the API and in PostgreSQL row-level security (Hibernate filter + FORCE RLS)
- ERP shell with dashboard and company profile
- categories and products (SKU, barcode, prices, GST slab, unit)
- remaining modules stay coming-soon (inventory, purchases, sales/POS, invoices)

Inventory quantities, purchases, sales, and GST invoices are intentionally not implemented yet.

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

MANAGER is a valid API role for company profile GET/PUT, but Milestone 1 signup and UI only create OWNER. There is no manager invitation flow.

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

Backend tests cover password hashing, JWT claims, signup/login, authorization, tenant isolation (including RLS), and catalog CRUD/isolation.

## Current milestone

**Milestone 2 — Categories + products (catalog)**

Done when a retailer can maintain categories and products inside their own tenant, with tenant-scoped SKU/barcode uniqueness and GST/unit master data. Inventory stock is **not** part of this milestone.

**Milestone 1** and **Milestone 1 Hardening** are complete.

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

UI routes: `/app/categories`, `/app/products`.

## Later milestones

1. Inventory and stock movements
2. Suppliers and purchases
3. Customers, POS, GST invoices
4. Reports, expenses, employees, notifications
5. VPS deployment with Caddy/Nginx
