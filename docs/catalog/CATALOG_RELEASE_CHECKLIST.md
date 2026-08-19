# Catalog release checklist

Дата локального acceptance: 19 августа 2026 года.

## Contract и client artifact

- modular OpenAPI 3.0.3 contract проходит `openApiValidate` и compatibility check;
- public и administrative catalog operations генерируются в Java interfaces/DTO и TypeScript Fetch sources;
- success и RFC 9457 error responses документируют `X-Trace-Id`;
- custom TypeScript package metadata не содержит generator repository placeholders;
- `verifyTypeScriptClientArtifact` проверяет version, обязательные files и public/admin operation surface;
- два clean build локального source ZIP дали одинаковый SHA-256 `6143a4c2869263e8e69374028df3155b1ab990a28878bd18840aa66cbf62a9c2`;
- generated TypeScript sources успешно прошли `tsc --noEmit` закреплённым frontend compiler;
- npm build/pack/publication остаются deferred gate до активации GitLab Package Registry по ADR-0002.

## Runtime и data

- полный `clean qualityGate` прошёл со 116 тестами, 652/907 covered branches и 2 802/2 962 covered lines;
- fixture 10 000 products / 40 000 variants проверяет actual query plans с runtime role и `statement_timeout=2s`;
- Flyway V1–V6 применяются к пустой PostgreSQL 18.4;
- runtime role не получает DDL, audit log является append-only;
- public empty category tree и product page возвращают корректные representations, ETag, cache policy и trace identifier.

## Container acceptance

- supply-chain policy script прошёл;
- Dockerfile прошёл Hadolint без замечаний;
- multi-stage image собран только из pinned digest bases;
- final distroless process запускается как `nonroot:nonroot`;
- отдельный smoke stack применил 6 migrations и создал 16 application tables;
- `/actuator/health` вернул `UP`;
- `/api/v1/catalog/categories` и `/api/v1/catalog/products?page=0&size=24` вернули `200`;
- временные smoke containers и network удалены; существующие local application containers не изменялись.

## Deferred production gates

- повторить query-plan baseline на production-like hardware и фактическом распределении данных;
- активировать GitLab remote, protected branches, runner, registry, SBOM/signing и package publication;
- подключить production secret manager, managed PostgreSQL backup/PITR monitoring и orchestrator probes;
- выполнить frontend integration acceptance из `docs/api/FRONTEND_CATALOG_CLIENT.md`.
