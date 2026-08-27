import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  ApiRequestError,
  inventoryApi,
  productApi,
  purchaseApi,
  supplierApi,
  type InventoryItem,
  type Product,
  type Supplier,
} from "@/lib/api";

const GST_RATES = [0, 5, 12, 18, 28] as const;

type Line = {
  key: string;
  productId: string;
  quantity: string;
  unitCost: string;
  gstRate: string;
};

function inr(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number.isFinite(value) ? value : 0);
}

function round2(value: number) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function lineMath(line: Line) {
  const quantity = Number(line.quantity);
  const unitCost = Number(line.unitCost);
  const gstRate = Number(line.gstRate);
  if (!Number.isFinite(quantity) || !Number.isFinite(unitCost) || !Number.isFinite(gstRate)) {
    return { subtotal: 0, tax: 0, total: 0 };
  }
  const subtotal = round2(quantity * unitCost);
  const tax = round2((subtotal * gstRate) / 100);
  return { subtotal, tax, total: round2(subtotal + tax) };
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

export function PurchaseFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [stockByProduct, setStockByProduct] = useState<Record<string, InventoryItem>>({});
  const [supplierId, setSupplierId] = useState("");
  const [purchaseDate, setPurchaseDate] = useState(today());
  const [notes, setNotes] = useState("");
  const [lines, setLines] = useState<Line[]>([emptyLine()]);
  const [productQuery, setProductQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void bootstrap();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function bootstrap() {
    setLoading(true);
    setError(null);
    try {
      const [vendorList, productPage, inventoryPage] = await Promise.all([
        supplierApi.active(),
        productApi.list({ active: true, page: 1, size: 100 }),
        inventoryApi.list({ page: 1, size: 100 }),
      ]);
      setSuppliers(vendorList);
      setProducts(productPage.items);
      setStockByProduct(Object.fromEntries(inventoryPage.items.map((item) => [item.productId, item])));
      if (id) {
        const purchase = await purchaseApi.get(id);
        if (purchase.status !== "DRAFT") {
          navigate(`/app/purchases/${id}`, { replace: true });
          return;
        }
        setSupplierId(purchase.supplierId);
        setPurchaseDate(purchase.purchaseDate);
        setNotes(purchase.notes ?? "");
        setLines(
          purchase.items.map((item) => ({
            key: item.id,
            productId: item.productId,
            quantity: String(item.quantity),
            unitCost: String(item.unitCost),
            gstRate: String(Number(item.gstRate)),
          })),
        );
      } else {
        setSupplierId(vendorList[0]?.id ?? "");
        setPurchaseDate(today());
        setNotes("");
        setLines([emptyLine()]);
      }
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load purchase form");
    } finally {
      setLoading(false);
    }
  }

  const filteredProducts = useMemo(() => {
    const q = productQuery.trim().toLowerCase();
    if (!q) return products;
    return products.filter(
      (product) => product.name.toLowerCase().includes(q) || product.sku.toLowerCase().includes(q),
    );
  }, [productQuery, products]);

  const totals = useMemo(() => {
    return lines.reduce(
      (acc, line) => {
        const math = lineMath(line);
        acc.subtotal += math.subtotal;
        acc.tax += math.tax;
        acc.total += math.total;
        return acc;
      },
      { subtotal: 0, tax: 0, total: 0 },
    );
  }, [lines]);

  function productFor(productId: string) {
    return products.find((product) => product.id === productId);
  }

  function updateLine(key: string, patch: Partial<Line>) {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)));
  }

  function selectProduct(key: string, productId: string) {
    const product = productFor(productId);
    updateLine(key, {
      productId,
      unitCost: product ? String(product.costPrice) : "",
      gstRate: product ? String(Number(product.gstRate)) : "18",
    });
  }

  function addLine() {
    setLines((current) => [...current, emptyLine()]);
  }

  function removeLine(key: string) {
    setLines((current) => (current.length === 1 ? current : current.filter((line) => line.key !== key)));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canMutate) return;
    if (!supplierId) {
      setFormError("Choose a supplier.");
      return;
    }
    if (!purchaseDate) {
      setFormError("Purchase date is required.");
      return;
    }
    const payloadItems = [];
    for (const line of lines) {
      if (!line.productId) {
        setFormError("Every line needs a product.");
        return;
      }
      const quantity = Number(line.quantity);
      const unitCost = Number(line.unitCost);
      if (!Number.isFinite(quantity) || quantity <= 0) {
        setFormError("Quantity must be greater than zero.");
        return;
      }
      if (!Number.isFinite(unitCost) || unitCost < 0) {
        setFormError("Unit cost cannot be negative.");
        return;
      }
      payloadItems.push({
        productId: line.productId,
        quantity,
        unitCost,
        gstRate: Number(line.gstRate),
      });
    }
    setSaving(true);
    setFormError(null);
    try {
      const body = {
        supplierId,
        purchaseDate,
        notes: notes.trim() || null,
        items: payloadItems,
      };
      const saved = id ? await purchaseApi.update(id, body) : await purchaseApi.create(body);
      toast.success(id ? "Draft purchase updated" : "Draft purchase saved");
      navigate(`/app/purchases/${saved.id}`);
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to save purchase");
    } finally {
      setSaving(false);
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

  if (error) {
    return <p className="text-sm text-destructive">{error}</p>;
  }

  return (
    <form className="space-y-6" onSubmit={onSubmit}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{id ? "Edit purchase" : "New purchase"}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Saving a draft does not add stock. Receive the purchase after the goods arrive.
          </p>
        </div>
        <Button type="button" variant="outline" asChild>
          <Link to="/app/purchases">Back to purchases</Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Supplier</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="purchase-supplier">Supplier</Label>
            <NativeSelect
              id="purchase-supplier"
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
              disabled={!canMutate}
            >
              <option value="">Select supplier</option>
              {suppliers.map((supplier) => (
                <option key={supplier.id} value={supplier.id}>
                  {supplier.name}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <Label htmlFor="purchase-date">Purchase date</Label>
            <Input
              id="purchase-date"
              type="date"
              value={purchaseDate}
              onChange={(e) => setPurchaseDate(e.target.value)}
              disabled={!canMutate}
            />
          </div>
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="purchase-notes">Notes</Label>
            <Textarea id="purchase-notes" value={notes} onChange={(e) => setNotes(e.target.value)} disabled={!canMutate} />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <CardTitle>Items</CardTitle>
          <Input
            className="sm:max-w-xs"
            placeholder="Filter products by name or SKU"
            value={productQuery}
            onChange={(e) => setProductQuery(e.target.value)}
            aria-label="Filter products"
          />
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Cost</TableHead>
                  <TableHead>GST</TableHead>
                  <TableHead>Total</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {lines.map((line) => {
                  const product = productFor(line.productId);
                  const stock = stockByProduct[line.productId];
                  const math = lineMath(line);
                  return (
                    <TableRow key={line.key}>
                      <TableCell className="min-w-64">
                        <NativeSelect
                          aria-label="Product"
                          value={line.productId}
                          onChange={(e) => selectProduct(line.key, e.target.value)}
                          disabled={!canMutate}
                        >
                          <option value="">Select product</option>
                          {filteredProducts.map((option) => (
                            <option key={option.id} value={option.id}>
                              {option.name} · {option.sku}
                            </option>
                          ))}
                        </NativeSelect>
                        {product ? (
                          <p className="mt-1 text-xs text-muted-foreground">
                            {product.unit}
                            {stock ? ` · stock ${stock.quantity}` : ""} · default cost {inr(Number(product.costPrice))}
                          </p>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <Input
                          aria-label="Quantity"
                          value={line.quantity}
                          onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
                          disabled={!canMutate}
                        />
                      </TableCell>
                      <TableCell>
                        <Input
                          aria-label="Unit cost"
                          value={line.unitCost}
                          onChange={(e) => updateLine(line.key, { unitCost: e.target.value })}
                          disabled={!canMutate}
                        />
                      </TableCell>
                      <TableCell>
                        <NativeSelect
                          aria-label="GST rate"
                          value={line.gstRate}
                          onChange={(e) => updateLine(line.key, { gstRate: e.target.value })}
                          disabled={!canMutate}
                        >
                          {GST_RATES.map((rate) => (
                            <option key={rate} value={String(rate)}>
                              {rate}%
                            </option>
                          ))}
                        </NativeSelect>
                      </TableCell>
                      <TableCell className="font-medium">{inr(math.total)}</TableCell>
                      <TableCell>
                        {canMutate ? (
                          <Button type="button" size="icon" variant="ghost" onClick={() => removeLine(line.key)} aria-label="Remove item">
                            <Trash2 />
                          </Button>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          {canMutate ? (
            <Button type="button" variant="outline" onClick={addLine}>
              <Plus className="mr-2 h-4 w-4" />
              Add product
            </Button>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="flex flex-col gap-2 pt-6 sm:items-end">
          <p className="text-sm text-muted-foreground">Subtotal: {inr(round2(totals.subtotal))}</p>
          <p className="text-sm text-muted-foreground">Tax: {inr(round2(totals.tax))}</p>
          <p className="text-lg font-semibold">Grand total: {inr(round2(totals.total))}</p>
          <p className="text-xs text-muted-foreground">Shown for review. The server recalculates GST and totals on save.</p>
          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
          {canMutate ? (
            <Button type="submit" disabled={saving}>
              Save draft
            </Button>
          ) : null}
        </CardContent>
      </Card>
    </form>
  );
}

function emptyLine(): Line {
  return { key: crypto.randomUUID(), productId: "", quantity: "1", unitCost: "", gstRate: "18" };
}
