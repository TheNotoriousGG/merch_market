import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    java
    checkstyle
    jacoco
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.9.0"
    id("net.ltgt.errorprone") version "5.1.0"
    id("net.ltgt.nullaway") version "3.1.0"
    id("org.sonarqube") version "7.3.1.8318"
    id("info.solidsoft.pitest") version "1.19.0"
    id("org.openapi.generator") version "7.22.0"
}

group = "ru.amra.market"
version = "0.1.0-SNAPSHOT"
description = "Production-ready backend for Amra Shop"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val openApiDiffCli =
    configurations.create("openApiDiffCli") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.modulith:spring-modulith-api")

    compileOnly("jakarta.annotation:jakarta.annotation-api")

    compileOnly("org.jspecify:jspecify:1.0.1")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.13.8")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
    testImplementation("org.springframework.boot:spring-boot-starter-session-jdbc-test")
    testImplementation(enforcedPlatform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.10.1")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.wiremock:wiremock:3.13.2")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    runtimeOnly("org.postgresql:postgresql")

    openApiDiffCli("org.openapitools.openapidiff:openapi-diff-cli:2.1.7")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.0")
    }
}

checkstyle {
    toolVersion = "14.0.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxErrors = 0
    maxWarnings = 0
}

jacoco {
    toolVersion = "0.8.15"
}

pitest {
    pitestVersion.set("1.25.9")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(setOf("ru.amra.market.*"))
    excludedClasses.set(
        setOf(
            "ru.amra.market.AmraMerchMarketBackendApplication",
            "ru.amra.market.platform.PlatformConfiguration",
            "ru.amra.market.identityaccess.SecurityConfiguration",
            "ru.amra.market.platform.generated.*",
        ),
    )
    threads.set(4)
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    mutationThreshold.set(80)
    coverageThreshold.set(80)
    failWhenNoMutations.set(false)
}

