## Цель

<!-- Какой законченный результат даёт merge request? -->

## Границы изменения

- Входит:
- Не входит:

## Решения и риски

- ADR/decision register:
- Failure modes:
- Migration/rollback:

## Проверки

- [ ] `./gradlew qualityGate`
- [ ] Применимые integration/contract/migration tests
- [ ] PIT для изменённой critical domain logic
- [ ] Generated и migration diff проверены
- [ ] Нет secrets, PII или unrelated changes

## API и данные

- [ ] OpenAPI backward compatibility проверена или не применимо
- [ ] Database expand/contract соблюдён или не применимо
- [ ] Idempotency/concurrency рассмотрены или не применимо

## Документация

- [ ] `PROJECT_STATUS.md` обновлён
- [ ] Decision register/ADR обновлены или изменение не требует этого
- [ ] Runbook/operational notes обновлены или не применимо

## Review notes

<!-- На какие trade-offs, invariants и строки reviewer должен обратить особое внимание? -->
