import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Search } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import {
  ApiRequestError,
  categoryApi,
  inventoryApi,
  type AdjustmentReason,
  type Category,
  type InventoryItem,
  type InventoryStatus,
  type InventorySummary,
  type StockMovement,
} from "@/lib/api";

const REASONS: { value: AdjustmentReason; label: string }[] = [
  { value: "DAMAGED", label: "Damaged goods" },
  { value: "EXPIRED", label: "Expired goods" },
  { value: "PHYSICAL_COUNT", label: "Physical stock correction" },
  { value: "MISSING", label: "Missing stock" },
  { value: "OPENING_CORRECTION", label: "Opening stock correction" },
  { value: "OTHER", label: "Other" },
];

function qty(value: number | string) {
  return Number(value).toLocaleString("en-IN", { maximumFractionDigits: 3 });
}

function statusLabel(status: InventoryStatus) {
  if (status === "LOW_STOCK") return "Low stock";
  if (status === "OUT_OF_STOCK") return "Out of stock";
  return "In stock";
}

function statusClass(status: InventoryStatus) {
  if (status === "LOW_STOCK") return "border-amber-300 bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-200";
  if (status === "OUT_OF_STOCK") return "border-destructive/30 bg-destructive/10 text-destructive";
  return "border-primary/30 bg-primary/10 text-primary";
}

function movementLabel(type: StockMovement["type"]) {
  if (type === "OPENING_STOCK") return "Opening stock";
  if (type === "ADJUSTMENT_IN") return "Stock in";
  if (type === "PURCHASE_RECEIPT") return "Purchase receipt";
  if (type === "SALE") return "Sale";
  return "Stock out";
}

function signedChange(movement: StockMovement) {
  const amount = qty(movement.quantity);
  return movement.type === "ADJUSTMENT_OUT" || movement.type === "SALE" ? `−${amount}` : `+${amount}`;
}

