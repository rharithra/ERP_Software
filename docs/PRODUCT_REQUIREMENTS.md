# RetailFlow — Product Requirements & Milestone 1 Development Specification

## 1. Product Vision

**Product Name:** RetailFlow

RetailFlow is a production-quality, multi-tenant SaaS ERP application for small and medium-sized Indian retail businesses.

This is NOT a college project, tutorial project, or simple CRUD demo.

The long-term goal is to build a commercially presentable SaaS product that can actually support the daily operations of a retail business and can be demonstrated to potential freelance clients.

### Target Customers
- Grocery stores
- Garment stores
- Mobile/electronics shops
- General retail stores
- Other small and medium-sized Indian retailers

### Long-term Business Flow

Company signup
→ Company setup
→ Products
→ Categories
→ Suppliers
→ Purchases
→ Inventory
→ Customers
→ POS/Sales
→ GST invoice
→ Reports
→ Dashboard
→ Business insights

The product must feel like a real commercial SaaS application, not a developer portfolio CRUD application.

The UI must be professional, modern, attractive, clean and business-focused.

---

# 2. Development Philosophy

Do NOT attempt to build the entire ERP in one pass.

Build incrementally through milestones.

Each milestone must:
1. Be implemented completely.
2. Be tested.
3. Be reviewed for security.
4. Be reviewed for multi-tenant isolation.
5. Be documented.
6. Leave the project in a runnable state.

Do not implement future modules prematurely.

Do not create fake implementations just to make a feature appear complete.

Do not use placeholder business logic where real logic is expected.

If something is unclear, stop and explain the ambiguity before making a major architectural decision.

Do not unnecessarily change previously approved architecture.

---

# 3. Technology Stack

## Backend
- Java 21
- Spring Boot 3.x
- Spring Security
- JWT authentication
- Spring Data JPA
- PostgreSQL
- Flyway database migrations
- Maven
- OpenAPI / Swagger
- Docker

## Frontend
- React
- TypeScript
- Tailwind CSS
- shadcn/ui
- React Router
- React Query / TanStack Query
- Lucide icons

## Infrastructure
- Docker Compose
- PostgreSQL
- Nginx/Caddy in eventual VPS deployment

The local development environment should work through Docker.

---

# 4. Architecture

Use clean, maintainable architecture.

Backend separation:

Controller
→ Service
→ Repository
→ Database

Use DTOs rather than exposing JPA entities directly through APIs.

Use:
- Request DTOs
- Response DTOs
- Validation
- Global exception handling
- Consistent API responses
- Proper HTTP status codes
- Logging
- Audit fields

Do not over-engineer with unnecessary microservices.

Use a modular monolith initially.

The application should be designed so modules remain clearly separated and can evolve later.

---

# 5. Multi-Tenancy

Multi-tenancy is a CORE architectural requirement.

Multiple companies must be able to use the same application.

Every tenant-owned business record must belong to a tenant.

Use:

`tenant_id`

as the primary tenant isolation mechanism.

Initial architecture:

**Application-level tenant enforcement + PostgreSQL Row Level Security (RLS)**

This provides defense in depth.

A user belonging to Tenant A must NEVER be able to access Tenant B's:
- products
- customers
- suppliers
- purchases
- sales
- inventory
- invoices
- reports
- settings
- employees
- expenses

Tenant context must be established from authenticated user information, not from an arbitrary tenant_id supplied by the frontend.

Never trust tenant_id from request payloads.

---

# 6. Tenant / Company Model

First release uses:

**Self-Signup**

A user should be able to:
1. Create an account.
2. Create a company/tenant.
3. Become the Owner of that tenant.
4. Configure basic company information.
5. Enter the ERP dashboard.

Initial roles:
- OWNER
- MANAGER
- CASHIER

Design role-based access control so permissions can be extended later.

---

# 7. Business Defaults

Initial target market:

**India**

Use:
- INR currency
- Indian GST-oriented tax model
- Indian business workflows

Do not over-generalize the tax system for every country in Version 1.

Design the model so internationalization can be added later.

---

# 8. Version 1 Modules

The eventual Version 1 will contain:

