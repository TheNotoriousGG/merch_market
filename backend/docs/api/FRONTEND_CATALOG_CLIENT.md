# Frontend catalog client

Этот документ фиксирует границу между отдельными проектами `amra-merch-market-backend` и `amra-merch-market-frontend` для завершённого catalog vertical slice.

## Источник контракта

Единственный canonical HTTP contract находится в `src/main/openapi/openapi.yaml`. Java transport interfaces/DTO и TypeScript Fetch client генерируются из него; generated files не редактируются вручную и не используются как domain или persistence models.

```shell
./gradlew generateTypeScriptClient verifyTypeScriptClientArtifact
```

Generated source находится в `build/generated/openapi/typescript`. Воспроизводимый локальный архив `build/distributions/amra-shop-api-client-<version>.zip` предназначен для передачи между локальными build stages. Это source artifact: публикация собранного npm package в GitLab Package Registry остаётся отложенной по ADR-0002 и должна добавить Node dependency lock, TypeScript build и `npm pack`/publish verification.

До активации registry frontend build должен детерминированно синхронизировать каталог `src` из generated output в собственную generated-папку и компилировать его своей закреплённой TypeScript-версией. Копии не меняются вручную; любое изменение начинается с OpenAPI contract.

## Public client

После включения generated sources в frontend клиент создаётся один раз на application boundary:

```typescript
import { CatalogApi, Configuration } from "./api/generated";

export const catalogApi = new CatalogApi(
  new Configuration({
    basePath: "/api/v1",
    credentials: "include",
  }),
);
```

Основные вызовы storefront:

```typescript
const navigation = await catalogApi.getCatalogCategories();

const page = await catalogApi.getCatalogProducts({
  category: "clothes",
  page: 0,
  size: 24,
  sort: "NEWEST",
});

const product = await catalogApi.getCatalogProduct({slug: "series-01-t-shirt"});
```

- дерево категорий наполняет мегабар;
- list endpoint наполняет компактные кликабельные карточки и обычную pagination;
- detail endpoint наполняет открытую карточку, варианты, размеры, характеристики и media;
- pricing, stock, cart и favorites принадлежат последующим backend-модулям и не подменяются catalog fields;
- historical slug получает `301`; стандартный browser Fetch следует на canonical URL автоматически;
- `ETag` и `X-Trace-Id` доступны через `get...Raw(...).raw.headers`, если frontend нужны cache revalidation или support diagnostics.

## Browser session и admin client

Все browser calls используют `credentials: "include"`. `AMRA_SESSION` остаётся HttpOnly. Для unsafe admin request frontend читает неcredential cookie `AMRA_CSRF` и передаёт значение в generated parameter `xAMRACSRF`, который становится header `X-AMRA-CSRF`.

Admin create commands требуют новый `Idempotency-Key`. Изменения существующего resource дополнительно требуют strong `ETag`, полученный через raw response, и передают его как `ifMatch`. Повтор одного логического create использует тот же key и тот же body; новая команда получает новый key.

Frontend не переводит HTTP status в произвольные тексты. Он разбирает RFC 9457 `application/problem+json`, использует stable `code` и `violations` для локализации, а `traceId` показывает в support UI. Основные concurrency outcomes: `428 PRECONDITION_REQUIRED` и `412 STALE_RESOURCE_VERSION`.

## Integration acceptance

Перед удалением frontend fixtures должны пройти следующие сценарии:

1. Мегабар строится из category tree без hardcoded taxonomy.
2. Category/collection/new/search/filter query меняет URL и перезапрашивает paged list.
3. Каждая compact product card открывает canonical detail.
4. Variant/size selection использует stable product/variant identifiers, а не display names.
5. Empty list и not-found отображаются отдельно от transport failure.
6. Admin mutation отправляет session, CSRF, idempotency и `If-Match` по contract.
7. `412` предлагает обновить representation, не перезаписывает чужие изменения.
8. Error UI сохраняет `X-Trace-Id`/`traceId` для диагностики.
