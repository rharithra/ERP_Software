import { useEffect, useMemo, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, paymentApi, type PaymentMethod } from "@/lib/api";
import { todayIso } from "@/lib/pipeline";
import { inr } from "@/lib/sale-math";

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title?: string;
  total: number | string;
  paid: number | string;
  saleId?: string | null;
  salesOrderId?: string | null;
  onRecorded: () => void;
};

export function RecordPaymentDialog({
  open,
  onOpenChange,
  title = "Record payment",
  total,
  paid,
  saleId,
  salesOrderId,
  onRecorded,
}: Props) {
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [date, setDate] = useState(todayIso());
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const totalN = Number(total) || 0;
  const paidN = Number(paid) || 0;
  const outstanding = Math.max(0, Math.round((totalN - paidN) * 100) / 100);
  const payment = Number(amount);
  const remaining = Number.isFinite(payment)
    ? Math.round((outstanding - payment) * 100) / 100
    : outstanding;

  useEffect(() => {
    if (open) {
      setAmount(outstanding > 0 ? String(outstanding) : "");
      setMethod("CASH");
      setDate(todayIso());
      setReference("");
      setNotes("");
      setError(null);
    }
  }, [open, outstanding]);

  const preview = useMemo(
    () => [
      { label: "Invoice / order total", value: inr(totalN) },
      { label: "Already paid", value: inr(paidN) },
      { label: "Outstanding", value: inr(outstanding) },
      { label: "This payment", value: Number.isFinite(payment) && payment > 0 ? inr(payment) : "—" },
      { label: "Remaining after payment", value: Number.isFinite(remaining) ? inr(Math.max(remaining, 0)) : "—" },
    ],
    [totalN, paidN, outstanding, payment, remaining],
  );

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!Number.isFinite(payment) || payment <= 0) {
      setError("Enter a payment amount greater than zero.");
      return;
    }
    if (payment > outstanding + 0.001) {
      setError("Payment cannot exceed the outstanding amount.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await paymentApi.create({
        saleId: saleId || undefined,
        salesOrderId: salesOrderId || undefined,
        amount: payment,
        paymentMethod: method,
        paymentDate: date,
        referenceNumber: reference.trim() || null,
        notes: notes.trim() || null,
      });
      toast.success("Payment recorded");
      onOpenChange(false);
      onRecorded();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to record payment");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            Payments are real ledger rows. Advances against a sales order stay linked when the order becomes a sale.
          </DialogDescription>
        </DialogHeader>
        <dl className="grid gap-2 rounded-lg border bg-muted/40 p-3 text-sm sm:grid-cols-2">
          {preview.map((row) => (
            <div key={row.label}>
              <dt className="text-muted-foreground">{row.label}</dt>
              <dd className="font-medium">{row.value}</dd>
            </div>
          ))}
        </dl>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="pay-amount">Amount</Label>
              <Input
                id="pay-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="pay-method">Payment method</Label>
              <NativeSelect id="pay-method" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
                <option value="CARD">Card</option>
                <option value="BANK_TRANSFER">Bank transfer</option>
                <option value="OTHER">Other</option>
              </NativeSelect>
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="pay-date">Payment date</Label>
              <Input id="pay-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="pay-ref">Reference number</Label>
              <Input id="pay-ref" value={reference} onChange={(e) => setReference(e.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="pay-notes">Notes</Label>
            <Textarea id="pay-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving || outstanding <= 0}>
              Record payment
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
