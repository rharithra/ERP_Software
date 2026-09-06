import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, returnApi, saleApi, type ReturnReason, type Sale } from "@/lib/api";
import { inr } from "@/lib/sale-math";

const REASONS: ReturnReason[] = [
  "DAMAGED",
  "DEFECTIVE",
  "WRONG_PRODUCT",
  "CUSTOMER_CHANGED_MIND",
  "QUALITY_ISSUE",
  "NOT_REQUIRED",
  "OTHER",
];

function reasonLabel(reason: ReturnReason) {
  return reason.replaceAll("_", " ").toLowerCase();
}

export function ReturnCreatePage() {
  const [params] = useSearchParams();
  const saleId = params.get("saleId") ?? "";
  const navigate = useNavigate();
  const [sale, setSale] = useState<Sale | null>(null);
  const [qty, setQty] = useState<Record<string, string>>({});
  const [itemReason, setItemReason] = useState<Record<string, ReturnReason | "">>({});
  const [notes, setNotes] = useState("");
  const [reason, setReason] = useState<ReturnReason | "">("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!saleId) {
      setError("Open a completed sale and choose Create return.");
      setLoading(false);
      return;
    }
    saleApi
      .get(saleId)
      .then((record) => {
        setSale(record);
        const next: Record<string, string> = {};
        for (const item of record.items) {
          next[item.id] = "0";
        }
        setQty(next);
      })
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : "Unable to load sale"))
      .finally(() => setLoading(false));
  }, [saleId]);

  const selected = useMemo(() => {
    if (!sale) return [];
    return sale.items
      .map((item) => {
        const quantity = Number(qty[item.id] ?? 0);
        const available = Number(item.availableToReturn ?? item.quantity);
        const ratio = Number(item.quantity) > 0 ? quantity / Number(item.quantity) : 0;
        return {
          item,
          quantity,
          available,
          lineTotal: Number(item.lineTotal) * ratio,
          tax: Number(item.taxAmount) * ratio,
        };
      })
      .filter((row) => row.quantity > 0);
  }, [sale, qty]);

  const subtotal = selected.reduce((sum, row) => sum + Number(row.item.taxableAmount) * (row.quantity / Number(row.item.quantity)), 0);
  const tax = selected.reduce((sum, row) => sum + row.tax, 0);
  const total = selected.reduce((sum, row) => sum + row.lineTotal, 0);

  async function submit(completeAfter: boolean) {
    if (!sale) return;
    setFormError(null);
    if (selected.length === 0) {
      setFormError("Enter a return quantity for at least one product.");
      toast.error("Enter a return quantity for at least one product.");
      return;
    }
    for (const row of selected) {
      if (row.quantity > row.available + 0.0001) {
        const message = `Only ${row.available} of ${row.item.productName} can still be returned.`;
        setFormError(message);
        toast.error(message);
        return;
      }
    }
    setWorking(true);
    try {
      const created = await returnApi.create({
        saleId: sale.id,
        reason: reason || undefined,
        notes: notes.trim() || null,
        items: selected.map((row) => ({
          saleItemId: row.item.id,
          quantity: row.quantity,
          reason: (itemReason[row.item.id] || reason || undefined) as ReturnReason | undefined,
        })),
      });
      if (completeAfter) {
        await returnApi.complete(created.id);
        toast.success(`Return ${created.returnNumber} completed. Stock increased.`);
      } else {
        toast.success(`Draft return ${created.returnNumber} saved. Stock was not changed.`);
      }
      navigate(`/app/returns/${created.id}`);
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to save return");
    } finally {
      setWorking(false);
    }
  }

  if (loading) {
    return <Skeleton className="h-48 w-full" />;
  }

  if (error || !sale) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Sale not found"}</p>
        <Button variant="outline" asChild>
          <Link to="/app/sales">Back to sales</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Create return</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {sale.invoiceNumber ?? sale.saleNumber} · {sale.customerName} · {sale.saleDate}
          </p>
        </div>
        <Button variant="outline" asChild>
          <Link to={`/app/sales/${sale.id}`}>Back to sale</Link>
        </Button>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Products</CardTitle>
          <CardDescription>Original sold quantities stay on the sale. Enter only what is coming back.</CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Product</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead>Sold</TableHead>
                <TableHead>Already returned</TableHead>
                <TableHead>Available</TableHead>
                <TableHead>Return qty</TableHead>
                <TableHead>Reason</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sale.items.map((item) => {
                const available = Number(item.availableToReturn ?? item.quantity);
                return (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.productName}</TableCell>
                    <TableCell className="font-mono text-xs">{item.sku}</TableCell>
                    <TableCell>
                      {item.quantity} {item.unit}
                    </TableCell>
                    <TableCell>{item.returnedQuantity ?? 0}</TableCell>
                    <TableCell>{available}</TableCell>
                    <TableCell>
                      <Input
                        type="number"
                        min={0}
                        max={available}
                        step="0.001"
                        className="w-24"
                        value={qty[item.id] ?? "0"}
                        onChange={(e) => setQty((current) => ({ ...current, [item.id]: e.target.value }))}
                      />
                    </TableCell>
                    <TableCell>
                      <NativeSelect
                        value={itemReason[item.id] ?? ""}
                        onChange={(e) =>
                          setItemReason((current) => ({ ...current, [item.id]: e.target.value as ReturnReason | "" }))
                        }
                      >
                        <option value="">Header reason</option>
                        {REASONS.map((value) => (
                          <option key={value} value={value}>
                            {reasonLabel(value)}
                          </option>
                        ))}
                      </NativeSelect>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Return summary</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label>Reason</Label>
              <NativeSelect value={reason} onChange={(e) => setReason(e.target.value as ReturnReason | "")}>
                <option value="">None</option>
                {REASONS.map((value) => (
                  <option key={value} value={value}>
                    {reasonLabel(value)}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label>Notes</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
          </div>
          <div className="space-y-1 text-sm sm:text-right">
            <p>Taxable: {inr(subtotal)}</p>
            <p>Tax: {inr(tax)}</p>
            <p className="text-base font-semibold">Total return amount: {inr(total)}</p>
          </div>
          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="outline" asChild>
              <Link to={`/app/sales/${sale.id}`}>Cancel</Link>
            </Button>
            <Button variant="outline" disabled={working} onClick={() => void submit(false)}>
              Save draft
            </Button>
            <Button disabled={working} onClick={() => void submit(true)}>
              Complete return
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
