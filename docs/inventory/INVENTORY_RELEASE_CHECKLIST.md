# Inventory release checklist

Статус: локальный acceptance блока 12/12 завершён.

Дата: 19 августа 2026 года.

## Contract и client artifact

- OpenAPI 3.0.3 contract проходит validation и backward-compatibility check;
- Java interfaces/transport DTO и TypeScript Fetch client генерируются из одного contract tree;
- artifact policy проверяет `InventoryApi`, `InventoryAdministrationApi`, availability и mutation models, а также все четыре inventory operation IDs;
- generated sources не являются domain/JPA model и не редактируются вручную;
- две clean-сборки source ZIP дали одинаковый SHA-256 `4a6c77a3b9833eb630502cf69041700a5996b2916043c6ba96e0ac73a21909c1`;
- generated sources прошли `tsc --noEmit` закреплённым TypeScript 5.9.3;
- browser handoff и ограничения exact stock описаны в `docs/api/FRONTEND_INVENTORY_CLIENT.md`;
- npm publication остаётся deferred до активации GitLab Package Registry по ADR-0002.

## Runtime и data

- `clean qualityGate` проходит со 173 тестами без failures/errors; branch coverage — 871/1 220 (71,4%), line coverage — 3 732/3 956 (94,3%);
- Flyway V1–V10 применяются к пустой PostgreSQL;
- anonymous availability не раскрывает exact quantities;
- warehouse mutations защищены role, verified identity, MFA, CSRF, ETag/idempotency и append-only audit;
- representative query plans описаны в `docs/persistence/INVENTORY_QUERY_PLANS.md`.

## Container acceptance

- supply-chain policy и Hadolint проходят;
- application image собирается из pinned digest bases и запускается как `nonroot:nonroot`;
- isolated PostgreSQL 18.4 smoke database применяет 10/10 migrations V1–V10 и создаёт 27 application tables;
- `/actuator/health` возвращает `UP`;
- anonymous availability для отсутствующего UUID возвращает `OUT_OF_STOCK` без exact stock;
- временные containers/network удалены; persistent volume не создавался.

## Deferred production gates

- активировать GitLab remote, protected branch, registry, SBOM/signing и package publication;
- повторить performance baseline на production-like hardware/data distribution;
- подключить production Secret Manager, managed PostgreSQL PITR/restore drills и orchestrator probes;
- выполнить frontend integration acceptance из inventory handoff.
