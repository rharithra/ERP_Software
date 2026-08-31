# Architecture

RetailFlow is a modular monolith. One Spring Boot process and one React SPA serve every tenant. There are no microservices.

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

1. **Application** — `TenantContext` ThreadLocal from the JWT membership (never from query/body/path). Hibernate filter `tenantFilter` restricts tenant-owned rows.
2. **Database** — `FORCE ROW LEVEL SECURITY` on `tenants`, `tenant_memberships`, `categories`, `products`, `inventory_balances`, `stock_movements`, `suppliers`, `purchase_number_counters`, `purchases`, and `purchase_items`. Policies allow a row only when `app.current_tenant_id` matches, unless `app.bypass_rls` is `on` for bootstrap/auth lookups.

Tenant binding is **always on** for each new Spring transaction:

- A `TransactionExecutionListener` runs after the transaction begins.
- If `@TenantBypass` is active on the thread (set by an Aspect **before** the transactional interceptor), the listener enables RLS bypass only and leaves the Hibernate tenant filter off. Used solely for signup, login, and membership lookup.
- Else if `TenantContext` has a tenant id, the listener enables the Hibernate filter and sets `app.current_tenant_id` with `app.bypass_rls=off`.
- Else it fail-closes: empty tenant GUC, bypass off, filter disabled. RLS then returns no tenant-owned rows.

GUCs use `SET LOCAL` via `set_config(..., true)`, so bypass cannot leak into the next transaction on a pooled connection.

Users table has no RLS so login can resolve email without a tenant session.

## Authorization

Method security:

- `GET /api/v1/tenant` — OWNER, MANAGER, CASHIER
- `PUT /api/v1/tenant` — OWNER, MANAGER
- Catalog reads (`GET /api/v1/categories`, `GET /api/v1/products`, …) — OWNER, MANAGER, CASHIER
- Catalog writes — OWNER, MANAGER
- Inventory reads — OWNER, MANAGER, CASHIER
- Inventory opening stock, adjustments, reorder level — OWNER, MANAGER

MANAGER is not creatable through signup or the UI in Milestone 1. Automated tests insert a MANAGER membership with SQL. There is no invitation flow.

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

The SPA stores the access token in `localStorage` (same-origin, no refresh cookie yet). Protected routes load `/api/v1/auth/me`. A 401 on any authenticated API call clears the token and sends the browser to `/login`. Login and signup 401 responses do not redirect, so the forms can show errors. Categories, products, inventory, suppliers, and purchases are live. Later modules still render a coming-soon page instead of fake CRUD.

Theme uses CSS variables and `next-themes` (`class` on `html`) for light and dark mode.

## Catalog (Milestone 2)

Tenant-owned master data. Tenant id is never taken from the client. Catalog services do **not** use `@TenantBypass`.

### Category

`id`, `tenant`, `name` (unique per tenant, case-insensitive), optional `description`, `active`, audit timestamps. No subcategories. Inactive is preferred over hard delete.

### Product

`id`, `tenant`, required `category` (same tenant — composite FK `(category_id, tenant_id)`), `name`, `sku` (unique per tenant, case-insensitive), optional `barcode` (unique per tenant when present), optional `description`, `costPrice` / `sellingPrice` as `NUMERIC(12,2)` / `BigDecimal` (never float), `gstRate`, `unit`, `active`, audit timestamps.

SKU example: `AMUL-MILK-500`. Barcode example: `8901234567890`. Barcode is optional; no scanner hardware in this milestone.

Two tenants may reuse the same SKU or barcode.

### GST

`GstRate` enum: 0, 5, 12, 18, 28 percent. Stored on the product as `NUMERIC(4,2)`. Future sales/invoices must reuse this type instead of scattering hardcoded rates.

### Units

`ProductUnit`: PCS, KG, G, L, ML, BOX, PACK, BOTTLE. Identifies how the SKU is sold. No conversion engine.

### Pagination

`PageResponse`: 1-based `page`, `size` (default 20, max 100), `items`, `totalItems`, `totalPages`. Search is SQL `LIKE` on name (categories) and name/SKU/barcode (products).

