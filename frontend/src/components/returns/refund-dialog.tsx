import { useEffect, useMemo, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/auth/auth-context";
import { ApiRequestError, refundApi, type PaymentMethod } from "@/lib/api";
import { inr } from "@/lib/sale-math";

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  saleId: string;
  saleReturnId?: string | null;
  availableCredit: number;
  onRecorded: () => void;
};

export function RefundDialog({ open, onOpenChange, saleId, saleReturnId, availableCredit, onRecorded }: Props) {
  const { user } = useAuth();
  const canComplete = user?.role === "OWNER" || user?.role === "MANAGER";
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setAmount(availableCredit > 0 ? String(availableCredit) : "");
      setMethod("CASH");
      setReference("");
      setNotes("");
      setError(null);
    }
  }, [open, availableCredit]);

  const refund = Number(amount);
  const remaining = Number.isFinite(refund) ? Math.round((availableCredit - refund) * 100) / 100 : availableCredit;

  const preview = useMemo(
    () => [
      { label: "Available credit", value: inr(availableCredit) },
      { label: "Refund amount", value: Number.isFinite(refund) && refund > 0 ? inr(refund) : "—" },
      { label: "Remaining credit", value: Number.isFinite(remaining) ? inr(Math.max(remaining, 0)) : "—" },
    ],
    [availableCredit, refund, remaining],
  );

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!Number.isFinite(refund) || refund <= 0) {
      setError("Enter a refund amount greater than zero.");
      return;
    }
    if (refund > availableCredit + 0.001) {
      setError("Refund cannot exceed available customer credit.");
      return;
    }
    if (!canComplete) {
      setError("Cashiers can create a pending refund. An owner or manager must complete it.");
    }
    setSaving(true);
    setError(null);
    try {
      const created = await refundApi.create({
        saleId,
        saleReturnId,
        amount: refund,
        paymentMethod: method,
        referenceNumber: reference.trim() || null,
        notes: notes.trim() || null,
      });
      if (canComplete) {
        await refundApi.complete(created.id);
        toast.success(`Refund ${created.refundNumber} completed`);
      } else {
        toast.success(`Pending refund ${created.refundNumber} recorded`);
      }
      onOpenChange(false);
      onRecorded();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to record refund");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Refund customer</DialogTitle>
          <DialogDescription>Do not delete original payments. This creates a separate refund document.</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-2">
            <Label htmlFor="refund-amount">Amount</Label>
            <Input id="refund-amount" type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Payment method</Label>
            <NativeSelect value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
              <option value="CASH">Cash</option>
              <option value="UPI">UPI</option>
              <option value="CARD">Card</option>
              <option value="BANK_TRANSFER">Bank transfer</option>
              <option value="OTHER">Other</option>
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <Label>Reference number</Label>
            <Input value={reference} onChange={(e) => setReference(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Notes</Label>
            <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} />
          </div>
          <ul className="space-y-1 rounded-md border p-3 text-sm">
            {preview.map((row) => (
              <li key={row.label} className="flex justify-between gap-3">
                <span className="text-muted-foreground">{row.label}</span>
                <span>{row.value}</span>
              </li>
            ))}
          </ul>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Keep as credit
            </Button>
            <Button type="submit" disabled={saving}>
              {canComplete ? "Complete refund" : "Save pending refund"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
