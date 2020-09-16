import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.3.3"
val jacksonVersion = "2.11.2"
val javaxActivationVersion = "1.1.1"
val kafkaEmbeddedVersion = "2.4.0"
val postgresEmbeddedVersion = "0.13.3"
val kluentVersion = "1.49"
val ktorVersion = "1.3.2"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "5.1"
val mockkVersion = "1.10.0"
val nimbusdsVersion = "7.5.1"
val prometheusVersion = "0.6.0"
val smCommonVersion = "1.7cb158e"
val spekVersion = "2.0.9"
val testContainerKafkaVersion = "1.12.5"
val confluentVersion = "5.5.1"

val postgresVersion = "42.2.15"
val flywayVersion = "6.5.4"
val hikariVersion = "3.4.5"
val brukernotifikasjonAvroVersion = "1.2020.08.13-13.50-3b6ca1881161"

val ktlint by configurations.creating

val outputDir = "${project.buildDir}/reports/ktlint/"
val inputFiles = project.fileTree(mapOf("dir" to "src", "include" to "**/*.kt"))

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
}

plugins {
    id("org.jmailen.kotlinter") version "2.1.1"
    kotlin("jvm") version "1.3.70"
    id("com.diffplug.gradle.spotless") version "3.23.1"
    id("com.github.johnrengelman.shadow") version "4.0.4"
    id("com.github.ben-manes.versions") version "0.29.0"
    jacoco
}

buildscript {
    dependencies {
        classpath("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
        classpath("org.glassfish.jaxb:jaxb-runtime:2.4.0-b180830.0438")
        classpath("com.sun.activation:javax.activation:1.2.0")
    }
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    ktlint("com.pinterest:ktlint:0.37.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")

    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")

    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("com.github.navikt:brukernotifikasjon-schemas:$brukernotifikasjonAvroVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }

    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")
}

val ktlintCheck by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    outputs.dir(outputDir)

    description = "Check Kotlin code style."
    classpath = ktlint
    main = "com.pinterest.ktlint.Main"
    args = listOf("src/**/*.kt")
}

val ktlintFormat by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    outputs.dir(outputDir)

    description = "Fix Kotlin code style deviations."
    classpath = ktlint
    main = "com.pinterest.ktlint.Main"
    args = listOf("-F", "src/**/*.kt")
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }
}

tasks {

    create("printVersion") {
        println(project.version)
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "12"
    }

    withType<JacocoReport> {
        classDirectories.setFrom(
            sourceSets.main.get().output.asFileTree.matching {
                exclude()
            }
        )
    }
    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }

    "check" {
        dependsOn("formatKotlin")
    }
}

tasks.named("dependencyUpdates", DependencyUpdatesTask::class.java).configure {
    // optional parameters
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}
