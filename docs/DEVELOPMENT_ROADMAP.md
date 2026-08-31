# Development roadmap

## Milestone 1 — Foundation — COMPLETE

Authentication, company onboarding, tenant isolation, ERP shell, company profile.

## Milestone 1 Hardening — COMPLETE

JWT validation, fail-closed tenant binding, FORCE RLS, membership reload, global 401 handling, JWT secret for Docker.

## Milestone 2 — Catalog — COMPLETE

Categories and products with SKU, barcode, GST slab, unit, cost/selling price. Tenant-scoped uniqueness. No stock quantities. No HSN in this slice (GST rate only).

## Milestone 3 — Inventory — COMPLETE

Single-location stock balances, opening stock, manual adjustments, reorder levels, derived stock status, and an append-only movement ledger. No purchases, sales, or warehouses.

## Milestone 4 — Purchases — COMPLETE

Suppliers, purchase drafts, goods receiving, and inventory integration. Purchase returns and supplier payments are later.

## Milestone 5 — Sales and GST — COMPLETE

Customers, POS, GST-aware invoices (invoice = completed sale), SALE stock movements through InventoryService, printer-friendly receipts. Returns, credit, and CGST/SGST split are later.

## Milestone 5.1 — Business-aware sales foundation — COMPLETE

Business type and sales mode on the tenant. Recommended sales experience at onboarding. Owner can change it in Settings. Navigation is mode-aware. **Sales Pipeline modules such as Leads, Follow-ups, Quotations and Sales Orders are NOT implemented in this milestone.** Quick Sale and Pipeline remain entry workflows into the same Sale → Invoice → Inventory engine.

## Milestone 6 — Sales pipeline & CRM — COMPLETE

Leads, follow-ups, quotations, sales orders, advance/partial payments, outstanding, pipeline board, lead timeline, in-app notifications. Pipeline converts to the existing Sale → Invoice → Inventory path. Quotation/SO/advance do not reduce stock.

## Milestone 7 — Control plane

Reports, expenses, employees/invites.

## Milestone 8 — Operate

VPS Docker Compose, Caddy/Nginx TLS, backups, log hygiene.
