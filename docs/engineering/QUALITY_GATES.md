# Локальные quality gates

Статус: обязательны для каждой feature-ветки.

Обновлено: 19 августа 2026 года.

## Основная команда

```shell
./gradlew qualityGate
```

Задача выполняет:

1. Spotless check для Java, Gradle Kotlin DSL и project text files;
2. compilation на Java 25 с Error Prone 2.50.0 и NullAway 0.13.8 в JSpecify mode;
3. Checkstyle 14.0.0 для main и test sources;
4. Javadoc doclint без требования бессодержательного Javadoc для каждого очевидного public element;
5. JUnit Platform 6.0.3 tests, включая Spring Modulith и ArchUnit verification;
6. JaCoCo 0.8.15 report и thresholds: line ≥80%, branch ≥70%.
7. OpenAPI semantic validation, local project-policy tests и backward-compatibility comparison с `main`;
8. generation Java transport contracts и локальная упаковка TypeScript Fetch client.

После появления persistence foundation test gate требует работающий Docker daemon и запускает PostgreSQL 18.4 Testcontainer. Замена реальной базы на H2 для обхода этого требования запрещена.

Mutation testing запускается отдельно:

```shell
./gradlew pitest
```

PIT использует version 1.25.9 и JUnit Platform plugin 1.2.3. В foundation `failWhenNoMutations=false`, потому что business code ещё отсутствует. Для critical domain modules отсутствие mutations и mutation score ниже 80% становятся blocking condition в той же feature-ветке, где появляется module code. Чистые Spring `@Configuration` wiring-классы исключаются из mutation scope и проверяются context/integration tests; security policies, claims mapping, filters и application behaviour из PIT не исключаются.

## Исправление formatting

```shell
./gradlew spotlessApply
```

Auto-format не должен смешиваться с behavioural changes в одном commit без необходимости.

## Dependency integrity

Lock state и checksums являются committed artifacts:

- `gradle.lockfile`;
- `gradle/verification-metadata.xml`;
- `gradle/wrapper/gradle-wrapper.properties` с distribution checksum.

При намеренном изменении dependencies:

```shell
./gradlew qualityGate --write-locks --write-verification-metadata sha256
```

Generated diff проверяется до commit. Blind trust-all metadata и отключение verification запрещены.

## Fault-injection matrix

| Gate | Контрольное нарушение | Ожидаемый результат |
| --- | --- | --- |
| Spotless | Неформатированный Java/Kotlin DSL source | `spotlessCheck` падает и показывает target file |
| Checkstyle | `System.out`, wildcard import или отсутствующие braces | `checkstyleMain`/`checkstyleTest` падает с rule name |
| Error Prone | Заведомо ошибочный pattern, поддерживаемый Error Prone | `compileJava` падает с checker name |
| NullAway | Dereference `@Nullable` внутри `@NullMarked` package | `compileJava` падает с `NullAway` |
| Javadoc | Некорректный HTML/tag в documented API | `javadoc` падает через doclint |
| Unit/property test | Намеренно неверный assertion/property | `test` падает и сохраняет report |
| Spring Modulith | Cycle или обращение к internal package другого module | architecture test падает с dependency path |
| ArchUnit layers | Domain зависит от Spring/JPA/infrastructure | architecture test падает с violating classes |
| JaCoCo | Uncovered production class снижает threshold | `jacocoTestCoverageVerification` падает с actual ratio |
| PIT | Выжившие mutations снижают critical-module score | `pitest` падает ниже configured threshold |
| OpenAPI validation | Неразрешимый `$ref` или некорректная schema | `openApiValidate`/policy test падает |
| OpenAPI compatibility | Удаление `GET /api/v1/` из candidate contract | `checkOpenApiCompatibility` падает с incompatible change log |

Эта матрица повторяется при значимом изменении build logic. В CI stage соответствующие gates будут разделены на диагностируемые jobs, но локальная `qualityGate` остаётся обязательной.

## Допустимые исключения

Suppression разрешается только узко: конкретный checker, минимальный scope, причина в comment/Javadoc и ссылка на ADR/issue, если исключение устойчивое. Глобальное отключение checker запрещено без изменения decision register.