## Inventory (Milestone 3)

Single-location stock per tenant. No warehouses. Tenant id is never taken from the client. Inventory services do **not** use `@TenantBypass`.

### Balance

One `inventory_balances` row per product (`UNIQUE (tenant_id, product_id)`). `quantity` and `reorderLevel` are `NUMERIC(19,3)` / `BigDecimal` (never float). Quantity is in the product's unit. Composite FK `(product_id, tenant_id)` prevents cross-tenant products.

### Status (derived, not stored)

- `OUT_OF_STOCK` — quantity = 0
- `LOW_STOCK` — quantity > 0 and quantity ≤ reorder level
- `IN_STOCK` — quantity > reorder level

### Movements (append-only)

`OPENING_STOCK`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `PURCHASE_RECEIPT`. Each row stores quantity (always positive), before/after, optional reason/notes, `createdBy`, timestamp, and optional `reference_type` / `reference_id`. Users cannot edit or delete history; a later adjustment corrects mistakes.

Opening stock is allowed once. Further corrections use adjustments. Adjustment out that would go negative is rejected with `INSUFFICIENT_STOCK`.

### Concurrency

Balance updates take a PostgreSQL row lock (`SELECT FOR UPDATE` / JPA `PESSIMISTIC_WRITE`) inside a transaction so concurrent adjustments cannot lose updates.

### Future postings

Milestone 5 posts `SALE` movements through `InventoryService.applySale` and decreases quantity. There is still a single stock quantity on `InventoryBalance`.

## Purchases (Milestone 4)

Tenant-scoped procurement. Inventory remains the only current-stock store (`InventoryBalance.quantity`). Purchases never duplicate stock on product, supplier, or purchase rows.

```
Supplier → Purchase (DRAFT) → Receive → InventoryService.applyPurchaseReceipt
                                         → InventoryBalance + StockMovement PURCHASE_RECEIPT
```

### Supplier

`suppliers`: name unique per tenant (case-insensitive). Optional contact, phone, email, address, GSTIN, notes. Deactivate instead of delete. OWNER/MANAGER mutate; CASHIER may list/view.

### Purchase

Human number `PUR-000001` from a per-tenant counter (`SELECT FOR UPDATE`). Status: `DRAFT` → `RECEIVED` or `DRAFT` → `CANCELLED`. Received purchases cannot return to draft, cannot be edited, and cannot be cancelled. No partial receiving.

Items snapshot product name, SKU, unit, unit cost, and GST at save time. Line math (HALF_UP, scale 2):

- `lineSubtotal = quantity × unitCost`
- `lineTax = lineSubtotal × gstRate / 100`
- `lineTotal = lineSubtotal + lineTax`

Purchase totals are sums of lines. The API ignores client-supplied totals.

### Receiving

`PurchaseService.receive` is one transaction: lock the purchase row, verify DRAFT and non-empty items, lock each inventory balance via the existing inventory service, increase quantity, write `PURCHASE_RECEIPT` with `referenceType=PURCHASE` and `referenceId=purchaseId`, then set `RECEIVED` + `receivedAt`. A second receive returns 409 and does not add stock again.

CASHIER cannot create, edit, receive, or cancel.

## Sales (Milestone 5)

Tenant-scoped customers, POS, invoices, and inventory decrease. Inventory remains the only current-stock store.

```
Customer (optional) → Sale (DRAFT) → Complete → InventoryService.applySale
                                                → InventoryBalance decrease + StockMovement SALE
                                                → invoice number INV-000001
```

### Customer

`customers`: name required. Optional phone (unique per tenant when present), email, address, GSTIN, notes. Deactivate instead of delete. OWNER/MANAGER mutate; CASHIER may list/search/view. Tenant id never accepted from the client.

### Sale

Human number `SAL-000001` from a per-tenant counter (`SELECT FOR UPDATE`). Status: `DRAFT` → `COMPLETED` or `DRAFT` → `CANCELLED`. Completed sales cannot be edited or completed again. Cancelled sales cannot be completed. Walk-in customer is `customer_id` null with snapshot name `Walk-in`.