nullaway {
    onlyNullMarked.set(true)
    jspecifyMode.set(true)
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat("2.97.0")
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("projectFiles") {
        target("*.md", "*.yml", "*.yaml", "docs/**/*.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

sonar {
    properties {
        property("sonar.projectKey", "amra-merch-market-backend")
        property("sonar.projectName", "Amra Merch Market Backend")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.exclusions", "**/generated/**,**/*Application.java")
    }
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.errorprone.disableWarningsInGeneratedCode.set(true)
    options.errorprone.excludedPaths.set(".*/build/generated/.*")
    options.errorprone.nullaway {
        error()
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<Javadoc>().configureEach {
    exclude("ru/amra/market/platform/generated/**")
    val standardOptions = options as StandardJavadocDocletOptions
    standardOptions.encoding = "UTF-8"
    standardOptions.charSet = "UTF-8"
    standardOptions.addBooleanOption("Xdoclint:all,-missing", true)
}

val openApiSpec = layout.projectDirectory.file("src/main/openapi/openapi.yaml")
val generatedJavaDirectory = layout.buildDirectory.dir("generated/openapi/java")
val generatedTypeScriptDirectory = layout.buildDirectory.dir("generated/openapi/typescript")
val openApiContractTree = layout.projectDirectory.dir("src/main/openapi")

sourceSets.main {
    java.srcDir(generatedJavaDirectory.map { it.dir("src/main/java") })
    resources.srcDir("src/main/openapi")
}

val generateJavaApi =
    tasks.register<GenerateTask>("generateJavaApi") {
        group = "openapi tools"
        description = "Generates Spring API interfaces and transport models from the canonical OpenAPI contract."
        generatorName.set("spring")
        library.set("spring-boot")
        inputSpec.set(openApiSpec.asFile.absolutePath)
        inputs.dir(openApiContractTree)
        outputDir.set(generatedJavaDirectory.get().asFile.absolutePath)
        apiPackage.set("ru.amra.market.platform.generated.api")
        modelPackage.set("ru.amra.market.platform.generated.model")
        modelNameSuffix.set("Dto")
        globalProperties.set(
            mapOf(
                "apis" to "",
                "models" to "",
                "supportingFiles" to "ApiUtil.java",
            ),
        )
        configOptions.set(
            mapOf(
                "annotationLibrary" to "none",
                "documentationProvider" to "none",
                "interfaceOnly" to "true",
                "openApiNullable" to "false",
                "requestMappingMode" to "api_interface",
                "skipDefaultInterface" to "true",
                "useBeanValidation" to "true",
                "useJakartaEe" to "true",
                "useSpringBoot4" to "true",
                "useSpringBuiltInValidation" to "true",
                "useTags" to "true",
            ),
        )
        typeMappings.set(mapOf("OffsetDateTime" to "java.time.Instant"))
    }

val generateTypeScriptClient =
    tasks.register<GenerateTask>("generateTypeScriptClient") {
        group = "openapi tools"
        description = "Generates the frontend TypeScript Fetch client from the canonical OpenAPI contract."
        generatorName.set("typescript-fetch")
        inputSpec.set(openApiSpec.asFile.absolutePath)
        inputs.dir(openApiContractTree)
        outputDir.set(generatedTypeScriptDirectory.get().asFile.absolutePath)
        packageName.set("@amra-shop/api-client")
        configOptions.set(
            mapOf(
                "enumPropertyNaming" to "UPPERCASE",
                "npmName" to "@amra-shop/api-client",
                "npmVersion" to project.version.toString().removeSuffix("-SNAPSHOT"),
                "supportsES6" to "true",
                "typescriptThreePlus" to "true",
                "withInterfaces" to "true",
            ),
        )
    }

tasks.named<ValidateTask>("openApiValidate") {
    group = "verification"
    description = "Validates the canonical OpenAPI contract."
    inputSpec.set(openApiSpec.asFile.absolutePath)
    inputs.dir(openApiContractTree)
    recommend.set(false)
    treatWarningsAsErrors.set(false)
}

val openApiBaselineDirectory = layout.buildDirectory.dir("openapi-baseline")
val openApiBaselineSpec = openApiBaselineDirectory.map { it.file("src/main/openapi/openapi.yaml") }
val openApiCandidateSpec =
    providers.gradleProperty("openapiCandidate").orElse(openApiSpec.asFile.absolutePath)

val prepareOpenApiBaseline =
    tasks.register<Exec>("prepareOpenApiBaseline") {
        group = "verification"
        description = "Extracts the canonical OpenAPI contract from the local main branch when available."
        outputs.dir(openApiBaselineDirectory)
        inputs.dir(openApiContractTree)
        inputs.property(
            "mainCommit",
            providers
                .exec {
                    commandLine("git", "rev-parse", "main")
                    isIgnoreExitValue = true
                }.standardOutput.asText
                .map(String::trim),
        )
        commandLine(
            "ci/prepare-openapi-baseline.sh",
            openApiBaselineDirectory.get().asFile.absolutePath,
        )
    }

val checkOpenApiCompatibility =
    tasks.register<JavaExec>("checkOpenApiCompatibility") {
        group = "verification"
        description = "Fails when the candidate contract breaks the OpenAPI contract on main."
        dependsOn(prepareOpenApiBaseline)
        classpath = openApiDiffCli
        mainClass.set("org.openapitools.openapidiff.cli.Main")
        args(
            openApiBaselineSpec.get().asFile.absolutePath,
            openApiCandidateSpec.get(),
            "--fail-on-incompatible",
        )
        inputs.file(openApiCandidateSpec)
    }

val packageTypeScriptClient =
    tasks.register<Zip>("packageTypeScriptClient") {
        group = "build"
        description = "Packages the generated TypeScript client as a local immutable build artifact."
        dependsOn(generateTypeScriptClient)
        from(generatedTypeScriptDirectory)
        exclude(".openapi-generator/**", ".openapi-generator-ignore")
        archiveBaseName.set("amra-shop-api-client")
        archiveVersion.set(project.version.toString().removeSuffix("-SNAPSHOT"))
    }

tasks.compileJava {
    dependsOn(generateJavaApi)
}

tasks.compileTestJava {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    dependsOn(tasks.named("openApiValidate"))
}

tasks.checkstyleMain {
    exclude("**/generated/**")
}

val coverageClasses =
    provider {
        sourceSets.main.get().output.asFileTree.matching {
            exclude("**/*Application.class", "**/generated/**")
        }
    }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClasses)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(checkOpenApiCompatibility)
    dependsOn(tasks.named("openApiValidate"))
    dependsOn(packageTypeScriptClient)
    dependsOn(tasks.javadoc)
    dependsOn(tasks.jacocoTestReport)
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(tasks.spotlessCheck)
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs all local engineering quality gates."
    dependsOn(tasks.check)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.bootJar {
    archiveFileName.set("application.jar")
}

tasks.jar {
    enabled = false
}
