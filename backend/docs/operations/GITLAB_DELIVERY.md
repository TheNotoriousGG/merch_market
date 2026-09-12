# GitLab delivery и supply chain

Статус: спроектировано; активация временно отложена владельцем по ADR-0002.

Обновлено: 19 августа 2026 года.

## Pipeline

`.gitlab-ci.yml` создаёт один pipeline на commit: merge request pipeline заменяет branch pipeline при открытом MR, поэтому один и тот же commit не проверяется параллельно двумя расходящимися конфигурациями.

| Stage | Blocking checks и artifact |
| --- | --- |
| `lint` | Spotless, Checkstyle, Error Prone/NullAway compilation, Javadoc doclint и Hadolint |
| `test` | JUnit/architecture tests, JaCoCo report/thresholds и PIT |
| `integration` | `integrationTest`, когда в repository появляется `src/integrationTest` |
| `build` | Один reproducible `application.jar` и его SHA-256 |
| `container` | Rootless BuildKit, commit-SHA image, SPDX SBOM и SLSA provenance |
| `security` | GitLab SAST, secret, dependency и container scanning |
| `smoke` | Запуск того же commit-SHA image и проверка actuator health |

Gradle, BuildKit, Hadolint, curl, extractor и distroless runtime images закреплены multi-architecture digest. Обновление любого digest выполняется отдельным maintenance MR после проверки provenance, release notes, сканирования и полного pipeline.

Dependency Scanning v2 читает committed `gradle.lockfile`; встроенный Gradle resolution job отключён, чтобы scanner не разрешал иной dependency graph старым Gradle/JDK. BuildKit получает только уже проверенный `build/libs/application.jar`, а не компилирует source повторно.

## Требования к GitLab

- GitLab 19.x или совместимая версия, в которой `Dependency-Scanning.v2` является GA.
- GitLab Ultimate для встроенных application security reports, merge request security approval policy и immutable container tags.
- Linux Docker/Kubernetes runner, разрешающий необходимые rootless BuildKit user namespaces без unrestricted privileged Docker daemon.
- GitLab Container Registry и metadata database включены.
- Runner clock синхронизирован; outbound TLS разрешён только к approved package/image registries и GitLab services.

Если выбранный GitLab tier не предоставляет обязательный gate, MR блокируется до принятого ADR о self-managed эквиваленте. Молчаливое `allow_failure`, отключение scanner или публикация без отчёта запрещены.

## Обязательные project settings

### Repository и merge requests

1. Default branch: `main`.
2. Branch rule `main`: `Allowed to push and merge = No one`, force push запрещён, merge — Maintainers после approvals.
3. Merge method: `Fast-forward merge`; automatic rebase разрешён; squash по умолчанию выключен, чтобы сохранить logical commits.
4. Включить `Pipelines must succeed`, `All threads must be resolved` и удаление source branch после merge.
5. Минимум один независимый approval; автор и авторы commits не подтверждают собственный MR; approvals сбрасываются новым commit.
6. Protected Git tags `v*`: создавать могут Maintainers; release tags — annotated и signed.
7. Minimum role to use pipeline variables: `No one allowed`; изменения поведения проходят только versioned CI configuration/typed inputs.

### Security policy

После первого успешного default-branch pipeline включить merge request approval policy:

- scanners: SAST, secret detection, dependency scanning и container scanning;
- `vulnerabilities_allowed: 0` для новых `critical` и `high` findings;
- state: `new_needs_triage`;
- минимум один approval AppSec/maintainer для документированного risk acceptance;
- missing scanner report трактуется fail-closed;
- author/commit-author approval запрещён, approvals сбрасываются новым commit.

Scanner job failure блокирует pipeline. Сами security findings оцениваются approval policy, поскольку GitLab scanner report и exit code решают разные задачи.

### Container Registry

1. Включить immutable tag rule `^[0-9a-f]{40}$` для commit-SHA tags.
2. Добавить immutable rule для release tags `^v[0-9]+\.[0-9]+\.[0-9]+([.-].*)?$`.
3. Запретить ручную публикацию в production repository; publish выполняет только protected CI identity.
4. Deployment принимает `IMAGE_REFERENCE` вида `repository@sha256:...`, а не mutable tag.
5. Cleanup policy не затрагивает immutable release tags и активные deployment digests.

## CI credentials

Pipeline использует только predefined short-lived `CI_REGISTRY_USER`/`CI_REGISTRY_PASSWORD`. Новые credentials создаются как masked, protected и environment-scoped variables; значения не передаются через build arguments, artifacts или logs. BuildKit provenance работает в `mode=max`, поэтому build arguments могут содержать только публичные metadata.

## Promotion и rollback

Результат `container-build` — commit-SHA tag, digest, SBOM и provenance. После security и smoke gates release/deployment должен продвигать этот digest без rebuild. Rollback выбирает ранее проверенный digest из release metadata; повторная сборка старого commit не считается rollback.

До появления production orchestrator pipeline заканчивается smoke gate и не выполняет deployment. Добавление promotion/deployment требует отдельного ADR с environments, approvals, concurrency lock и rollback runbook.

## Локальная проверка

```shell
./gradlew clean qualityGate pitest
./gradlew bootJar
sh ci/verify-supply-chain.sh
docker build --pull -t amra-merch-market-backend:local .
```

После сборки образ проверяется с read-only root filesystem, dropped Linux capabilities и `no-new-privileges`; оркестратор обязан сохранить эти ограничения.
