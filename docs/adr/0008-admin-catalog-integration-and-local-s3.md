# ADR-0008: Admin catalog integration и local S3

- Статус: принято
- Дата: 20 августа 2026 года
- Владельцы решения: владелец продукта и backend architecture

## Контекст

Catalog и Inventory command API реализованы, но frontend не имеет административной поверхности. Текущий контракт также не предоставляет административные списки товаров/категорий и upload lifecycle, поэтому UI не может восстановить рабочее состояние после перезагрузки или безопасно загрузить media.

## Решение

Перед product stage 9 выполнить ограниченный admin integration track без изменения Catalog/Inventory invariants:

- добавить paged admin product query с typed search/status/category/sort;
- добавить admin category tree/list query;
- добавить S3 port/adapter и presigned media upload lifecycle;
- использовать MinIO в local development и integration tests;
- не передавать storage credentials browser-коду;
- хранить в PostgreSQL object key и presentation metadata, а не binary;
- подтвердить объект перед созданием catalog media metadata;
- очищать неподтверждённые uploads bounded job;
- сохранить существующие RBAC, verified-email, MFA, CSRF, idempotency и ETag rules;
- доставить frontend только через generated TypeScript contract.

Production S3-compatible provider, bucket policy, CDN и Secret Manager этим решением не выбираются. Endpoint, region, bucket и credentials являются adapter configuration.

## Почему MinIO локально

MinIO даёт persistent S3-compatible service и web console для реального browser upload flow. Узкий mock недостаточен для ежедневной проверки presigned URL, CORS и object lifecycle. Код приложения зависит от S3 port и стандартного protocol client, а не MinIO API.

## Последствия

Положительные:

- полный admin-to-storefront flow воспроизводим локально;
- production storage можно заменить конфигурацией adapter;
- binary traffic не проходит через application heap;
- media behavior проверяется integration tests.

Отрицательные:

- local stack получает ещё один stateful container;
- presigned flow требует CORS и cleanup orphan objects;
- S3 compatibility не заменяет acceptance с выбранным production provider.

## Gates

- OpenAPI compatibility и generated-client artifact;
- authorization/CSRF/ETag/idempotency contract tests;
- MinIO/Testcontainers upload-confirm-cleanup integration tests;
- frontend build/tests и full-stack browser acceptance;
- повторный local seed не создаёт duplicate objects или catalog records.
