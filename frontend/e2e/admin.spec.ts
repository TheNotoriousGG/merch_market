import { expect, test } from "@playwright/test";

test("административное меню доступно на мобильном экране", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route("**/api/v1/session", route => route.fulfill({ json: {
    authenticated: true, emailVerified: true, subject: "admin",
    displayName: "Администратор", permissions: ["ADMIN"],
  } }));

  await page.goto("/admin");
  const menu = page.getByRole("navigation", { name: "Административные разделы" });
  await expect(menu).not.toBeInViewport();
  await page.getByRole("button", { name: "☰ Меню", exact: true }).click();
  await expect(menu).toBeInViewport();
  await expect(page.getByRole("link", { name: "Склад" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(menu).not.toBeInViewport();
});

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
  let receiptRequests=0;
  await page.route("**/api/v1/session", route => route.fulfill({ json: {
    authenticated: true, emailVerified: true, subject: "warehouse-manager",
    displayName: "Кладовщик", permissions: ["WAREHOUSE_MANAGER"],
  } }));
  await page.route("**/api/v1/admin/inventory/receipts", async route => {receiptRequests++;expect(route.request().postDataJSON().lines).toEqual([{variantId:"22222222-2222-2222-2222-222222222222",quantity:4}]);await route.fulfill({json:{documentId:"receipt-test",lineCount:1,totalQuantity:4,movements:[]}})});
  await page.route("**/api/v1/admin/inventory/movements?*",route=>route.fulfill({json:{items:[{id:"33333333-3333-3333-3333-333333333333",variantId:"22222222-2222-2222-2222-222222222222",productName:"Худи Urban Blue",sku:"AMRA-HUB-M",variantLabel:"Синий / M",type:"RECEIPT",quantityDelta:10,reason:"Свободная приёмка",occurredAt:"2026-09-12T12:00:00Z"}],page:{hasNext:false}}}));
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
  await page.getByRole("button",{name:"Провести приёмку"}).click();
  await expect.poll(()=>receiptRequests).toBe(1);
  await page.getByRole("button",{name:"Приёмка",exact:true}).click();
  await expect(page.getByRole("link", { name: "+ Создать карточку товара" })).toBeVisible();
  await page.getByRole("button", { name: "Корректировка" }).click();
  await page.getByRole("combobox", { name: "Товар и вариант" }).fill("Urban");
  await page.getByRole("option", { name: /Худи Urban Blue/ }).click();
  await expect(page.getByText("Сейчас на складе:").locator("..")).toContainText("10 шт.");
  await page.getByRole("button", { name: "Движения" }).click();
  await expect(page.getByRole("cell", { name: "Свободная приёмка", exact: true })).toBeVisible();
});

test("редактор товара показывает сохранение и объясняет, что мешает публикации", async ({ page }) => {
  const productId = "11111111-1111-1111-1111-111111111111";
  await page.route("**/api/v1/session", route => route.fulfill({ json: {
    authenticated: true, emailVerified: true, subject: "catalog-manager",
    displayName: "Каталог-менеджер", permissions: ["CATALOG_MANAGER"],
  } }));
  await page.route("**/api/v1/admin/catalog/categories", route => route.fulfill({ json: { items: [{
    id: "22222222-2222-2222-2222-222222222222", slug: "clothes", name: "Одежда",
    displayOrder: 0, status: "ACTIVE", archived: false, version: 1, updatedAt: "2026-09-12T12:00:00Z",
  }] } }));
  await page.route(`**/api/v1/admin/catalog/products/${productId}`, async route => {
    if (route.request().method() === "PATCH") {
      const body = route.request().postDataJSON();
      await route.fulfill({ headers: { ETag: '"2"' }, json: {
        id: productId, status: "DRAFT", variants: [], media: [], ...body,
      } });
      return;
    }
    await route.fulfill({ headers: { ETag: '"1"' }, json: {
      id: productId, name: "Худи Base", slug: "hoodie-base", shortDescription: "Короткое описание",
      description: "Полное описание", primaryCategoryId: "22222222-2222-2222-2222-222222222222",
      priceMinor: 549000, status: "DRAFT", variants: [], media: [], merchandising: null,
    } });
  });

  await page.goto(`/admin/catalog/products/${productId}`);
  await expect(page.getByText("Все изменения сохранены", { exact: true })).toBeVisible();
  await page.getByLabel("Название товара").fill("Худи Base 2");
  await expect(page.getByText("Есть несохранённые изменения")).toBeVisible();
  await page.getByRole("button", { name: "Сохранить изменения" }).click();
  await expect(page.locator("body")).not.toContainText("Не удалось выполнить действие");
  await expect(page.getByText("Все изменения сохранены", { exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "Опубликовать товар" }).click();
  await expect(page.getByText(/Чтобы опубликовать товар, добавьте: хотя бы один размер, фотография/)).toBeVisible();
});

test("менеджер создаёт коллекцию и управляет её товарами", async ({page})=>{
  await page.route("**/api/v1/session",route=>route.fulfill({json:{authenticated:true,emailVerified:true,subject:"catalog-manager",displayName:"Менеджер",permissions:["CATALOG_MANAGER"]}}));
  await page.route("**/api/v1/admin/catalog/collections",async route=>{if(route.request().method()==="POST")return route.fulfill({status:201,headers:{ETag:'"v0"'},json:{id:"collection-1",slug:"vybor-nedeli",name:"Выбор недели",description:"Лучшие товары",status:"HIDDEN",displayOrder:0,productIds:[],version:0}});return route.fulfill({json:{items:[]}})});
  await page.route("**/api/v1/admin/catalog/products**",route=>route.fulfill({json:{items:[{id:"product-1",slug:"hoodie",name:"Худи Amra",status:"ACTIVE",variantCount:1,hasPrimaryMedia:true,updatedAt:"2026-09-13T00:00:00Z"}],page:{page:0,size:100,totalElements:1,totalPages:1}}}));
  await page.goto("/admin/collections");
  await page.getByLabel("Название").fill("Выбор недели");await page.getByLabel("Описание").fill("Лучшие товары");
  await page.getByLabel("Найти товар").fill("Худи");await page.getByRole("button",{name:"+ Худи Amra"}).click();
  await expect(page.getByText("Товары в коллекции · 1")).toBeVisible();
});

test("баннер объясняет, почему его нельзя опубликовать",async({page})=>{
  await page.route("**/api/v1/session",route=>route.fulfill({json:{authenticated:true,emailVerified:true,subject:"catalog-manager",displayName:"Менеджер",permissions:["CATALOG_MANAGER"]}}));
  await page.route("**/api/v1/admin/storefront/banners",route=>route.fulfill({json:{items:[]}}));
  await page.goto("/admin/banners");
  await page.getByLabel("Название для сотрудников").fill("Главный баннер");await page.getByLabel("Заголовок",{exact:true}).fill("Новая коллекция");await page.getByLabel("Описание").fill("Уже на витрине");
  await page.getByLabel("Статус").selectOption("PUBLISHED");await page.getByRole("button",{name:"Сохранить и опубликовать"}).click();
  await expect(page.getByText("Для публикации загрузите desktop-фотографию.")).toBeVisible();
});
