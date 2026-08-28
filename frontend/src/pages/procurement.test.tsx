import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import {
  ApiRequestError,
  authApi,
  inventoryApi,
  productApi,
  purchaseApi,
  storeToken,
  supplierApi,
} from "@/lib/api";
import { PurchaseDetailPage } from "@/pages/purchase-detail-page";
import { PurchaseFormPage } from "@/pages/purchase-form-page";
import { PurchasesPage } from "@/pages/purchases-page";
import { SuppliersPage } from "@/pages/suppliers-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn() },
    supplierApi: {
      list: vi.fn(),
      summary: vi.fn(),
      active: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    },
    purchaseApi: {
      list: vi.fn(),
      summary: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      receive: vi.fn(),
      cancel: vi.fn(),
    },
    productApi: { list: vi.fn() },
    inventoryApi: { list: vi.fn() },
  };
});

const supplier = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "ABC Distributors",
  contactPerson: "Ramesh",
  phone: "9876543210",
  email: null,
  address: null,
  gstin: "27AABCU9603R1ZX",
  notes: null,
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const milk = {
  id: "22222222-2222-2222-2222-222222222222",
  name: "Aavin Milk 500ml",
  sku: "AAVIN-500",
  barcode: null,
  description: null,
  categoryId: "c1",
  categoryName: "Dairy",
  costPrice: "25.00",
  sellingPrice: "30.00",
  gstRate: "5",
  unit: "ML",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const cola = {
  ...milk,
  id: "33333333-3333-3333-3333-333333333333",
  name: "Coca Cola 750ml",
  sku: "COLA-750",
  costPrice: "35.00",
  gstRate: "28",
};

const purchase = {
  id: "44444444-4444-4444-4444-444444444444",
  purchaseNumber: "PUR-000001",
  supplierId: supplier.id,
  supplierName: supplier.name,
  purchaseDate: "2026-08-28",
  status: "DRAFT" as const,
  subtotal: "4250.00",
  taxAmount: "615.00",
  totalAmount: "4865.00",
  notes: null,
  receivedAt: null,
  createdBy: "u1",
  createdByName: "Ananya Sharma",
  createdAt: "2026-08-28T00:00:00Z",
  updatedAt: "2026-08-28T00:00:00Z",
  itemCount: 2,
  items: [
    {
      id: "i1",
      productId: milk.id,
      productName: milk.name,
      sku: milk.sku,
      unit: "ML",
      quantity: "100",
      unitCost: "25.00",
      gstRate: "5",
      lineSubtotal: "2500.00",
      taxAmount: "125.00",
      lineTotal: "2625.00",
    },
    {
      id: "i2",
      productId: cola.id,
      productName: cola.name,
      sku: cola.sku,
      unit: "ML",
      quantity: "50",
      unitCost: "35.00",
      gstRate: "28",
      lineSubtotal: "1750.00",
      taxAmount: "490.00",
      lineTotal: "2240.00",
    },
  ],
};

function pageOf<T>(items: T[]) {
  return { items, page: 1, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0 };
}

function renderPage(ui: ReactElement, path = "/app/suppliers") {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </AuthProvider>,
  );
}

describe("Suppliers page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
    vi.mocked(supplierApi.list).mockReset();
    vi.mocked(supplierApi.summary).mockReset();
    vi.mocked(supplierApi.create).mockReset();
  });

  it("renders supplier rows and summary counts", async () => {
    vi.mocked(supplierApi.list).mockResolvedValue(pageOf([supplier]));
    vi.mocked(supplierApi.summary).mockResolvedValue({ totalSuppliers: 1, activeSuppliers: 1, inactiveSuppliers: 0 });
    renderPage(<SuppliersPage />);
    expect(await screen.findAllByText("ABC Distributors")).not.toHaveLength(0);
    expect(screen.getAllByText("Ramesh").length).toBeGreaterThan(0);
    expect(screen.getByText("Total suppliers")).toBeInTheDocument();
  });

  it("validates supplier name before calling the API", async () => {
    vi.mocked(supplierApi.list).mockResolvedValue(pageOf([]));
    vi.mocked(supplierApi.summary).mockResolvedValue({ totalSuppliers: 0, activeSuppliers: 0, inactiveSuppliers: 0 });
    renderPage(<SuppliersPage />);
    expect(await screen.findByText(/Add distributors you buy from/)).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Add supplier" }));
    await user.type(screen.getByLabelText("Name"), "A");
    await user.click(screen.getByRole("button", { name: "Create supplier" }));
    expect(await screen.findByText("Supplier name must be at least 2 characters.")).toBeInTheDocument();
    expect(supplierApi.create).not.toHaveBeenCalled();
  });

  it("hides supplier mutations for cashiers", async () => {
    storeToken("cashier-token");
    vi.mocked(authApi.me).mockResolvedValue({
      id: "u1",
      email: "cashier@shop.test",
      fullName: "Ravi Cashier",
      role: "CASHIER",
      tenant: { id: "t1", name: "Shop", currency: "INR", timezone: "Asia/Kolkata", businessType: "OTHER", salesMode: "HYBRID" },
    });
    vi.mocked(supplierApi.list).mockResolvedValue(pageOf([supplier]));
    vi.mocked(supplierApi.summary).mockResolvedValue({ totalSuppliers: 1, activeSuppliers: 1, inactiveSuppliers: 0 });
    renderPage(<SuppliersPage />);
    expect(await screen.findAllByText("ABC Distributors")).not.toHaveLength(0);
    expect(screen.queryByRole("button", { name: "Add supplier" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });
});

describe("Purchases page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(purchaseApi.list).mockReset();
    vi.mocked(purchaseApi.summary).mockReset();
    vi.mocked(supplierApi.list).mockReset();
  });

  it("renders purchase list, totals, and status", async () => {
    vi.mocked(purchaseApi.list).mockResolvedValue(pageOf([purchase]));
    vi.mocked(purchaseApi.summary).mockResolvedValue({
      totalPurchases: 1,
      draftPurchases: 1,
      receivedPurchases: 0,
      receivedValue: "0",
    });
    vi.mocked(supplierApi.list).mockResolvedValue(pageOf([supplier]));
    renderPage(<PurchasesPage />, "/app/purchases");
    expect((await screen.findAllByText("PUR-000001")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("ABC Distributors").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Draft").length).toBeGreaterThan(0);
  });

  it("shows purchase list errors", async () => {
    vi.mocked(purchaseApi.list).mockRejectedValue(new ApiRequestError(500, "INTERNAL_ERROR", "Purchases unavailable"));
    vi.mocked(purchaseApi.summary).mockRejectedValue(new ApiRequestError(500, "INTERNAL_ERROR", "Purchases unavailable"));
    vi.mocked(supplierApi.list).mockResolvedValue(pageOf([]));
    renderPage(<PurchasesPage />, "/app/purchases");
    expect(await screen.findByText("Purchases unavailable")).toBeInTheDocument();
  });
});

