import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("jvm") version "1.9.22"
}

group = "no.nav.helse.flex"
version = "1.0.0"
description = "spinnsyn-backend"
java.sourceCompatibility = JavaVersion.VERSION_21

ext["okhttp3.version"] = "4.9.3" // Token-support tester trenger Mockwebserver.

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

val testContainersVersion = "1.19.3"
val logstashLogbackEncoderVersion = "7.4"
val kluentVersion = "1.73"
val tokenSupportVersion = "3.2.0"
val sykepengesoknadKafkaVersion = "2024.01.25-15.27-ee3bd993"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.slf4j:slf4j-api")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.postgresql:postgresql")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")
    implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    testImplementation(platform("org.testcontainers:testcontainers-bom:$testContainersVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    this.archiveFileName.set("app.jar")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")

        if (System.getenv("CI") == "true") {
            allWarningsAsErrors.set(true)
        }
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("STARTED", "PASSED", "FAILED", "SKIPPED")
        exceptionFormat = FULL
    }
    failFast = false
}
