import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, quotationApi, salesOrderApi, type Quotation } from "@/lib/api";
import { inr } from "@/lib/sale-math";

export function QuotationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [quote, setQuote] = useState<Quotation | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  async function load() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      setQuote(await quotationApi.get(id));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load quotation");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function run(action: () => Promise<Quotation>, label: string) {
    setWorking(true);
    try {
      setQuote(await action());
      toast.success(label);
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to update quotation");
    } finally {
      setWorking(false);
    }
  }

  async function createOrder() {
    if (!id) return;
    setWorking(true);
    try {
      const order = await salesOrderApi.fromQuotation(id);
      toast.success(`${order.orderNumber} created`);
      navigate(`/app/sales-orders/${order.id}`);
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to create sales order");
    } finally {
      setWorking(false);
    }
  }

  if (loading) return <Skeleton className="h-48" />;
  if (error || !quote) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Quotation not found"}</p>
        <Button variant="outline" asChild>
          <Link to="/app/quotations">Back</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between print:hidden">
        <div>
          <p className="font-mono text-sm text-muted-foreground">{quote.quotationNumber}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{quote.customerName}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {quote.quotationDate} · valid until {quote.validUntil}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge>{quote.status}</Badge>
          <Button variant="outline" asChild>
            <Link to="/app/quotations">Back</Link>
          </Button>
          <Button variant="outline" onClick={() => window.print()}>
            Print
          </Button>
          {quote.status === "DRAFT" ? (
            <Button disabled={working} onClick={() => void run(() => quotationApi.send(quote.id), "Marked sent")}>
              Mark sent
            </Button>
          ) : null}
          {quote.status === "SENT" ? (
            <>
              <Button disabled={working} onClick={() => void run(() => quotationApi.accept(quote.id), "Quotation accepted")}>
                Accept
              </Button>
              <Button variant="outline" disabled={working} onClick={() => void run(() => quotationApi.reject(quote.id), "Rejected")}>
                Reject
              </Button>
            </>
          ) : null}
          {quote.status === "ACCEPTED" ? (
            <Button disabled={working} onClick={() => void createOrder()}>
              Create sales order
            </Button>
          ) : null}
          {quote.status === "DRAFT" || quote.status === "SENT" ? (
            <Button variant="ghost" disabled={working} onClick={() => void run(() => quotationApi.cancel(quote.id), "Cancelled")}>
              Cancel
            </Button>
          ) : null}
        </div>
      </div>

      <article className="rounded-lg border bg-white p-6 text-black print:border-0 dark:bg-white">
        <header className="mb-6 flex justify-between border-b pb-4">
          <div>
            <p className="text-lg font-semibold">Quotation {quote.quotationNumber}</p>
            <p className="text-sm">Customer: {quote.customerName}</p>
            {quote.leadName ? <p className="text-sm">Lead: {quote.leadName}</p> : null}
          </div>
          <div className="text-sm text-right">
            <p>Date: {quote.quotationDate}</p>
            <p>Valid until: {quote.validUntil}</p>
            <p>Status: {quote.status}</p>
          </div>
        </header>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Product</TableHead>
              <TableHead>SKU</TableHead>
              <TableHead>Qty</TableHead>
              <TableHead>Price</TableHead>
              <TableHead>GST</TableHead>
              <TableHead>Total</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {quote.items.map((item) => (
              <TableRow key={item.id}>
                <TableCell>{item.productName}</TableCell>
                <TableCell className="font-mono text-xs">{item.sku}</TableCell>
                <TableCell>
                  {item.quantity} {item.unit}
                </TableCell>
                <TableCell>{inr(item.unitPrice)}</TableCell>
                <TableCell>{Number(item.gstRate)}%</TableCell>
                <TableCell>{inr(item.lineTotal)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <div className="mt-6 space-y-1 text-sm sm:text-right">
          <p>Subtotal: {inr(quote.subtotal)}</p>
          <p>Discount: {inr(quote.discount)}</p>
          <p>GST: {inr(quote.taxTotal)}</p>
          <p className="text-base font-semibold">Grand total: {inr(quote.grandTotal)}</p>
        </div>
        {quote.termsAndConditions ? <p className="mt-6 text-sm">{quote.termsAndConditions}</p> : null}
      </article>

      <Card className="print:hidden">
        <CardHeader>
          <CardTitle>Inventory note</CardTitle>
          <CardDescription>Accepting this quotation does not decrease stock. Completing the eventual sale does.</CardDescription>
        </CardHeader>
      </Card>
    </div>
  );
}