describe("Purchase form", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(supplierApi.active).mockResolvedValue([supplier]);
    vi.mocked(productApi.list).mockResolvedValue(pageOf([milk, cola]));
    vi.mocked(inventoryApi.list).mockResolvedValue(
      pageOf([
        {
          productId: milk.id,
          productName: milk.name,
          sku: milk.sku,
          categoryId: "c1",
          categoryName: "Dairy",
          unit: "ML",
          quantity: "20",
          reorderLevel: "0",
          status: "IN_STOCK",
          openingRecorded: true,
          updatedAt: "2026-01-01T00:00:00Z",
        },
      ]),
    );
    vi.mocked(purchaseApi.create).mockReset();
  });

  it("adds and removes lines and shows running totals", async () => {
    renderPage(<PurchaseFormPage />, "/app/purchases/new");
    expect(await screen.findByLabelText("Supplier")).toBeInTheDocument();
    const user = userEvent.setup();
    await user.selectOptions(screen.getAllByLabelText("Product")[0], milk.id);
    const qty = screen.getAllByLabelText("Quantity")[0];
    await user.clear(qty);
    await user.type(qty, "100");
    expect(screen.getByText(/Grand total/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Add product" }));
    expect(screen.getAllByLabelText("Product")).toHaveLength(2);
    await user.click(screen.getAllByRole("button", { name: "Remove item" })[1]);
    expect(screen.getAllByLabelText("Product")).toHaveLength(1);
  });

  it("requires a product before saving", async () => {
    renderPage(<PurchaseFormPage />, "/app/purchases/new");
    await screen.findByLabelText("Supplier");
    await userEvent.setup().click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("Every line needs a product.")).toBeInTheDocument();
    expect(purchaseApi.create).not.toHaveBeenCalled();
  });
});

describe("Purchase detail receive flow", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(purchaseApi.get).mockReset();
    vi.mocked(purchaseApi.receive).mockReset();
  });

  it("asks for confirmation before receiving", async () => {
    vi.mocked(purchaseApi.get).mockResolvedValue(purchase);
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={[`/app/purchases/${purchase.id}`]}>
          <Routes>
            <Route path="/app/purchases/:id" element={<PurchaseDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );
    expect(await screen.findByText("PUR-000001")).toBeInTheDocument();
    expect(screen.getByText("Aavin Milk 500ml")).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Receive purchase" }));
    expect(await screen.findByText("Receive this purchase?")).toBeInTheDocument();
    expect(purchaseApi.receive).not.toHaveBeenCalled();
    vi.mocked(purchaseApi.receive).mockResolvedValue({
      ...purchase,
      status: "RECEIVED",
      receivedAt: "2026-08-28T10:00:00Z",
    });
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Receive purchase" }));
    expect(purchaseApi.receive).toHaveBeenCalledWith(purchase.id);
    expect(await screen.findByText(/Inventory posting/)).toBeInTheDocument();
  });

  it("hides receive actions for cashiers", async () => {
    storeToken("cashier-token");
    vi.mocked(authApi.me).mockResolvedValue({
      id: "u1",
      email: "cashier@shop.test",
      fullName: "Ravi Cashier",
      role: "CASHIER",
      tenant: { id: "t1", name: "Shop", currency: "INR", timezone: "Asia/Kolkata", businessType: "OTHER", salesMode: "HYBRID" },
    });
    vi.mocked(purchaseApi.get).mockResolvedValue(purchase);
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={[`/app/purchases/${purchase.id}`]}>
          <Routes>
            <Route path="/app/purchases/:id" element={<PurchaseDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );
    expect(await screen.findByText("PUR-000001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Receive purchase" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });
});
