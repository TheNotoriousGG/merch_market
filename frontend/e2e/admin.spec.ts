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

test("кладовщик видит остатки, приёмку и движения без технических UUID", async ({ page }) => {
  await page.route("**/api/v1/session", route => route.fulfill({ json: {
    authenticated: true, emailVerified: true, subject: "warehouse-manager",
    displayName: "Кладовщик", permissions: ["WAREHOUSE_MANAGER"],
  } }));
  await page.route("**/api/v1/admin/inventory", route => route.fulfill({ json: {
    items: [{
      productId: "11111111-1111-1111-1111-111111111111", productName: "Худи Urban Blue",
      productStatus: "ACTIVE", variantId: "22222222-2222-2222-2222-222222222222",
      sku: "AMRA-HUB-M", variantLabel: "Синий / M", onHand: 10, reserved: 2,
      available: 8, version: 3, updatedAt: "2026-09-12T12:00:00Z",
    }],
    movements: [{
      id: "33333333-3333-3333-3333-333333333333", variantId: "22222222-2222-2222-2222-222222222222",
      productName: "Худи Urban Blue", sku: "AMRA-HUB-M", variantLabel: "Синий / M",
      type: "RECEIPT", quantityDelta: 10, reason: "Свободная приёмка", occurredAt: "2026-09-12T12:00:00Z",
    }],
  } }));

  await page.goto("/admin/inventory");
  await expect(page.getByRole("heading", { name: "Склад" })).toBeVisible();
  await expect(page.getByText("AMRA-HUB-M")).toBeVisible();
  await expect(page.getByRole("table").getByText("8", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "+ Новая приёмка" }).click();
  await expect(page.getByRole("heading", { name: "Приёмка товара" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Приёмка", exact: true })).toHaveAttribute("class", /viewTabActive/);
  await page.getByRole("button", { name: "Провести приёмку" }).click();
  await expect(page.getByText("Сначала добавьте хотя бы одну позицию в приёмку.")).toBeVisible();
  await page.getByRole("combobox", { name: "Поиск товара" }).fill("HUB-M");
  await page.getByRole("option", { name: /Худи Urban Blue/ }).click();
  await page.getByRole("spinbutton", { name: "Количество", exact: true }).fill("4");
  await page.getByRole("button", { name: "Добавить", exact: true }).click();
  await expect(page.getByText("4 ед. будет принято")).toBeVisible();
  await expect(page.getByText("14", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "+ Создать карточку товара" })).toBeVisible();
  await page.getByRole("button", { name: "Движения" }).click();
  await expect(page.getByRole("cell", { name: "Свободная приёмка", exact: true })).toBeVisible();
});
