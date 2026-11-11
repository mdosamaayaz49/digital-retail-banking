import { test, expect } from "@playwright/test";
test("preview navigation and transfer validation", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByText("Read-only preview")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: /Good morning/ }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Transfer money", exact: true })
    .first()
    .click();
  await page.getByRole("button", { name: "Review transfer" }).click();
  await expect(page.getByText("Choose your account")).toBeVisible();
  await expect(page.getByText("Choose a recipient")).toBeVisible();
});
test("mobile overview fits viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /Good morning/ }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.getByRole("button", { name: "Toggle navigation" }).click();
  await expect(
    page
      .getByRole("navigation", { name: "Main navigation" })
      .getByRole("link", { name: "My accounts", exact: true }),
  ).toBeVisible();
});
