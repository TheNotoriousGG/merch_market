# Амра Шоп

Интерактивный прототип интернет-магазина мерча Амра. Проект вдохновлён удобством T‑Shop, но использует самостоятельный фирменный стиль: графитовый, белый и жёлтый цвета, крупную типографику, модульные блоки и скруглённые формы.

## Быстрый старт всего решения

Frontend собирается и запускается вместе с backend, PostgreSQL, Keycloak и MinIO из соседнего backend-репозитория:

```bash
cd ../amra-merch-market-infra
docker compose up --build
```

После запуска storefront и админка доступны на `http://localhost:3001` и `http://localhost:3001/admin`.

## Отдельный frontend-процесс

Требуется Node.js `>=22.13.0`.

```bash
npm install
npm run dev
npm run build
```

Локальное превью обычно запускается на `http://localhost:3001`.

## Документация проекта

- [PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) — продукт, дизайн, реализованные сценарии и ближайшие задачи.
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — технологии, маршруты, состояние магазина и ключевые файлы.
- [AGENTS.md](AGENTS.md) — обязательные правила работы для будущих сессий Codex.

## Текущее состояние

Это frontend-прототип, который пока не подключён к существующему отдельному backend. Корзина и избранное сохраняются в `localStorage`, а данные профиля, заказов и товаров остаются демонстрационными. Backend Catalog/Inventory и OIDC foundation готовы, но generated client и browser integration ещё не подключены.

Проект подготовлен для OpenAI Sites: конфигурация находится в `.openai/hosting.json`.
