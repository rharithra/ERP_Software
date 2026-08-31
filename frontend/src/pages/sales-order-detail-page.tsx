import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { RecordPaymentDialog } from "@/components/payments/record-payment-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, paymentApi, salesOrderApi, type PaymentRecord, type SalesOrder } from "@/lib/api";
import { paymentStatusLabel } from "@/lib/pipeline";
import { inr } from "@/lib/sale-math";

export function SalesOrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState<SalesOrder | null>(null);
  const [payments, setPayments] = useState<PaymentRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [payOpen, setPayOpen] = useState(false);

  async function load() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [record, history] = await Promise.all([salesOrderApi.get(id), paymentApi.list({ salesOrderId: id })]);
      setOrder(record);
      setPayments(history);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load sales order");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function run(action: () => Promise<SalesOrder>, label: string) {
    setWorking(true);
    try {
      setOrder(await action());
      toast.success(label);
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to update sales order");
    } finally {
      setWorking(false);
    }
  }

  async function convert() {
    if (!id) return;
    setWorking(true);
    try {
      const sale = await salesOrderApi.convertSale(id);
      toast.success(`${sale.saleNumber} completed. Inventory decreased and invoice generated.`);
      navigate(`/app/sales/${sale.id}`);
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to convert to sale");
    } finally {
      setWorking(false);
    }
  }

  if (loading) return <Skeleton className="h-48" />;
  if (error || !order) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Sales order not found"}</p>
        <Button variant="outline" asChild>
          <Link to="/app/sales-orders">Back</Link>
        </Button>
      </div>
    );
  }

  const convertible = ["CONFIRMED", "PROCESSING", "READY"].includes(order.status) && !order.saleId;
  const canAdvance = order.status !== "CANCELLED" && Number(order.outstandingAmount) > 0 && !order.saleId;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="font-mono text-sm text-muted-foreground">{order.orderNumber}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{order.customerName}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {order.orderDate}
            {order.quotationNumber ? ` · from ${order.quotationNumber}` : ""}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge>{order.status}</Badge>
          <Button variant="outline" asChild>
            <Link to="/app/sales-orders">Back</Link>
          </Button>
          {order.status === "DRAFT" ? (
            <Button disabled={working} onClick={() => void run(() => salesOrderApi.confirm(order.id), "Confirmed — stock unchanged")}>
              Confirm
            </Button>
          ) : null}
          {order.status === "CONFIRMED" ? (
            <Button variant="outline" disabled={working} onClick={() => void run(() => salesOrderApi.process(order.id), "Processing")}>
              Process
            </Button>
          ) : null}
          {order.status === "PROCESSING" || order.status === "CONFIRMED" ? (
            <Button variant="outline" disabled={working} onClick={() => void run(() => salesOrderApi.ready(order.id), "Marked ready")}>
              Ready
            </Button>
          ) : null}
          {canAdvance ? (
            <Button variant="outline" onClick={() => setPayOpen(true)}>
              Record advance
            </Button>
          ) : null}
          {convertible ? (
            <Button disabled={working} onClick={() => void convert()}>
              Convert to sale
            </Button>
          ) : null}
          {order.saleId ? (
            <Button asChild>
              <Link to={`/app/sales/${order.saleId}`}>Open sale {order.saleNumber}</Link>
            </Button>
          ) : null}
          {order.status === "DRAFT" || order.status === "CONFIRMED" ? (
            <Button variant="ghost" disabled={working} onClick={() => void run(() => salesOrderApi.cancel(order.id), "Cancelled")}>
              Cancel
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Order total</CardDescription>
            <CardTitle>{inr(order.grandTotal)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Paid</CardDescription>
            <CardTitle>{inr(order.advancePaid)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Outstanding</CardDescription>
            <CardTitle>{inr(order.outstandingAmount)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Payment status</CardDescription>
            <CardTitle className="text-xl">{paymentStatusLabel(order.paymentStatus)}</CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Items</CardTitle>
          <CardDescription>Snapshots from the quotation or catalog at order time.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Product</TableHead>
                <TableHead>Qty</TableHead>
                <TableHead>Price</TableHead>
                <TableHead>GST</TableHead>
                <TableHead>Total</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {order.items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{item.productName}</TableCell>
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
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Payment history</CardTitle>
          <CardDescription>Advances remain these same PAY- rows after conversion.</CardDescription>
        </CardHeader>
        <CardContent>
          {payments.length === 0 ? (
            <p className="text-sm text-muted-foreground">No payments yet.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Payment</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Method</TableHead>
                  <TableHead>Amount</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {payments.map((payment) => (
                  <TableRow key={payment.id}>
                    <TableCell className="font-mono text-xs">{payment.paymentNumber}</TableCell>
                    <TableCell>{payment.paymentDate}</TableCell>
                    <TableCell>{payment.paymentMethod}</TableCell>
                    <TableCell>{inr(payment.amount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <RecordPaymentDialog
        open={payOpen}
        onOpenChange={setPayOpen}
        title="Record advance payment"
        total={order.grandTotal}
        paid={order.advancePaid}
        salesOrderId={order.id}
        onRecorded={() => void load()}
      />
    </div>
  );
}
