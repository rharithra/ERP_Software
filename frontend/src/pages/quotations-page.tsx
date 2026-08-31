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
import { ApiRequestError, quotationApi, type Quotation, type QuotationStatus } from "@/lib/api";
import { inr } from "@/lib/sale-math";

const STATUSES: QuotationStatus[] = ["DRAFT", "SENT", "ACCEPTED", "REJECTED", "EXPIRED", "CANCELLED"];

export function QuotationsPage() {
  const [items, setItems] = useState<Quotation[]>([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"all" | QuotationStatus>("all");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const list = await quotationApi.list({
        q: query || undefined,
        status: status === "all" ? undefined : status,
        page,
        size: 20,
      });
      setItems(list.items);
      setTotalPages(Math.max(list.totalPages, 1));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load quotations");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, page]);

  const emptyCopy = useMemo(() => {
    if (query || status !== "all") return "No quotations match these filters.";
    return "Quotations never reduce stock. Accept one, then create a sales order.";
  }, [query, status]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Quotations</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            GST is snapshotted from the product at quote time. Later catalog changes do not rewrite history.
          </p>
        </div>
        <Button asChild>
          <Link to="/app/quotations/new">
            <Plus className="mr-2 h-4 w-4" />
            New quotation
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
                placeholder="Search quotation number or customer"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search quotations"
              />
            </div>
            <NativeSelect
              aria-label="Filter quotations"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | QuotationStatus);
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
                      <TableHead>Quotation</TableHead>
                      <TableHead>Customer</TableHead>
                      <TableHead>Valid until</TableHead>
                      <TableHead>Total</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((quote) => (
                      <TableRow key={quote.id}>
                        <TableCell>
                          <Link className="font-mono text-sm hover:underline" to={`/app/quotations/${quote.id}`}>
                            {quote.quotationNumber}
                          </Link>
                        </TableCell>
                        <TableCell>{quote.customerName}</TableCell>
                        <TableCell>{quote.validUntil}</TableCell>
                        <TableCell>{inr(quote.grandTotal)}</TableCell>
                        <TableCell>
                          <Badge>{quote.status}</Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {items.map((quote) => (
                  <Link key={quote.id} to={`/app/quotations/${quote.id}`} className="block rounded-lg border p-4">
                    <p className="font-mono text-sm">{quote.quotationNumber}</p>
                    <p className="font-medium">{quote.customerName}</p>
                    <p className="text-sm text-muted-foreground">{inr(quote.grandTotal)}</p>
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
