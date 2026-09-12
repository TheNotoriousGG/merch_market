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
