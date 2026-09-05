import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  ApiRequestError,
  usersApi,
  type MembershipStatus,
  type StaffRole,
  type TenantUser,
} from "@/lib/api";
import { cn } from "@/lib/utils";

const emptyAdd = {
  fullName: "",
  email: "",
  role: "MANAGER" as StaffRole,
  temporaryPassword: "",
  confirmPassword: "",
};

function roleBadgeClass(role: TenantUser["role"]) {
  if (role === "OWNER") return "border-primary/30 bg-primary/10 text-primary";
  if (role === "MANAGER") return "border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200";
  return "border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100";
}

function formatJoined(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function UsersPage() {
  const { user, loading: authLoading } = useAuth();
  const isOwner = user?.role === "OWNER";
  const [items, setItems] = useState<TenantUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [addForm, setAddForm] = useState(emptyAdd);
  const [addError, setAddError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [target, setTarget] = useState<TenantUser | null>(null);
  const [confirmKind, setConfirmKind] = useState<"deactivate" | "reactivate" | "role" | "password" | null>(null);
  const [nextRole, setNextRole] = useState<StaffRole>("CASHIER");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  async function load() {
    if (authLoading) {
      return;
    }
    if (!isOwner) {
      setDenied(true);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const list = await usersApi.list();
      setItems(list);
      setDenied(false);
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 403) {
        setDenied(true);
        return;
      }
      setError(err instanceof ApiRequestError ? err.message : "Unable to load users");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOwner, authLoading]);

  function closeAction() {
    setConfirmKind(null);
    setTarget(null);
    setPassword("");
    setConfirmPassword("");
    setActionError(null);
  }

  async function onAdd(event: FormEvent) {
    event.preventDefault();
    setAddError(null);
    if (addForm.fullName.trim().length < 2) {
      setAddError("Full name is required.");
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(addForm.email.trim())) {
      setAddError("Enter a valid email address.");
      return;
    }
    if (addForm.temporaryPassword.length < 8 || addForm.temporaryPassword.length > 72) {
      setAddError("Temporary password must be 8–72 characters.");
      return;
    }
    if (addForm.temporaryPassword !== addForm.confirmPassword) {
      setAddError("Passwords do not match.");
      return;
    }
    setSaving(true);
    try {
      const created = await usersApi.create({
        fullName: addForm.fullName.trim(),
        email: addForm.email.trim(),
        role: addForm.role,
        temporaryPassword: addForm.temporaryPassword,
      });
      setItems((current) =>
        [...current, created].sort((a, b) => a.fullName.localeCompare(b.fullName)),
      );
      setAddOpen(false);
      setAddForm(emptyAdd);
      toast.success(`${created.fullName} added as ${created.role}`);
    } catch (err) {
      setAddError(err instanceof ApiRequestError ? err.message : "Unable to create user");
    } finally {
      setSaving(false);
    }
  }

  async function confirmAction() {
    if (!target || !confirmKind) return;
    setActionError(null);
    setSaving(true);
    try {
      if (confirmKind === "role") {
        const updated = await usersApi.changeRole(target.id, nextRole);
        setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)));
        toast.success(`Role updated to ${updated.role}`);
      } else if (confirmKind === "deactivate" || confirmKind === "reactivate") {
        const status: MembershipStatus = confirmKind === "deactivate" ? "INACTIVE" : "ACTIVE";
        const updated = await usersApi.changeStatus(target.id, status);
        setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)));
        toast.success(status === "INACTIVE" ? "User deactivated" : "User reactivated");
      } else {
        if (password.length < 8 || password.length > 72) {
          setActionError("Temporary password must be 8–72 characters.");
          setSaving(false);
          return;
        }
        if (password !== confirmPassword) {
          setActionError("Passwords do not match.");
          setSaving(false);
          return;
        }
        await usersApi.resetPassword(target.id, password);
        toast.success("Temporary password saved");
      }
      closeAction();
    } catch (err) {
      setActionError(err instanceof ApiRequestError ? err.message : "Unable to update user");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (denied) {
    return (
      <div className="mx-auto max-w-lg space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">Access denied</h1>
        <p className="text-sm text-muted-foreground">
          Only the business owner can manage users and roles. Ask the owner if you need access for someone on the team.
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-3">
        <h1 className="text-2xl font-semibold tracking-tight">Users & Roles</h1>
        <p className="text-sm text-destructive">{error}</p>
        <Button variant="outline" onClick={() => void load()}>
          Try again
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Users & Roles</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage who can access this business and what they can do.
          </p>
        </div>
        <Button onClick={() => setAddOpen(true)}>+ Add User</Button>
      </div>

      <div className="overflow-x-auto rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Joined</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <UserRow
                key={item.id}
                item={item}
                onRole={() => {
                  setTarget(item);
                  setNextRole(item.role === "MANAGER" ? "CASHIER" : "MANAGER");
                  setConfirmKind("role");
                }}
                onPassword={() => {
                  setTarget(item);
                  setConfirmKind("password");
                }}
                onStatus={() => {
                  setTarget(item);
                  setConfirmKind(item.status === "ACTIVE" ? "deactivate" : "reactivate");
                }}
              />
            ))}
          </TableBody>
        </Table>
      </div>

      <Dialog
        open={addOpen}
        onOpenChange={(open) => {
          setAddOpen(open);
          if (!open) {
            setAddForm(emptyAdd);
            setAddError(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add User</DialogTitle>
            <DialogDescription>Create a manager or cashier with a temporary password. They can sign in immediately.</DialogDescription>
          </DialogHeader>
          <form className="space-y-3" onSubmit={onAdd}>
            <div className="space-y-2">
              <Label htmlFor="user-full-name">Full Name</Label>
              <Input
                id="user-full-name"
                required
                value={addForm.fullName}
                onChange={(e) => setAddForm({ ...addForm, fullName: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="user-email">Email</Label>
              <Input
                id="user-email"
                type="email"
                required
                value={addForm.email}
                onChange={(e) => setAddForm({ ...addForm, email: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="user-role">Role</Label>
              <NativeSelect
                id="user-role"
                aria-label="Role"
                value={addForm.role}
                onChange={(e) => setAddForm({ ...addForm, role: e.target.value as StaffRole })}
              >
                <option value="MANAGER">MANAGER</option>
                <option value="CASHIER">CASHIER</option>
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label htmlFor="user-password">Temporary Password</Label>
              <Input
                id="user-password"
                type="password"
                required
                minLength={8}
                maxLength={72}
                value={addForm.temporaryPassword}
                onChange={(e) => setAddForm({ ...addForm, temporaryPassword: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="user-password-confirm">Confirm password</Label>
              <Input
                id="user-password-confirm"
                type="password"
                required
                value={addForm.confirmPassword}
                onChange={(e) => setAddForm({ ...addForm, confirmPassword: e.target.value })}
              />
            </div>
            {addError ? <p className="text-sm text-destructive">{addError}</p> : null}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setAddOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? "Creating..." : "Create user"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmKind != null} onOpenChange={(open) => !open && closeAction()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {confirmKind === "deactivate"
                ? "Deactivate user"
                : confirmKind === "reactivate"
                  ? "Reactivate user"
                  : confirmKind === "role"
                    ? "Change role"
                    : "Reset password"}
            </DialogTitle>
            <DialogDescription>
              {confirmKind === "deactivate"
                ? "Deactivate this user? They will no longer be able to sign in or access this business."
                : confirmKind === "reactivate"
                  ? "Reactivate this user? They will be able to sign in again with their current role."
                  : confirmKind === "role"
                    ? `Change ${target?.fullName ?? "this user"} from ${target?.role} to ${nextRole}?`
                    : `Set a new temporary password for ${target?.fullName ?? "this user"}.`}
            </DialogDescription>
          </DialogHeader>
          {confirmKind === "password" ? (
            <div className="space-y-3">
              <div className="space-y-2">
                <Label htmlFor="reset-password">Temporary password</Label>
                <Input
                  id="reset-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reset-password-confirm">Confirm password</Label>
                <Input
                  id="reset-password-confirm"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                />
              </div>
            </div>
          ) : null}
          {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={closeAction}>
              Cancel
            </Button>
            <Button
              variant={confirmKind === "deactivate" ? "destructive" : "default"}
              disabled={saving}
              onClick={() => void confirmAction()}
            >
              {confirmKind === "deactivate"
                ? "Deactivate"
                : confirmKind === "reactivate"
                  ? "Reactivate"
                  : confirmKind === "role"
                    ? "Change role"
                    : "Save password"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function UserRow({
  item,
  onRole,
  onPassword,
  onStatus,
}: {
  item: TenantUser;
  onRole: () => void;
  onPassword: () => void;
  onStatus: () => void;
}) {
  const inactive = item.status === "INACTIVE";
  return (
    <TableRow className={cn(inactive && "text-muted-foreground")}>
      <TableCell className="font-medium">{item.fullName}</TableCell>
      <TableCell>{item.email}</TableCell>
      <TableCell>
        <Badge className={roleBadgeClass(item.role)}>{item.role}</Badge>
      </TableCell>
      <TableCell>{item.status}</TableCell>
      <TableCell>{formatJoined(item.createdAt)}</TableCell>
      <TableCell className="text-right">
        {item.role === "OWNER" ? (
          <span className="text-xs text-muted-foreground">Owner</span>
        ) : (
          <div className="flex justify-end gap-2">
            <Button size="sm" variant="outline" onClick={onRole}>
              Change Role
            </Button>
            <Button size="sm" variant="outline" onClick={onPassword}>
              Reset Password
            </Button>
            <Button size="sm" variant={inactive ? "outline" : "destructive"} onClick={onStatus}>
              {inactive ? "Activate" : "Deactivate"}
            </Button>
          </div>
        )}
      </TableCell>
    </TableRow>
  );
}
