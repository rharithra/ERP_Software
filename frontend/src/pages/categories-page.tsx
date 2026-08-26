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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, categoryApi, type Category } from "@/lib/api";

export function CategoriesPage() {
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [items, setItems] = useState<Category[]>([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"all" | "true" | "false">("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const activeFilter = status === "all" ? undefined : status === "true";

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const page = await categoryApi.list({ q: query || undefined, active: activeFilter, page: 1, size: 50 });
      setItems(page.items);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load categories");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  const emptyCopy = useMemo(() => {
    if (query || status !== "all") {
      return "No categories match this search.";
    }
    return "Add your first category — Dairy, Snacks, Garments — so products have a home.";
  }, [query, status]);

  function openCreate() {
    setEditing(null);
    setName("");
    setDescription("");
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(category: Category) {
    setEditing(category);
    setName(category.name);
    setDescription(category.description ?? "");
    setFormError(null);
    setDialogOpen(true);
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (name.trim().length < 2) {
      setFormError("Category name must be at least 2 characters.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const payload = { name: name.trim(), description: description.trim() || null };
      if (editing) {
        await categoryApi.update(editing.id, payload);
        toast.success("Category updated");
      } else {
        await categoryApi.create(payload);
        toast.success("Category added");
      }
      setDialogOpen(false);
      await load();
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to save category");
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(category: Category) {
    try {
      await categoryApi.updateStatus(category.id, !category.active);
      toast.success(category.active ? "Category deactivated" : "Category activated");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to update status");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Categories</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Group the goods you sell. Categories stay inside your store and never mix with another tenant.
          </p>
        </div>
        {canMutate ? (
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4" />
            Add category
          </Button>
        ) : null}
      </div>

      <Card>
        <CardContent className="space-y-4 pt-6">
          <form
            className="flex flex-col gap-3 sm:flex-row"
            onSubmit={(event) => {
              event.preventDefault();
              void load();
            }}
          >
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="Search categories"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search categories"
              />
            </div>
            <NativeSelect
              aria-label="Filter by status"
              value={status}
              onChange={(e) => setStatus(e.target.value as "all" | "true" | "false")}
              className="sm:w-40"
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
                      <TableHead>Category</TableHead>
                      <TableHead>Description</TableHead>
                      <TableHead>Status</TableHead>
                      {canMutate ? <TableHead className="text-right">Actions</TableHead> : null}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((category) => (
                      <TableRow key={category.id}>
                        <TableCell className="font-medium">{category.name}</TableCell>
                        <TableCell className="max-w-md text-muted-foreground">{category.description || "—"}</TableCell>
                        <TableCell>
                          <Badge className={category.active ? "border-primary/30 bg-primary/10 text-primary" : ""}>
                            {category.active ? "Active" : "Inactive"}
                          </Badge>
                        </TableCell>
                        {canMutate ? (
                          <TableCell className="space-x-2 text-right">
                            <Button size="sm" variant="outline" onClick={() => openEdit(category)}>
                              Edit
                            </Button>
                            <Button size="sm" variant="ghost" onClick={() => void toggleStatus(category)}>
                              {category.active ? "Deactivate" : "Activate"}
                            </Button>
                          </TableCell>
                        ) : null}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {items.map((category) => (
                  <div key={category.id} className="rounded-lg border p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{category.name}</p>
                        <p className="mt-1 text-sm text-muted-foreground">{category.description || "No description"}</p>
                      </div>
                      <Badge className={category.active ? "border-primary/30 bg-primary/10 text-primary" : ""}>
                        {category.active ? "Active" : "Inactive"}
                      </Badge>
                    </div>
                    {canMutate ? (
                      <div className="mt-3 flex gap-2">
                        <Button size="sm" variant="outline" onClick={() => openEdit(category)}>
                          Edit
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => void toggleStatus(category)}>
                          {category.active ? "Deactivate" : "Activate"}
                        </Button>
                      </div>
                    ) : null}
                  </div>
                ))}
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? "Edit category" : "Add category"}</DialogTitle>
            <DialogDescription>Name is unique inside your store. Other shops can reuse the same name.</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="category-name">Name</Label>
              <Input
                id="category-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                minLength={2}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="category-description">Description</Label>
              <Textarea
                id="category-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
              />
            </div>
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            <Button type="submit" disabled={saving}>
              {saving ? "Saving..." : editing ? "Save changes" : "Create category"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
