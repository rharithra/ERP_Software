import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  ApiRequestError,
  authApi,
  clearToken,
  getStoredToken,
  storeToken,
  type AuthUser,
} from "@/lib/api";
import type { BusinessType, SalesMode } from "@/lib/sales-experience";

type AuthContextValue = {
  user: AuthUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (input: {
    fullName: string;
    email: string;
    password: string;
    companyName: string;
    businessType: BusinessType;
    salesMode: SalesMode;
  }) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getStoredToken();
    if (!token) {
      setLoading(false);
      return;
    }
    authApi
      .me()
      .then(setUser)
      .catch((error: unknown) => {
        if (error instanceof ApiRequestError && error.status === 401) {
          clearToken();
        }
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const refreshUser = useCallback(async () => {
    const token = getStoredToken();
    if (!token) {
      setUser(null);
      return;
    }
    const me = await authApi.me();
    setUser(me);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const result = await authApi.login({ email, password });
    storeToken(result.accessToken);
    setUser(result.user);
  }, []);

  const signup = useCallback(
    async (input: {
      fullName: string;
      email: string;
      password: string;
      companyName: string;
      businessType: BusinessType;
      salesMode: SalesMode;
    }) => {
      const result = await authApi.signup(input);
      storeToken(result.accessToken);
      setUser(result.user);
    },
    [],
  );

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, signup, logout, refreshUser }),
    [user, loading, login, signup, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