Items snapshot product name, SKU, unit, unit selling price, and GST at save time. Line math (HALF_UP, scale 2):

- `taxable = quantity × unitPrice − lineDiscount`
- `tax = taxable × gstRate / 100`
- `lineTotal = taxable + tax`

Sale-level discount reduces grand total after line GST: `grandTotal = subtotal − discount + taxTotal`. Discount cannot exceed subtotal. The API ignores client-supplied totals.

Payment methods: `CASH`, `UPI`, `CARD`, `BANK_TRANSFER`, `OTHER`. POS completion still records a payment for the full grand total (`PAID`). Pipeline sales may be `UNPAID` or `PARTIALLY_PAID` when advances exist. No payment gateways.

### Completing

`SaleService.complete` is one transaction: lock the sale, verify DRAFT and non-empty items, lock each inventory row (product ids sorted to reduce deadlock), `InventoryService.applySale` (insufficient stock → `INSUFFICIENT_STOCK`, full rollback), allocate `INV-000001`, snapshot company profile onto the sale, mark COMPLETED. Invoice is the completed sale plus those snapshots — no duplicate financial table.

CASHIER may create, complete, cancel, and view sales and invoices. CASHIER cannot manage customers, products, inventory adjustments, suppliers, or company settings.

## Business-aware sales foundation (Milestone 5.1)

`business_type` and `sales_mode` live on `tenants`. They describe **what** the shop sells and **how** it enters a sale. They do not split the ledger.

Quick Sale and Sales Pipeline are different **entry workflows** into the same underlying **Sale → Invoice → Payment → Inventory** system. There is no second sale table, invoice table, or stock quantity.

Recommended `sales_mode` (owner may override): grocery → `QUICK_SALE`; water purifier / furniture → `PIPELINE`; electronics, mobiles, hardware, other → `HYBRID`.

Existing tenants migrate to `OTHER` / `HYBRID`. OWNER updates via `PUT /api/v1/tenant`. MANAGER may still update company identity, not sales configuration. Navigation hides pipeline CRM for Quick Sale. Pipeline and Hybrid show Leads, Follow-ups, Quotations, Sales orders, Outstanding, and the pipeline board (OWNER/MANAGER). Changing sales mode never deletes data.

## Sales pipeline & CRM (Milestone 6)

Leads, follow-ups, quotations, and sales orders are a **workflow**. They feed the existing `SaleService` / invoice / `PaymentService` / `InventoryService`. There is no second sale, invoice, or stock engine.

**Inventory:** quotation, sales order, and advance payment do **not** change stock. Only `SaleService.complete` / `completeConvertedSale` calls `InventoryService.applySale`.

**Numbers:** `LEAD-000001`, `QT-000001`, `SO-000001`, `PAY-000001` via locked per-tenant counters (not max+1).

**Payments:** every rupee is a `payments` row. Advances on a sales order keep the same rows when the order converts (`sale_id` attached). Paid and outstanding are sums, never a lone `advanceAmount` field. Payment amount cannot exceed outstanding.

**Authorization:** CRM (leads through sales orders, pipeline dashboard) is OWNER/MANAGER. CASHIER keeps POS, sales, invoices, and payment recording. Tenant id is taken only from JWT → membership → `TenantContext`. New tables use FORCE RLS.

**APIs:** `/api/v1/leads`, `/follow-ups`, `/quotations`, `/sales-orders`, `/payments`, `/pipeline/dashboard`, `/notifications`, `/tenant/members`.

**UI:** `/app/pipeline`, `/app/leads`, `/app/follow-ups`, `/app/quotations`, `/app/sales-orders`, `/app/outstanding`. In-app notifications only (no email/SMS/WhatsApp).

## What this repo will not do in M6

WhatsApp/email/SMS, payment gateways, customer portal, marketing automation, AI scoring, loyalty, coupons, commissions, GL, CGST/SGST split, warehouses, batches, serials, delivery logistics, subscriptions, credit notes, sales returns, refunds, advanced aging.
