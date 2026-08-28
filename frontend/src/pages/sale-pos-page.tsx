import { useEffect, useMemo, useState, type FormEvent, type KeyboardEvent } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  ApiRequestError,
  customerApi,
  inventoryApi,
  productApi,
  saleApi,
  type Customer,
  type InventoryItem,
  type PaymentMethod,
  type Product,
} from "@/lib/api";
import { inr, lineTotals, saleTotals } from "@/lib/sale-math";

type CartLine = {
  productId: string;
  productName: string;
  sku: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  gstRate: number;
};

function today() {
  return new Date().toISOString().slice(0, 10);
}

export function SalePosPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canCreateCustomer = user?.role !== "CASHIER";
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [stockByProduct, setStockByProduct] = useState<Record<string, InventoryItem>>({});
  const [customerId, setCustomerId] = useState("");
  const [barcode, setBarcode] = useState("");
  const [productQuery, setProductQuery] = useState("");
  const [cart, setCart] = useState<CartLine[]>([]);
  const [discount, setDiscount] = useState("0");
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [customerOpen, setCustomerOpen] = useState(false);
  const [newCustomerName, setNewCustomerName] = useState("");
  const [newCustomerPhone, setNewCustomerPhone] = useState("");

  useEffect(() => {
    void bootstrap();
  }, []);

  async function bootstrap() {
    setLoading(true);
    setError(null);
    try {
      const [people, productPage, inventoryPage] = await Promise.all([
        customerApi.active(),
        productApi.list({ active: true, page: 1, size: 100 }),
        inventoryApi.list({ page: 1, size: 100 }),
      ]);
      setCustomers(people);
      setProducts(productPage.items);
      setStockByProduct(Object.fromEntries(inventoryPage.items.map((item) => [item.productId, item])));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load POS");
    } finally {
      setLoading(false);
    }
  }

  const matches = useMemo(() => {
    const q = productQuery.trim().toLowerCase();
    if (!q) return products.slice(0, 8);
    return products
      .filter(
        (product) =>
          product.name.toLowerCase().includes(q) ||
          product.sku.toLowerCase().includes(q) ||
          (product.barcode ?? "").toLowerCase().includes(q),
      )
      .slice(0, 8);
  }, [productQuery, products]);

  const totals = saleTotals(cart, Number(discount) || 0);

  function addProduct(product: Product, quantity = 1) {
    setFormError(null);
    setCart((current) => {
      const existing = current.find((line) => line.productId === product.id);
      if (existing) {
        return current.map((line) =>
          line.productId === product.id ? { ...line, quantity: line.quantity + quantity } : line,
        );
      }
      return [
        ...current,
        {
          productId: product.id,
          productName: product.name,
          sku: product.sku,
          unit: product.unit,
          quantity,
          unitPrice: Number(product.sellingPrice),
          gstRate: Number(product.gstRate),
        },
      ];
    });
  }

  async function onBarcodeEnter(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== "Enter") return;
    event.preventDefault();
    const code = barcode.trim();
    if (!code) return;
    const exact =
      products.find((product) => (product.barcode ?? "") === code) ??
      products.find((product) => product.sku === code);
    if (exact) {
      addProduct(exact);
      setBarcode("");
      return;
    }
    try {
      const page = await productApi.list({ q: code, active: true, page: 1, size: 20 });
      const found =
        page.items.find((product) => (product.barcode ?? "") === code) ??
        page.items.find((product) => product.sku === code) ??
        page.items[0];
      if (!found) {
        setFormError("No product matches this barcode or SKU.");
        return;
      }
      addProduct(found);
      setBarcode("");
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to search products");
    }
  }

  function setQuantity(productId: string, quantity: number) {
    if (quantity <= 0) {
      setCart((current) => current.filter((line) => line.productId !== productId));
      return;
    }
    setCart((current) => current.map((line) => (line.productId === productId ? { ...line, quantity } : line)));
  }

  async function createCustomer(event: FormEvent) {
    event.preventDefault();
    if (newCustomerName.trim().length < 2) {
      toast.error("Customer name must be at least 2 characters.");
      return;
    }
    try {
      const created = await customerApi.create({
        name: newCustomerName.trim(),
        phone: newCustomerPhone.trim() || null,
      });
      setCustomers((current) => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setCustomerId(created.id);
      setCustomerOpen(false);
      setNewCustomerName("");
      setNewCustomerPhone("");
      toast.success("Customer added");
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to create customer");
    }
  }

  async function checkout() {
    if (cart.length === 0) {
      setFormError("Add at least one product before completing the sale.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const draft = await saleApi.create({
        customerId: customerId || null,
        saleDate: today(),
        discount: Number(discount) || 0,
        notes: notes.trim() || null,
        items: cart.map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          unitPrice: line.unitPrice,
          gstRate: line.gstRate,
        })),
      });
      const completed = await saleApi.complete(draft.id, paymentMethod);
      toast.success(`Sale ${completed.saleNumber} completed`);
      navigate(`/app/sales/${completed.id}/invoice`);
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to complete sale");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error}</p>
        <Button variant="outline" onClick={() => void bootstrap()}>
          Retry
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">POS</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Scan or search a product, add it to the cart, then complete the sale. Stock decreases only on complete.
          </p>
        </div>
        <Button variant="outline" asChild>
          <Link to="/app/sales">Sales history</Link>
        </Button>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.4fr_0.8fr]">
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Scan or search</CardTitle>
              <CardDescription>A barcode scanner types into this field and presses Enter.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-2">
                <Label htmlFor="barcode">Barcode / SKU</Label>
                <Input
                  id="barcode"
                  autoFocus
                  value={barcode}
                  onChange={(e) => setBarcode(e.target.value)}
                  onKeyDown={(event) => void onBarcodeEnter(event)}
                  placeholder="Scan barcode and press Enter"
                  aria-label="Barcode search"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="product-search">Product name, SKU, or barcode</Label>
                <Input
                  id="product-search"
                  value={productQuery}
                  onChange={(e) => setProductQuery(e.target.value)}
                  placeholder="Type to filter products"
                  aria-label="Search products"
                />
              </div>
              {matches.length === 0 ? (
                <p className="text-sm text-muted-foreground">No products match this search.</p>
              ) : (
                <div className="grid gap-2 sm:grid-cols-2">
                  {matches.map((product) => {
                    const stock = stockByProduct[product.id];
                    return (
                      <button
                        key={product.id}
                        type="button"
                        className="rounded-lg border p-3 text-left hover:bg-muted/50"
                        onClick={() => addProduct(product)}
                      >
                        <p className="font-medium">{product.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {product.sku}
                          {product.barcode ? ` · ${product.barcode}` : ""}
                        </p>
                        <p className="mt-1 text-sm">
                          {inr(product.sellingPrice)} · GST {Number(product.gstRate)}%
                        </p>
                        <p className="text-xs text-muted-foreground">
                          Stock {stock ? Number(stock.quantity) : 0} {product.unit}
                        </p>
                      </button>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Cart</CardTitle>
            </CardHeader>
            <CardContent>
              {cart.length === 0 ? (
                <p className="text-sm text-muted-foreground">Cart is empty. Scan a barcode or select a product.</p>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Product</TableHead>
                        <TableHead>Qty</TableHead>
                        <TableHead>Price</TableHead>
                        <TableHead>GST</TableHead>
                        <TableHead>Line</TableHead>
                        <TableHead />
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {cart.map((line) => {
                        const totalsLine = lineTotals(line.quantity, line.unitPrice, line.gstRate);
                        return (
                          <TableRow key={line.productId}>
                            <TableCell>
                              <p className="font-medium">{line.productName}</p>
                              <p className="font-mono text-xs text-muted-foreground">{line.sku}</p>
                            </TableCell>
                            <TableCell>
                              <Input
                                aria-label={`Quantity for ${line.productName}`}
                                className="w-20"
                                type="number"
                                min={1}
                                value={line.quantity}
                                onChange={(e) => {
                                  const value = e.target.value;
                                  if (value === "") {
                                    return;
                                  }
                                  setQuantity(line.productId, Number(value));
                                }}
                              />
                            </TableCell>
                            <TableCell>{inr(line.unitPrice)}</TableCell>
                            <TableCell>{line.gstRate}%</TableCell>
                            <TableCell className="font-medium">{inr(totalsLine.total)}</TableCell>
                            <TableCell>
                              <Button
                                size="icon"
                                variant="ghost"
                                aria-label={`Remove ${line.productName}`}
                                onClick={() => setCart((current) => current.filter((row) => row.productId !== line.productId))}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Checkout</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="customer">Customer</Label>
              <NativeSelect id="customer" value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
                <option value="">Walk-in Customer</option>
                {customers.map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {customer.name}
                    {customer.phone ? ` (${customer.phone})` : ""}
                  </option>
                ))}
              </NativeSelect>
              {canCreateCustomer ? (
                <Button type="button" variant="outline" size="sm" onClick={() => setCustomerOpen(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  New customer
                </Button>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="discount">Sale discount (₹)</Label>
              <Input id="discount" type="number" min={0} value={discount} onChange={(e) => setDiscount(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="payment">Payment method</Label>
              <NativeSelect
                id="payment"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
              >
                <option value="CASH">Cash</option>
                <option value="UPI">UPI</option>
                <option value="CARD">Card</option>
                <option value="OTHER">Other</option>
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label htmlFor="notes">Notes</Label>
              <Input id="notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
            <div className="space-y-1 rounded-lg border p-3 text-sm">
              <p>Subtotal: {inr(totals.subtotal)}</p>
              <p>Discount: {inr(totals.discount)}</p>
              <p>GST: {inr(totals.taxTotal)}</p>
              <p className="text-base font-semibold">Grand total: {inr(totals.grandTotal)}</p>
            </div>
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            <Button className="w-full" onClick={() => void checkout()} disabled={saving}>
              Complete sale
            </Button>
          </CardContent>
        </Card>
      </div>

      <Dialog open={customerOpen} onOpenChange={setCustomerOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add customer</DialogTitle>
            <DialogDescription>Cashiers cannot create customers from POS. Owners and managers can.</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={createCustomer}>
            <div className="space-y-2">
              <Label htmlFor="new-customer-name">Name</Label>
              <Input id="new-customer-name" value={newCustomerName} onChange={(e) => setNewCustomerName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="new-customer-phone">Phone</Label>
              <Input id="new-customer-phone" value={newCustomerPhone} onChange={(e) => setNewCustomerPhone(e.target.value)} />
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setCustomerOpen(false)}>
                Cancel
              </Button>
              <Button type="submit">Create customer</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
