# Amra Merch Market Infra

Единая локальная инфраструктура для sibling-проектов `amra-merch-market-backend` и `amra-merch-market-frontend`.

```bash
docker compose up --build --detach
docker compose ps
docker compose logs --follow
docker compose down
```

Compose собирает приложения через Dockerfile каждого репозитория и поднимает шесть контейнеров с фиксированными именами `amra-merch-market-*`: frontend, backend, PostgreSQL, Keycloak, отдельную PostgreSQL Keycloak и MinIO.

Адреса: frontend `http://localhost:3001`, backend `http://localhost:8080`, Keycloak `http://localhost:8081`, MinIO `http://localhost:9000`, MinIO Console `http://localhost:9001`.

Локальные пользователи Keycloak: `catalog-manager`, `warehouse-manager`, `amra-admin`; пароль — `amra-local`.
