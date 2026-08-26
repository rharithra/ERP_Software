# Development roadmap

## Milestone 1 — Foundation — COMPLETE

Authentication, company onboarding, tenant isolation, ERP shell, company profile.

## Milestone 1 Hardening — COMPLETE

JWT validation, fail-closed tenant binding, FORCE RLS, membership reload, global 401 handling, JWT secret for Docker.

## Milestone 2 — Catalog — COMPLETE

Categories and products with SKU, barcode, GST slab, unit, cost/selling price. Tenant-scoped uniqueness. No stock quantities. No HSN in this slice (GST rate only).

## Milestone 3 — Inventory — COMPLETE

Single-location stock balances, opening stock, manual adjustments, reorder levels, derived stock status, and an append-only movement ledger. No purchases, sales, or warehouses.

## Milestone 4 — Purchases

Suppliers, purchase orders, GRN, purchase invoices in INR.

## Milestone 5 — Sales and GST

Customers, POS, GST-aware invoices, printer-friendly receipts.

## Milestone 6 — Control plane

Reports, expenses, employees/invites, notification center.

## Milestone 7 — Operate

VPS Docker Compose, Caddy/Nginx TLS, backups, log hygiene.
