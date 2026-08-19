import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

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
}

group = "ru.amra.market"
version = "0.1.0-SNAPSHOT"
description = "Production-ready backend for Amra Shop"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    compileOnly("org.jspecify:jspecify:1.0.1")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.13.8")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    val standardOptions = options as StandardJavadocDocletOptions
    standardOptions.encoding = "UTF-8"
    standardOptions.charSet = "UTF-8"
    standardOptions.addBooleanOption("Xdoclint:all,-missing", true)
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
