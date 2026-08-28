import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import { NavList, navItemsFor } from "@/components/layout/nav-config";
import { authApi, storeToken, tenantApi } from "@/lib/api";
import { recommendedSalesMode } from "@/lib/sales-experience";
import { SettingsPage } from "@/pages/settings-page";
import { SignupPage } from "@/pages/signup-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn() },
    tenantApi: { get: vi.fn(), update: vi.fn() },
  };
});

const profile = {
  id: "t1",
  name: "Shop",
  legalName: null,
  gstin: null,
  phone: null,
  email: null,
  addressLine1: null,
  addressLine2: null,
  city: null,
  state: null,
  pincode: null,
  currency: "INR",
  timezone: "Asia/Kolkata",
  businessType: "GROCERY_SUPERMARKET" as const,
  salesMode: "QUICK_SALE" as const,
};

const owner = {
  id: "u1",
  email: "owner@shop.test",
  fullName: "Ananya Sharma",
  role: "OWNER" as const,
  tenant: {
    id: "t1",
    name: "Shop",
    currency: "INR",
    timezone: "Asia/Kolkata",
    businessType: "GROCERY_SUPERMARKET" as const,
    salesMode: "QUICK_SALE" as const,
  },
};

describe("sales experience mapping", () => {
  it("recommends a mode for each business type", () => {
    expect(recommendedSalesMode("GROCERY_SUPERMARKET")).toBe("QUICK_SALE");
    expect(recommendedSalesMode("ELECTRONICS_COMPUTER")).toBe("HYBRID");
    expect(recommendedSalesMode("MOBILE_ACCESSORIES")).toBe("HYBRID");
    expect(recommendedSalesMode("APPLIANCES_WATER_PURIFIER")).toBe("PIPELINE");
    expect(recommendedSalesMode("FURNITURE")).toBe("PIPELINE");
    expect(recommendedSalesMode("HARDWARE_BUILDING_MATERIALS")).toBe("HYBRID");
    expect(recommendedSalesMode("OTHER")).toBe("HYBRID");
  });

  it("hides pipeline navigation for quick sale and shows coming-soon items for pipeline/hybrid", () => {
    const quick = navItemsFor("QUICK_SALE").map((item) => item.label);
    expect(quick).toContain("POS / Sales");
    expect(quick).not.toContain("Leads");
    expect(quick).toContain("Settings");

    const pipeline = navItemsFor("PIPELINE").map((item) => item.label);
    expect(pipeline).toContain("Leads");
    expect(pipeline).toContain("Quotations");
    expect(pipeline).toContain("Sales");

    const hybrid = navItemsFor("HYBRID").map((item) => item.label);
    expect(hybrid).toContain("Leads");
    expect(hybrid).toContain("POS / Sales");
  });
});

describe("signup business profile", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.signup).mockReset();
  });

  it("updates the recommended sales mode until the owner overrides it", async () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <SignupPage />
        </MemoryRouter>
      </AuthProvider>,
    );
    const user = userEvent.setup();
    expect(screen.getByRole("radio", { name: /Quick Sale/ })).toBeChecked();
    await user.selectOptions(screen.getByLabelText("Business type"), "FURNITURE");
    expect(screen.getByRole("radio", { name: /Sales Pipeline/ })).toBeChecked();
    await user.click(screen.getByRole("radio", { name: /Hybrid/ }));
    await user.selectOptions(screen.getByLabelText("Business type"), "GROCERY_SUPERMARKET");
    expect(screen.getByRole("radio", { name: /Hybrid/ })).toBeChecked();
  });
});

describe("settings business profile", () => {
  beforeEach(() => {
    localStorage.clear();
    storeToken("owner-token");
    vi.mocked(authApi.me).mockResolvedValue(owner);
    vi.mocked(tenantApi.get).mockResolvedValue(profile);
    vi.mocked(tenantApi.update).mockReset();
  });

  it("lets the owner change sales mode after confirmation and refreshes auth", async () => {
    vi.mocked(tenantApi.update).mockResolvedValue({ ...profile, salesMode: "HYBRID", businessType: "GROCERY_SUPERMARKET" });
    vi.mocked(authApi.me).mockResolvedValue({
      ...owner,
      tenant: { ...owner.tenant, salesMode: "HYBRID" },
    });
    render(
      <AuthProvider>
        <MemoryRouter>
          <SettingsPage />
        </MemoryRouter>
      </AuthProvider>,
    );
    const user = userEvent.setup();
    await screen.findByText("Business profile");
    await user.selectOptions(screen.getByLabelText("Sales experience"), "HYBRID");
    await user.click(screen.getByRole("button", { name: "Save business profile" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Change sales experience" }));
    await waitFor(() => expect(tenantApi.update).toHaveBeenCalled());
    expect(tenantApi.update).toHaveBeenCalledWith(expect.objectContaining({ salesMode: "HYBRID" }));
    await waitFor(() => expect(authApi.me).toHaveBeenCalled());
  });

  it("hides save for cashiers", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...owner, role: "CASHIER" });
    render(
      <AuthProvider>
        <MemoryRouter>
          <SettingsPage />
        </MemoryRouter>
      </AuthProvider>,
    );
    await screen.findByText("Business profile");
    expect(screen.queryByRole("button", { name: "Save business profile" })).not.toBeInTheDocument();
    expect(screen.getByText(/Only the owner can change/)).toBeInTheDocument();
  });
});

describe("navigation uses tenant sales mode", () => {
  it("does not show leads for a quick-sale tenant", async () => {
    vi.mocked(authApi.me).mockResolvedValue(owner);
    localStorage.setItem("retailflow.accessToken", "token");
    render(
      <AuthProvider>
        <MemoryRouter>
          <NavList />
        </MemoryRouter>
      </AuthProvider>,
    );
    expect(await screen.findByText("POS / Sales")).toBeInTheDocument();
    expect(screen.queryByText("Leads")).not.toBeInTheDocument();
  });

  it("shows coming-soon pipeline items for hybrid tenants", async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      ...owner,
      tenant: { ...owner.tenant, businessType: "ELECTRONICS_COMPUTER", salesMode: "HYBRID" },
    });
    localStorage.setItem("retailflow.accessToken", "token");
    render(
      <AuthProvider>
        <MemoryRouter>
          <NavList />
        </MemoryRouter>
      </AuthProvider>,
    );
    expect(await screen.findByText("Leads")).toBeInTheDocument();
    expect(screen.getByText("Quotations")).toBeInTheDocument();
    expect(screen.getByText("POS / Sales")).toBeInTheDocument();
  });
});
