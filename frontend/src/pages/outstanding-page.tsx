import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, paymentApi, type OutstandingRow } from "@/lib/api";
import { paymentStatusLabel } from "@/lib/pipeline";
import { inr } from "@/lib/sale-math";

export function OutstandingPage() {
  const [filter, setFilter] = useState<"OUTSTANDING" | "PAID" | "ALL">("OUTSTANDING");
  const [rows, setRows] = useState<OutstandingRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    paymentApi
      .outstanding(filter)
      .then(setRows)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : "Unable to load outstanding"))
      .finally(() => setLoading(false));
  }, [filter]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Customer outstanding</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Completed invoices only. Paid is the sum of payment records, including advances transferred from sales orders.
          </p>
        </div>
        <NativeSelect
          aria-label="Filter outstanding"
          className="w-48"
          value={filter}
          onChange={(e) => setFilter(e.target.value as "OUTSTANDING" | "PAID" | "ALL")}
        >
          <option value="OUTSTANDING">Outstanding</option>
          <option value="PAID">Paid</option>
          <option value="ALL">All</option>
        </NativeSelect>
      </div>
      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <Skeleton className="h-32" />
          ) : error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : rows.length === 0 ? (
            <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
              No invoices match this filter.
            </p>
          ) : (
            <>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Customer</TableHead>
                      <TableHead>Invoice</TableHead>
                      <TableHead>Total</TableHead>
                      <TableHead>Paid</TableHead>
                      <TableHead>Outstanding</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.map((row) => (
                      <TableRow key={row.saleId}>
                        <TableCell>{row.customerName}</TableCell>
                        <TableCell>
                          <Link className="font-mono text-sm hover:underline" to={`/app/sales/${row.saleId}`}>
                            {row.invoiceNumber ?? "—"}
                          </Link>
                        </TableCell>
                        <TableCell>{inr(row.grandTotal)}</TableCell>
                        <TableCell>{inr(row.paid)}</TableCell>
                        <TableCell>{inr(row.outstanding)}</TableCell>
                        <TableCell>
                          <Badge>{paymentStatusLabel(row.paymentStatus)}</Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {rows.map((row) => (
                  <Link key={row.saleId} to={`/app/sales/${row.saleId}`} className="block rounded-lg border p-4">
                    <p className="font-medium">{row.customerName}</p>
                    <p className="font-mono text-xs">{row.invoiceNumber}</p>
                    <p className="text-sm text-muted-foreground">
                      Outstanding {inr(row.outstanding)} of {inr(row.grandTotal)}
                    </p>
                  </Link>
                ))}
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
