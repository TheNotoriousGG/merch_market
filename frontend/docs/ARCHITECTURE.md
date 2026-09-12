# Архитектура Амра Шоп

## Стек

- React 19 + TypeScript.
- Vinext/Vite с Next-совместимой структурой `app` router.
- Общие design tokens/reset в `app/styles`; локальные стили компонентов — CSS Modules.
- `app/globals.css` временно содержит legacy-стили витрины и уменьшается по мере безопасной миграции.
- React Context хранит клиентское представление корзины и избранного, а источником данных служит backend Customer/Cart API.
- TypeScript Fetch client генерируется из canonical backend OpenAPI и синхронизируется командой `npm run api:sync`.
- Playwright проверяет критические browser-сценарии в воспроизводимом Chromium-контейнере.
- OpenAI Sites / Cloudflare-совместимая сборка через `@openai/sites-vite-plugin`.
- D1 и R2 в frontend-проекте не подключены: оба значения в `.openai/hosting.json` равны `null`.

## Граница проектов

Архитектурное решение: frontend и backend разделяются с первого этапа.

```text
amra-merch-market-frontend/    frontend-репозиторий
amra-merch-market-backend/     существующий отдельный backend-репозиторий
```

Frontend отвечает за интерфейс, клиентскую навигацию, отображение данных и обращения к API. Backend владеет Catalog, Inventory, Identity, Customer/Cart, Pricing и Ordering.

Проекты взаимодействуют только через версионируемый HTTP API, например `/api/v1`. Контракт должен быть описан в OpenAPI; клиентские TypeScript-типы желательно генерировать из этого контракта, чтобы не поддерживать две независимые копии моделей вручную.

Backend не размещается внутри этого репозитория и не зависит от OpenAI Sites. Frontend может публиковаться через Sites и обращаться к отдельно развёрнутому API по HTTPS.

## Маршруты

| Маршрут | Назначение |
| --- | --- |
| `/` | Главная витрина и подборки |
| `/catalog/[category]` | Каталог категории; секция задаётся `?section=` |
| `/favorites` | Избранные товары |
| `/cart` | Корзина и управление количеством |
| `/checkout` | Контакты, адрес и атомарное оформление заказа |
| `/orders/[publicNumber]` | Восстанавливаемая страница подтверждённого заказа |
| `/account` | Демо-профиль, заказы и история |
| `/privacy` | Политика конфиденциальности |
| `/offer` | Публичная оферта |
| `/admin` | Рабочий обзор с учётом permissions сотрудника |
| `/admin/catalog` | Список и фильтрация товаров |
| `/admin/catalog/products/[id]` | Секционный редактор товара |
| `/admin/categories` | Дерево категорий |
| `/admin/collections` | Редакционные коллекции |
| `/admin/banners` | Баннеры главной: контент, переходы, изображения, порядок и публикация |
| `/admin/inventory` | Остатки, приёмка и сверка |

## Ключевые файлы

| Файл | Ответственность |
| --- | --- |
| `app/page.tsx` | Главная страница, подборки, слайдеры и мегабар |
| `app/styles/tokens.css` | Единые цвета, шрифты, радиусы и тени storefront/admin |
| `app/styles/base.css` | Минимальный глобальный reset и базовые element defaults |
| `app/globals.css` | Legacy-стили витрины; новые component styles сюда не добавляются |
| `app/layout.tsx` | Метаданные, шрифты и корневой `ShopStateProvider` |
| `app/components/StoreHeader.tsx` | Единый плавающий хедер и счётчики |
| `app/components/ShopState.tsx` | Корзина, избранное, суммы и синхронизация с `localStorage` |
| `app/components/ShopProductDialog.tsx` | Универсальное подробное окно товара |
| `app/components/ShopIcons.tsx` | Общие иконки избранного и кнопки корзины |
| `app/catalog/[category]/page.tsx` | URL state, фильтры, сортировка и пагинация каталога |
| `app/catalog/catalog-data.ts` | Временные типизированные fixtures оставшихся storefront-секций до API-интеграции |
| `app/api/generated/` | Не редактируемый вручную TypeScript-клиент canonical OpenAPI |
| `app/api/generated-manifest.json` | Версия API и SHA-256 контракта/артефакта |
| `app/storefront/banner-api.ts` | Публичный read model баннеров через generated client |
| `app/catalog/components/` | Карточка и подробный диалог каталога |
| `app/cart/page.tsx` | Корзина и изменение количества |
| `app/favorites/page.tsx` | Избранное |
| `app/account/page.tsx` | Профиль и история заказов |

## Состояние магазина

`ShopStateProvider` оборачивает приложение в `app/layout.tsx` и предоставляет:

- `cart`, `cartCount`, `cartTotal`;
- `favorites`, `favoriteCount`;
- `addToCart`, `removeFromCart`, `setQuantity`;
- `toggleFavorite`, `isFavorite`, `isInCart`.

Локальный ключ хранения: `amra-shop-state-v1`.

Позиция корзины сейчас идентифицируется только по `product.id`. Размер и другие варианты товара в модели `CartLine` не записываются. Это нужно учесть до создания серверной корзины.

## Модель товара

Общая клиентская модель `ShopProduct` содержит:

```ts
type ShopProduct = {
  id: string;
  name: string;
  price: number;
  art: string;
  colorClass: string;
};
```

Каталоговая страница использует расширенную локальную модель с описанием, цветом, материалом, размерами и признаком новинки, затем преобразует её в `ShopProduct` для корзины и избранного.

## Известный технический долг

- Данные товаров продублированы между главной и каталогом; нужен единый источник.
- В проекте есть два похожих компонента подробной карточки товара.
- Legacy-файл `app/globals.css` ещё объединяет несколько независимых storefront-поверхностей; миграция выполняется по компонентам с обязательным regression gate.
- Административное управление заказами, платёжные и доставочные интеграции ещё не реализованы.
- Нужна модель вариантов товара и перенос постоянного состояния с `localStorage` в отдельный backend перед запуском реальных заказов.
- Нужно определить URL API, CORS-политику, формат ошибок и способ аутентификации между frontend и backend.

## Проверка изменений

Минимальная проверка после правок:

```bash
npm run build
npm run typecheck
npm run lint
npm run e2e
```

При изменениях интерактивности дополнительно проверить: открытие карточки, выбор размера, добавление в корзину, переход по состоянию `В корзине · N`, лайк, счётчики хедера и внутренние маршруты.

## Локальная контейнерная среда

Полный developer stack принадлежит отдельному sibling-проекту `amra-merch-market-infra`. Его Compose собирает frontend и backend через Dockerfile соответствующих репозиториев. Локальный Node и Java для обычного запуска не требуются: `docker compose up --build` поднимает frontend, backend, две PostgreSQL, Keycloak и MinIO. Browser обращается к API через `http://localhost:8080`, поэтому OIDC redirect и presigned MinIO URL остаются достижимыми с host-машины.

## Конвенции frontend-кода

- Route component отвечает за URL state и композицию страницы, а не хранит большие fixture-наборы или универсальные UI-компоненты.
- Общие transport/domain-facing типы размещаются рядом с соответствующей feature boundary.
- Новый компонент получает локальный `*.module.css`; глобальный класс допустим только для действительно общей legacy-поверхности.
- Единство storefront и admin обеспечивают tokens, а не общие глобальные селекторы.
- Общие имена вроде `.active`, `.card` и `.button` не добавляются в глобальную область.
- Поведенческий рефакторинг, визуальное изменение и API-интеграция оформляются отдельными logical commits.
