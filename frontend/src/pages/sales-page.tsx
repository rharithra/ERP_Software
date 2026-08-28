import { useEffect, useMemo, useState } from "react";
import { Plus, Search } from "lucide-react";
import { Link } from "react-router-dom";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, customerApi, saleApi, type Customer, type Sale, type SaleStatus, type SaleSummary } from "@/lib/api";
import { inr } from "@/lib/sale-math";

function statusLabel(status: SaleStatus) {
  if (status === "DRAFT") return "Draft";
  if (status === "COMPLETED") return "Completed";
  return "Cancelled";
}

function statusClass(status: SaleStatus) {
  if (status === "COMPLETED") return "border-primary/30 bg-primary/10 text-primary";
  if (status === "CANCELLED") return "border-destructive/30 bg-destructive/10 text-destructive";
  return "border-amber-300 bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-200";
}

export function SalesPage() {
  const [items, setItems] = useState<Sale[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [summary, setSummary] = useState<SaleSummary | null>(null);
  const [query, setQuery] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [status, setStatus] = useState<"all" | SaleStatus>("all");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [list, counts, people] = await Promise.all([
        saleApi.list({
          q: query || undefined,
          customerId: customerId || undefined,
          status: status === "all" ? undefined : status,
          fromDate: fromDate || undefined,
          toDate: toDate || undefined,
          page,
          size: 20,
        }),
        saleApi.summary(),
        customerApi.list({ page: 1, size: 100 }),
      ]);
      setItems(list.items);
      setTotalPages(Math.max(list.totalPages, 1));
      setSummary(counts);
      setCustomers(people.items);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load sales");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId, status, page]);

  const emptyCopy = useMemo(() => {
    if (query || customerId || status !== "all" || fromDate || toDate) {
      return "No sales match these filters.";
    }
    return "Open POS to bill the first customer. Drafts do not decrease stock until you complete the sale.";
  }, [query, customerId, status, fromDate, toDate]);

  const cards = [
    { title: "Total sales", value: summary?.totalSales ?? "—" },
    { title: "Draft", value: summary?.draftSales ?? "—" },
    { title: "Completed", value: summary?.completedSales ?? "—" },
    { title: "Completed value", value: summary ? inr(summary.completedValue) : "—" },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Sales</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Counter bills for this shop. Completing a sale decreases inventory through the existing stock ledger.
          </p>
        </div>
        <Button asChild>
          <Link to="/app/sales/new">
            <Plus className="mr-2 h-4 w-4" />
            New sale / POS
          </Link>
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <Card key={card.title}>
            <CardHeader className="pb-2">
              <CardDescription>{card.title}</CardDescription>
              <CardTitle className="text-2xl sm:text-3xl">{loading ? "…" : card.value}</CardTitle>
            </CardHeader>
          </Card>
        ))}
      </div>

      <Card>
        <CardContent className="space-y-4 pt-6">
          <form
            className="grid gap-3 lg:grid-cols-[1fr_10rem_9rem_9rem_9rem_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              setPage(1);
              void load();
            }}
          >
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="Search sale or invoice number"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search sales"
              />
            </div>
            <NativeSelect
              aria-label="Filter by customer"
              value={customerId}
              onChange={(e) => {
                setCustomerId(e.target.value);
                setPage(1);
              }}
            >
              <option value="">All customers</option>
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </NativeSelect>
            <NativeSelect
              aria-label="Filter by status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | SaleStatus);
                setPage(1);
              }}
            >
              <option value="all">All statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </NativeSelect>
            <Input type="date" aria-label="From date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            <Input type="date" aria-label="To date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            <Button type="submit" variant="outline">
              Search
            </Button>
          </form>

          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">{emptyCopy}</div>
          ) : (
            <>
              <div className="hidden lg:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Sale number</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead>Customer</TableHead>
                      <TableHead>Items</TableHead>
                      <TableHead>Total</TableHead>
                      <TableHead>Payment</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((sale) => (
                      <TableRow key={sale.id}>
                        <TableCell className="font-mono text-sm">{sale.saleNumber}</TableCell>
                        <TableCell>{sale.saleDate}</TableCell>
                        <TableCell>{sale.customerName}</TableCell>
                        <TableCell>{sale.itemCount}</TableCell>
                        <TableCell className="font-medium">{inr(sale.grandTotal)}</TableCell>
                        <TableCell>{sale.paymentMethod ?? "—"}</TableCell>
                        <TableCell>
                          <Badge className={statusClass(sale.status)}>{statusLabel(sale.status)}</Badge>
                        </TableCell>
                        <TableCell className="space-x-1 text-right">
                          <Button size="sm" variant="ghost" asChild>
                            <Link to={`/app/sales/${sale.id}`}>View</Link>
                          </Button>
                          {sale.status === "COMPLETED" ? (
                            <>
                              <Button size="sm" variant="ghost" asChild>
                                <Link to={`/app/sales/${sale.id}/invoice`}>Invoice</Link>
                              </Button>
                              <Button size="sm" variant="ghost" asChild>
                                <Link to={`/app/sales/${sale.id}/invoice`}>Print</Link>
                              </Button>
                            </>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 lg:hidden">
                {items.map((sale) => (
                  <Link key={sale.id} to={`/app/sales/${sale.id}`} className="block rounded-lg border p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-mono text-sm">{sale.saleNumber}</p>
                        <p className="font-medium">{sale.customerName}</p>
                        <p className="text-sm text-muted-foreground">
                          {sale.itemCount} items · {inr(sale.grandTotal)}
                        </p>
                      </div>
                      <Badge className={statusClass(sale.status)}>{statusLabel(sale.status)}</Badge>
                    </div>
                  </Link>
                ))}
              </div>
              {totalPages > 1 ? (
                <div className="flex items-center justify-end gap-2">
                  <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    Page {page} of {totalPages}
                  </span>
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
