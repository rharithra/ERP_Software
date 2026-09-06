# Milestone 8 — Returns, refunds, and customer credit

RetailFlow keeps **one Sale engine**, **one Payment ledger**, **one Invoice** (completed sale), and **one InventoryService**. Returns do not rewrite history.

## Documents

```
Sale (immutable after complete)
 ├── Sale items (never edited after complete)
 ├── Payments (never deleted or reversed in place)
 ├── Invoice number on the sale
 └── Sale returns (RET-000001, tenant counter)
       ├── Return items (linked to original sale_item_id)
       ├── SALE_RETURN inventory movements (on complete only)
       └── optional Refunds (REF-000001)
Customer credit ledger (CREDIT_CREATED / CREDIT_APPLIED / CREDIT_REFUNDED)
```

Statuses: return `DRAFT → COMPLETED | CANCELLED`. Completed returns are immutable. Refund `PENDING → COMPLETED | CANCELLED`.

## Inventory

Draft and cancelled returns do not touch stock. Completing a return calls `InventoryService.applySaleReturn` (`SALE_RETURN`, quantity increase, reference `SALE_RETURN` + return id). Concurrent completes lock the sale (`SELECT … FOR UPDATE`) and re-validate remaining quantity.

Returned goods go back to available stock. Damaged/quarantine locations are not in M8.

## Settlement (`SaleSettlementService`)

```
netSale = originalGrandTotal − completedReturnTotal
actualPaid = sum(payment rows)   // paidAmount kept as this for compatibility
settlement = actualPaid + creditAppliedToThisSale
outstanding = max(0, netSale − settlement)
customerCredit (this sale) = remaining overpay after refunds and credit applied onward
```

Payment status:

| Status | Meaning |
| --- | --- |
| `UNPAID` | Nothing settled and net sale > 0 |
| `PARTIALLY_PAID` | Some settlement, outstanding remains |
| `PAID` | Net sale fully settled and no remaining customer credit on this sale |
| `REFUND_DUE` | Customer has remaining credit (paid more than net sale) |

There is no `REFUNDED` status: after credit is refunded or applied, status returns to `PAID` (or `UNPAID`/`PARTIALLY_PAID` if a later sale still has a balance). Derived `returnStatus`: `NONE` / `PARTIALLY_RETURNED` / `FULLY_RETURNED`. The sale is never auto-cancelled.

`paidAmount` remains **actual cash/UPI/card payments**. Use `actualPaidAmount`, `customerCreditApplied`, `netSaleAmount`, and `customerCreditAmount` for the full picture.

## Credit ledger

Customer balance = `CREDIT_CREATED − CREDIT_APPLIED − CREDIT_REFUNDED`. Completing a return that leaves the customer overpaid writes `CREDIT_CREATED`. Applying store credit to a sale writes `CREDIT_APPLIED` (FIFO from source sales). Completing a refund writes `CREDIT_REFUNDED`. Credit is never a negative payment row.

POS complete accepts optional `creditAmount` and `paymentAmount`. Default remains: pay the remainder after credit (full settlement for existing POS clients).

## Authorization

| Action | OWNER | MANAGER | CASHIER |
| --- | --- | --- | --- |
| Create / complete / cancel returns | yes | yes | yes |
| View receivables, credit, refunds | yes | yes | yes |
| Apply store credit on a sale | yes | yes | yes |
| Create pending refund | yes | yes | yes |
| Complete refund | yes | yes | no |

Tenant id is never taken from the client. New tables use Hibernate `tenantFilter` and PostgreSQL `FORCE ROW LEVEL SECURITY`. Cross-tenant GET returns 404.

## APIs

- `GET/POST /api/v1/returns`, `POST /api/v1/returns/{id}/complete|cancel`
- `GET/POST /api/v1/refunds`, `POST /api/v1/refunds/{id}/complete|cancel`
- `POST /api/v1/sales/{id}/apply-credit`
- `GET /api/v1/customers/{id}/credit`, `/credit-transactions`, `/financial`, `/returns`, `/refunds`

Business errors include `RETURN_QUANTITY_EXCEEDS_AVAILABLE`, `RETURN_ALREADY_COMPLETED`, `REFUND_EXCEEDS_AVAILABLE_CREDIT`, `CUSTOMER_CREDIT_INSUFFICIENT`, `SALE_NOT_RETURNABLE`.

## UI

`/app/returns` (all sales modes), create from sale details, return detail, refund dialog, customer financial summary, dashboard metrics from live queries.

## Out of scope (not M8)

GST credit notes, payment-gateway refunds, damaged/repair inventory, serials/batches, exchanges, loyalty, GL, aging reports, configurable cashier refund limits, email/SMS.