- Authentication
- Company onboarding
- Dashboard
- Categories
- Products
- Inventory
- Customers
- Suppliers
- Purchases
- Sales/POS
- Invoices
- Reports
- Expenses
- Employees
- Settings
- Notifications

Not all modules should be built in Milestone 1.

---

# 9. UI / UX

The application must have a premium SaaS appearance.

Design inspiration:
- Stripe Dashboard
- Shopify Admin
- Linear
- Vercel
- Notion

Use:

**Tailwind CSS + shadcn/ui + Lucide icons**

Do NOT blindly use default shadcn styling. Customize the design system.

### UI Principles
- Professional
- Clean
- Modern
- Attractive
- Excellent spacing
- Strong typography
- Consistent components
- Responsive
- Accessible
- Fast
- Business focused

Avoid:
- Excessive gradients
- Excessive glassmorphism
- Flashy animations
- Unnecessary visual effects

Use subtle micro-interactions only when useful.

Support:
- Light mode
- Dark mode

Use a consistent spacing system.

Use Inter or an equivalent professional UI font.

---

# 10. UI Quality Requirements

This application will be shown to real freelance clients.

Therefore every screen must look presentation-ready.

Do not use:
- Lorem ipsum
- "Test User"
- Meaningless placeholder cards
- Ugly default browser forms
- Random colors
- Inconsistent button styles

When demo data is eventually created, use realistic Indian retail business data.

---

# 11. Dashboard

The eventual dashboard will contain:
- Today's Revenue
- Today's Sales
- Orders
- Profit
- Low Stock
- Top Selling Products
- Recent Transactions
- Sales Charts
- Business Insights

For the first milestone, create only the dashboard shell and appropriate placeholder states where necessary.

Do NOT implement fake analytics before the required data exists.

### Dashboard Customization
Initial version:
**Preset Layouts**

Do NOT implement drag-and-drop dashboard customization yet.

---

# 12. Notification System

The eventual application will have a Notification Center:
- Bell icon
- Unread count
- Notification panel
- Persistent business notifications

Examples:
- Low stock
- Purchase received
- Invoice generated
- New customer

Do not build the full notification system in Milestone 1 unless required for the foundation.

---

# 13. Landing Website

The eventual product website will be a full SaaS website containing:
- Home
- Features
- Industries
- Pricing
- Demo
- FAQ
- Contact
- Login
- Signup

Do not allow the marketing website to distract from the ERP foundation in Milestone 1 unless the existing project structure requires a minimal entry page.

---

# 14. POS Direction

The eventual POS must be:
- Barcode ready
- Scanner-friendly
- GST aware
- Printable
- Suitable for Indian retail

Receipt printing should use printer-friendly web layouts.

Do NOT implement hardware SDK integrations in Version 1.

---

# 15. File Storage

Initial file storage:

**Docker-mounted local volumes**

Do not introduce S3/MinIO unnecessarily at this stage.

---

# 16. Deployment

Eventually deploy using:

Single Linux VPS
+
Docker Compose
+
Nginx/Caddy
+
React frontend
+
Spring Boot backend
+
PostgreSQL

The local development environment should therefore be Docker-friendly from the beginning.

---

# 17. Testing Strategy

Use a backend-heavy testing strategy.

### Backend
- Unit tests
- Integration tests
- Security tests
- Repository tests where appropriate

### Frontend
- Focused tests for important components and flows

Do not chase 100% coverage.

Prioritize tests for business-critical functionality.

Especially:
- Authentication
- Tenant isolation
- Authorization
- Inventory
- Sales
- Purchases
- Invoice calculations

---

# 18. Security

Security is important from the beginning.

Implement:
- Password hashing
- JWT authentication
- Proper token validation
- Role-based authorization
- Input validation
- Tenant isolation
- Secure error handling
- No sensitive information in logs
- No passwords in plain text
- No secrets hardcoded in source code

Use environment variables for secrets and configuration.

Never commit credentials to source control.

---

# 19. Database Principles

Use PostgreSQL.

Use Flyway for migrations.

Do not allow Hibernate to silently modify production schema.

Use explicit migrations.

Include appropriate:
- Primary keys
- Foreign keys
- Unique constraints
- Indexes
- Audit timestamps

For tenant-owned tables, design indexes and constraints with tenant isolation in mind.

