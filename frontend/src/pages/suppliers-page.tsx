import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Plus, Search } from "lucide-react";
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
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, supplierApi, type Supplier, type SupplierPayload, type SupplierSummary } from "@/lib/api";

const emptyForm = {
  name: "",
  contactPerson: "",
  phone: "",
  email: "",
  address: "",
  gstin: "",
  notes: "",
  active: true,
};

export function SuppliersPage() {
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [items, setItems] = useState<Supplier[]>([]);
  const [summary, setSummary] = useState<SupplierSummary | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"all" | "true" | "false">("all");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [detail, setDetail] = useState<Supplier | null>(null);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const activeFilter = status === "all" ? undefined : status === "true";

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [list, counts] = await Promise.all([
        supplierApi.list({ q: query || undefined, active: activeFilter, page, size: 20 }),
        supplierApi.summary(),
      ]);
      setItems(list.items);
      setTotalPages(Math.max(list.totalPages, 1));
      setSummary(counts);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load suppliers");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, page]);

  const emptyCopy = useMemo(() => {
    if (query || status !== "all") {
      return "No suppliers match these filters.";
    }
    return "Add distributors you buy from — milk dairies, FMCG stockists, garment wholesalers.";
  }, [query, status]);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(supplier: Supplier) {
    setEditing(supplier);
    setForm({
      name: supplier.name,
      contactPerson: supplier.contactPerson ?? "",
      phone: supplier.phone ?? "",
      email: supplier.email ?? "",
      address: supplier.address ?? "",
      gstin: supplier.gstin ?? "",
      notes: supplier.notes ?? "",
      active: supplier.active,
    });
    setFormError(null);
    setDialogOpen(true);
  }

  function setField<K extends keyof typeof emptyForm>(key: K, value: (typeof emptyForm)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (form.name.trim().length < 2) {
      setFormError("Supplier name must be at least 2 characters.");
      return;
    }
    const payload: SupplierPayload = {
      name: form.name.trim(),
      contactPerson: form.contactPerson.trim() || null,
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      address: form.address.trim() || null,
      gstin: form.gstin.trim() || null,
      notes: form.notes.trim() || null,
    };
    setSaving(true);
    setFormError(null);
    try {
      const saved = editing ? await supplierApi.update(editing.id, payload) : await supplierApi.create(payload);
      if (editing && saved.active !== form.active) {
        await supplierApi.updateStatus(saved.id, form.active);
      }
      toast.success(editing ? "Supplier updated" : "Supplier added");
      setDialogOpen(false);
      await load();
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to save supplier");
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(supplier: Supplier) {
    try {
      await supplierApi.updateStatus(supplier.id, !supplier.active);
      toast.success(supplier.active ? "Supplier deactivated" : "Supplier activated");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to update status");
    }
  }

  const cards = [
    { title: "Total suppliers", value: summary?.totalSuppliers ?? "—" },
    { title: "Active", value: summary?.activeSuppliers ?? "—" },
    { title: "Inactive", value: summary?.inactiveSuppliers ?? "—" },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Suppliers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Vendors this shop buys from. Deactivate instead of deleting once a supplier has purchases.
          </p>
        </div>
        {canMutate ? (
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            Add supplier
          </Button>
        ) : null}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
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
            className="grid gap-3 md:grid-cols-[1fr_10rem_auto]"
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
                placeholder="Search name, contact, or phone"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search suppliers"
              />
            </div>
            <NativeSelect
              aria-label="Filter by status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | "true" | "false");
                setPage(1);
              }}
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
            </div>
          ) : error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">{emptyCopy}</div>
          ) : (
            <>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Supplier</TableHead>
                      <TableHead>Contact person</TableHead>
                      <TableHead>Phone</TableHead>
                      <TableHead>GSTIN</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((supplier) => (
                      <TableRow key={supplier.id}>
                        <TableCell className="font-medium">{supplier.name}</TableCell>
                        <TableCell>{supplier.contactPerson ?? "—"}</TableCell>
                        <TableCell>{supplier.phone ?? "—"}</TableCell>
                        <TableCell className="font-mono text-xs">{supplier.gstin ?? "—"}</TableCell>
                        <TableCell>
                          <Badge variant={supplier.active ? "secondary" : "outline"}>
                            {supplier.active ? "Active" : "Inactive"}
                          </Badge>
                        </TableCell>
                        <TableCell className="space-x-2 text-right">
                          <Button size="sm" variant="ghost" onClick={() => setDetail(supplier)}>
                            View
                          </Button>
                          {canMutate ? (
                            <>
                              <Button size="sm" variant="ghost" onClick={() => openEdit(supplier)}>
                                Edit
                              </Button>
                              <Button size="sm" variant="ghost" onClick={() => void toggleStatus(supplier)}>
                                {supplier.active ? "Deactivate" : "Activate"}
                              </Button>
                            </>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {items.map((supplier) => (
                  <div key={supplier.id} className="rounded-lg border p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{supplier.name}</p>
                        <p className="text-sm text-muted-foreground">{supplier.contactPerson ?? "No contact"}</p>
                        <p className="text-sm text-muted-foreground">{supplier.phone ?? "No phone"}</p>
                      </div>
                      <Badge variant={supplier.active ? "secondary" : "outline"}>
                        {supplier.active ? "Active" : "Inactive"}
                      </Badge>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Button size="sm" variant="outline" onClick={() => setDetail(supplier)}>
                        View
                      </Button>
                      {canMutate ? (
                        <>
                          <Button size="sm" variant="outline" onClick={() => openEdit(supplier)}>
                            Edit
                          </Button>
                          <Button size="sm" variant="outline" onClick={() => void toggleStatus(supplier)}>
                            {supplier.active ? "Deactivate" : "Activate"}
                          </Button>
                        </>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
              {totalPages > 1 ? (
                <div className="flex items-center justify-end gap-2">
                  <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    Page {page} of {totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </div>
              ) : null}
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit supplier" : "Add supplier"}</DialogTitle>
            <DialogDescription>Only the supplier name is required. GSTIN and phone help invoices later.</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="supplier-name">Name</Label>
              <Input id="supplier-name" value={form.name} onChange={(e) => setField("name", e.target.value)} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="supplier-contact">Contact person</Label>
                <Input
                  id="supplier-contact"
                  value={form.contactPerson}
                  onChange={(e) => setField("contactPerson", e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="supplier-phone">Phone</Label>
                <Input id="supplier-phone" value={form.phone} onChange={(e) => setField("phone", e.target.value)} />
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="supplier-email">Email</Label>
                <Input id="supplier-email" value={form.email} onChange={(e) => setField("email", e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="supplier-gstin">GSTIN</Label>
                <Input id="supplier-gstin" value={form.gstin} onChange={(e) => setField("gstin", e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="supplier-address">Address</Label>
              <Textarea
                id="supplier-address"
                value={form.address}
                onChange={(e) => setField("address", e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="supplier-notes">Notes</Label>
              <Textarea id="supplier-notes" value={form.notes} onChange={(e) => setField("notes", e.target.value)} />
            </div>
            {editing ? (
              <div className="flex items-center justify-between rounded-lg border px-3 py-2">
                <Label htmlFor="supplier-active">Active</Label>
                <Switch id="supplier-active" checked={form.active} onCheckedChange={(value) => setField("active", value)} />
              </div>
            ) : null}
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving}>
                {editing ? "Save supplier" : "Create supplier"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={detail != null} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{detail?.name}</DialogTitle>
            <DialogDescription>{detail?.active ? "Active supplier" : "Inactive supplier"}</DialogDescription>
          </DialogHeader>
          {detail ? (
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">Contact</dt>
                <dd>{detail.contactPerson ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Phone</dt>
                <dd>{detail.phone ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Email</dt>
                <dd>{detail.email ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">GSTIN</dt>
                <dd>{detail.gstin ?? "—"}</dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-muted-foreground">Address</dt>
                <dd>{detail.address ?? "—"}</dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-muted-foreground">Notes</dt>
                <dd>{detail.notes ?? "—"}</dd>
              </div>
            </dl>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
