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

test("покупатель оформляет корзину и получает номер заказа", async ({ page }) => {
  const productId = "01999999-9999-7999-8999-999999999981";
  const variantId = "01999999-9999-7999-8999-999999999982";
  let cartItems = [{ variantId, productId, slug: "amra-checkout", name: "Худи Amra", variantLabel: "L", quantity: 1, unitPriceMinor: 590000, lineSubtotalMinor: 590000, discountMinor: 0, available: true }];
  await page.route("**/api/v1/catalog/products**", route => route.fulfill({ json: { items: [], page: { page: 0, size: 60, totalElements: 0, totalPages: 0 } } }));
  await page.route("**/api/v1/customer/context", route => route.fulfill({ json: { authenticated: false, favoriteProductIds: [] } }));
  await page.route("**/api/v1/customer/cart", route => route.fulfill({ json: { id: "01999999-9999-7999-8999-999999999983", version: cartItems.length ? 4 : 5, items: cartItems, subtotalMinor: cartItems.length ? 590000 : 0, currency: "RUB", notices: [] } }));
  await page.route("**/api/v1/customer/checkout", async route => {
    const request = route.request();
    expect(request.headers()["idempotency-key"]).toBeTruthy();
    expect(request.postDataJSON().cartVersion).toBe(4);
    cartItems = [];
    await route.fulfill({ status: 201, json: order });
  });
  const order = { id: "01999999-9999-7999-8999-999999999984", publicNumber: "AMR-TEST00000001", status: "CONFIRMED", currency: "RUB", subtotalMinor: 590000, discountMinor: 0, totalMinor: 590000, email: "buyer@example.com", recipientName: "Анна Амра", phone: "+79991234567", postalCode: "101000", city: "Москва", street: "Тверская, 1", guestAccessToken: "guest-order-token", lines: [{ variantId, sku: "AMRA-HOODIE-L", productName: "Худи Amra", variantLabel: "L", quantity: 1, unitPriceMinor: 590000, discountMinor: 0, totalMinor: 590000 }], createdAt: "2026-09-13T00:00:00Z" };
  await page.route("**/api/v1/customer/orders/AMR-TEST00000001", route => {
    expect(route.request().headers()["x-guest-order-token"]).toBe("guest-order-token");
    return route.fulfill({ json: { ...order, guestAccessToken: undefined } });
  });

  await page.goto("/cart");
  await page.getByRole("link", { name: /Перейти к оформлению/ }).click();
  await page.getByLabel("Email").fill("buyer@example.com");
  await page.getByLabel("Имя получателя").fill("Анна Амра");
  await page.getByLabel("Телефон").fill("+79991234567");
  await page.getByLabel("Индекс").fill("101000");
  await page.getByLabel("Город").fill("Москва");
  await page.getByLabel("Улица и дом").fill("Тверская, 1");
  await page.getByRole("button", { name: /Подтвердить заказ/ }).click();
  await expect(page).toHaveURL(/\/orders\/AMR-TEST00000001/);
  await expect(page.getByText("AMR-TEST00000001")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Спасибо за заказ" })).toBeVisible();
});
