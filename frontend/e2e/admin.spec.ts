import { expect, test } from "@playwright/test";

test("каталог-менеджер видит дерево категорий", async ({ page }) => {
  await page.route("**/api/v1/session", route => route.fulfill({ json: {
    authenticated: true,
    emailVerified: true,
    subject: "catalog-manager",
    displayName: "Каталог-менеджер",
    permissions: ["CATALOG_MANAGER"],
  } }));
  await page.route("**/api/v1/admin/catalog/categories", route => route.fulfill({ json: { items: [{
    id: "11111111-1111-1111-1111-111111111111",
    slug: "clothes",
    name: "Одежда",
    displayOrder: 0,
    status: "ACTIVE",
    archived: false,
    version: 1,
    updatedAt: "2026-09-12T12:00:00Z",
  }] } }));

  await page.goto("/admin/categories");
  await expect(page.getByText("Каталог-менеджер")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Категории" })).toBeVisible();
  await expect(page.locator("strong", { hasText: "Одежда" })).toBeVisible();
  await expect(page.getByText("На витрине", { exact: true })).toBeVisible();
});
