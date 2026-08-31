import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, salesOrderApi, type SalesOrder, type SalesOrderStatus } from "@/lib/api";
import { paymentStatusLabel } from "@/lib/pipeline";
import { inr } from "@/lib/sale-math";

const STATUSES: SalesOrderStatus[] = ["DRAFT", "CONFIRMED", "PROCESSING", "READY", "COMPLETED", "CANCELLED"];

export function SalesOrdersPage() {
  const [items, setItems] = useState<SalesOrder[]>([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"all" | SalesOrderStatus>("all");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const list = await salesOrderApi.list({
        q: query || undefined,
        status: status === "all" ? undefined : status,
        page,
        size: 20,
      });
      setItems(list.items);
      setTotalPages(Math.max(list.totalPages, 1));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load sales orders");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, page]);

  const emptyCopy = useMemo(() => {
    if (query || status !== "all") return "No sales orders match these filters.";
    return "Confirming an order does not decrease stock. Convert to a sale when goods leave the shop.";
  }, [query, status]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Sales orders</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Collect advances here. Outstanding is calculated from payment records, not a free-text amount.
          </p>
        </div>
        <Button asChild>
          <Link to="/app/sales-orders/new">
            <Plus className="mr-2 h-4 w-4" />
            New sales order
          </Link>
        </Button>
      </div>
      <Card>
        <CardContent className="space-y-4 pt-6">
          <form
            className="grid gap-3 md:grid-cols-[1fr_12rem_auto]"
            onSubmit={(e) => {
              e.preventDefault();
              setPage(1);
              void load();
            }}
          >
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="Search order number or customer"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search sales orders"
              />
            </div>
            <NativeSelect
              aria-label="Filter sales orders"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | SalesOrderStatus);
                setPage(1);
              }}
            >
              <option value="all">All statuses</option>
              {STATUSES.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </NativeSelect>
            <Button type="submit" variant="outline">
              Search
            </Button>
          </form>
          {loading ? (
            <Skeleton className="h-32" />
          ) : error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">{emptyCopy}</div>
          ) : (
            <>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Order</TableHead>
                      <TableHead>Customer</TableHead>
                      <TableHead>Total</TableHead>
                      <TableHead>Paid</TableHead>
                      <TableHead>Outstanding</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((order) => (
                      <TableRow key={order.id}>
                        <TableCell>
                          <Link className="font-mono text-sm hover:underline" to={`/app/sales-orders/${order.id}`}>
                            {order.orderNumber}
                          </Link>
                        </TableCell>
                        <TableCell>{order.customerName}</TableCell>
                        <TableCell>{inr(order.grandTotal)}</TableCell>
                        <TableCell>{inr(order.advancePaid)}</TableCell>
                        <TableCell>{inr(order.outstandingAmount)}</TableCell>
                        <TableCell>
                          <Badge>{order.status}</Badge>
                          <p className="text-xs text-muted-foreground">{paymentStatusLabel(order.paymentStatus)}</p>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {items.map((order) => (
                  <Link key={order.id} to={`/app/sales-orders/${order.id}`} className="block rounded-lg border p-4">
                    <p className="font-mono text-sm">{order.orderNumber}</p>
                    <p className="font-medium">{order.customerName}</p>
                    <p className="text-sm text-muted-foreground">
                      {inr(order.grandTotal)} · outstanding {inr(order.outstandingAmount)}
                    </p>
                  </Link>
                ))}
              </div>
              {totalPages > 1 ? (
                <div className="flex justify-end gap-2">
                  <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </Button>
                  <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>
                    Next
                  </Button>
                </div>
              ) : null}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
