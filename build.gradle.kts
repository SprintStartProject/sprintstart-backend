plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.2.20"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.1"
    id("jacoco")
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.sprintstart"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// The module API declarations (`package-info.java` with @NamedInterface/@ApplicationModule) sit
// next to the Kotlin code. Without this, javac never compiles them and Spring Modulith sees none.
sourceSets {
    main {
        java.srcDir("src/main/kotlin")
    }
}

repositories {
    mavenCentral()
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
    baseline = file("$rootDir/detekt-baseline.xml")
}

extra["springModulithVersion"] = "2.0.6"

val pitVersion = "1.30.0"
val pitPlugins by sourceSets.creating

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.boot:spring-boot-starter-aop:4.0.0-M2")
    implementation("org.jsoup:jsoup:1.7.2")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("com.lemonappdev:konsist:0.18.1")
    testImplementation("com.networknt:json-schema-validator:1.5.9")
    // PIT's JUnit plugin would bring launcher 1.x, which cannot run Spring Boot 4's JUnit 6 engine.
    pitest("org.junit.platform:junit-platform-launcher:6.0.3")
    "pitPluginsCompileOnly"("org.pitest:pitest-entry:$pitVersion")
    pitest(pitPlugins.output)
    testImplementation(pitPlugins.output)
    testImplementation("org.pitest:pitest-entry:$pitVersion")
    testImplementation("com.h2database:h2")
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        // A warning fails the build rather than joining a pile nobody reads.
        allWarningsAsErrors.set(true)
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "2g"
}

// Mutation testing: `bash scripts/quality/mutation.sh` runs it on the classes a branch
// changed and fails below MUTATION_MIN. Run it directly with -PpitTargets=com.example.Foo,com.example.Bar.
pitest {
    pitestVersion.set(pitVersion)
    junit5PluginVersion.set("1.2.3")
    addJUnitPlatformLauncher.set(false)
    targetClasses.set(
        providers.gradleProperty("pitTargets").map { it.split(",") }.orElse(listOf("com.sprintstart.*")),
    )
    // Every test may kill a mutant; PIT picks the ones that cover it. The default would only run tests
    // whose names match the mutated classes, and miss e.g. a controller test exercising a service.
    targetTests.set(listOf("com.sprintstart.*"))
    // -PpitLocalExcludedTests=... is for local runs only, e.g. GitChangeSetTest on Windows, where a file
    // name cannot hold a tab; CI never sets it, so the gate there always runs against the whole suite.
    val localExclusions = providers.gradleProperty("pitLocalExcludedTests").map { it.split(",") }.orElse(emptyList())
    excludedTestClasses.set(
        localExclusions.map {
            listOf(
                "*IntegrationTest",
                "*ApplicationTests",
                "*.architecture.*",
                "*.contracts.*",
            ) + it
        },
    )
    // Every core on CI; a local run passes -PpitThreads=4 to keep the machine usable meanwhile.
    threads.set(
        providers.gradleProperty("pitThreads").map { it.toInt() }.orElse(Runtime.getRuntime().availableProcessors()),
    )
    outputFormats.set(listOf("XML", "HTML"))
    timestampedReports.set(false)
    verbose.set(providers.gradleProperty("pitVerbose").map { it.toBoolean() }.orElse(false))
    exportLineCoverage.set(false)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            limit {
                minimum = "0.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
