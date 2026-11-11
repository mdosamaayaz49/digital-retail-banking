import { test, expect, type Page } from "@playwright/test";
import * as OTPAuth from "otpauth";
const password = process.env.DEMO_PASSWORD;
test.describe("authenticated banking", () => {
  test.skip(
    !password,
    "Set DEMO_PASSWORD and start the local stack to run authenticated journeys.",
  );
  async function login(page: Page, user = "amira") {
    await page.goto("/login");
    await page
      .getByRole("button", { name: "Continue to secure sign-in" })
      .click();
    await page.locator("#username").fill(user);
    await page.locator("#password").fill(password!);
    await page.locator("#kc-login").click();
    await expect(page.getByText("Secure session", { exact: true })).toBeVisible(
      { timeout: 30000 },
    );
  }
  test("open and close account; generate custom-range PDF; logout", async ({
    page,
  }) => {
    await login(page);
    await page.goto("/accounts");
    await page
      .getByRole("button", { name: "Open account", exact: true })
      .click();
    const name = "Interview reserve " + Date.now();
    await page.getByLabel("Account name", { exact: true }).fill(name);
    await page
      .getByRole("button", { name: "Create account", exact: true })
      .click();
    await page.getByRole("heading", { name, exact: true }).click();
    page.once("dialog", (d) => d.accept());
    await page.getByRole("button", { name: "Close empty account" }).click();
    await expect(page.getByText("Closed", { exact: true })).toBeVisible();
    await page.goto("/statements");
    await page
      .getByRole("combobox", { name: "Account", exact: true })
      .selectOption({ label: "Everyday Savings · 0192" });
    await page.getByLabel("Statement period").selectOption("range");
    const today = new Date().toLocaleDateString("en-CA", {
      timeZone: "Asia/Kuala_Lumpur",
    });
    await page.getByLabel("From date").fill(today.slice(0, 7) + "-01");
    await page.getByLabel("To date").fill(today);
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Generate & download PDF" }).click();
    const pdf = await download;
    expect(pdf.suggestedFilename()).toBe("Nusantara-statement.pdf");
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(
      page.getByText("Read-only preview", { exact: true }),
    ).toBeVisible({ timeout: 20000 });
  });
  test("transfer review and admin compensation through the UI", async ({
    page,
    browser,
  }) => {
    await login(page);
    await page.goto("/transfer");
    await page.getByRole("button", { name: "Between my accounts" }).click();
    await page
      .getByLabel("From account")
      .selectOption("10000000-0000-0000-0000-000000000001");
    await page
      .getByLabel("To account")
      .selectOption("10000000-0000-0000-0000-000000000002");
    await page.getByLabel("Amount", { exact: false }).fill("1.23");
    const reference = "Browser transfer " + Date.now();
    await page.getByLabel("Payment reference").fill(reference);
    await page.getByRole("button", { name: "Review transfer" }).click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Confirm transfer", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Your transfer is complete." }),
    ).toBeVisible();
    const context = await browser.newContext({
      baseURL: process.env.APP_URL || "http://localhost:5173",
    });
    const admin = await context.newPage();
    try {
      await login(admin, "reviewer");
      await admin.goto("/admin/transactions");
      const row = admin
        .getByRole("row")
        .filter({ has: admin.getByText(reference, { exact: true }) });
      admin.once("dialog", (d) => d.accept());
      await row.getByRole("button", { name: "Reverse" }).click();
      await expect(row.getByText("Reversed", { exact: true })).toBeVisible();
      await admin.goto("/admin/reconciliation");
      await expect(admin.getByText("Verified", { exact: true })).toBeVisible();
    } finally {
      await context.close();
    }
  });
  test("mock-network rejection releases its reservation", async ({ page }) => {
    await login(page);
    await page.goto("/payments");
    await page
      .getByLabel("From account")
      .selectOption("10000000-0000-0000-0000-000000000001");
    await page
      .getByLabel("Fictional payment alias")
      .selectOption("reject@example.test");
    await page.getByLabel("Amount (MYR)", { exact: true }).fill("2.00");
    const reference = "Rejected browser payment " + Date.now();
    await page.getByLabel("Payment reference").fill(reference);
    await page.getByRole("button", { name: "Review payment" }).click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Confirm payment" })
      .click();
    const row = page
      .getByRole("row")
      .filter({ has: page.getByText(reference, { exact: true }) });
    await expect(row.getByText("Rejected", { exact: true })).toBeVisible({
      timeout: 20000,
    });
    await expect(
      row.getByText("MOCK_NETWORK_REJECTED", { exact: true }),
    ).toBeVisible();
  });
  test("Keycloak supported authenticator enrollment and MFA login", async ({
    page,
    request,
  }) => {
    test.setTimeout(90000);
    test.skip(
      !process.env.KEYCLOAK_ADMIN_PASSWORD,
      "Requires the generated local identity-administrator password.",
    );
    const identity = process.env.VITE_OIDC_URL || "http://localhost:8180";
    const adminResponse = await request.post(
      identity + "/realms/master/protocol/openid-connect/token",
      {
        form: {
          client_id: "admin-cli",
          grant_type: "password",
          username: "platform-admin",
          password: process.env.KEYCLOAK_ADMIN_PASSWORD!,
        },
      },
    );
    expect(adminResponse.status()).toBe(200);
    const access = (await adminResponse.json()).access_token as string;
    const headers = { Authorization: "Bearer " + access };
    const username = "mfa-browser-" + Date.now();
    const create = await request.post(
      identity + "/admin/realms/nusantara/users",
      {
        headers,
        data: {
          username,
          enabled: true,
          emailVerified: true,
          email: username + "@example.test",
          firstName: "MFA",
          lastName: "Fictional",
          requiredActions: ["CONFIGURE_TOTP"],
          credentials: [
            { type: "password", value: password, temporary: false },
          ],
        },
      },
    );
    expect(create.status()).toBe(201);
    const resource = create.headers()["location"];
    try {
      await page.goto("/login");
      await page
        .getByRole("button", { name: "Continue to secure sign-in" })
        .click();
      await page.locator("#username").fill(username + "@example.test");
      await page.locator("#password").fill(password!);
      await page.locator("#kc-login").click();
      await page
        .locator("#totpSecret")
        .waitFor({ state: "attached", timeout: 15000 });
      const secret = await page.locator("#totpSecret").inputValue();
      const authenticator = new OTPAuth.TOTP({
        issuer: "Nusantara",
        label: username,
        algorithm: "SHA1",
        digits: 6,
        period: 30,
        secret: OTPAuth.Secret.fromUTF8(secret),
      });
      const enrollmentCode = authenticator.generate();
      await page.locator("#totp").fill(enrollmentCode);
      await page.locator("#userLabel").fill("Fictional browser authenticator");
      await page.locator("#saveTOTPBtn").click();
      await expect(
        page.getByText("Secure session", { exact: true }),
      ).toBeVisible({ timeout: 30000 });
      await page.getByRole("button", { name: "Sign out", exact: true }).click();
      await expect(page.getByText("Read-only preview")).toBeVisible();
      await page.goto("/login");
      await page
        .getByRole("button", { name: "Continue to secure sign-in" })
        .click();
      await page.locator("#username").fill(username + "@example.test");
      await page.locator("#password").fill(password!);
      await page.locator("#kc-login").click();
      await expect
        .poll(() => authenticator.generate() !== enrollmentCode, {
          timeout: 35000,
          intervals: [500],
        })
        .toBeTruthy();
      await page.locator("#otp").fill(authenticator.generate());
      await page.locator("#kc-login").click();
      await expect(
        page.getByText("Secure session", { exact: true }),
      ).toBeVisible({ timeout: 30000 });
    } finally {
      await request.delete(resource, { headers });
    }
  });
});