---

# 20. CURRENT MILESTONE 1

## Foundation + Authentication + Multi-Tenant Onboarding

The objective is NOT to build the entire ERP.

At the end of Milestone 1, this flow must work:

User visits RetailFlow
↓
Signup
↓
Create account
↓
Create company
↓
User becomes OWNER
↓
Company/tenant is created
↓
User logs in
↓
JWT authentication
↓
Tenant context established
↓
ERP application shell
↓
Dashboard

---

# 21. Milestone 1 Backend Requirements

Implement:
1. Project configuration
2. PostgreSQL connection
3. Flyway
4. Base database structure
5. User entity
6. Tenant/Company entity
7. User-Tenant relationship
8. Role model
9. Authentication
10. Password hashing
11. JWT
12. Login
13. Signup
14. Tenant/company creation
15. Owner assignment
16. Tenant context
17. Tenant-aware security
18. Global exception handling
19. Request validation
20. API documentation
21. Unit/integration tests

### Minimum Conceptual Entities
- User
- Tenant / Company
- Role

Supporting entities may be introduced if architecturally necessary, but avoid unnecessary complexity.

---

# 22. Authentication Flow

Signup request should contain appropriate information such as:
- User name
- Email
- Password
- Company name

Backend should:
1. Validate request.
2. Check email uniqueness according to the chosen identity model.
3. Create tenant.
4. Create user.
5. Assign OWNER role.
6. Associate user with tenant.
7. Hash password.
8. Return appropriate authentication response.

Login should:
1. Validate credentials.
2. Authenticate user.
3. Determine tenant membership.
4. Generate JWT.
5. Include only appropriate claims.
6. Return authenticated user information needed by frontend.

Do NOT trust tenant_id supplied by the client.

---

# 23. Milestone 1 Frontend

Create:
- Application shell
- Login page
- Signup page
- Company onboarding page if separated from signup
- Protected routes
- Dashboard shell
- Sidebar
- Top navigation
- User menu
- Theme switcher
- Responsive layout
- Error states
- Loading states
- Toast notifications where appropriate

### Sidebar for Future Modules

Dashboard
Sales
Products
Inventory
Customers
Suppliers
Purchases
Reports
Expenses
Employees
Settings

For modules not yet implemented:
- Show disabled/coming-soon state
OR
- Route to a professional "Coming Soon" page

Do not create fake CRUD screens for future modules.

---

# 24. Design System

Before creating many pages, establish reusable components:
- Button
- Input
- Select
- Form
- Card
- Dialog
- Dropdown
- Table foundation
- Badge
- Toast
- Alert
- Loading skeleton
- Empty state
- Page header
- Sidebar
- Topbar

Use shadcn/ui where appropriate and customize it consistently.

Do not duplicate UI code unnecessarily.

---

# 25. API Design

Use REST APIs.

Use clear naming.

Examples:

`POST /api/v1/auth/signup`
`POST /api/v1/auth/login`
`GET /api/v1/auth/me`

`GET /api/v1/tenant`
`PUT /api/v1/tenant`

Do not expose internal implementation details.

Use consistent response/error structures.

---

# 26. Documentation

Create/update:

`README.md`

Include:
- Project overview
- Architecture
- Technology stack
- Local setup
- Environment variables
- Docker setup
- Database setup
- Running backend
- Running frontend
- Testing
- API documentation
- Current milestone
- Future milestones

Also create:

`ARCHITECTURE.md`

Document important architectural decisions.

Recommended additional documentation:

`docs/PRODUCT_REQUIREMENTS.md`
`docs/DEVELOPMENT_ROADMAP.md`

---

# 27. Docker

Create a local Docker Compose setup for:
- PostgreSQL
- Backend
- Frontend if appropriate

Use environment variables.

Make sure a new developer can clone the repository and understand how to start the project.

---

# 28. Git

Keep commits logical.

Do not create one giant commit for the entire milestone.

Suggested commits:
- project initialization
- database foundation
- authentication
- tenant onboarding
- security and tenant context
- frontend shell
- authentication UI
- tests
- documentation

---

# 29. AI Development Behavior

As my AI coding companion:

Before implementing a major feature:

