# ADR-0002: Отложить активацию GitLab для local-only разработки

- Статус: принято
- Дата: 2026-08-19
- Владельцы решения: владелец продукта, backend engineering
- Заменяет: не применимо

## Контекст

GitLab CI, Container Registry и project protection полностью спроектированы в этапе 3, однако shared GitLab project, remote и runner ещё не подключены. Владелец проекта решил продолжить локальную contract-first разработку и вернуться к GitLab позднее.

Без явного исключения утверждённый последовательный план блокировал бы OpenAPI foundation. Полное удаление CI gate создало бы риск расхождения local и remote delivery.

## Решение

Считать локальную часть этапа 3 достаточной для перехода к следующим local-only этапам при одновременном выполнении условий:

- repository не имеет shared remote и не используется другими разработчиками;
- image, package, release и deployment никуда не публикуются;
- каждый logical block проходит все доступные local quality gates;
- `.gitlab-ci.yml`, Dockerfile и delivery documentation сохраняются и не ослабляются;
- активация GitLab 19.x compatible project, runner, Registry и protection settings обязательна до первого shared remote, release или deployment;
- после активации полный pipeline должен пройти до продолжения любой release-oriented работы.

## Рассмотренные альтернативы

### Остановить разработку до подключения GitLab

Наиболее строго соблюдает исходный gate, но не даёт продуктовой ценности, пока владелец сознательно не готов заниматься инфраструктурой.

### Удалить GitLab этап и вернуться к CI перед production

Отклонено: слишком поздно обнаружит несовместимость pipeline, Registry и generated artifacts и позволит незаметно размыть delivery requirements.

## Последствия

Положительные:

- OpenAPI и последующие локальные foundations можно разрабатывать сейчас;
- готовая CI configuration остаётся частью repository;
- точка обязательной активации сформулирована fail-closed.

Отрицательные и риски:

- до активации не доказаны GitLab template compatibility, security reports и Registry immutability;
- local fast-forward не является эквивалентом independent review;
- накопленные этапы могут потребовать исправлений при первом remote pipeline.

## Migration и rollback

Для завершения исключения подключить GitLab remote/runner, применить `docs/operations/GITLAB_DELIVERY.md`, запустить pipeline на актуальном `main` и устранить все расхождения. Если local gates перестают воспроизводить заявленный pipeline, дальнейшие этапы останавливаются до восстановления эквивалентности.

## Критерии пересмотра

Исключение прекращается при любом из событий:

- добавлен shared remote;
- в проект входит второй разработчик;
- требуется публикация package/image;
- начинается release, staging или deployment.

## Проверка соблюдения

- `PROJECT_STATUS.md` сохраняет GitLab activation как deferred обязательство;
- `git remote -v` остаётся пустым в local-only режиме;
- local `qualityGate`, `pitest`, supply-chain policy, container build и smoke выполняются перед слиянием этапа;
- release/deployment automation не добавляется до закрытия исключения.
