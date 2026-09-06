import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { RefundDialog } from "@/components/returns/refund-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuth } from "@/auth/auth-context";
import { ApiRequestError, refundApi, returnApi, saleApi, type RefundRecord, type Sale, type SaleReturn } from "@/lib/api";
import { inr } from "@/lib/sale-math";

export function ReturnDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canRefund = user?.role === "OWNER" || user?.role === "MANAGER";
  const [record, setRecord] = useState<SaleReturn | null>(null);
  const [sale, setSale] = useState<Sale | null>(null);
  const [refunds, setRefunds] = useState<RefundRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refundOpen, setRefundOpen] = useState(false);
  const [working, setWorking] = useState(false);

  async function load() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const ret = await returnApi.get(id);
      setRecord(ret);
      const [saleRecord, saleRefunds] = await Promise.all([
        saleApi.get(ret.saleId),
        refundApi.list({ saleId: ret.saleId }),
      ]);
      setSale(saleRecord);
      setRefunds(saleRefunds);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load return");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function complete() {
    if (!id) return;
    setWorking(true);
    try {
      const updated = await returnApi.complete(id);
      setRecord(updated);
      toast.success("Return completed. Inventory increased.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to complete return");
    } finally {
      setWorking(false);
    }
  }

  async function cancel() {
    if (!id) return;
    setWorking(true);
    try {
      const updated = await returnApi.cancel(id);
      setRecord(updated);
      toast.success("Return cancelled. Inventory was not changed.");
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to cancel return");
    } finally {
      setWorking(false);
    }
  }

  if (loading) {
    return <Skeleton className="h-48 w-full" />;
  }
  if (error || !record) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Return not found"}</p>
        <Button variant="outline" onClick={() => navigate("/app/returns")}>
          Back to returns
        </Button>
      </div>
    );
  }

  const credit = Number(sale?.customerCreditAmount ?? 0);
  const draft = record.status === "DRAFT";

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="font-mono text-sm text-muted-foreground">{record.returnNumber}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{record.customerName}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {record.returnDate} · {record.invoiceNumber ?? record.saleNumber}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge>{record.status}</Badge>
          <Button variant="outline" asChild>
            <Link to="/app/returns">Back</Link>
          </Button>
          <Button variant="outline" asChild>
            <Link to={`/app/sales/${record.saleId}`}>Open sale</Link>
          </Button>
          {draft ? (
            <>
              <Button variant="outline" disabled={working} onClick={() => void cancel()}>
                Cancel
              </Button>
              <Button disabled={working} onClick={() => void complete()}>
                Complete return
              </Button>
            </>
          ) : null}
          {record.status === "COMPLETED" && credit > 0 && canRefund ? (
            <Button onClick={() => setRefundOpen(true)}>Refund</Button>
          ) : null}
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Return total</CardDescription>
            <CardTitle>{inr(record.totalAmount)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Customer credit on this sale</CardDescription>
            <CardTitle>{inr(credit)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Outstanding</CardDescription>
            <CardTitle>{inr(sale?.outstandingAmount ?? 0)}</CardTitle>
          </CardHeader>
        </Card>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Returned items</CardTitle>
          <CardDescription>Linked to original sale lines. The completed sale was not edited.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Product</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead>Qty</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Reason</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {record.items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{item.productName}</TableCell>
                  <TableCell className="font-mono text-xs">{item.sku}</TableCell>
                  <TableCell>
                    {item.quantity} {item.unit}
                  </TableCell>
                  <TableCell>{inr(item.totalAmount)}</TableCell>
                  <TableCell>{item.reason ?? record.reason ?? "—"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Refunds</CardTitle>
          <CardDescription>Refunds are separate records. Original payments are never deleted.</CardDescription>
        </CardHeader>
        <CardContent>
          {refunds.length === 0 ? (
            <p className="text-sm text-muted-foreground">No refunds yet. You can keep the balance as store credit.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Refund</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Method</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {refunds.map((refund) => (
                  <TableRow key={refund.id}>
                    <TableCell className="font-mono text-xs">{refund.refundNumber}</TableCell>
                    <TableCell>{inr(refund.amount)}</TableCell>
                    <TableCell>{refund.paymentMethod}</TableCell>
                    <TableCell>{refund.status}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
      <RefundDialog
        open={refundOpen}
        onOpenChange={setRefundOpen}
        saleId={record.saleId}
        saleReturnId={record.id}
        availableCredit={credit}
        onRecorded={() => void load()}
      />
    </div>
  );
}
