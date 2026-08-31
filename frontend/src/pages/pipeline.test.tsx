import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import { NavList, navItemsFor } from "@/components/layout/nav-config";
import { RecordPaymentDialog } from "@/components/payments/record-payment-dialog";
import { ApiRequestError, authApi, customerApi, leadApi, paymentApi, productApi } from "@/lib/api";
import { QuotationFormPage } from "@/pages/quotation-form-page";
import { LeadsPage } from "@/pages/leads-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn() },
    customerApi: { active: vi.fn(), list: vi.fn() },
    productApi: { list: vi.fn() },
    leadApi: {
      list: vi.fn(),
      board: vi.fn(),
      get: vi.fn(),
      timeline: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      changeStatus: vi.fn(),
      convertCustomer: vi.fn(),
    },
    quotationApi: { create: vi.fn(), send: vi.fn() },
    paymentApi: { create: vi.fn() },
  };
});

const owner = {
  id: "u1",
  email: "owner@shop.test",
  fullName: "Ananya Sharma",
  role: "OWNER" as const,
  tenant: {
    id: "t1",
    name: "Aqua Dealers",
    currency: "INR",
    timezone: "Asia/Kolkata",
    businessType: "APPLIANCES_WATER_PURIFIER" as const,
    salesMode: "PIPELINE" as const,
  },
};

const purifier = {
  id: "p1",
  name: "Kent Grand",
  sku: "KENT-GRAND",
  barcode: null,
  description: null,
  categoryId: "c1",
  categoryName: "Purifiers",
  costPrice: "30000",
  sellingPrice: "40000",
  gstRate: "18",
  unit: "PCS",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function wrap(ui: ReactElement) {
  return (
    <AuthProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </AuthProvider>
  );
}

describe("pipeline navigation", () => {
  it("includes pipeline routes for pipeline and hybrid modes only", () => {
    expect(navItemsFor("QUICK_SALE").some((item) => item.label === "Leads")).toBe(false);
    expect(navItemsFor("PIPELINE").map((item) => item.label)).toEqual(
      expect.arrayContaining(["Pipeline", "Leads", "Follow-ups", "Quotations", "Sales orders", "Sales"]),
    );
    expect(navItemsFor("HYBRID").map((item) => item.label)).toEqual(
      expect.arrayContaining(["Leads", "POS / Sales"]),
    );
  });

  it("hides CRM nav for cashiers even in hybrid", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...owner, role: "CASHIER", tenant: { ...owner.tenant, salesMode: "HYBRID" } });
    localStorage.setItem("retailflow.accessToken", "token");
    render(wrap(<NavList />));
    expect(await screen.findByText("POS / Sales")).toBeInTheDocument();
    expect(screen.queryByText("Leads")).not.toBeInTheDocument();
  });
});

describe("leads authorization", () => {
  beforeEach(() => {
    localStorage.setItem("retailflow.accessToken", "token");
    vi.mocked(authApi.me).mockResolvedValue(owner);
  });

  it("shows the API error when the cashier is forbidden", async () => {
    vi.mocked(authApi.me).mockResolvedValue({ ...owner, role: "CASHIER" });
    vi.mocked(leadApi.list).mockRejectedValue(new ApiRequestError(403, "FORBIDDEN", "You are not allowed to manage leads"));
    render(wrap(<LeadsPage />));
    expect(await screen.findByText("You are not allowed to manage leads")).toBeInTheDocument();
  });
});

describe("quotation GST snapshot UI", () => {
  beforeEach(() => {
    localStorage.setItem("retailflow.accessToken", "token");
    vi.mocked(authApi.me).mockResolvedValue(owner);
    vi.mocked(customerApi.active).mockResolvedValue([
      {
        id: "cust1",
        name: "Ravi Kumar",
        phone: "9000000000",
        email: null,
        address: null,
        gstin: null,
        notes: null,
        active: true,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      },
    ]);
    vi.mocked(leadApi.list).mockResolvedValue({ items: [], page: 1, size: 100, totalItems: 0, totalPages: 0 });
    vi.mocked(productApi.list).mockResolvedValue({
      items: [purifier],
      page: 1,
      size: 100,
      totalItems: 1,
      totalPages: 1,
    });
  });

  it("fills GST from the product and shows tax on ₹40,000 at 18%", async () => {
    render(wrap(<QuotationFormPage />));
    const user = userEvent.setup();
    await screen.findByLabelText("Customer");
    await user.selectOptions(screen.getByLabelText("Product"), "p1");
    expect(await screen.findByTestId("quote-tax")).toHaveTextContent("₹7,200.00");
    expect(screen.getByTestId("quote-grand")).toHaveTextContent("₹47,200.00");
  });
});

describe("record payment dialog", () => {
  it("previews remaining balance and rejects overpayment client-side", async () => {
    const onRecorded = vi.fn();
    render(
      <RecordPaymentDialog
        open
        onOpenChange={() => undefined}
        total={100000}
        paid={20000}
        saleId="s1"
        onRecorded={onRecorded}
      />,
    );
    expect(screen.getByText("Invoice / order total")).toBeInTheDocument();
    expect(screen.getAllByText("₹20,000.00").length).toBeGreaterThan(0);
    const user = userEvent.setup();
    const amount = screen.getByLabelText("Amount");
    await user.clear(amount);
    await user.type(amount, "90000");
    await user.click(screen.getByRole("button", { name: "Record payment" }));
    expect(screen.getByText("Payment cannot exceed the outstanding amount.")).toBeInTheDocument();
    expect(paymentApi.create).not.toHaveBeenCalled();
  });
});
