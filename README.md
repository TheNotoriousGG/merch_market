# Amra Merch Market

Monorepo интернет-магазина Amra:

- `backend` — Spring Boot, PostgreSQL, OpenAPI и backend tests;
- `frontend` — Vinext/React, generated API client и Playwright;
- `infrastructure` — локальный Docker Compose, Keycloak, MinIO и E2E runner.

## Локальный запуск

```bash
make up
make status
```

Витрина доступна на <http://localhost:3001>, backend — на
<http://localhost:8080>, Keycloak — на <http://localhost:8081>.

## Проверки

```bash
make backend-check
make frontend-check
make e2e
```

Compose-конфигурация предназначена для разработки и интеграционных тестов.
Production infrastructure будет добавлена после выбора платформы размещения.

## Руководство по проверке

Интерактивная HTML-документация клиентской и административной частей находится в
[`docs/index.html`](docs/index.html). Её можно открыть как обычный файл или запустить
локальный сервер из корня репозитория:

```bash
python3 -m http.server 8090 --directory docs
```

После этого руководство доступно на <http://localhost:8090>.