export function InventoryPage() {
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [summary, setSummary] = useState<InventorySummary | null>(null);
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [status, setStatus] = useState<"all" | InventoryStatus>("all");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [detail, setDetail] = useState<InventoryItem | null>(null);
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [openingItem, setOpeningItem] = useState<InventoryItem | null>(null);
  const [adjustItem, setAdjustItem] = useState<InventoryItem | null>(null);
  const [reorderItem, setReorderItem] = useState<InventoryItem | null>(null);

  const [openingQty, setOpeningQty] = useState("");
  const [openingError, setOpeningError] = useState<string | null>(null);
  const [adjustType, setAdjustType] = useState<"ADJUSTMENT_IN" | "ADJUSTMENT_OUT">("ADJUSTMENT_IN");
  const [adjustQty, setAdjustQty] = useState("");
  const [adjustReason, setAdjustReason] = useState<AdjustmentReason>("PHYSICAL_COUNT");
  const [adjustNotes, setAdjustNotes] = useState("");
  const [adjustConfirm, setAdjustConfirm] = useState(false);
  const [adjustError, setAdjustError] = useState<string | null>(null);
  const [reorderQty, setReorderQty] = useState("");
  const [reorderError, setReorderError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [pageResult, cats, totals] = await Promise.all([
        inventoryApi.list({
          q: query || undefined,
          categoryId: categoryId || undefined,
          status: status === "all" ? undefined : status,
          page,
          size: 20,
        }),
        categoryApi.list({ page: 1, size: 100 }),
        inventoryApi.summary(),
      ]);
      setItems(pageResult.items);
      setTotalPages(Math.max(pageResult.totalPages, 1));
      setCategories(cats.items);
      setSummary(totals);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load inventory");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryId, status, page]);

  const emptyCopy = useMemo(() => {
    if (query || categoryId || status !== "all") {
      return "No products match these inventory filters.";
    }
    return "Add products in the catalog first, then record opening stock from the shop floor.";
  }, [query, categoryId, status]);

  const previewAfter = useMemo(() => {
    if (!adjustItem) return null;
    const current = Number(adjustItem.quantity);
    const change = Number(adjustQty);
    if (!Number.isFinite(change) || change <= 0) return null;
    return adjustType === "ADJUSTMENT_OUT" ? current - change : current + change;
  }, [adjustItem, adjustQty, adjustType]);

  async function openDetail(item: InventoryItem) {
    setDetail(item);
    try {
      const result = await inventoryApi.movements(item.productId, { page: 1, size: 50 });
      setMovements(result.items);
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to load movements");
      setMovements([]);
    }
  }

  async function submitOpening(event: FormEvent) {
    event.preventDefault();
    if (!openingItem) return;
    const quantity = Number(openingQty);
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setOpeningError("Opening quantity must be greater than zero.");
      return;
    }
    setSaving(true);
    setOpeningError(null);
    try {
      await inventoryApi.openingStock(openingItem.productId, { quantity });
      toast.success("Opening stock added successfully.");
      setOpeningItem(null);
      await load();
    } catch (err) {
      setOpeningError(err instanceof ApiRequestError ? err.message : "Unable to record opening stock");
    } finally {
      setSaving(false);
    }
  }

  async function submitAdjust(event: FormEvent) {
    event.preventDefault();
    if (!adjustItem) return;
    const quantity = Number(adjustQty);
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setAdjustError("Adjustment quantity must be greater than zero.");
      return;
    }
    if (adjustType === "ADJUSTMENT_OUT" && quantity > Number(adjustItem.quantity)) {
      setAdjustError(`Insufficient stock. Available quantity: ${qty(adjustItem.quantity)}.`);
      return;
    }
    if (!adjustConfirm) {
      setAdjustError("Confirm this adjustment before saving.");
      return;
    }
    setSaving(true);
    setAdjustError(null);
    try {
      await inventoryApi.adjust(adjustItem.productId, {
        type: adjustType,
        quantity,
        reason: adjustReason,
        notes: adjustNotes.trim() || null,
      });
      toast.success("Stock adjustment saved.");
      setAdjustItem(null);
      await load();
    } catch (err) {
      setAdjustError(err instanceof ApiRequestError ? err.message : "Unable to adjust stock");
    } finally {
      setSaving(false);
    }
  }

  async function submitReorder(event: FormEvent) {
    event.preventDefault();
    if (!reorderItem) return;
    const reorderLevel = Number(reorderQty);
    if (!Number.isFinite(reorderLevel) || reorderLevel < 0) {
      setReorderError("Reorder level cannot be negative.");
      return;
    }
    setSaving(true);
    setReorderError(null);
    try {
      await inventoryApi.updateReorderLevel(reorderItem.productId, reorderLevel);
      toast.success("Reorder level updated.");
      setReorderItem(null);
      await load();
    } catch (err) {
      setReorderError(err instanceof ApiRequestError ? err.message : "Unable to update reorder level");
    } finally {
      setSaving(false);
    }
  }

  const cards = [
    { title: "Total products", value: summary?.totalProducts ?? "—" },
    { title: "In stock", value: summary?.inStock ?? "—" },
    { title: "Low stock", value: summary?.lowStock ?? "—" },
    { title: "Out of stock", value: summary?.outOfStock ?? "—" },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Inventory</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Single-location stock for this shop. Purchases post PURCHASE_RECEIPT. Completed POS sales post SALE and
          decrease quantity.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <Card key={card.title}>
            <CardHeader className="pb-2">
              <CardDescription>{card.title}</CardDescription>
              <CardTitle className="text-3xl">{loading ? "…" : card.value}</CardTitle>
            </CardHeader>
          </Card>
        ))}
      </div>

      <Card>
        <CardContent className="space-y-4 pt-6">
          <form
            className="grid gap-3 md:grid-cols-[1fr_10rem_10rem_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              setPage(1);
              void load();
            }}
          >
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="Search name or SKU"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search inventory"
              />
            </div>
            <NativeSelect aria-label="Filter by category" value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(1); }}>
              <option value="">All categories</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </NativeSelect>
            <NativeSelect
              aria-label="Filter by stock status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | InventoryStatus);
                setPage(1);
              }}
            >
              <option value="all">All statuses</option>
              <option value="IN_STOCK">In stock</option>
              <option value="LOW_STOCK">Low stock</option>
              <option value="OUT_OF_STOCK">Out of stock</option>
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
                      <TableHead>Current stock</TableHead>
                      <TableHead>Reorder</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((item) => (
                      <TableRow key={item.productId}>
                        <TableCell className="font-medium">{item.productName}</TableCell>
                        <TableCell className="font-mono text-xs">{item.sku}</TableCell>
                        <TableCell>{item.categoryName}</TableCell>
                        <TableCell>
                          {qty(item.quantity)} {item.unit}
                        </TableCell>
                        <TableCell>
                          {qty(item.reorderLevel)} {item.unit}
                        </TableCell>
                        <TableCell>
                          <Badge className={statusClass(item.status)}>{statusLabel(item.status)}</Badge>
                        </TableCell>
                        <TableCell className="space-x-2 text-right">
                          <Button size="sm" variant="ghost" onClick={() => void openDetail(item)}>
                            View
                          </Button>
                          {canMutate ? (
                            <>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                  setOpeningItem(item);
                                  setOpeningQty("");
                                  setOpeningError(null);
                                }}
                              >
                                Opening stock
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                  setAdjustItem(item);
                                  setAdjustType("ADJUSTMENT_IN");
                                  setAdjustQty("");
                                  setAdjustReason("PHYSICAL_COUNT");
                                  setAdjustNotes("");
                                  setAdjustConfirm(false);
                                  setAdjustError(null);
                                }}
                              >
                                Adjust
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
                {items.map((item) => (
                  <div key={item.productId} className="rounded-lg border p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{item.productName}</p>
                        <p className="mt-1 font-mono text-xs text-muted-foreground">{item.sku}</p>
                        <p className="mt-1 text-sm">
                          {qty(item.quantity)} {item.unit} · {item.categoryName}
                        </p>
                      </div>
                      <Badge className={statusClass(item.status)}>{statusLabel(item.status)}</Badge>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Button size="sm" variant="ghost" onClick={() => void openDetail(item)}>
                        View
                      </Button>
                      {canMutate ? (
                        <>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              setOpeningItem(item);
                              setOpeningQty("");
                              setOpeningError(null);
                            }}
                          >
                            Opening stock
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              setAdjustItem(item);
                              setAdjustType("ADJUSTMENT_IN");
                              setAdjustQty("");
                              setAdjustReason("PHYSICAL_COUNT");
                              setAdjustNotes("");
                              setAdjustConfirm(false);
                              setAdjustError(null);
                            }}
                          >
                            Adjust
                          </Button>
                        </>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
              <div className="flex items-center justify-between text-sm text-muted-foreground">
                <span>
                  Page {page} of {totalPages}
                </span>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={page >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={detail !== null} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{detail?.productName}</DialogTitle>
            <DialogDescription>Current balance and an audit trail of every stock change.</DialogDescription>
          </DialogHeader>
          {detail ? (
            <div className="space-y-6">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                <div>
                  <dt className="text-muted-foreground">SKU</dt>
                  <dd className="font-mono">{detail.sku}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Category</dt>
                  <dd>{detail.categoryName}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Current stock</dt>
                  <dd>
                    {qty(detail.quantity)} {detail.unit}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Reorder level</dt>
                  <dd>
                    {qty(detail.reorderLevel)} {detail.unit}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Status</dt>
                  <dd>
                    <Badge className={statusClass(detail.status)}>{statusLabel(detail.status)}</Badge>
                  </dd>
                </div>
              </dl>
              {canMutate ? (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setReorderItem(detail);
                    setReorderQty(String(Number(detail.reorderLevel)));
                    setReorderError(null);
                  }}
                >
                  Change reorder level
                </Button>
              ) : null}
              <div>
                <h3 className="mb-3 text-sm font-semibold">Stock movement history</h3>
                {movements.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No movements yet. Record opening stock to start the ledger.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>When</TableHead>
                          <TableHead>Type</TableHead>
                          <TableHead>Change</TableHead>
                          <TableHead>Before</TableHead>
                          <TableHead>After</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {movements.map((movement) => (
                          <TableRow key={movement.id}>
                            <TableCell className="whitespace-nowrap text-xs">
                              {new Date(movement.createdAt).toLocaleString("en-IN")}
                            </TableCell>
                            <TableCell>
                              {movementLabel(movement.type)}
                              {movement.createdByName ? (
                                <span className="block text-xs text-muted-foreground">{movement.createdByName}</span>
                              ) : null}
                            </TableCell>
                            <TableCell>{signedChange(movement)}</TableCell>
                            <TableCell>{qty(movement.quantityBefore)}</TableCell>
                            <TableCell>{qty(movement.quantityAfter)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={openingItem !== null} onOpenChange={(open) => !open && setOpeningItem(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Opening stock</DialogTitle>
            <DialogDescription>Record the quantity already on the shelf when you start RetailFlow.</DialogDescription>
          </DialogHeader>
          {openingItem?.openingRecorded ? (
            <p className="text-sm text-muted-foreground">
              Opening stock is already recorded for {openingItem.productName}. Current stock is {qty(openingItem.quantity)}{" "}
              {openingItem.unit}. Use Adjust stock to correct the count.
            </p>
          ) : (
            <form className="space-y-4" onSubmit={submitOpening}>
              <p className="text-sm">
                <span className="font-medium">{openingItem?.productName}</span>
                <span className="block text-muted-foreground">Current stock: {qty(openingItem?.quantity ?? 0)}</span>
              </p>
              <div className="space-y-2">
                <Label htmlFor="opening-qty">Opening quantity</Label>
                <Input
                  id="opening-qty"
                  type="number"
                  min="0"
                  step="0.001"
                  value={openingQty}
                  onChange={(e) => setOpeningQty(e.target.value)}
                />
              </div>
              {openingError ? <p className="text-sm text-destructive">{openingError}</p> : null}
              <Button type="submit" disabled={saving}>
                {saving ? "Saving..." : "Initialize stock"}
              </Button>
            </form>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={adjustItem !== null} onOpenChange={(open) => !open && setAdjustItem(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Adjust stock</DialogTitle>
            <DialogDescription>Manual correction only. Purchases and sales will post automatically later.</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={submitAdjust}>
            <p className="text-sm">
              <span className="font-medium">{adjustItem?.productName}</span>
              <span className="block text-muted-foreground">
                Current stock: {qty(adjustItem?.quantity ?? 0)} {adjustItem?.unit}
              </span>
            </p>
            <div className="space-y-2">
              <Label htmlFor="adjust-type">Adjustment type</Label>
              <NativeSelect
                id="adjust-type"
                value={adjustType}
                onChange={(e) => setAdjustType(e.target.value as "ADJUSTMENT_IN" | "ADJUSTMENT_OUT")}
              >
                <option value="ADJUSTMENT_IN">Stock in</option>
                <option value="ADJUSTMENT_OUT">Stock out</option>
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label htmlFor="adjust-qty">Quantity</Label>
              <Input
                id="adjust-qty"
                type="number"
                min="0.001"
                step="0.001"
                value={adjustQty}
                onChange={(e) => setAdjustQty(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="adjust-reason">Reason</Label>
              <NativeSelect
                id="adjust-reason"
                value={adjustReason}
                onChange={(e) => setAdjustReason(e.target.value as AdjustmentReason)}
              >
                {REASONS.map((reason) => (
                  <option key={reason.value} value={reason.value}>
                    {reason.label}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label htmlFor="adjust-notes">Notes</Label>
              <Textarea id="adjust-notes" rows={2} value={adjustNotes} onChange={(e) => setAdjustNotes(e.target.value)} />
            </div>
            {previewAfter !== null ? (
              <p className="rounded-md border bg-muted/40 px-3 py-2 text-sm">
                New stock: {previewAfter < 0 ? "invalid (would go negative)" : `${qty(previewAfter)} ${adjustItem?.unit}`}
              </p>
            ) : null}
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={adjustConfirm}
                onChange={(e) => setAdjustConfirm(e.target.checked)}
                className="h-4 w-4"
              />
              I confirm this adjustment is correct
            </label>
            {adjustError ? <p className="text-sm text-destructive">{adjustError}</p> : null}
            <Button type="submit" disabled={saving}>
              {saving ? "Saving..." : "Save adjustment"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={reorderItem !== null} onOpenChange={(open) => !open && setReorderItem(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reorder level</DialogTitle>
            <DialogDescription>Alert as low stock when quantity is at or below this level.</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={submitReorder}>
            <div className="space-y-2">
              <Label htmlFor="reorder-qty">Reorder level</Label>
              <Input
                id="reorder-qty"
                type="number"
                min="0"
                step="0.001"
                value={reorderQty}
                onChange={(e) => setReorderQty(e.target.value)}
                required
              />
            </div>
            {reorderError ? <p className="text-sm text-destructive">{reorderError}</p> : null}
            <Button type="submit" disabled={saving}>
              {saving ? "Saving..." : "Save reorder level"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
