import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import {
  ApiRequestError,
  authApi,
  customerApi,
  inventoryApi,
  productApi,
  saleApi,
  storeToken,
} from "@/lib/api";
import { lineTotals, saleTotals } from "@/lib/sale-math";
import { CustomersPage } from "@/pages/customers-page";
import { SaleInvoicePage } from "@/pages/sale-invoice-page";
import { SalePosPage } from "@/pages/sale-pos-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn() },
    customerApi: {
      list: vi.fn(),
      summary: vi.fn(),
      active: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    },
    saleApi: {
      list: vi.fn(),
      summary: vi.fn(),
      dashboard: vi.fn(),
      get: vi.fn(),
      invoice: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      complete: vi.fn(),
      cancel: vi.fn(),
    },
    productApi: { list: vi.fn() },
    inventoryApi: { list: vi.fn() },
  };
});

const cola = {
  id: "33333333-3333-3333-3333-333333333333",
  name: "Coca Cola 750ml",
  sku: "COLA-750",
  barcode: "8901234567890",
  description: null,
  categoryId: "c1",
  categoryName: "Drinks",
  costPrice: "30.00",
  sellingPrice: "40.00",
  gstRate: "28",
  unit: "ML",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const customer = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Meena Iyer",
  phone: "9876500001",
  email: "meena@example.com",
  address: null,
  gstin: null,
  notes: null,
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const completedSale = {
  id: "55555555-5555-5555-5555-555555555555",
  saleNumber: "SAL-000001",
  invoiceNumber: "INV-000001",
  customerId: customer.id,
  customerName: "Meena Iyer",
  customerPhone: "9876500001",
  customerGstin: null,
  saleDate: "2026-08-28",
  status: "COMPLETED" as const,
  subtotal: "120.00",
  discount: "0.00",
  taxTotal: "33.60",
  grandTotal: "153.60",
  paymentMethod: "CASH" as const,
  paymentStatus: "PAID" as const,
  notes: null,
  completedAt: "2026-08-28T10:00:00Z",
  createdBy: "u1",
  createdByName: "Ananya Sharma",
  createdAt: "2026-08-28T10:00:00Z",
  updatedAt: "2026-08-28T10:00:00Z",
  itemCount: 1,
  items: [
    {
      id: "i1",
      productId: cola.id,
      productName: "Coca Cola 750ml",
      sku: "COLA-750",
      unit: "ML",
      quantity: "3",
      unitPrice: "40.00",
      gstRate: "28",
      discount: "0.00",
      taxableAmount: "120.00",
      taxAmount: "33.60",
      lineTotal: "153.60",
    },
  ],
};

function pageOf<T>(items: T[]) {
  return { items, page: 1, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0 };
}

function renderPage(ui: ReactElement, path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/app/customers" element={<CustomersPage />} />
          <Route path="/app/sales/new" element={<SalePosPage />} />
          <Route path="/app/sales/:id/invoice" element={<SaleInvoicePage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe("sale math", () => {
  it("calculates GST line and sale totals with a sale-level discount", () => {
    expect(lineTotals(3, 40, 28)).toEqual({ taxable: 120, tax: 33.6, total: 153.6 });
    expect(saleTotals([{ quantity: 10, unitPrice: 20, gstRate: 5 }, { quantity: 5, unitPrice: 80, gstRate: 5 }], 50)).toEqual({
      subtotal: 600,
      discount: 50,
      taxTotal: 30,
      grandTotal: 580,
    });
  });
});

describe("Customers page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
    vi.mocked(customerApi.list).mockReset();
    vi.mocked(customerApi.summary).mockReset();
    vi.mocked(customerApi.create).mockReset();
  });

  it("validates customer name before calling the API", async () => {
    vi.mocked(customerApi.list).mockResolvedValue(pageOf([]));
    vi.mocked(customerApi.summary).mockResolvedValue({ totalCustomers: 0, activeCustomers: 0, inactiveCustomers: 0 });
    renderPage(<CustomersPage />, "/app/customers");
    expect(await screen.findByText(/Add regular buyers/)).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Add customer" }));
    await user.type(screen.getByLabelText("Name"), "A");
    await user.click(screen.getByRole("button", { name: "Create customer" }));
    expect(await screen.findByText("Customer name must be at least 2 characters.")).toBeInTheDocument();
    expect(customerApi.create).not.toHaveBeenCalled();
  });

  it("creates a customer from the form", async () => {
    vi.mocked(customerApi.list).mockResolvedValue(pageOf([]));
    vi.mocked(customerApi.summary).mockResolvedValue({ totalCustomers: 0, activeCustomers: 0, inactiveCustomers: 0 });
    vi.mocked(customerApi.create).mockResolvedValue(customer);
    renderPage(<CustomersPage />, "/app/customers");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Add customer" }));
    await user.type(screen.getByLabelText("Name"), "Meena Iyer");
    await user.type(screen.getByLabelText("Phone"), "9876500001");
    await user.click(screen.getByRole("button", { name: "Create customer" }));
    await waitFor(() => expect(customerApi.create).toHaveBeenCalled());
    expect(customerApi.create).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Meena Iyer", phone: "9876500001" }),
    );
  });
});