1. Inspect the existing project.
2. Explain what you found.
3. State your implementation plan.
4. Identify files that will change.
5. Implement.
6. Run tests/build.
7. Report what changed.
8. Report remaining issues.

Do not overwrite working code unnecessarily.

Do not create duplicate classes/components.

Do not change architecture merely because another approach is possible.

Prefer simple, maintainable solutions.

---

# 30. Definition of Done — Milestone 1

Milestone 1 is complete only when:

- [ ] Backend starts successfully
- [ ] PostgreSQL starts successfully
- [ ] Flyway migrations run successfully
- [ ] User can signup
- [ ] Company/tenant is created
- [ ] User becomes Owner
- [ ] Password is securely hashed
- [ ] User can login
- [ ] JWT authentication works
- [ ] Protected API endpoints work
- [ ] Tenant context is established securely
- [ ] Tenant isolation is enforced
- [ ] Unauthorized requests are rejected
- [ ] Role authorization works
- [ ] Frontend login works
- [ ] Frontend signup works
- [ ] Protected dashboard works
- [ ] ERP shell is responsive
- [ ] Light/dark mode works
- [ ] Backend tests pass
- [ ] Frontend builds successfully
- [ ] Docker setup works
- [ ] README is updated
- [ ] Architecture documentation is updated
- [ ] No secrets are committed
- [ ] No major TODO placeholders exist in completed functionality

---

# 31. Do NOT Implement in Milestone 1

Do NOT implement:
- Product CRUD
- Inventory CRUD
- Purchase module
- POS
- Invoice generation
- Reports
- Expenses
- Employees
- Subscription billing
- Platform admin
- Hardware integrations
- S3
- Complex dashboard customization
- AI features

These belong to later milestones.

---

# 32. First Instruction to Cursor

FIRST inspect the current repository/workspace.

Do NOT immediately generate code.

Tell me:

1. What files/projects already exist.
2. What technology is already configured.
3. What can be reused.
4. What needs to be created.
5. Whether there are any conflicts with the architecture above.
6. Your proposed Milestone 1 implementation plan.

Then WAIT for my approval before making major structural changes.

Remember:

We are building a real product.

Prioritize correctness, security, maintainability, UX quality, and business usefulness over simply generating a large amount of code.

---

# 33. Milestone 2 — Categories and products (COMPLETE)

Milestone 1 and Milestone 1 Hardening are complete. Milestone 2 is the product catalog only.

## Models

**Category** — tenant-owned: id (UUID), name (required, unique per tenant), description (optional), active, createdAt, updatedAt. No hierarchy. Deactivate instead of hard delete.

**Product** — tenant-owned: id, category (required, same tenant), name, sku (unique per tenant), barcode (optional, unique per tenant when set), description, costPrice, sellingPrice (`BigDecimal` / `NUMERIC`, never float), gstRate (0/5/12/18/28), unit (PCS, KG, G, L, ML, BOX, PACK, BOTTLE), active, timestamps.

Products cannot reference another tenant’s category (Hibernate tenant filter + composite FK). Tenant id is never accepted from the client.

## APIs

`/api/v1/categories` and `/api/v1/products` with list (search, status, pagination), get, create, update, patch status. Writes: OWNER/MANAGER. Reads: OWNER/MANAGER/CASHIER. `@TenantBypass` is not used.

## UI

`/app/categories` and `/app/products` inside the ERP shell. Inventory, purchases, sales, customers, suppliers, reports remain coming-soon.

## Out of scope

Stock quantities, warehouses, purchases, POS, invoices, expenses, employees, reports, AI, barcode scanners.

---

# 34. Milestone 3 — Inventory (COMPLETE)

Single-location inventory for the authenticated tenant.

## Models

**Inventory balance** — one row per product per tenant: quantity (`NUMERIC(19,3)`), reorder level, opening-recorded flag, timestamps.

**Stock movement** — append-only ledger: OPENING_STOCK, ADJUSTMENT_IN, ADJUSTMENT_OUT, PURCHASE_RECEIPT. Stores before/after, reason, notes, actor, optional purchase reference. History cannot be edited or deleted.

**Status** — derived: IN_STOCK, LOW_STOCK, OUT_OF_STOCK. Negative stock is rejected.

