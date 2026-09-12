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

Compose-проект называется `amra-merch-market`. Он собирает приложения через Dockerfile каждого репозитория и поднимает шесть runtime-сервисов: `frontend`, `backend`, `postgres`, `keycloak`, `keycloak-db` и `minio`. Сервис `e2e` включается только профилем `test`.

Имена контейнеров назначает Compose, например `amra-merch-market-backend-1`. Суффикс `-1` обозначает номер реплики и позволяет масштабировать и пересоздавать сервисы без конфликтов. Для обращения между контейнерами используются стабильные DNS-имена сервисов (`backend`, `postgres`, `keycloak-db`), а не имена конкретных контейнеров.

Образы приложений получают локальные имена `amra/merch-market-backend:local` и `amra/merch-market-frontend:local`. Метки `ru.amra.project` и `ru.amra.component` позволяют однозначно фильтровать ресурсы и дают понятное соответствие будущим Kubernetes workload labels.

Тома данных пока имеют явные имена `amra-merch-market-*`, чтобы сохранять локальные данные независимо от расположения infra-репозитория. Сеть создаётся и изолируется самим Compose в рамках проекта.

Адреса: frontend `http://localhost:3001`, backend `http://localhost:8080`, Keycloak `http://localhost:8081`, MinIO `http://localhost:9000`, MinIO Console `http://localhost:9001`.

Дефолтный пользователь админки: `admin` / `admin`. Дополнительные локальные пользователи Keycloak: `catalog-manager`, `warehouse-manager`, `amra-admin`; пароль — `amra-local`.

Keycloak публикует OIDC issuer через `localhost:8081`, а backend использует динамический внутренний backchannel. Эти адреса намеренно различаются: browser должен видеть публичный issuer, контейнеры обращаются к сервису `keycloak` внутри Compose-сети.
