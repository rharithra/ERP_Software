import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import { NavList } from "@/components/layout/nav-config";
import { ApiRequestError, authApi, storeToken, usersApi, type TenantUser } from "@/lib/api";
import { UsersPage } from "@/pages/users-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn() },
    usersApi: {
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      changeRole: vi.fn(),
      changeStatus: vi.fn(),
      resetPassword: vi.fn(),
    },
  };
});

const owner = {
  id: "owner-1",
  email: "owner@shop.test",
  fullName: "Harithra",
  role: "OWNER" as const,
  tenant: {
    id: "t1",
    name: "Shop",
    currency: "INR",
    timezone: "Asia/Kolkata",
    businessType: "GROCERY_SUPERMARKET" as const,
    salesMode: "HYBRID" as const,
  },
};

const users: TenantUser[] = [
  {
    id: "owner-1",
    fullName: "Harithra",
    email: "owner@shop.test",
    role: "OWNER",
    status: "ACTIVE",
    createdAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "mgr-1",
    fullName: "Meena Iyer",
    email: "meena@shop.test",
    role: "MANAGER",
    status: "ACTIVE",
    createdAt: "2026-02-01T00:00:00Z",
  },
  {
    id: "cash-1",
    fullName: "Ravi Cash",
    email: "ravi@shop.test",
    role: "CASHIER",
    status: "INACTIVE",
    createdAt: "2026-03-01T00:00:00Z",
  },
];

function wrap(ui: ReactElement) {
  return (
    <AuthProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </AuthProvider>
  );
}

describe("Users & Roles navigation", () => {
  beforeEach(() => {
    localStorage.clear();
    storeToken("token");
  });

  it("shows Users & Roles for OWNER", async () => {
    vi.mocked(authApi.me).mockResolvedValue(owner);
    render(wrap(<NavList />));
    expect(await screen.findByText("Users & Roles")).toBeInTheDocument();
  });

  it("hides Users & Roles for MANAGER", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...owner, role: "MANAGER", id: "mgr-1" });
    render(wrap(<NavList />));
    expect(await screen.findByText("Settings")).toBeInTheDocument();
    expect(screen.queryByText("Users & Roles")).not.toBeInTheDocument();
  });

  it("hides Users & Roles for CASHIER", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...owner, role: "CASHIER", id: "cash-1" });
    render(wrap(<NavList />));
    expect(await screen.findByText("Settings")).toBeInTheDocument();
    expect(screen.queryByText("Users & Roles")).not.toBeInTheDocument();
  });
});