## APIs

`/api/v1/inventory` list, summary, get, movements, opening-stock, adjustments, reorder-level. Reads: OWNER/MANAGER/CASHIER. Mutations: OWNER/MANAGER. Tenant from JWT only. `@TenantBypass` is not used. Concurrent updates lock the balance row.

## UI

`/app/inventory` with live summary cards, search/filters, opening stock, adjustments, reorder level, movement history.

## Out of scope

GRN in M3, sales/POS, customers, warehouses, batches, serials, barcode scanners.

---

# 35. Milestone 4 — Suppliers and purchases (COMPLETE)

Procurement for the authenticated tenant. Inventory stays the single source of truth for quantity.

## Models

**Supplier** — tenant-owned vendor master. Name required and unique per tenant. Optional contact, phone, email, address, GSTIN, notes. Active flag; no hard delete in this slice.

**Purchase** — `PUR-000001` style number, supplier, date, DRAFT / RECEIVED / CANCELLED, server-calculated subtotal / tax / total (`NUMERIC` / `BigDecimal`), notes, receivedAt, createdBy.

**Purchase item** — product (same tenant), snapshotted name/SKU/unit, quantity (`NUMERIC(19,3)`), unit cost, GST slab (0/5/12/18/28), line totals.

## Lifecycle

DRAFT does not change stock and can be edited or cancelled. RECEIVED is full receiving only: `PurchaseService` → `InventoryService.applyPurchaseReceipt` → balance lock → `PURCHASE_RECEIPT` movement (`referenceType=PURCHASE`). Double receive is rejected. Received purchases cannot be edited or cancelled. Purchase returns are out of scope.

## APIs

`/api/v1/suppliers` CRUD + status + summary. `/api/v1/purchases` list/summary/get/create/update/receive/cancel. Reads: OWNER/MANAGER/CASHIER. Mutations: OWNER/MANAGER. Tenant from JWT only.

## UI

`/app/suppliers`, `/app/purchases`, `/app/purchases/new`, `/app/purchases/:id`, `/app/purchases/:id/edit`. Receive confirmation dialog. Cashiers can view, not mutate.

## Out of scope

Sales returns, refunds, credit/receivables, coupons, loyalty, payment gateways, thermal printer SDKs, CGST/SGST/IGST split, e-invoicing, warehouses, FIFO/weighted average costing.

---

# 36. Milestone 5 — Customers, sales/POS, and invoices (COMPLETE)

Daily retail billing for the authenticated tenant. Inventory stays the single source of truth for quantity.

## Models

**Customer** — tenant-owned buyer master. Name required. Optional phone (unique per tenant when set), email, address, GSTIN, notes. Active flag; no hard delete.

**Sale** — `SAL-000001` style number, optional customer (walk-in allowed), date, DRAFT / COMPLETED / CANCELLED, server-calculated subtotal / discount / tax / grand total (`NUMERIC` / `BigDecimal`), payment method, notes, createdBy. Completed sales also store invoice number `INV-000001`, payment status PAID, and company/customer snapshots for printing.

**Sale item** — product (same tenant), snapshotted name/SKU/unit, quantity (`NUMERIC(19,3)`), unit selling price, GST slab (0/5/12/18/28), line taxable/tax/total.

## Lifecycle

DRAFT does not change stock and can be edited or cancelled. COMPLETED is full completion only: `SaleService` → `InventoryService.applySale` → balance lock → `SALE` movement (`referenceType=SALE`). Insufficient stock rolls back the entire sale (`INSUFFICIENT_STOCK`). Double complete is rejected. Completed sales cannot be edited. Cancelled sales cannot be completed. Returns/refunds are out of scope.

## POS

`/app/sales/new`: search by name/SKU/barcode, scanner-as-keyboard Enter to add, cart quantities, sale-level discount, walk-in or existing customer, payment method CASH/UPI/CARD/OTHER. Completing posts stock and opens the invoice.

## Invoice

Browser print / Save as PDF of the completed sale. Route `/app/sales/:id/invoice`. Company fields come from Tenant snapshots taken at complete time. No server-side PDF.

## APIs

