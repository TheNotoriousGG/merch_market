import { expect, test } from "@playwright/test";

test("витрина открывает поиск и сохраняет единый хедер", async ({ page }) => {
  await page.route("**/api/v1/catalog/categories", route => route.fulfill({ json: { categories: [] } }));
  await page.route("**/api/v1/catalog/products**", route => route.fulfill({ json: { items: [], page: { page: 0, size: 60, totalElements: 0, totalPages: 0 } } }));
  await page.route("**/api/v1/storefront/banners", route => route.fulfill({ json: { items: [] } }));

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Худи и свитшоты" })).toBeVisible();
  await page.getByRole("button", { name: "Открыть поиск" }).click();
  await expect(page.getByRole("search")).toBeVisible();
  await page.getByRole("searchbox", { name: "Название товара" }).fill("худи");
  await page.getByRole("button", { name: "Найти" }).click();
  await expect(page).toHaveURL(/\/search\?q=/);
  await expect(page.getByRole("link", { name: /Амра Шоп, на главную/ })).toBeVisible();
});

test("клиентские служебные страницы доступны из хедера", async ({ page }) => {
  await page.goto("/favorites");
  await expect(page.getByRole("heading", { name: "Избранное" })).toBeVisible();
  await page.getByRole("link", { name: /Корзина/ }).click();
  await expect(page.getByRole("heading", { name: "Корзина", exact: true })).toBeVisible();
});

test("избранное и корзина восстанавливаются из backend shopping context", async ({ page }) => {
  const productId = "01999999-9999-7999-8999-999999999991";
  const variantId = "01999999-9999-7999-8999-999999999992";
  const product = {
    id: productId,
    slug: "amra-test-shirt",
    name: "Футболка Amra Test",
    shortDescription: "Тестовый товар",
    priceMinor: 250000,
    newArrival: true,
    onSale: false,
    featured: false,
    currency: "RUB",
    primaryMedia: { id: "01999999-9999-7999-8999-999999999993", url: "/test.webp", alt: "Футболка", width: 1200, height: 1500, displayOrder: 0 },
    publishedAt: "2026-09-12T12:00:00Z",
    variantOptions: [],
  };
  await page.route("**/api/v1/catalog/products**", (route) => route.fulfill({
    json: { items: [product], page: { page: 0, size: 60, totalElements: 1, totalPages: 1 } },
  }));
  await page.route("**/api/v1/customer/context", (route) => route.fulfill({
    json: { authenticated: false, favoriteProductIds: [productId] },
  }));
  await page.route("**/api/v1/customer/cart", (route) => route.fulfill({
    json: {
      id: "01999999-9999-7999-8999-999999999994",
      version: 2,
      items: [{ variantId, productId, slug: product.slug, name: product.name, variantLabel: "M", quantity: 2, unitPriceMinor: 250000, lineSubtotalMinor: 500000, discountMinor: 0, available: false }],
      subtotalMinor: 0,
      currency: "RUB",
      notices: [{ variantId, code: "OUT_OF_STOCK", message: "Товар временно недоступен" }],
    },
  }));

  await page.goto("/favorites");
  await expect(page.getByRole("heading", { name: product.name })).toBeVisible();
  await page.getByRole("link", { name: /Корзина/ }).click();
  await expect(page.getByRole("heading", { name: product.name })).toBeVisible();
  await expect(page.getByText("Товар временно недоступен")).toBeVisible();
  await expect(page.getByRole("region", { name: "Товары в корзине" }).getByText("2", { exact: true })).toBeVisible();
});
