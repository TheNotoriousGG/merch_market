# Amra Merch Market Infra

Единая локальная инфраструктура для sibling-проектов `amra-merch-market-backend` и `amra-merch-market-frontend`.

```bash
docker compose up --build --detach
docker compose ps
docker compose logs --follow
docker compose down
```

Browser E2E запускаются отдельным профилем после старта приложений:

```bash
docker compose --profile test build e2e
docker compose --profile test run --rm --no-deps e2e
```

Тестовый контейнер использует официальный Chromium image Playwright и сохраняет HTML-report, trace, screenshot и video при сбоях.

Compose-проект называется `amra-merch-market`. Он собирает приложения через Dockerfile каждого репозитория и поднимает frontend, backend, PostgreSQL, Keycloak и MinIO. Одноразовый `demo-seed` после миграций создаёт полноценную локальную витрину: категории, товары, варианты, характеристики, изображения, цены, остатки, акции, подборки и баннеры. Повторный запуск добавляет отсутствующие записи без дублей и не возвращает проданные остатки. Сервис `e2e` включается только профилем `test`.

В наборе есть девять опубликованных товаров, один черновик для проверки админского workflow, пятнадцать SKU, три вида ценовых акций, три подборки и два баннера. У одного размера намеренно нулевой остаток, чтобы можно было проверить недоступный вариант. Данные загружаются автоматически при `docker compose up`; вручную повторить загрузку можно командой `docker compose up demo-seed`.

Имена контейнеров назначает Compose, например `amra-merch-market-backend-1`. Суффикс `-1` обозначает номер реплики и позволяет масштабировать и пересоздавать сервисы без конфликтов. Для обращения между контейнерами используются стабильные DNS-имена сервисов (`backend`, `postgres`, `keycloak-db`), а не имена конкретных контейнеров.

Образы приложений получают локальные имена `amra/merch-market-backend:local` и `amra/merch-market-frontend:local`. Метки `ru.amra.project` и `ru.amra.component` позволяют однозначно фильтровать ресурсы и дают понятное соответствие будущим Kubernetes workload labels.

Тома данных имеют явные имена, чтобы сохранять локальные данные независимо от расположения репозитория. PostgreSQL монорепозитория использует `merch-market-monorepo-postgres-data`; старый sibling-volume сохраняется отдельно и не удаляется автоматически. Сеть создаётся и изолируется самим Compose в рамках проекта.

Адреса: frontend `http://localhost:3001`, backend `http://localhost:8080`, Keycloak `http://localhost:8081`, MinIO `http://localhost:9000`, MinIO Console `http://localhost:9001`.

Дефолтный пользователь админки: `admin` / `admin`. Дополнительные локальные пользователи Keycloak: `catalog-manager`, `warehouse-manager`, `amra-admin`; пароль — `amra-local`.

Keycloak публикует OIDC issuer через `localhost:8081`, а backend использует динамический внутренний backchannel. Эти адреса намеренно различаются: browser должен видеть публичный issuer, контейнеры обращаются к сервису `keycloak` внутри Compose-сети.
