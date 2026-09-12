# Аудит контекста frontend и backend

Статус: выполнен 19 августа 2026 года.

## Цель и область проверки

Аудит проверяет, можно ли по Markdown-файлам восстановить фактическое состояние проекта между сессиями и безопасно начать следующий этап. Проверены:

- обязательные backend-файлы `AGENTS.md`, `ENGINEERING_CHARTER.md`, `DECISION_REGISTER.md`, `IMPLEMENTATION_PLAN.md` и `PROJECT_STATUS.md`;
- backend README, developer guide, ADR, requirements, release checklist и frontend handoff;
- фактические Git, Gradle, OpenAPI, Flyway, test и build artifacts;
- frontend README, `AGENTS.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, package configuration и фактические data flows;
- все локальные Markdown-ссылки backend и все commit references из `PROJECT_STATUS.md`.

## Подтверждённые факты backend

| Проверка | Результат |
| --- | --- |
| Репозиторий | `amra-merch-market-backend` |
| Основная ветка | чистая `main` на `4192ce4` до начала этого документационного изменения |
| Завершённые product slices | Catalog, Inventory |
| Следующий разрешённый product stage | этап 9 — Customer, favorites и cart |
| Runtime/build | Java 25, Spring Boot 4.1.0, Gradle Wrapper 9.6.1 |
| Persistence | PostgreSQL 18.4, Flyway V1–V10 |
| HTTP contract | modular OpenAPI 3.0.3, 24 operation IDs |
| Последний принятый test result | 173 tests, 0 skipped, 0 failures, 0 errors |
| JaCoCo | 871/1 220 branches и 3 732/3 956 lines |
| JAR | `build/libs/application.jar`, SHA-256 `096976adf5e77249a7e7a8f22331b6b604e67d67567c12378c4f8ac4c79bc086` |
| Markdown links | отсутствующие локальные targets не найдены |
| Status commit references | все 9 сокращённых commit hashes существуют |

Значения test/coverage/JAR относятся к последнему закрытому Inventory acceptance. Документационные изменения этого аудита не объявляют новый product acceptance.

## Найденные расхождения backend

### Исправляются этим документационным изменением

1. `PROJECT_STATUS.md` называл раздел Inventory «Следующим этапом» и говорил, что этап 8 выполняется, хотя тот же раздел фиксировал завершение 12/12 и merge в `main`.
2. `PROJECT_STATUS.md` не давал короткого однозначного ответа, какие этапы 0–15 завершены, какой следующий и какие отложены.
3. `IMPLEMENTATION_PLAN.md` подробно описывал stages, но не содержал единой status matrix.
4. README и developer guide описывали backend local start, но не отделяли его от ещё не существующего полного frontend+backend local workflow.
5. Не было одного документа, связывающего backend stages с последовательной заменой frontend fixtures.

### Зафиксированы как integration blockers

1. Logout существует в Spring Security как `POST /api/v1/session/logout` и описан в security guide, но отсутствует в canonical OpenAPI. До frontend-интеграции он должен стать contract-first operation.
2. OIDC login использует saved-request behavior, но безопасный возврат после прямого входа с frontend на `http://localhost:3001` не зафиксирован отдельным integration contract/test.
3. Local Keycloak realm намеренно не содержит пользователей; воспроизводимого local identity bootstrap пока нет.
4. Flyway намеренно не содержит demo catalog data; воспроизводимого local seed через application/admin boundaries пока нет.
5. Catalog возвращает media delivery URL, но local media fixture service/route не определён.
6. Catalog contract намеренно не содержит цену. Полная товарная карточка, Sale и сумма корзины не могут стать backend-driven до этапа 10 Pricing.
7. Customer profile, favorites и cart отсутствуют до этапа 9; checkout/orders — до этапа 11.

## Состояние frontend-контекста

Frontend фактически остаётся рабочим интерактивным прототипом:

- catalog/home data захардкожены в React-файлах;
- cart/favorites находятся в `ShopStateProvider` и `localStorage`;
- профиль и история заказов демонстрационные;
- вызовов backend `/api/v1` в application code нет;
- `app/chatgpt-auth.ts` присутствует как Sites helper, но storefront его не использует;
- API base URL и generated TypeScript client в frontend не подключены.

При этом frontend-контекст устарел:

- `PROJECT_CONTEXT.md` утверждает, что реального backend нет и его stack ещё не выбран;
- `ARCHITECTURE.md` называет backend будущим и считает API/CORS/auth нерешёнными;
- `AGENTS.md` использует формулировки «будущий backend» и «после создания проекта»;
- список следующих этапов всё ещё предлагает спроектировать уже созданные backend и OpenAPI.

Frontend working tree на момент аудита содержит существующие modified/untracked files. Они не изменялись из backend-задачи, чтобы не перезаписать пользовательскую работу. Актуализация frontend context должна быть отдельным первым frontend commit после сверки этого dirty state.

## Проверка frontend build и tests

Чтобы не менять dirty frontend tree, проверка выполнена из read-only source mount в отдельной временной Node 22/Linux-среде.

- `npm ci` по committed `package-lock.json` завершился;
- `npm run build` успешно собрал все семь маршрутов;
- существующий `tests/rendered-html.test.mjs` не соответствует текущему магазину: оба теста всё ещё ожидают starter loading skeleton, `codex-preview` metadata и удалённый `app/_sites-preview/preview.css`; результат — 0/2;
- `npm audit` сообщил 20 dependency findings: 1 low, 4 moderate и 15 high, без critical;
- среди direct dependencies с findings находятся `@cloudflare/vite-plugin`, `drizzle-kit`, `react-server-dom-webpack`, `vinext`, `vite` и `wrangler`; автоматический `audit fix --force` не выполнялся, потому что совместимость upgrades должна проверяться отдельным maintenance change.

Следовательно, frontend компилируется, но его test и supply-chain gates сейчас не зелёные. До начала API wiring требуется отдельный frontend cleanup: заменить starter tests реальными storefront assertions и провести управляемый dependency upgrade/triage.

## Что признано корректным

- Названия и физическое разделение frontend/backend проектов соответствуют принятому решению.
- Java/Spring/Gradle/PostgreSQL/Keycloak/OpenAPI versions согласованы с build и configuration.
- Catalog и Inventory requirements, invariants и release checklists соответствуют реализованным migrations/API/tests.
- Generated Java/TypeScript ownership и запрет ручного редактирования описаны последовательно.
- GitLab activation, production platform, Secret Manager и real providers явно отложены, а не ошибочно объявлены готовыми.
- Developer guide корректно отделяет реализованные modules от зарезервированных packages.

## Проверка изменений аудита

- backend `qualityGate` завершился успешно;
- OpenAPI compatibility: differences отсутствуют;
- `git diff --check` не нашёл whitespace errors;
- повторная проверка Markdown links не нашла отсутствующих локальных targets;
- frontend source files не изменялись; build/test выполнялись только во временной директории.

## Результат аудита

Backend-контекст после исправления статуса пригоден для продолжения с этапа 9. Полноценное использование сайта требует не одного «подключения API», а последовательного integration track, описанного в [плане frontend/backend-интеграции](FRONTEND_BACKEND_INTEGRATION_PLAN.md). До его утверждения product implementation не начинается.
