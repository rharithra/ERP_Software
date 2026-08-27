import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, purchaseApi, type Purchase, type PurchaseStatus } from "@/lib/api";

function inr(value: number | string) {
  const amount = typeof value === "string" ? Number(value) : value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number.isFinite(amount) ? amount : 0);
}

function statusLabel(status: PurchaseStatus) {
  if (status === "DRAFT") return "Draft";
  if (status === "RECEIVED") return "Received";
  return "Cancelled";
}

export function PurchaseDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [purchase, setPurchase] = useState<Purchase | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [receiveOpen, setReceiveOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [working, setWorking] = useState(false);

  async function load() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      setPurchase(await purchaseApi.get(id));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load purchase");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function receive() {
    if (!id) return;
    setWorking(true);
    try {
      const updated = await purchaseApi.receive(id);
      setPurchase(updated);
      setReceiveOpen(false);
      toast.success("Purchase received successfully. Inventory has been updated.");
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to receive purchase");
    } finally {
      setWorking(false);
    }
  }

  async function cancel() {
    if (!id) return;
    setWorking(true);
    try {
      const updated = await purchaseApi.cancel(id);
      setPurchase(updated);
      setCancelOpen(false);
      toast.success("Draft purchase cancelled. Inventory was not changed.");
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to cancel purchase");
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

  if (error || !purchase) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Purchase not found"}</p>
        <Button variant="outline" onClick={() => navigate("/app/purchases")}>
          Back to purchases
        </Button>
      </div>
    );
  }

  const draft = purchase.status === "DRAFT";

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="font-mono text-sm text-muted-foreground">{purchase.purchaseNumber}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{purchase.supplierName}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {purchase.purchaseDate} · created by {purchase.createdByName}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge>{statusLabel(purchase.status)}</Badge>
          <Button variant="outline" asChild>
            <Link to="/app/purchases">Back</Link>
          </Button>
          {canMutate && draft ? (
            <>
              <Button variant="outline" asChild>
                <Link to={`/app/purchases/${purchase.id}/edit`}>Edit</Link>
              </Button>
              <Button variant="outline" onClick={() => setCancelOpen(true)}>
                Cancel purchase
              </Button>
              <Button onClick={() => setReceiveOpen(true)}>Receive purchase</Button>
            </>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Status</CardDescription>
            <CardTitle className="text-xl">{statusLabel(purchase.status)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Received at</CardDescription>
            <CardTitle className="text-xl">
              {purchase.receivedAt ? new Date(purchase.receivedAt).toLocaleString("en-IN") : "Not received"}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Items</CardDescription>
            <CardTitle className="text-xl">{purchase.itemCount}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Grand total</CardDescription>
            <CardTitle className="text-xl">{inr(purchase.totalAmount)}</CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Line items</CardTitle>
          <CardDescription>Quantities, costs, and GST are snapshotted at save time.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>SKU</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Unit</TableHead>
                  <TableHead>Unit cost</TableHead>
                  <TableHead>GST</TableHead>
                  <TableHead>Subtotal</TableHead>
                  <TableHead>Tax</TableHead>
                  <TableHead>Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {purchase.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.productName}</TableCell>
                    <TableCell className="font-mono text-xs">{item.sku}</TableCell>
                    <TableCell>{item.quantity}</TableCell>
                    <TableCell>{item.unit}</TableCell>
                    <TableCell>{inr(item.unitCost)}</TableCell>
                    <TableCell>{Number(item.gstRate)}%</TableCell>
                    <TableCell>{inr(item.lineSubtotal)}</TableCell>
                    <TableCell>{inr(item.taxAmount)}</TableCell>
                    <TableCell className="font-medium">{inr(item.lineTotal)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="mt-6 space-y-1 text-sm sm:text-right">
            <p>Subtotal: {inr(purchase.subtotal)}</p>
            <p>Tax: {inr(purchase.taxAmount)}</p>
            <p className="text-base font-semibold">Grand total: {inr(purchase.totalAmount)}</p>
          </div>
        </CardContent>
      </Card>

      {purchase.status === "RECEIVED" ? (
        <Card>
          <CardHeader>
            <CardTitle>Inventory posting</CardTitle>
            <CardDescription>
              Stock increased through InventoryService as PURCHASE_RECEIPT movements. Reference type PURCHASE, id{" "}
              <span className="font-mono">{purchase.id}</span>.
            </CardDescription>
          </CardHeader>
        </Card>
      ) : null}

      <Dialog open={receiveOpen} onOpenChange={setReceiveOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Receive this purchase?</DialogTitle>
            <DialogDescription>
              This will add all purchase quantities to inventory and mark the purchase as RECEIVED. This action cannot
              be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setReceiveOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => void receive()} disabled={working}>
              Receive purchase
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cancel this draft?</DialogTitle>
            <DialogDescription>Cancelled purchases do not change inventory. You cannot receive them later.</DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setCancelOpen(false)}>
              Keep draft
            </Button>
            <Button variant="destructive" onClick={() => void cancel()} disabled={working}>
              Cancel purchase
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
