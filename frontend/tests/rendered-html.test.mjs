import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render(pathname = "/") {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set(
    "test",
    `${process.pid}-${Date.now()}-${encodeURIComponent(pathname)}`,
  );
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request(new URL(pathname, "http://localhost"), {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

async function renderHtml(pathname) {
  const response = await render(pathname);
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  return response.text();
}

test("server-renders the Amra storefront", async () => {
  const html = await renderHtml("/");

  assert.match(html, /<html lang="ru">/i);
  assert.match(html, /<title>Амра Шоп — вещи с характером<\/title>/i);
  assert.match(html, /амра шоп/i);
  assert.match(html, /Новинки/i);
  assert.match(html, /Товары недели/i);
  assert.match(html, /Худи и свитшоты/i);
  assert.match(html, /Sale/i);
  assert.match(html, /Покупателям/i);
  assert.doesNotMatch(html, /codex-preview|Building your site|SkeletonPreview/i);
});

test("server-renders a catalog route with storefront navigation", async () => {
  const html = await renderHtml("/catalog/clothes");

  assert.match(html, /Каталог Amra/i);
  assert.match(html, /Загружаем подборку/i);
  assert.match(html, /Фильтры/i);
  assert.match(html, /Сортировка/i);
  assert.match(html, /Избранное, 0 товаров/i);
});

test("keeps product state and API contract metadata explicit", async () => {
  const [layout, shopState, packageJson, apiManifest] = await Promise.all([
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/ShopState.tsx", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../app/api/generated-manifest.json", import.meta.url), "utf8"),
  ]);

  assert.match(layout, /lang="ru"/);
  assert.match(layout, /<ShopStateProvider>\{children\}<\/ShopStateProvider>/);
  assert.match(shopState, /customerApi\.getCustomerContext/);
  assert.match(shopState, /ifMatch: etag/);
  assert.doesNotMatch(shopState, /localStorage/);
  assert.match(packageJson, /"name": "amra-merch-market-frontend"/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
  assert.match(apiManifest, /"apiVersion": "1\.0\.0"/);
  assert.match(apiManifest, /"specSha256": "[a-f0-9]{64}"/);
});
