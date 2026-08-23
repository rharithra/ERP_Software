# RetailFlow

RetailFlow is a multi-tenant ERP for Indian retail shops: kirana, garments, mobile, and general stores.

Milestone 1 delivers the commercial foundation only:

- company self-signup
- owner account with BCrypt passwords
- JWT login
- tenant isolation in the API and in PostgreSQL row-level security
- an ERP shell with dashboard, company profile, and coming-soon modules

Sales, inventory, purchases, and GST invoices are intentionally not implemented yet.

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

Backend coverage in this milestone focuses on password hashing, JWT claims, signup/login, authorization, and tenant isolation (including RLS).

## Current milestone

**Milestone 1 — Foundation + authentication + multi-tenant onboarding**

Done when a retailer can sign up, become OWNER of a company, sign in, and open a tenant-scoped ERP shell.

## Later milestones

1. Catalog: categories and products
2. Inventory and stock movements
3. Suppliers and purchases
4. Customers, POS, GST invoices
5. Reports, expenses, employees, notifications
6. VPS deployment with Caddy/Nginx
