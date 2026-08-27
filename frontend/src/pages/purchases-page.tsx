import { useEffect, useMemo, useState } from "react";
import { Plus, Search } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "@/auth/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  ApiRequestError,
  purchaseApi,
  supplierApi,
  type Purchase,
  type PurchaseStatus,
  type PurchaseSummary,
  type Supplier,
} from "@/lib/api";

function inr(value: number | string) {
  const amount = typeof value === "string" ? Number(value) : value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number.isFinite(amount) ? amount : 0);
}

function statusLabel(status: PurchaseStatus) {
  if (status === "DRAFT") return "Draft";
  if (status === "RECEIVED") return "Received";
  return "Cancelled";
}

function statusClass(status: PurchaseStatus) {
  if (status === "RECEIVED") return "border-primary/30 bg-primary/10 text-primary";
  if (status === "CANCELLED") return "border-destructive/30 bg-destructive/10 text-destructive";
  return "border-amber-300 bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-200";
}

export function PurchasesPage() {
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [items, setItems] = useState<Purchase[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [summary, setSummary] = useState<PurchaseSummary | null>(null);
  const [query, setQuery] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [status, setStatus] = useState<"all" | PurchaseStatus>("all");
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
      const [list, counts, vendorPage] = await Promise.all([
        purchaseApi.list({
          q: query || undefined,
          supplierId: supplierId || undefined,
          status: status === "all" ? undefined : status,
          fromDate: fromDate || undefined,
          toDate: toDate || undefined,
          page,
          size: 20,
        }),
        purchaseApi.summary(),
        supplierApi.list({ page: 1, size: 100 }),
      ]);
      setItems(list.items);
      setTotalPages(Math.max(list.totalPages, 1));
      setSummary(counts);
      setSuppliers(vendorPage.items);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load purchases");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supplierId, status, page]);

  const emptyCopy = useMemo(() => {
    if (query || supplierId || status !== "all" || fromDate || toDate) {
      return "No purchases match these filters.";
    }
    return "Create a draft purchase first. Stock increases only when you receive the goods.";
  }, [query, supplierId, status, fromDate, toDate]);

  const cards = [
    { title: "Total purchases", value: summary?.totalPurchases ?? "—" },
    { title: "Draft", value: summary?.draftPurchases ?? "—" },
    { title: "Received", value: summary?.receivedPurchases ?? "—" },
    { title: "Received value", value: summary ? inr(summary.receivedValue) : "—" },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Purchases</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Drafts do not touch inventory. Receiving a purchase adds quantities through the existing stock ledger.
          </p>
        </div>
        {canMutate ? (
          <Button asChild>
            <Link to="/app/purchases/new">
              <Plus className="mr-2 h-4 w-4" />
              New purchase
            </Link>
          </Button>
        ) : null}
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
                placeholder="Search purchase number"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search purchase number"
              />
            </div>
            <NativeSelect
              aria-label="Filter by supplier"
              value={supplierId}
              onChange={(e) => {
                setSupplierId(e.target.value);
                setPage(1);
              }}
            >
              <option value="">All suppliers</option>
              {suppliers.map((supplier) => (
                <option key={supplier.id} value={supplier.id}>
                  {supplier.name}
                </option>
              ))}
            </NativeSelect>
            <NativeSelect
              aria-label="Filter by status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | PurchaseStatus);
                setPage(1);
              }}
            >
              <option value="all">All statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="RECEIVED">Received</option>
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
                      <TableHead>Purchase number</TableHead>
                      <TableHead>Supplier</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead>Items</TableHead>
                      <TableHead>Subtotal</TableHead>
                      <TableHead>Tax</TableHead>
                      <TableHead>Total</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((purchase) => (
                      <TableRow key={purchase.id}>
                        <TableCell className="font-mono text-sm">{purchase.purchaseNumber}</TableCell>
                        <TableCell>{purchase.supplierName}</TableCell>
                        <TableCell>{purchase.purchaseDate}</TableCell>
                        <TableCell>{purchase.itemCount}</TableCell>
                        <TableCell>{inr(purchase.subtotal)}</TableCell>
                        <TableCell>{inr(purchase.taxAmount)}</TableCell>
                        <TableCell className="font-medium">{inr(purchase.totalAmount)}</TableCell>
                        <TableCell>
                          <Badge className={statusClass(purchase.status)}>{statusLabel(purchase.status)}</Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <Button size="sm" variant="ghost" asChild>
                            <Link to={`/app/purchases/${purchase.id}`}>View</Link>
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 lg:hidden">
                {items.map((purchase) => (
                  <Link
                    key={purchase.id}
                    to={`/app/purchases/${purchase.id}`}
                    className="block rounded-lg border p-4"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-mono text-sm">{purchase.purchaseNumber}</p>
                        <p className="font-medium">{purchase.supplierName}</p>
                        <p className="text-sm text-muted-foreground">
                          {purchase.itemCount} items · {inr(purchase.totalAmount)}
                        </p>
                      </div>
                      <Badge className={statusClass(purchase.status)}>{statusLabel(purchase.status)}</Badge>
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
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
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
