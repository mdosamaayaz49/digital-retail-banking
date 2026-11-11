import Keycloak from "keycloak-js";
export const identity = new Keycloak({
  url: import.meta.env.VITE_OIDC_URL || "http://localhost:8180",
  realm: "nusantara",
  clientId: "banking-web",
});
let ready: Promise<boolean> | undefined;
export function initAuth() {
  return (ready ??= identity.init({
    onLoad:
      sessionStorage.getItem("nusantara-login") === "1" ||
      new URLSearchParams(location.search).has("code")
        ? "login-required"
        : undefined,
    pkceMethod: "S256",
    checkLoginIframe: false,
  }));
}
export async function login(register = false) {
  sessionStorage.setItem("nusantara-login", "1");
  await initAuth();
  if (register)
    await identity.register({ redirectUri: location.origin + "/profile" });
  else await identity.login({ redirectUri: location.origin + "/" });
}
export async function logout() {
  sessionStorage.removeItem("nusantara-login");
  await identity.logout({ redirectUri: location.origin });
}
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  if (!identity.authenticated)
    throw new Error("Sign in to use your fictional banking accounts.");
  await identity.updateToken(40);
  const response = await fetch(
    (import.meta.env.VITE_API_URL || "") + "/api/v1" + path,
    {
      ...options,
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + identity.token,
        ...options.headers,
      },
    },
  );
  if (!response.ok) {
    const problem = await response
      .json()
      .catch(() => ({ detail: "The banking service is unavailable." }));
    throw new Error(
      problem.detail || "Request failed (" + response.status + ").",
    );
  }
  if (response.status === 204 || response.headers.get("content-length") === "0")
    return undefined as T;
  const text = await response.text();
  return text ? JSON.parse(text) : (undefined as T);
}
export async function downloadStatement(id: string) {
  await identity.updateToken(40);
  const r = await fetch(
    (import.meta.env.VITE_API_URL || "") +
      "/api/v1/statements/" +
      id +
      "/download",
    { headers: { Authorization: "Bearer " + identity.token } },
  );
  if (!r.ok) throw new Error("Statement could not be downloaded.");
  const url = URL.createObjectURL(await r.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = "Nusantara-statement.pdf";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export type Account = {
  id: string;
  accountNumber: string;
  name: string;
  type: string;
  status: string;
  balance: number;
  availableBalance: number;
  currency: string;
};
export type Transaction = {
  id: string;
  reference: string;
  description: string;
  status: string;
  created_at: string;
  amount: number;
  account_name: string;
  account_id: string;
  currency: string;
};
export type Beneficiary = {
  id: string;
  account_id: string;
  nickname: string;
  account_number: string;
};
export type Profile = {
  display_name: string;
  email: string;
  phone: string;
  kyc_status: string;
};
export type Page<T> = { items: T[]; page: number; size: number; total: number };
export const money = (value: number | string) =>
  new Intl.NumberFormat("en-MY", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value));
export const date = (value: string) =>
  new Date(value).toLocaleDateString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Kuala_Lumpur",
  });
export const previewAccounts: Account[] = [
  {
    id: "10000000-0000-0000-0000-000000000001",
    accountNumber: "1100 8274 0192",
    name: "Everyday Savings",
    type: "SAVINGS",
    status: "ACTIVE",
    balance: 12850,
    availableBalance: 12850,
    currency: "MYR",
  },
  {
    id: "10000000-0000-0000-0000-000000000002",
    accountNumber: "1100 8274 0281",
    name: "Lifestyle Current",
    type: "CURRENT",
    status: "ACTIVE",
    balance: 5680.5,
    availableBalance: 5680.5,
    currency: "MYR",
  },
];
export const previewBeneficiaries: Beneficiary[] = [
  {
    id: "20000000-0000-0000-0000-000000000001",
    account_id: "10000000-0000-0000-0000-000000000003",
    nickname: "Daniel Tan",
    account_number: "1100 5532 0816",
  },
];
export const previewTransactions: Transaction[] = [
  {
    id: "p1",
    reference: "PREVIEW-001",
    description: "Monthly salary",
    status: "SETTLED",
    created_at: "2026-11-10T08:00:00+08:00",
    amount: 5200,
    account_name: "Everyday Savings",
    account_id: previewAccounts[0].id,
    currency: "MYR",
  },
  {
    id: "p2",
    reference: "PREVIEW-002",
    description: "Transfer to Daniel Tan",
    status: "SETTLED",
    created_at: "2026-11-09T12:00:00+08:00",
    amount: -250,
    account_name: "Everyday Savings",
    account_id: previewAccounts[0].id,
    currency: "MYR",
  },
  {
    id: "p3",
    reference: "PREVIEW-003",
    description: "Weekend groceries",
    status: "SETTLED",
    created_at: "2026-11-08T16:00:00+08:00",
    amount: -186.4,
    account_name: "Lifestyle Current",
    account_id: previewAccounts[1].id,
    currency: "MYR",
  },
  {
    id: "p4",
    reference: "PREVIEW-004",
    description: "Savings top-up",
    status: "SETTLED",
    created_at: "2026-11-07T09:00:00+08:00",
    amount: 750,
    account_name: "Everyday Savings",
    account_id: previewAccounts[0].id,
    currency: "MYR",
  },
];

export const bankDate = (value: Date = new Date()) =>
  value.toLocaleDateString("en-CA", { timeZone: "Asia/Kuala_Lumpur" });
