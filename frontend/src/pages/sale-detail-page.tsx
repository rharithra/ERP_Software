import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, saleApi, type Sale, type SaleStatus } from "@/lib/api";
import { inr } from "@/lib/sale-math";

function statusLabel(status: SaleStatus) {
  if (status === "DRAFT") return "Draft";
  if (status === "COMPLETED") return "Completed";
  return "Cancelled";
}

export function SaleDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [sale, setSale] = useState<Sale | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [working, setWorking] = useState(false);

  async function load() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      setSale(await saleApi.get(id));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load sale");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function cancel() {
    if (!id) return;
    setWorking(true);
    try {
      const updated = await saleApi.cancel(id);
      setSale(updated);
      setCancelOpen(false);
      toast.success("Draft sale cancelled. Inventory was not changed.");
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to cancel sale");
    } finally {
      setWorking(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (error || !sale) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Sale not found"}</p>
        <Button variant="outline" onClick={() => navigate("/app/sales")}>
          Back to sales
        </Button>
      </div>
    );
  }

  const draft = sale.status === "DRAFT";
  const completed = sale.status === "COMPLETED";

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="font-mono text-sm text-muted-foreground">{sale.saleNumber}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{sale.customerName}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {sale.saleDate} · created by {sale.createdByName}
            {sale.invoiceNumber ? ` · ${sale.invoiceNumber}` : ""}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge>{statusLabel(sale.status)}</Badge>
          <Button variant="outline" asChild>
            <Link to="/app/sales">Back</Link>
          </Button>
          {completed ? (
            <Button asChild>
              <Link to={`/app/sales/${sale.id}/invoice`}>Invoice</Link>
            </Button>
          ) : null}
          {draft ? (
            <Button variant="outline" onClick={() => setCancelOpen(true)}>
              Cancel sale
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Status</CardDescription>
            <CardTitle className="text-xl">{statusLabel(sale.status)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Payment</CardDescription>
            <CardTitle className="text-xl">{sale.paymentMethod ?? "Not paid"}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Items</CardDescription>
            <CardTitle className="text-xl">{sale.itemCount}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Grand total</CardDescription>
            <CardTitle className="text-xl">{inr(sale.grandTotal)}</CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Line items</CardTitle>
          <CardDescription>Name, SKU, price, and GST are snapshotted at sale time.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>SKU</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Unit price</TableHead>
                  <TableHead>GST</TableHead>
                  <TableHead>Taxable</TableHead>
                  <TableHead>Tax</TableHead>
                  <TableHead>Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sale.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.productName}</TableCell>
                    <TableCell className="font-mono text-xs">{item.sku}</TableCell>
                    <TableCell>
                      {item.quantity} {item.unit}
                    </TableCell>
                    <TableCell>{inr(item.unitPrice)}</TableCell>
                    <TableCell>{Number(item.gstRate)}%</TableCell>
                    <TableCell>{inr(item.taxableAmount)}</TableCell>
                    <TableCell>{inr(item.taxAmount)}</TableCell>
                    <TableCell className="font-medium">{inr(item.lineTotal)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="mt-6 space-y-1 text-sm sm:text-right">
            <p>Subtotal: {inr(sale.subtotal)}</p>
            <p>Discount: {inr(sale.discount)}</p>
            <p>GST: {inr(sale.taxTotal)}</p>
            <p className="text-base font-semibold">Grand total: {inr(sale.grandTotal)}</p>
          </div>
        </CardContent>
      </Card>

      {completed ? (
        <Card>
          <CardHeader>
            <CardTitle>Inventory posting</CardTitle>
            <CardDescription>
              Stock decreased through InventoryService as SALE movements. Reference type SALE, id{" "}
              <span className="font-mono">{sale.id}</span>.
            </CardDescription>
          </CardHeader>
        </Card>
      ) : null}

      <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cancel this draft?</DialogTitle>
            <DialogDescription>Cancelled sales do not change inventory. You cannot complete them later.</DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setCancelOpen(false)}>
              Keep draft
            </Button>
            <Button variant="destructive" onClick={() => void cancel()} disabled={working}>
              Cancel sale
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