describe("POS cart", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(customerApi.active).mockResolvedValue([customer]);
    vi.mocked(productApi.list).mockResolvedValue(pageOf([cola]));
    vi.mocked(inventoryApi.list).mockResolvedValue(
      pageOf([
        {
          productId: cola.id,
          productName: cola.name,
          sku: cola.sku,
          categoryId: cola.categoryId,
          categoryName: cola.categoryName,
          unit: cola.unit,
          quantity: "100",
          reorderLevel: "0",
          status: "IN_STOCK",
          openingRecorded: true,
          updatedAt: "2026-01-01T00:00:00Z",
        },
      ]),
    );
    vi.mocked(saleApi.create).mockReset();
    vi.mocked(saleApi.complete).mockReset();
    vi.mocked(saleApi.invoice).mockResolvedValue({
      sale: completedSale,
      companyName: "Sale Kirana",
      companyGstin: null,
      companyAddress: null,
      companyPhone: null,
    });
  });

  it("adds a product, changes quantity, and shows GST totals", async () => {
    renderPage(<SalePosPage />, "/app/sales/new");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /Coca Cola 750ml/ }));
    expect(await screen.findByText("Grand total: ₹51.20")).toBeInTheDocument();
    const qty = screen.getByLabelText("Quantity for Coca Cola 750ml");
    await user.clear(qty);
    await user.type(qty, "3");
    expect(await screen.findByText("Grand total: ₹153.60")).toBeInTheDocument();
  });

  it("adds a matching product when Enter is pressed in the barcode field", async () => {
    renderPage(<SalePosPage />, "/app/sales/new");
    const user = userEvent.setup();
    const input = await screen.findByLabelText("Barcode search");
    await user.type(input, "8901234567890{Enter}");
    expect(await screen.findByText("Grand total: ₹51.20")).toBeInTheDocument();
  });

  it("shows insufficient stock from checkout and does not navigate away", async () => {
    vi.mocked(saleApi.create).mockResolvedValue({ ...completedSale, status: "DRAFT", invoiceNumber: null, paymentMethod: null });
    vi.mocked(saleApi.complete).mockRejectedValue(
      new ApiRequestError(409, "INSUFFICIENT_STOCK", "Insufficient stock for Coca Cola 750ml. Available quantity: 2"),
    );
    renderPage(<SalePosPage />, "/app/sales/new");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /Coca Cola 750ml/ }));
    await user.click(screen.getByRole("button", { name: "Complete sale" }));
    expect(await screen.findByText(/Insufficient stock for Coca Cola 750ml/)).toBeInTheDocument();
  });

  it("completes checkout with cash payment", async () => {
    vi.mocked(saleApi.create).mockResolvedValue({ ...completedSale, status: "DRAFT", invoiceNumber: null, paymentMethod: null });
    vi.mocked(saleApi.complete).mockResolvedValue(completedSale);
    renderPage(<SalePosPage />, "/app/sales/new");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /Coca Cola 750ml/ }));
    await user.click(screen.getByRole("button", { name: "Complete sale" }));
    await vi.waitFor(() => expect(saleApi.complete).toHaveBeenCalledWith(completedSale.id, "CASH"));
  });
});

describe("Invoice page", () => {
  it("renders company, customer, snapshot lines, and totals", async () => {
    vi.mocked(saleApi.invoice).mockResolvedValue({
      sale: completedSale,
      companyName: "Sale Kirana",
      companyGstin: "27AAAAA0000A1Z5",
      companyAddress: "Pune, Maharashtra",
      companyPhone: "0201234567",
    });
    renderPage(<SaleInvoicePage />, `/app/sales/${completedSale.id}/invoice`);
    expect(await screen.findByText("Sale Kirana")).toBeInTheDocument();
    expect(screen.getByText("INV-000001")).toBeInTheDocument();
    expect(screen.getAllByText("Coca Cola 750ml").length).toBeGreaterThan(0);
    expect(screen.getByText("COLA-750")).toBeInTheDocument();
    expect(screen.getByText("Grand total: ₹153.60")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Print Invoice" })).toBeInTheDocument();
  });
});
