# Frontend inventory client

Этот документ фиксирует inventory-границу между `amra-merch-market-backend` и отдельным `amra-merch-market-frontend`.

## Storefront availability

Generated `InventoryApi` предоставляет только batch-операцию `getInventoryAvailability`. Frontend передаёт 1–60 variant UUID, а backend возвращает first-seen de-duplicated snapshot со статусами `IN_STOCK`/`OUT_OF_STOCK` и `asOf`.

```typescript
import { Configuration, InventoryApi } from "./api/generated";

export const inventoryApi = new InventoryApi(
  new Configuration({basePath: "/api/v1", credentials: "include"}),
);

const availability = await inventoryApi.getInventoryAvailability({variantId: selectedVariantIds});
```

Storefront не получает `onHand`, `reserved`, warehouse, reservation или movement data. Ответ имеет `Cache-Control: no-store`; frontend не должен сохранять его как долгоживущий stock truth и повторно проверяет availability перед будущим checkout.

## Warehouse client

Generated `InventoryAdministrationApi` предоставляет:

- `getAdminInventoryBalance` — exact primary-warehouse snapshot и strong `ETag` через raw response;
- `receiveInventoryStock` — receipt с новым `Idempotency-Key`;
- `adjustInventoryStock` — reconciliation с новым `Idempotency-Key` и актуальным `If-Match`.

Все browser calls используют `credentials: "include"`. Unsafe requests передают cookie `AMRA_CSRF` в header `X-AMRA-CSRF`. UI обрабатывает stable RFC 9457 codes; `412 STALE_RESOURCE_VERSION` требует обновить balance, а `428 PRECONDITION_REQUIRED` означает отсутствие ETag. Exact quantities показываются только пользователю с warehouse/admin permission.

## Integration acceptance

1. Карточки запрашивают availability одним batch, без N+1 и без hardcoded остатков.
2. Unknown/inactive/missing variant отображается как `OUT_OF_STOCK`.
3. Warehouse balance читает `ETag` из raw response.
4. Retry одной receipt/reconciliation команды сохраняет idempotency key и body.
5. Новая команда получает новый key; reconciliation использует последний ETag.
6. `401`, authorization `403`, CSRF `403`, `412` и business conflict показываются различимо по stable code.
7. `X-Trace-Id`/`traceId` сохраняется для диагностики, credentials и exact stock не логируются.
