import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import { ProtectedRoute } from "@/components/layout/protected-route";
import { ApiRequestError, apiRequest, authApi, handleUnauthorized, storeToken } from "@/lib/api";
import { LoginPage } from "@/pages/login-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: {
      me: vi.fn(),
      login: vi.fn(),
      signup: vi.fn(),
    },
  };
});

const TOKEN_KEY = "retailflow.accessToken";

describe("ProtectedRoute", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
  });

  it("redirects unauthenticated users to /login", async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/app"]}>
          <Routes>
            <Route path="/login" element={<p>login screen</p>} />
            <Route element={<ProtectedRoute />}>
              <Route path="/app" element={<p>private app</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(await screen.findByText("login screen")).toBeInTheDocument();
    expect(screen.queryByText("private app")).not.toBeInTheDocument();
  });
});

describe("login failure", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
    vi.mocked(authApi.login).mockReset();
  });

  it("shows an error and stays on the form", async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiRequestError(401, "INVALID_CREDENTIALS", "Invalid email or password"),
    );

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/login"]}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/app" element={<p>private app</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Email"), "owner@shop.test");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
    expect(screen.queryByText("private app")).not.toBeInTheDocument();
  });
});

describe("401 handling", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("clears the token and redirects for authenticated API 401s", () => {
    storeToken("stale-token");
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });
    handleUnauthorized("/api/v1/tenant");
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(assign).toHaveBeenCalledWith("/login");
    vi.unstubAllGlobals();
  });

  it("does not redirect on login 401", () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });
    handleUnauthorized("/api/v1/auth/login");
    expect(assign).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it("apiRequest invokes unauthorized handling on 401", async () => {
    storeToken("stale-token");
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({
          success: false,
          error: { code: "UNAUTHENTICATED", message: "Please sign in" },
        }),
      }),
    );

    await expect(apiRequest("/api/v1/tenant")).rejects.toBeInstanceOf(ApiRequestError);
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(assign).toHaveBeenCalledWith("/login");
    vi.unstubAllGlobals();
  });

  it("clears auth state when /me returns 401", async () => {
    storeToken("expired");
    vi.mocked(authApi.me).mockRejectedValue(new ApiRequestError(401, "UNAUTHENTICATED", "Please sign in"));

    render(
      <AuthProvider>
        <MemoryRouter>
          <p>ready</p>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(await screen.findByText("ready")).toBeInTheDocument();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
