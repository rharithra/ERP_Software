import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import { ApiRequestError, authApi, categoryApi, productApi } from "@/lib/api";
import { CategoriesPage } from "@/pages/categories-page";
import { ProductsPage } from "@/pages/products-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: {
      me: vi.fn(),
      login: vi.fn(),
      signup: vi.fn(),
    },
    categoryApi: {
      list: vi.fn(),
      active: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    },
    productApi: {
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    },
  };
});

const dairy = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Dairy",
  description: "Milk and curd",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const milk = {
  id: "22222222-2222-2222-2222-222222222222",
  name: "Amul Taaza 500ml",
  sku: "AMUL-MILK-500",
  barcode: "8901234567890",
  description: "Toned milk",
  categoryId: dairy.id,
  categoryName: "Dairy",
  costPrice: "24.00",
  sellingPrice: "28.00",
  gstRate: "5",
  unit: "ML",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function emptyPage<T>(items: T[]) {
  return { items, page: 1, size: 50, totalItems: items.length, totalPages: items.length ? 1 : 0 };
}

function renderPage(ui: ReactElement) {
  return render(
    <AuthProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </AuthProvider>,
  );
}

describe("Categories page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
    vi.mocked(categoryApi.list).mockReset();
  });

  it("renders the category list", async () => {
    vi.mocked(categoryApi.list).mockResolvedValue(emptyPage([dairy]));
    renderPage(<CategoriesPage />);
    expect(await screen.findAllByText("Dairy")).not.toHaveLength(0);
    expect(screen.getAllByText("Milk and curd").length).toBeGreaterThan(0);
  });

  it("validates category name before calling the API", async () => {
    vi.mocked(categoryApi.list).mockResolvedValue(emptyPage([]));
    renderPage(<CategoriesPage />);
    expect(await screen.findByText(/Add your first category/)).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Add category" }));
    await user.type(screen.getByLabelText("Name"), "A");
    await user.click(screen.getByRole("button", { name: "Create category" }));

    expect(await screen.findByText("Category name must be at least 2 characters.")).toBeInTheDocument();
    expect(categoryApi.create).not.toHaveBeenCalled();
  });
});

describe("Products page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
    vi.mocked(categoryApi.list).mockReset();
    vi.mocked(productApi.list).mockReset();
    vi.mocked(productApi.create).mockReset();
  });

  it("renders the product list", async () => {
    vi.mocked(categoryApi.list).mockResolvedValue(emptyPage([dairy]));
    vi.mocked(productApi.list).mockResolvedValue(emptyPage([milk]));
    renderPage(<ProductsPage />);
    expect(await screen.findAllByText("Amul Taaza 500ml")).not.toHaveLength(0);
    expect(screen.getAllByText("AMUL-MILK-500").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Dairy").length).toBeGreaterThan(0);
  });

  it("shows category options on the product form", async () => {
    vi.mocked(categoryApi.list).mockResolvedValue(emptyPage([dairy]));
    vi.mocked(productApi.list).mockResolvedValue(emptyPage([]));
    renderPage(<ProductsPage />);
    expect(await screen.findByText(/Add SKUs your counter staff/)).toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole("button", { name: "Add product" }));
    const category = screen.getByLabelText("Category");
    expect(category).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Dairy" })).toBeInTheDocument();
  });

  it("validates required product fields", async () => {
    vi.mocked(categoryApi.list).mockResolvedValue(emptyPage([dairy]));
    vi.mocked(productApi.list).mockResolvedValue(emptyPage([]));
    renderPage(<ProductsPage />);
    await screen.findByText(/Add SKUs your counter staff/);

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Add product" }));
    await user.type(screen.getByLabelText("Product name"), "A");
    await user.type(screen.getByLabelText("SKU / product code"), "AMUL-1");
    await user.type(screen.getByLabelText("Cost price (₹)"), "10");
    await user.type(screen.getByLabelText("Selling price (₹)"), "12");
    await user.click(screen.getByRole("button", { name: "Create product" }));

    expect(await screen.findByText("Product name is required.")).toBeInTheDocument();
    expect(productApi.create).not.toHaveBeenCalled();
  });

  it("shows API errors on the product list", async () => {
    vi.mocked(categoryApi.list).mockResolvedValue(emptyPage([dairy]));
    vi.mocked(productApi.list).mockRejectedValue(
      new ApiRequestError(500, "INTERNAL_ERROR", "Catalog is temporarily unavailable"),
    );
    renderPage(<ProductsPage />);
    expect(await screen.findByText("Catalog is temporarily unavailable")).toBeInTheDocument();
  });
});
