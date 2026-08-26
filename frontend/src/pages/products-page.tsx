import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Plus, Search } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  ApiRequestError,
  categoryApi,
  productApi,
  type Category,
  type Product,
  type ProductPayload,
} from "@/lib/api";

const UNITS = ["PCS", "KG", "G", "L", "ML", "BOX", "PACK", "BOTTLE"] as const;
const GST_RATES = [0, 5, 12, 18, 28] as const;

const emptyForm = {
  name: "",
  categoryId: "",
  description: "",
  sku: "",
  barcode: "",
  costPrice: "",
  sellingPrice: "",
  gstRate: "18",
  unit: "PCS",
  active: true,
};

function inr(value: number | string) {
  const amount = typeof value === "string" ? Number(value) : value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(Number.isFinite(amount) ? amount : 0);
}

export function ProductsPage() {
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [items, setItems] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [status, setStatus] = useState<"all" | "true" | "false">("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [detail, setDetail] = useState<Product | null>(null);
  const [editing, setEditing] = useState<Product | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const activeFilter = status === "all" ? undefined : status === "true";

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [page, cats] = await Promise.all([
        productApi.list({
          q: query || undefined,
          categoryId: categoryId || undefined,
          active: activeFilter,
          page: 1,
          size: 50,
        }),
        categoryApi.list({ page: 1, size: 100 }),
      ]);
      setItems(page.items);
      setCategories(cats.items);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load products");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryId, status]);

  const emptyCopy = useMemo(() => {
    if (query || categoryId || status !== "all") {
      return "No products match these filters.";
    }
    return "Add SKUs your counter staff will recognise — milk packets, shirt sizes, mobile accessories.";
  }, [query, categoryId, status]);

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyForm, categoryId: categories.find((c) => c.active)?.id ?? "" });
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(product: Product) {
    setEditing(product);
    setForm({
      name: product.name,
      categoryId: product.categoryId,
      description: product.description ?? "",
      sku: product.sku,
      barcode: product.barcode ?? "",
      costPrice: String(product.costPrice),
      sellingPrice: String(product.sellingPrice),
      gstRate: String(Number(product.gstRate)),
      unit: product.unit,
      active: product.active,
    });
    setFormError(null);
    setDialogOpen(true);
  }

  function setField<K extends keyof typeof emptyForm>(key: K, value: (typeof emptyForm)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function validate(): string | null {
    if (form.name.trim().length < 2) {
      return "Product name is required.";
    }
    if (!form.categoryId) {
      return "Choose a category.";
    }
    if (form.sku.trim().length < 2) {
      return "SKU / product code is required.";
    }
    if (Number(form.costPrice) < 0 || Number.isNaN(Number(form.costPrice))) {
      return "Cost price cannot be negative.";
    }
    if (Number(form.sellingPrice) < 0 || Number.isNaN(Number(form.sellingPrice))) {
      return "Selling price cannot be negative.";
    }
    return null;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const message = validate();
    if (message) {
      setFormError(message);
      return;
    }
    const payload: ProductPayload = {
      name: form.name.trim(),
      categoryId: form.categoryId,
      description: form.description.trim() || null,
      sku: form.sku.trim(),
      barcode: form.barcode.trim() || null,
      costPrice: Number(form.costPrice),
      sellingPrice: Number(form.sellingPrice),
      gstRate: Number(form.gstRate),
      unit: form.unit,
    };
    setSaving(true);
    setFormError(null);
    try {
      const saved = editing ? await productApi.update(editing.id, payload) : await productApi.create(payload);
      if (editing && saved.active !== form.active) {
        await productApi.updateStatus(saved.id, form.active);
      }
      toast.success(editing ? "Product updated" : "Product added");
      setDialogOpen(false);
      await load();
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to save product");
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(product: Product) {
    try {
      await productApi.updateStatus(product.id, !product.active);
      toast.success(product.active ? "Product deactivated" : "Product activated");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to update status");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Products</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Catalogue of what this shop sells. Stock quantities come in the inventory milestone — not here.
          </p>
        </div>
        {canMutate ? (
          <Button onClick={openCreate} disabled={categories.length === 0}>
            <Plus className="h-4 w-4" />
            Add product
          </Button>
        ) : null}
      </div>

      {categories.length === 0 && !loading ? (
        <p className="text-sm text-muted-foreground">Create a category before adding products.</p>
      ) : null}

      <Card>
        <CardContent className="space-y-4 pt-6">
          <form
            className="grid gap-3 md:grid-cols-[1fr_10rem_9rem_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              void load();
            }}
          >
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="Search name, SKU or barcode"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search products"
              />
            </div>
            <NativeSelect aria-label="Filter by category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">All categories</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </NativeSelect>
            <NativeSelect
              aria-label="Filter by status"
              value={status}
              onChange={(e) => setStatus(e.target.value as "all" | "true" | "false")}
            >
              <option value="all">All statuses</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </NativeSelect>
            <Button type="submit" variant="outline">
              Search
            </Button>
          </form>

          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">{emptyCopy}</div>
          ) : (
            <>
              <div className="hidden lg:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Product</TableHead>
                      <TableHead>SKU</TableHead>
                      <TableHead>Category</TableHead>
                      <TableHead>Selling price</TableHead>
                      <TableHead>GST</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((product) => (
                      <TableRow key={product.id}>
                        <TableCell className="font-medium">{product.name}</TableCell>
                        <TableCell className="font-mono text-xs">{product.sku}</TableCell>
                        <TableCell>{product.categoryName}</TableCell>
                        <TableCell>{inr(product.sellingPrice)}</TableCell>
                        <TableCell>{Number(product.gstRate)}%</TableCell>
                        <TableCell>
                          <Badge className={product.active ? "border-primary/30 bg-primary/10 text-primary" : ""}>
                            {product.active ? "Active" : "Inactive"}
                          </Badge>
                        </TableCell>
                        <TableCell className="space-x-2 text-right">
                          <Button size="sm" variant="ghost" onClick={() => setDetail(product)}>
                            View
                          </Button>
                          {canMutate ? (
                            <>
                              <Button size="sm" variant="outline" onClick={() => openEdit(product)}>
                                Edit
                              </Button>
                              <Button size="sm" variant="ghost" onClick={() => void toggleStatus(product)}>
                                {product.active ? "Deactivate" : "Activate"}
                              </Button>
                            </>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 lg:hidden">
                {items.map((product) => (
                  <div key={product.id} className="rounded-lg border p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{product.name}</p>
                        <p className="mt-1 font-mono text-xs text-muted-foreground">{product.sku}</p>
                        <p className="mt-1 text-sm">
                          {product.categoryName} · {inr(product.sellingPrice)} · {Number(product.gstRate)}% GST
                        </p>
                      </div>
                      <Badge className={product.active ? "border-primary/30 bg-primary/10 text-primary" : ""}>
                        {product.active ? "Active" : "Inactive"}
                      </Badge>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Button size="sm" variant="ghost" onClick={() => setDetail(product)}>
                        View
                      </Button>
                      {canMutate ? (
                        <>
                          <Button size="sm" variant="outline" onClick={() => openEdit(product)}>
                            Edit
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => void toggleStatus(product)}>
                            {product.active ? "Deactivate" : "Activate"}
                          </Button>
                        </>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit product" : "Add product"}</DialogTitle>
            <DialogDescription>SKU is unique in your store. Barcode is optional until you use a scanner.</DialogDescription>
          </DialogHeader>
          <form className="space-y-6" onSubmit={onSubmit}>
            <section className="space-y-3">
              <h3 className="text-sm font-semibold">Basic information</h3>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="product-name">Product name</Label>
                  <Input id="product-name" value={form.name} onChange={(e) => setField("name", e.target.value)} required />
                </div>
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="product-category">Category</Label>
                  <NativeSelect
                    id="product-category"
                    value={form.categoryId}
                    onChange={(e) => setField("categoryId", e.target.value)}
                    required
                  >
                    <option value="">Select category</option>
                    {categories.map((category) => (
                      <option key={category.id} value={category.id}>
                        {category.name}
                        {category.active ? "" : " (inactive)"}
                      </option>
                    ))}
                  </NativeSelect>
                </div>
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="product-description">Description</Label>
                  <Textarea
                    id="product-description"
                    value={form.description}
                    onChange={(e) => setField("description", e.target.value)}
                    rows={2}
                  />
                </div>
              </div>
            </section>
            <section className="space-y-3">
              <h3 className="text-sm font-semibold">Identification</h3>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="product-sku">SKU / product code</Label>
                  <Input id="product-sku" value={form.sku} onChange={(e) => setField("sku", e.target.value)} required />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="product-barcode">Barcode</Label>
                  <Input id="product-barcode" value={form.barcode} onChange={(e) => setField("barcode", e.target.value)} />
                </div>
              </div>
            </section>
            <section className="space-y-3">
              <h3 className="text-sm font-semibold">Pricing</h3>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="product-cost">Cost price (₹)</Label>
                  <Input
                    id="product-cost"
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.costPrice}
                    onChange={(e) => setField("costPrice", e.target.value)}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="product-selling">Selling price (₹)</Label>
                  <Input
                    id="product-selling"
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.sellingPrice}
                    onChange={(e) => setField("sellingPrice", e.target.value)}
                    required
                  />
                </div>
              </div>
            </section>
            <section className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="product-gst">GST rate</Label>
                <NativeSelect id="product-gst" value={form.gstRate} onChange={(e) => setField("gstRate", e.target.value)}>
                  {GST_RATES.map((rate) => (
                    <option key={rate} value={rate}>
                      {rate}%
                    </option>
                  ))}
                </NativeSelect>
              </div>
              <div className="space-y-2">
                <Label htmlFor="product-unit">Unit</Label>
                <NativeSelect id="product-unit" value={form.unit} onChange={(e) => setField("unit", e.target.value)}>
                  {UNITS.map((unit) => (
                    <option key={unit} value={unit}>
                      {unit}
                    </option>
                  ))}
                </NativeSelect>
              </div>
            </section>
            {editing ? (
              <div className="flex items-center justify-between rounded-lg border px-3 py-2">
                <Label htmlFor="product-active">Active</Label>
                <Switch id="product-active" checked={form.active} onCheckedChange={(checked) => setField("active", checked)} />
              </div>
            ) : null}
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            <Button type="submit" disabled={saving}>
              {saving ? "Saving..." : editing ? "Save product" : "Create product"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={detail !== null} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{detail?.name}</DialogTitle>
            <DialogDescription>Master data only. Inventory quantities are not shown yet.</DialogDescription>
          </DialogHeader>
          {detail ? (
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              <div>
                <dt className="text-muted-foreground">SKU</dt>
                <dd className="font-mono">{detail.sku}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Barcode</dt>
                <dd>{detail.barcode || "—"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Category</dt>
                <dd>{detail.categoryName}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Unit</dt>
                <dd>{detail.unit}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Cost price</dt>
                <dd>{inr(detail.costPrice)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Selling price</dt>
                <dd>{inr(detail.sellingPrice)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">GST</dt>
                <dd>{Number(detail.gstRate)}%</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Status</dt>
                <dd>{detail.active ? "Active" : "Inactive"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Created</dt>
                <dd>{new Date(detail.createdAt).toLocaleString("en-IN")}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Updated</dt>
                <dd>{new Date(detail.updatedAt).toLocaleString("en-IN")}</dd>
              </div>
            </dl>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