`/api/v1/customers` CRUD + status + summary. Reads: OWNER/MANAGER/CASHIER. Mutations: OWNER/MANAGER.

`/api/v1/sales` list/summary/dashboard/get/invoice/create/update/complete/cancel. OWNER/MANAGER/CASHIER for sales writes. Tenant from JWT only.

## UI

`/app/customers`, `/app/sales`, `/app/sales/new`, `/app/sales/:id`, `/app/sales/:id/invoice`. Dashboard shows today's orders/revenue from completed sales.

## Out of scope

Returns, refunds, credit, coupons, loyalty, payment gateways, hardware SDKs, CGST/SGST/IGST engine, accounting, warehouses, batches, serials.

---

# 37. Milestone 5.1 — Business-aware sales foundation (COMPLETE)

Tenant configuration so different shops see the right **entry** into one sales engine.

## Models

**Business type** (what you sell): GROCERY_SUPERMARKET, ELECTRONICS_COMPUTER, MOBILE_ACCESSORIES, APPLIANCES_WATER_PURIFIER, FURNITURE, HARDWARE_BUILDING_MATERIALS, OTHER.

**Sales mode** (how you sell): QUICK_SALE, PIPELINE, HYBRID. Recommendations exist; the owner may override. Changing mode never deletes sales, invoices, products, inventory, or customers.

Quick Sale and Sales Pipeline are different entry workflows into the same Sale → Invoice → Payment → Inventory system. This milestone does **not** implement Leads, Follow-ups, Quotations, or Sales Orders.

## APIs

Signup accepts optional `businessType` / `salesMode`. Omitted type defaults to OTHER; omitted mode uses the recommendation. `GET/PUT /api/v1/tenant` includes both fields. OWNER-only mutation of those fields.

## UI

Signup: business type + recommended sales mode. Settings: Business profile. Navigation: Quick Sale hides CRM items; Pipeline/Hybrid show them. Dashboard states the configured experience. Changing mode never deletes data.

## Out of scope for 5.1

Pipeline screens themselves (delivered in Milestone 6), returns.

---

# 38. Milestone 6 — Sales pipeline & CRM (COMPLETE)

Support retailers where a sale is not immediate at POS, without splitting the ledger.

## Workflows

**Quick Sale:** Customer → POS → Sale → Invoice → Payment → Inventory.

**Sales Pipeline:** Lead → Follow-up → Quotation → Sales Order → Payment/Advance → Sale → Invoice → Payment → Inventory.

Both use `SaleService`, existing invoices, `PaymentService`, and `InventoryService.applySale`.

## Inventory rule

Quotation: no stock change. Sales order: no stock change. Advance payment: no stock change. Completed sale: decrease stock with a SALE movement.

## Domain

- Leads (`LEAD-000001`) with sources, priority, status NEW…WON/LOST, convert to existing Customer module (phone match avoids duplicates).
- Follow-ups: CALL/VISIT/WHATSAPP/EMAIL/MEETING/OTHER; PENDING/COMPLETED/CANCELLED; today/overdue/upcoming.
- Quotations (`QT-000001`) with product snapshots including GST; DRAFT→SENT→ACCEPTED (locked) | REJECTED | EXPIRED | CANCELLED.
- Sales orders (`SO-000001`) DRAFT→CONFIRMED→PROCESSING→READY→COMPLETED; convert uses `SaleService`.
- Payments (`PAY-000001`) against sales order and/or sale; outstanding = total − sum(payments); reject amount > outstanding.
- Append-only `sales_pipeline_activities` timeline. In-app notifications (follow-up due/overdue, quotation expiry/accepted, payment outstanding).

## Authorization

OWNER/MANAGER: CRM. CASHIER: POS, sales, invoices, payments as in M5. Tenant id never from the client.

## UI

`/app/pipeline` (metrics + kanban), `/app/leads`, `/app/follow-ups`, `/app/quotations`, `/app/sales-orders`, `/app/outstanding`, record-payment dialog, customer payment history.

## Out of scope

WhatsApp/email/SMS, payment gateways, customer portal, marketing, AI scoring, loyalty, coupons, commissions, GL, CGST/SGST engine, warehouses, batches, serials, delivery, subscriptions, credit notes, returns, refunds, aging.



