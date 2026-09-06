import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, returnApi, type SaleReturn, type SaleReturnStatus } from "@/lib/api";
import { inr } from "@/lib/sale-math";

function statusLabel(status: SaleReturnStatus) {
  if (status === "DRAFT") return "Draft";
  if (status === "COMPLETED") return "Completed";
  return "Cancelled";
}

export function ReturnsPage() {
  const [items, setItems] = useState<SaleReturn[]>([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<SaleReturnStatus | "all">("all");
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
      const result = await returnApi.list({
        q: query || undefined,
        status: status === "all" ? undefined : status,
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        page,
        size: 20,
      });
      setItems(result.items);
      setTotalPages(Math.max(result.totalPages, 1));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load returns");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, page, fromDate, toDate]);

  const empty = useMemo(() => {
    if (query || status !== "all" || fromDate || toDate) {
      return "No returns match these filters.";
    }
    return "Completed sales can be returned from the sale page. Drafts do not change stock.";
  }, [query, status, fromDate, toDate]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Returns</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Sale returns are separate documents. Original invoices and payments stay unchanged.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Search return number, invoice, or customer.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-5">
          <div className="relative md:col-span-2">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              className="pl-8"
              placeholder="RET-000001 or customer"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  setPage(1);
                  void load();
                }
              }}
            />
          </div>
          <NativeSelect
            value={status}
            onChange={(e) => {
              setPage(1);
              setStatus(e.target.value as SaleReturnStatus | "all");
            }}
          >
            <option value="all">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </NativeSelect>
          <Input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
          <Input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Return documents</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <Skeleton className="h-40 w-full" />
          ) : error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : items.length === 0 ? (
            <p className="text-sm text-muted-foreground">{empty}</p>
          ) : (
            <>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Return</TableHead>
                      <TableHead>Sale / Invoice</TableHead>
                      <TableHead>Customer</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead>Amount</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>
                          <Link className="font-mono text-sm text-primary" to={`/app/returns/${row.id}`}>
                            {row.returnNumber}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <Link className="text-sm text-primary" to={`/app/sales/${row.saleId}`}>
                            {row.invoiceNumber ?? row.saleNumber}
                          </Link>
                        </TableCell>
                        <TableCell>{row.customerName}</TableCell>
                        <TableCell>{row.returnDate}</TableCell>
                        <TableCell>{inr(row.totalAmount)}</TableCell>
                        <TableCell>
                          <Badge>{statusLabel(row.status)}</Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              {totalPages > 1 ? (
                <div className="mt-4 flex justify-end gap-2">
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