describe("Users page", () => {
  beforeEach(() => {
    localStorage.clear();
    storeToken("token");
    vi.mocked(authApi.me).mockResolvedValue(owner);
    vi.mocked(usersApi.list).mockReset();
    vi.mocked(usersApi.list).mockResolvedValue(users);
    vi.mocked(usersApi.create).mockReset();
    vi.mocked(usersApi.changeRole).mockReset();
    vi.mocked(usersApi.changeStatus).mockReset();
    vi.mocked(usersApi.resetPassword).mockReset();
  });

  it("renders the user list with roles and statuses", async () => {
    render(wrap(<UsersPage />));
    expect(await screen.findByRole("heading", { name: "Users & Roles" })).toBeInTheDocument();
    expect(screen.getByText("Manage who can access this business and what they can do.")).toBeInTheDocument();
    expect(screen.getByText("meena@shop.test")).toBeInTheDocument();
    expect(screen.getByText("INACTIVE")).toBeInTheDocument();
    expect(screen.getAllByText("OWNER").length).toBeGreaterThan(0);
  });

  it("validates the add user form", async () => {
    const user = userEvent.setup();
    render(wrap(<UsersPage />));
    await screen.findByRole("button", { name: "+ Add User" });
    await user.click(screen.getByRole("button", { name: "+ Add User" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText("Role")).toHaveValue("MANAGER");
    await user.selectOptions(within(dialog).getByLabelText("Role"), "CASHIER");
    await user.type(within(dialog).getByLabelText("Full Name"), "New Cash");
    await user.type(within(dialog).getByLabelText("Email"), "newcash@shop.test");
    await user.type(within(dialog).getByLabelText("Temporary Password"), "TempPass99");
    await user.type(within(dialog).getByLabelText("Confirm password"), "Different1");
    await user.click(within(dialog).getByRole("button", { name: "Create user" }));
    expect(usersApi.create).not.toHaveBeenCalled();
    expect(await within(dialog).findByText("Passwords do not match.")).toBeInTheDocument();
  });

  it("creates a user after a valid submit", async () => {
    vi.mocked(usersApi.create).mockResolvedValue({
      id: "new-1",
      fullName: "Kiran Rao",
      email: "kiran@shop.test",
      role: "CASHIER",
      status: "ACTIVE",
      createdAt: "2026-04-01T00:00:00Z",
    });
    const user = userEvent.setup();
    render(wrap(<UsersPage />));
    await user.click(await screen.findByRole("button", { name: "+ Add User" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Full Name"), "Kiran Rao");
    await user.type(within(dialog).getByLabelText("Email"), "kiran@shop.test");
    await user.selectOptions(within(dialog).getByLabelText("Role"), "CASHIER");
    await user.type(within(dialog).getByLabelText("Temporary Password"), "TempPass99");
    await user.type(within(dialog).getByLabelText("Confirm password"), "TempPass99");
    await user.click(within(dialog).getByRole("button", { name: "Create user" }));
    await waitFor(() =>
      expect(usersApi.create).toHaveBeenCalledWith({
        fullName: "Kiran Rao",
        email: "kiran@shop.test",
        role: "CASHIER",
        temporaryPassword: "TempPass99",
      }),
    );
    expect(await screen.findByText("kiran@shop.test")).toBeInTheDocument();
  });

  it("asks to confirm deactivation", async () => {
    vi.mocked(usersApi.changeStatus).mockResolvedValue({ ...users[1], status: "INACTIVE" });
    const user = userEvent.setup();
    render(wrap(<UsersPage />));
    await screen.findByText("Meena Iyer");
    await user.click(screen.getByRole("button", { name: "Deactivate" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText(
        "Deactivate this user? They will no longer be able to sign in or access this business.",
      ),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Deactivate" }));
    await waitFor(() => expect(usersApi.changeStatus).toHaveBeenCalledWith("mgr-1", "INACTIVE"));
  });

  it("asks to confirm reactivation", async () => {
    vi.mocked(usersApi.changeStatus).mockResolvedValue({ ...users[2], status: "ACTIVE" });
    const user = userEvent.setup();
    render(wrap(<UsersPage />));
    await screen.findByText("Ravi Cash");
    await user.click(screen.getByRole("button", { name: "Activate" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Reactivate this user/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Reactivate" }));
    await waitFor(() => expect(usersApi.changeStatus).toHaveBeenCalledWith("cash-1", "ACTIVE"));
  });

  it("confirms a role change", async () => {
    vi.mocked(usersApi.changeRole).mockResolvedValue({ ...users[1], role: "CASHIER" });
    const user = userEvent.setup();
    render(wrap(<UsersPage />));
    await screen.findByText("Meena Iyer");
    await user.click(screen.getAllByRole("button", { name: "Change Role" })[0]);
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/MANAGER to CASHIER/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Change role" }));
    await waitFor(() => expect(usersApi.changeRole).toHaveBeenCalledWith("mgr-1", "CASHIER"));
  });

  it("resets a password after confirmation", async () => {
    vi.mocked(usersApi.resetPassword).mockResolvedValue(null);
    const user = userEvent.setup();
    render(wrap(<UsersPage />));
    await screen.findByText("Meena Iyer");
    await user.click(screen.getAllByRole("button", { name: "Reset Password" })[0]);
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Temporary password"), "NewTemp99");
    await user.type(within(dialog).getByLabelText("Confirm password"), "NewTemp99");
    await user.click(within(dialog).getByRole("button", { name: "Save password" }));
    await waitFor(() => expect(usersApi.resetPassword).toHaveBeenCalledWith("mgr-1", "NewTemp99"));
  });

  it("shows access denied for managers", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...owner, role: "MANAGER", id: "mgr-1" });
    render(wrap(<UsersPage />));
    expect(await screen.findByRole("heading", { name: "Access denied" })).toBeInTheDocument();
    expect(usersApi.list).not.toHaveBeenCalled();
  });

  it("shows access denied when the API returns 403", async () => {
    vi.mocked(usersApi.list).mockRejectedValue(new ApiRequestError(403, "ACCESS_DENIED", "Forbidden"));
    render(wrap(<UsersPage />));
    expect(await screen.findByRole("heading", { name: "Access denied" })).toBeInTheDocument();
  });

  it("shows an error state when listing fails", async () => {
    vi.mocked(usersApi.list).mockRejectedValue(new ApiRequestError(500, "INTERNAL_ERROR", "Server down"));
    render(wrap(<UsersPage />));
    expect(await screen.findByText("Server down")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});
